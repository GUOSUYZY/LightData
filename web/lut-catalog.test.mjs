import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";

const base = new URL("./luts/", import.meta.url);

test("shipped LUTs resolve to selectable films and complete RGB cubes", async () => {
  const films = JSON.parse(await readFile(new URL("./data/films.json", import.meta.url)));
  const names = new Set(films.map(film => film.name));
  const { entries } = JSON.parse(await readFile(new URL("index.json", base)));
  const seen = new Set();
  for (const entry of entries) {
    assert.ok(names.has(entry.film), entry.film);
    assert.ok(["upstream", "derived"].includes(entry.kind), entry.file);
    assert.ok(entry.description?.trim(), entry.file);
    const url = new URL(entry.file, base);
    assert.ok(url.href.startsWith(base.href), entry.file);
    const key = JSON.stringify([entry.film, entry.file]);
    assert.ok(!seen.has(key), key);
    seen.add(key);
    const data = await readFile(url);
    assert.equal(data.length, 33 ** 3 * 3, entry.file);
    assert.equal(createHash("sha256").update(data).digest("hex"), entry.validation.sha256, entry.file);
    assert.equal(entry.validation.grayLumaReversals, 0, entry.file);
    const [black, white] = [entry.validation.grayAxisRGB8[0], entry.validation.grayAxisRGB8.at(-1)];
    assert.ok(white.reduce((a, b) => a+b, 0) > black.reduce((a, b) => a+b, 0), entry.file);
    assert.equal(entry.validation.referenceRGB8.samples, 33, entry.file);
    if (entry.mono) {
      for (let i = 0; i < data.length; i += 3) {
        if (data[i] !== data[i+1] || data[i] !== data[i+2]) assert.fail(entry.file+" has tinted silver output");
      }
    }
  }
  for (const name of ["EKTAR 100", "PORTRA 160", "PORTRA 800", "VERITA 200D",
    "VISION3 500T", "EKTACHROME E100", "PROVIA 100F", "VELVIA 100", "KODACHROME 64"]) {
    assert.ok(entries.some(e => e.film === name && e.kind === "upstream"), name);
  }
  const upstream = entries.filter(e => e.kind === "upstream");
  assert.equal(new Set(upstream.map(e => e.profile)).size, 20);
  assert.equal(new Set(upstream.map(e => e.printProfile).filter(Boolean)).size, 8);
  assert.equal(upstream.length, 80);
  for (const name of ["PRO 160S", "PAN F PLUS", "SUPERIA X-TRA 800"])
    assert.ok(entries.some(e => e.film === name && e.kind === "derived"), name);
  assert.deepEqual(new Set(entries.map(e => e.film)),
    new Set(films.filter(f => f.info.startsWith("厂家图采样")).map(f => f.name)));
  for (const film of ["HP5 PLUS", "T-MAX 400", "APX 100", "NEOPAN 400 PRESTO"]) {
    assert.ok(entries.some(e => e.film === film && e.mono), film);
  }
});
