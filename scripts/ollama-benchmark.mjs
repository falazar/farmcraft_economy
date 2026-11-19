import { createReadStream } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import { createInterface } from "node:readline";
import { parseArgs } from "node:util";

const { values } = parseArgs({
  options: {
    models: { type: "string", default: "llama3.2:3b,qwen2.5:0.5b" },
    "prompt-file": { type: "string" },
    log: { type: "string" },
    npc: { type: "string" },
    message: { type: "string" },
    runs: { type: "string", default: "3" },
    "timeout-seconds": { type: "string", default: "120" },
    think: { type: "string" },
    output: { type: "string" },
  },
});
const baseUrl = "http://127.0.0.1:11434";

async function api(path, body, timeout) {
  const response = await fetch(`${baseUrl}/api/${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeout),
  });
  if (!response.ok) throw new Error(`Ollama HTTP ${response.status}: ${await response.text()}`);
  const json = await response.json();
  if (json.error) throw new Error(`Ollama: ${json.error}`);
  return json;
}

async function loggedPrompt(path, npc) {
  let prompt = null;
  let collecting = false;
  const lines = createInterface({ input: createReadStream(path), crlfDelay: Infinity });
  for await (const line of lines) {
    if (/^\[\d{2}:\d{2}:\d{2}\]/.test(line)) {
      collecting = line.includes("[NPC Prompt]")
        && line.includes(`npc=${npc},`);
      if (collecting) prompt = [];
    } else if (collecting) {
      prompt.push(line);
    }
  }
  if (!prompt?.join("").trim()) throw new Error(`No logged NPC prompt found for ${npc}`);
  return prompt.join("\n").trimEnd();
}

function seconds(nanoseconds) {
  if (typeof nanoseconds !== "number" || !Number.isFinite(nanoseconds) || nanoseconds < 0) {
    throw new Error("Missing or invalid Ollama duration metrics");
  }
  return Math.round(nanoseconds / 1e6) / 1000;
}

async function main() {
  const runs = Number(values.runs);
  const timeout = Number(values["timeout-seconds"]) * 1000;
  if (values.think !== undefined && !["true", "false"].includes(values.think)) {
    throw new Error("--think must be true or false (omit to use the model default)");
  }
  const think = values.think === undefined ? undefined : values.think === "true";
  if (!Number.isInteger(runs) || runs < 1 || runs > 20
      || !Number.isInteger(timeout) || timeout < 1000 || timeout > 600000) {
    throw new Error("Use 1-20 runs and a timeout of 1-600 seconds");
  }
  if (Boolean(values["prompt-file"]) === Boolean(values.log) || !values.message
      || (values.log && !values.npc)) {
    throw new Error("Provide --message and either --prompt-file or --log with --npc");
  }
  const promptText = values.log
    ? await loggedPrompt(values.log, values.npc)
    : await readFile(values["prompt-file"], "utf8");
  const prompt = promptText.replace(/\r\n/g, "\n").trimEnd();
  if (!prompt.trim()) throw new Error("System prompt is empty");
  const models = values.models.split(",").map(model => model.trim());
  if (models.some(model => !model) || new Set(models).size !== models.length) {
    throw new Error("Model names must be nonempty and unique");
  }
  const installed = await api("tags", undefined, 5000);
  for (const name of models) {
    const model = installed.models.find(model => model.name === name);
    if (!model) throw new Error(`Model is not installed: ${name}`);
    if (model.remote_host || model.remote_model || name.endsWith("-cloud") || name.endsWith(":cloud")) {
      throw new Error(`Refusing cloud model; NPC prompts must stay local: ${name}`);
    }
  }
  const report = {
    startedAt: new Date().toISOString(),
    promptCharacters: prompt.length,
    message: values.message,
    thinking: think ?? "model-default",
    conditions: "Sequential, non-streaming production request shape; identical prompt repeated. "
      + "First run is not forced cold; subsequent runs can benefit from prompt caching. "
      + "No models are forcibly unloaded, no game configuration is changed. "
      + "42 seconds is the new limit; an older running JAR may still use 30.",
    results: [],
  };
  console.log(`System prompt: ${prompt.length} characters. ${report.conditions}`);
  for (const model of models) {
    const loaded = await api("ps", undefined, 5000);
    for (let run = 1; run <= runs; run++) {
      const start = performance.now();
      const result = { model, run, initiallyLoaded: loaded.models.some(item => item.name === model) };
      try {
        const reply = await api("chat", {
          model,
          stream: false,
          ...(think === undefined ? {} : { think }),
          messages: [
            { role: "system", content: prompt },
            { role: "user", content: values.message },
          ],
        }, timeout);
        result.wallSeconds = Math.round(performance.now() - start) / 1000;
        if (reply.done !== true || !reply.message?.content?.trim()) {
          throw new Error("Ollama did not return a completed, nonempty reply");
        }
        result.loadSeconds = seconds(reply.load_duration);
        result.promptSeconds = seconds(reply.prompt_eval_duration);
        result.generationSeconds = seconds(reply.eval_duration);
        result.promptTokens = reply.prompt_eval_count;
        result.outputTokens = reply.eval_count;
        result.tokensPerSecond = result.generationSeconds > 0
          ? Math.round(reply.eval_count / result.generationSeconds * 100) / 100 : null;
        result.within30Seconds = result.wallSeconds <= 30;
        result.within42Seconds = result.wallSeconds <= 42;
        result.doneReason = reply.done_reason;
        result.reply = reply.message.content;
        result.thinkingCharacters = reply.message.thinking?.length ?? 0;
        console.log(`${model} #${run}: ${result.wallSeconds}s `
          + `(load ${result.loadSeconds}s, prompt ${result.promptSeconds}s, `
          + `generation ${result.generationSeconds}s); <=42s=${result.within42Seconds}\n${result.reply}`);
      } catch (error) {
        result.wallSeconds = Math.round(performance.now() - start) / 1000;
        result.error = `${error.name}: ${error.message}`;
        console.error(`${model} #${run} FAILED after ${result.wallSeconds}s: ${result.error}`);
      }
      report.results.push(result);
      if (values.output) await writeFile(values.output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
      // Do not pile up more work after an HTTP timeout; Ollama may still be generating.
      if (result.error) {
        process.exitCode = 1;
        return;
      }
    }
  }
  console.table(report.results.map(({ model, run, wallSeconds, loadSeconds, promptSeconds,
    generationSeconds, tokensPerSecond, within42Seconds }) =>
    ({ model, run, wallSeconds, loadSeconds, promptSeconds, generationSeconds, tokensPerSecond, within42Seconds })));
}

main().catch(error => {
  console.error(`Benchmark failed: ${error.message}`);
  process.exitCode = 1;
});
