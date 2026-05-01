// Tiny Ollama smoke test.
// Usage examples:
//   npx tsx scripts/ollama-smoke.ts
//   npx tsx ollama-smoke.ts qwen2.5:0.5b
//   npx tsx ollama-smoke.ts qwen2.5:0.5b "Write a haiku about Ollama in 3 lines, each line no more than 10 words."
//   npx tsx ollama-smoke.ts llama3.2:3b "Say hi in one short sentence and tell me your hopes for the future of AI."
//   npx tsx ollama-smoke.ts llama3.2:3b


const model = process.argv[2] ?? "llama3.2:3b";
const prompt = process.argv.slice(3).join(" ") || "Reply with: Ollama smoke test passed.";

async function main() {
  const res = await fetch("http://127.0.0.1:11434/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({  
      model,
      stream: false,
      messages: [{ role: "user", content: prompt }],
    }),
  });

  if (!res.ok) {
    const err = await res.text();
    console.error(`Ollama HTTP ${res.status}: ${err}`);
    process.exit(1);
  }

  const json = (await res.json()) as {
    message?: { content?: string };
  };

  const content = json.message?.content?.trim() || "<no content>";
  console.log(`model=${model}`);
  console.log("AI response: " + content);
}

main().catch((err) => {
  console.error("Smoke test failed:", err);
  process.exit(1);
});
