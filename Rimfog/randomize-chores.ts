import * as fs from "fs";
import * as path from "path";

const filePath = path.join(__dirname, "chores.md");
const content = fs.readFileSync(filePath, "utf-8");

const lines = content.split("\n");

// Separate header lines (starting with #) from chore lines
const headers: string[] = [];
const chores: string[] = [];

for (const line of lines) {
  if (line.startsWith("#") || line.trim() === "") {
    headers.push(line);
  } else {
    chores.push(line);
  }
}

// Fisher-Yates shuffle
for (let i = chores.length - 1; i > 0; i--) {
  const j = Math.floor(Math.random() * (i + 1));
  [chores[i], chores[j]] = [chores[j], chores[i]];
}

const result = [...headers, ...chores].join("\n");
console.log(result);
