import { createHash } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";

function parseArgs(argv) {
  const values = {};
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (!key.startsWith("--")) throw new Error(`Неизвестный аргумент: ${key}`);
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) throw new Error(`Не задано значение для ${key}`);
    values[key.slice(2)] = value;
    index += 1;
  }
  return values;
}

function versionCode(version) {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(version);
  if (!match) throw new Error(`Версия должна быть вида 1.0.0: ${version}`);
  const [, major, minor, patch] = match;
  const minorValue = Number(minor);
  const patchValue = Number(patch);
  if (minorValue > 999 || patchValue > 999) {
    throw new Error("minor и patch должны быть от 0 до 999");
  }
  return Number(major) * 1_000_000 + minorValue * 1_000 + patchValue;
}

const args = parseArgs(process.argv.slice(2));
const version = args.version;
const apk = args.apk;
const out = args.out ?? "dist/app-manifest.json";
const repository = args.repository ?? "doc9830/distillarium-app";
const notes = args.notes ?? "";
if (!version || !apk) {
  throw new Error("Использование: node tools/app-release-manifest.mjs --version 1.0.0 --apk path [--out path] [--repository owner/repo]");
}

const code = versionCode(version);
const bytes = await readFile(apk);
const info = await stat(apk);
const sha256 = createHash("sha256").update(bytes).digest("hex");
const released = new Date().toISOString().slice(0, 10);
const manifest = {
  schemaVersion: 1,
  versionCode: code,
  versionName: version,
  minSdk: 26,
  released,
  apk: {
    url: `https://github.com/${repository}/releases/download/app-v${version}/distillarium-${version}.apk`,
    size: info.size,
    sha256,
  },
  notes: notes.trim() || `Версия ${version}`,
};

await mkdir(path.dirname(out), { recursive: true });
await writeFile(out, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
console.log(`Манифест: ${out}`);
console.log(`versionCode: ${code}`);
console.log(`sha256: ${sha256}`);
console.log(`size: ${info.size}`);
