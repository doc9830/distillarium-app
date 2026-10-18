#!/usr/bin/env node
// Копирует собранный пакет контента в assets приложения и проверяет хеши.
//
//   node tools/sync-content.mjs
//   node tools/sync-content.mjs --content-dir ../distillarium-content/dist
//
// Запускать после `node tools/build-content.mjs` в distillarium-content.
// Скрипт намеренно не собирает контент сам: сборка и приложение живут
// в разных репозиториях и не должны зависеть друг от друга в CI.

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function fail(message) {
  console.error(`✗ ${message}`);
  process.exit(1);
}

function sha256(data) {
  return crypto.createHash("sha256").update(data).digest("hex");
}

function parseArgs(argv) {
  const options = {
    contentDir: path.resolve(ROOT, "..", "distillarium-content", "dist"),
    assetsDir: path.join(ROOT, "app", "src", "main", "assets"),
    check: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const take = () => {
      const value = argv[i + 1];
      if (!value || value.startsWith("--")) throw new Error(`для ${arg} нужно значение`);
      i++;
      return value;
    };
    if (arg === "--content-dir") options.contentDir = path.resolve(take());
    else if (arg === "--assets-dir") options.assetsDir = path.resolve(take());
    else if (arg === "--check") options.check = true;
    else if (arg === "--help" || arg === "-h") {
      console.log("Копирует dist/content-pack.zip и dist/manifest.json в assets приложения.");
      process.exit(0);
    } else {
      throw new Error(`неизвестный аргумент: ${arg}`);
    }
  }
  return options;
}

function main() {
  let options;
  try {
    options = parseArgs(process.argv.slice(2));
  } catch (error) {
    fail(error.message);
  }

  const manifestPath = path.join(options.contentDir, "manifest.json");
  const packPath = path.join(options.contentDir, "content-pack.zip");
  const contentPath = path.join(options.contentDir, "content");
  for (const [file, what] of [
    [manifestPath, "манифест"],
    [packPath, "архив"],
    [contentPath, "распакованный пакет"],
  ]) {
    if (!fs.existsSync(file)) {
      fail(`нет ${what}: ${file}. Сначала соберите контент в distillarium-content.`);
    }
  }

  let manifest;
  try {
    manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  } catch (error) {
    fail(`manifest.json не разбирается: ${error.message}`);
  }

  const pack = fs.readFileSync(packPath);
  if (pack.length !== manifest.packSize) {
    fail(`размер архива ${pack.length}, в манифесте ${manifest.packSize}`);
  }
  if (sha256(pack) !== manifest.packSha256) {
    fail("sha256 архива не совпадает с manifest.json");
  }

  if (!Array.isArray(manifest.files) || manifest.files.length === 0) {
    fail("manifest.json: пустой files");
  }
  for (const entry of manifest.files) {
    const file = path.join(contentPath, entry.path);
    if (!fs.existsSync(file)) fail(`в пакете нет ${entry.path}`);
    const data = fs.readFileSync(file);
    if (data.length !== entry.size) {
      fail(`${entry.path}: размер ${data.length}, в манифесте ${entry.size}`);
    }
    if (sha256(data) !== entry.sha256) fail(`${entry.path}: sha256 не совпадает`);
  }

  if (!options.check) {
    fs.mkdirSync(options.assetsDir, { recursive: true });
    fs.copyFileSync(packPath, path.join(options.assetsDir, "content-pack.zip"));
    fs.copyFileSync(manifestPath, path.join(options.assetsDir, "manifest.json"));
  } else {
    const assetPack = path.join(options.assetsDir, "content-pack.zip");
    const assetManifest = path.join(options.assetsDir, "manifest.json");
    if (!fs.existsSync(assetPack) || !fs.existsSync(assetManifest)) {
      fail(`в ${options.assetsDir} нет content-pack.zip или manifest.json`);
    }
    if (sha256(fs.readFileSync(assetPack)) !== sha256(pack)) {
      fail("assets/content-pack.zip не совпадает с собранным пакетом");
    }
    if (sha256(fs.readFileSync(assetManifest)) !== sha256(fs.readFileSync(manifestPath))) {
      fail("assets/manifest.json не совпадает с собранным манифестом");
    }
  }

  console.log(`${options.check ? "Проверено" : "Скопировано"}:`);
  console.log(`  contentVersion: ${manifest.contentVersion}`);
  console.log(`  schemaVersion:  ${manifest.schemaVersion}`);
  console.log(`  minAppVersion:  ${manifest.minAppVersion}`);
  console.log(`  файлов:         ${manifest.files.length}`);
  console.log(`  архив:          ${pack.length} Б, sha256 ${sha256(pack)}`);
  console.log(`  assets:         ${path.relative(ROOT, options.assetsDir)}`);
}

main();
