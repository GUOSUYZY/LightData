import assert from "node:assert/strict";
import http from "node:http";
import fs from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";

// Real classic Worker + importScripts + embedded OpenCV WASM, without opening the app.
const root = path.dirname(fileURLToPath(import.meta.url));
assert.equal(createHash("sha256").update(await fs.readFile(path.join(root,
  "vendor/opencv-4.12.0.js"))).digest("hex"),
"bd0c3e6448043de04f6a64a12cb7b759f78c3ab8f7c35c9f2e0f71c88bb17103",
"Pinned OpenCV bundle must match its recorded provenance");
for (const license of ["LICENSE-OpenCV.txt", "LICENSE-TechStark-opencv-js.txt"]) {
  const contents = await fs.readFile(path.join(root, "vendor", license), "utf8");
  assert.ok(contents.includes("Apache License") && contents.includes("Version 2.0"),
    `${license} must retain the upstream Apache 2.0 license`);
}
const server = http.createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
    if (pathname === "/") {
      response.setHeader("Content-Type", "text/html");
      response.end("<!doctype html><title>OpenCV registration check</title>");
      return;
    }
    const file = path.resolve(root, "." + pathname);
    if (!file.startsWith(root + path.sep)) throw new Error("outside static root");
    response.setHeader("Content-Type", file.endsWith(".js") ? "application/javascript" : "application/octet-stream");
    response.end(await fs.readFile(file));
  } catch { response.writeHead(404); response.end(); }
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
let browser;
try {
  browser = await chromium.launch({ headless: true,
    executablePath: process.env.LIGHTDATA_CHROME || (process.platform === "win32"
      ? "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" : undefined) });
  const page = await browser.newPage();
  await page.goto(`http://127.0.0.1:${server.address().port}`);
  const report = await page.evaluate(async () => {
    const source = `(${workerCheck.toString()})(${JSON.stringify(location.origin)})`;
    const blob = URL.createObjectURL(new Blob([source], { type: "application/javascript" }));
    const worker = new Worker(blob);
    try {
      return await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error("OpenCV Worker timed out")), 60000);
        worker.onmessage = event => { clearTimeout(timer); resolve(event.data); };
        worker.onerror = event => { clearTimeout(timer); reject(new Error(event.message)); };
      });
    } finally { worker.terminate(); URL.revokeObjectURL(blob); }

    async function workerCheck(origin) {
      try {
        const started = performance.now();
        importScripts(origin + "/vendor/opencv-4.12.0.js");
        let cv = self.cv;
        if (typeof cv === "function") cv = cv();
        // This build exposes Emscripten's old self-resolving Module.then, not a
        // native Promise. Awaiting it directly recursively assimilates itself.
        if (typeof cv?.then === "function") {
          const ready = await new Promise(resolve => cv.then(module => resolve({ module })));
          cv = ready.module;
        }
        const loadMs = performance.now() - started;
        const api = Object.fromEntries(["Mat", "estimateAffinePartial2D", "findHomography",
          "goodFeaturesToTrack", "calcOpticalFlowPyrLK", "erode"].map(name => [name, typeof cv?.[name] === "function"]));
        const { alignOverlap } = await import(origin + "/opencv-aligner.js");
        const width = 256, height = 192, area = width * height;
        const mask = new Uint8Array(area).fill(255);
        function texture(seed) {
          let value = seed;
          const random = () => { value = (Math.imul(value, 1664525) + 1013904223) >>> 0; return value / 2 ** 32; };
          const pixels = new Uint8Array(area);
          for (let y = 0; y < height; y++) for (let x = 0; x < width; x++)
            pixels[y * width + x] = 105 + 17 * Math.sin(x * .19 + y * .13) +
              13 * Math.sin(x * .075 - y * .15) + random() * 18;
          for (let i = 0; i < 110; i++) {
            const left = Math.floor(random() * (width - 10)), top = Math.floor(random() * (height - 10));
            const edge = 3 + Math.floor(random() * 7), code = random() < .5 ? 35 : 220;
            for (let y = top; y < top + edge; y++) for (let x = left; x < left + edge; x++) pixels[y * width + x] = code;
          }
          return pixels;
        }
        function transform(rotation = 0, scale = 1, dx = 0, dy = 0) {
          const a = scale * Math.cos(rotation), b = scale * Math.sin(rotation);
          return { a, b, x: (1 - a) * width / 2 + b * height / 2 + dx,
            y: (1 - a) * height / 2 - b * width / 2 + dy };
        }
        function warp(reference, matrix) {
          const out = new Uint8Array(area), determinant = matrix.a ** 2 + matrix.b ** 2;
          for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
            const u = (matrix.a * (x - matrix.x) + matrix.b * (y - matrix.y)) / determinant;
            const v = (-matrix.b * (x - matrix.x) + matrix.a * (y - matrix.y)) / determinant;
            const xx = Math.floor(u), yy = Math.floor(v), tx = u - xx, ty = v - yy;
            if (xx < 0 || yy < 0 || xx >= width - 1 || yy >= height - 1) continue;
            const i = yy * width + xx;
            out[y * width + x] = (reference[i] * (1 - tx) + reference[i + 1] * tx) * (1 - ty) +
              (reference[i + width] * (1 - tx) + reference[i + width + 1] * tx) * ty;
          }
          return out;
        }
        function local(values, x, y) {
          if (!values) return 0;
          const u = Math.max(0, Math.min(3, x * 3 / width)), v = Math.max(0, Math.min(3, y * 3 / height));
          const col = Math.min(2, Math.floor(u)), row = Math.min(2, Math.floor(v));
          const tx = u - col, ty = v - row, i = row * 4 + col;
          return (values[i] * (1 - tx) + values[i + 1] * tx) * (1 - ty) +
            (values[i + 4] * (1 - tx) + values[i + 5] * tx) * ty;
        }
        function map(model, x, y, grid = true) {
          return { x: model.a * x - model.b * y + model.x + (grid ? width * local(model.offsetsX, x, y) : 0),
            y: model.b * x + model.a * y + model.y + (grid ? height * local(model.offsetsY, x, y) : 0) };
        }
        function summary(actual) {
          if (!actual) return {};
          const { offsetsX, offsetsY, gridSupport, ...rest } = actual;
          return { ...rest, gridNodes: gridSupport.filter(Boolean).length,
            gridMaxPixels: Math.max(...offsetsX.map((dx, i) => Math.hypot(dx * width, offsetsY[i] * height))) };
        }
        function error(actual, expected) {
          if (!actual) return null;
          let worst = 0;
          for (const x of [32, 128, 224]) for (const y of [32, 96, 160]) {
            const a = map(actual, x, y), e = map(expected, x, y);
            worst = Math.max(worst, Math.hypot(a.x - e.x, a.y - e.y));
          }
          return worst;
        }
        const reference = texture(123), cases = [];
        const moved = transform(.025, 1.008, 3.4, -2.7), incoming = warp(reference, moved);
        const translation = transform(0, 1, 6, -4);
        for (const [name, image, expected] of [["translation", warp(reference, translation), translation], ["rotation", incoming, moved]]) {
          const actual = alignOverlap(cv, reference, image, mask, width, height);
          cases.push({ name, ...summary(actual), maxError: error(actual, expected) });
        }
        const determinant = moved.a ** 2 + moved.b ** 2;
        const inverse = { a: moved.a / determinant, b: -moved.b / determinant,
          x: (-moved.a * moved.x - moved.b * moved.y) / determinant,
          y: (moved.b * moved.x - moved.a * moved.y) / determinant };
        const reversed = alignOverlap(cv, incoming, reference, mask, width, height);
        cases.push({ name: "inverse-direction", ...summary(reversed), maxError: error(reversed, inverse) });
        const occluded = incoming.slice(), other = texture(789);
        for (let y = 60; y < 125; y++) for (let x = 92; x < 150; x++) occluded[y * width + x] = other[y * width + x];
        const occlusion = alignOverlap(cv, reference, occluded, mask, width, height);
        cases.push({ name: "occlusion", ...summary(occlusion), maxError: error(occlusion, moved) });
        const movingSubject = incoming.slice();
        for (let y = 65; y < 125; y++) for (let x = 90; x < 150; x++) movingSubject[y * width + x] = reference[(y - 9) * width + x - 13];
        const outliers = alignOverlap(cv, reference, movingSubject, mask, width, height);
        cases.push({ name: "moving-outliers", ...summary(outliers), maxError: error(outliers, moved) });
        // A smooth displacement field models coherent parallax left after the
        // global inertial projection. Invert it iteratively to synthesize pixels.
        const parallaxShift = (x, y) => ({ x: 1.8 * Math.sin(2 * Math.PI * x / width),
          y: 1.1 * Math.sin(2 * Math.PI * y / height) });
        const parallaxPixels = new Uint8Array(area);
        for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
          let u = x, v = y;
          for (let i = 0; i < 6; i++) {
            const shift = parallaxShift(u, v);
            u = x - shift.x; v = y - shift.y;
          }
          const xx = Math.floor(u), yy = Math.floor(v), tx = u - xx, ty = v - yy;
          if (xx < 0 || yy < 0 || xx >= width - 1 || yy >= height - 1) continue;
          const i = yy * width + xx;
          parallaxPixels[y * width + x] = (reference[i] * (1 - tx) + reference[i + 1] * tx) * (1 - ty) +
            (reference[i + width] * (1 - tx) + reference[i + width + 1] * tx) * ty;
        }
        const parallax = alignOverlap(cv, reference, parallaxPixels, mask, width, height);
        if (!parallax) throw new Error("Coherent parallax must retain a reliable global fit");
        let globalSquared = 0, gridSquared = 0, samples = 0;
        for (let y = 24; y <= height - 24; y += 16) for (let x = 24; x <= width - 24; x += 16) {
          const shift = parallaxShift(x, y), expected = { x: x + shift.x, y: y + shift.y };
          for (const [grid, add] of [[false, value => globalSquared += value], [true, value => gridSquared += value]]) {
            const actual = map(parallax, x, y, grid);
            add((actual.x - expected.x) ** 2 + (actual.y - expected.y) ** 2);
          }
          samples++;
        }
        const gridChecks = { ...summary(parallax), globalRmse: Math.sqrt(globalSquared / samples),
          gridRmse: Math.sqrt(gridSquared / samples), arraysValid:
            parallax.offsetsX instanceof Float64Array && parallax.offsetsY instanceof Float64Array &&
            parallax.offsetsX.length === 16 && parallax.offsetsY.length === 16 &&
            parallax.width === width && parallax.height === height &&
            Array.from({ length: 16 }, (_, i) =>
              Number.isFinite(parallax.offsetsX[i]) && Number.isFinite(parallax.offsetsY[i]) &&
              Math.abs(parallax.offsetsX[i] * width) <= 3 && Math.abs(parallax.offsetsY[i] * height) <= 3 &&
              (parallax.gridSupport[i] >= 4 || (parallax.offsetsX[i] === 0 && parallax.offsetsY[i] === 0))).every(Boolean) };
        const isolated = warp(reference, translation);
        for (let y = 76; y < 110; y++) for (let x = 104; x < 146; x++)
          isolated[y * width + x] = reference[(y + 2) * width + x - 10];
        const isolatedFit = alignOverlap(cv, reference, isolated, mask, width, height);
        gridChecks.isolatedSubjectGridMax = isolatedFit ? summary(isolatedFit).gridMaxPixels : null;
        const flat = new Uint8Array(area).fill(100);
        const small = flat.slice();
        for (let y = 72; y < 112; y++) for (let x = 105; x < 150; x++) small[y * width + x] = reference[y * width + x];
        const rejected = {
          flat: alignOverlap(cv, flat, flat, mask, width, height) === null,
          localizedSubject: alignOverlap(cv, small, warp(small, translation), mask, width, height) === null,
          scale: alignOverlap(cv, reference, warp(reference, transform(0, 1.09)), mask, width, height) === null,
          excessiveRotation: alignOverlap(cv, reference, warp(reference, transform(.10)), mask, width, height) === null,
          excessiveShift: alignOverlap(cv, reference, warp(reference, transform(0, 1, 36)), mask, width, height) === null,
          emptyMask: alignOverlap(cv, reference, incoming, new Uint8Array(area), width, height) === null,
          oversized: alignOverlap(cv, reference, incoming, mask, 257, height) === null,
        };
        // Count actual Embind Mat deletions on a successful call and a forced
        // OpenCV exception. These are real WASM allocations, not a fake CV API.
        const allocations = { created: 0, deleted: 0 };
        function counted(mat) {
          allocations.created++;
          const dispose = mat.delete.bind(mat);
          mat.delete = () => { allocations.deleted++; dispose(); };
          return mat;
        }
        const checkedCv = Object.create(cv);
        checkedCv.Mat = function (...args) { return counted(new cv.Mat(...args)); };
        for (const name of ["matFromArray", "getStructuringElement", "findHomography", "estimateAffinePartial2D"])
          if (cv[name]) checkedCv[name] = (...args) => counted(cv[name](...args));
        if (!alignOverlap(checkedCv, reference, incoming, mask, width, height)) throw new Error("Instrumented alignment failed");
        checkedCv.findHomography = checkedCv.estimateAffinePartial2D = () => { throw new Error("Forced RANSAC exception"); };
        rejected.exception = alignOverlap(checkedCv, reference, incoming, mask, width, height) === null;
        const times = [];
        for (let i = 0; i < 16; i++) {
          const result = alignOverlap(cv, reference, incoming, mask, width, height);
          if (!result) throw new Error("Repeated alignment failed");
          if (i >= 4) times.push(result.ms);
        }
        times.sort((a, b) => a - b);
        postMessage({ api, loadMs, cases, rejected, allocations, gridChecks,
          medianMs: times[Math.floor(times.length / 2)], p95Ms: times[Math.floor(times.length * .95)] });
      } catch (error) { postMessage({ error: String(error), stack: error.stack }); }
    }
  });
  assert.ok(!report.error, report.error);
  for (const name of ["Mat", "findHomography", "goodFeaturesToTrack", "calcOpticalFlowPyrLK", "erode"])
    assert.equal(report.api[name], true, `Missing API ${name}`);
  for (const result of report.cases) {
    assert.equal(result.matched, true, `${result.name} must align: ${JSON.stringify(result)}`);
    assert.ok(result.maxError < .8, `${result.name} mapping error ${result.maxError}`);
    assert.ok(result.inliers >= Math.max(7, Math.ceil(.65 * result.tracked)));
  }
  for (const [name, rejected] of Object.entries(report.rejected)) assert.equal(rejected, true, name);
  assert.ok(report.allocations.created > 20);
  assert.equal(report.allocations.created, report.allocations.deleted, "Every Mat must be freed, including on exceptions");
  assert.equal(report.gridChecks.arraysValid, true);
  assert.ok(report.gridChecks.gridNodes >= 4);
  assert.ok(report.gridChecks.gridRmse < report.gridChecks.globalRmse * .85, JSON.stringify(report.gridChecks));
  assert.ok(report.gridChecks.isolatedSubjectGridMax !== null && report.gridChecks.isolatedSubjectGridMax < .65);
  console.log(JSON.stringify(report, (_, value) => typeof value === "number" ? Math.round(value * 1000) / 1000 : value));
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}
