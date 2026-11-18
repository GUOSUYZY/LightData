import assert from "node:assert/strict";
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "@playwright/test";

// Exercise the shipped classic Worker/WASM, its transfers, and the main-thread
// capture gate together. The camera never waits at a direction: logical frame
// timestamps remain 50 ms apart even when awaiting an asynchronous worker job.
const root = path.dirname(fileURLToPath(import.meta.url));
let server;
let browser;
try {
  let base = process.env.LIGHTDATA_URL;
  if (!base) {
    server = http.createServer(async (request, response) => {
      try {
        const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
        if (pathname === "/") {
          response.setHeader("Content-Type", "text/html");
          response.end("<!doctype html><title>Panorama engine check</title>");
          return;
        }
        const file = path.resolve(root, "." + pathname);
        if (!file.startsWith(root + path.sep)) throw new Error("outside static root");
        response.setHeader("Content-Type", file.endsWith(".js") ? "application/javascript" : "application/octet-stream");
        response.setHeader("Cache-Control", "no-store");
        response.end(await fs.readFile(file));
      } catch { response.writeHead(404); response.end(); }
    });
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    base = `http://127.0.0.1:${server.address().port}/`;
  }
  base = new URL(base).href;
  if (!base.endsWith("/") && !new URL(base).pathname.split("/").at(-1).includes(".")) base += "/";
  browser = await chromium.launch({ headless: true,
    executablePath: process.env.LIGHTDATA_CHROME || (process.platform === "win32"
      ? "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" : undefined) });
  const page = await browser.newPage();
  page.setDefaultTimeout(120000);
  await page.goto(base, { waitUntil: "domcontentloaded" });
  const report = await page.evaluate(async moduleBase => {
    const [{ Panorama, yawPitch }, { PanoramaEngine }] = await Promise.all([
      import(new URL("./panorama.js", moduleBase)),
      import(new URL("./panorama-engine.js", moduleBase)),
    ]);
    const source = { x: .7, y: .53 }, target = { x: 1, y: .42 };
    const width = 640, height = Math.round(width * source.y / source.x);
    const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
    const srgb = value => value <= .0031308 ? value * 12.92 : 1.055 * value ** (1 / 2.4) - .055;
    const linear = Float64Array.from({ length: 256 }, (_, code) => {
      const value = code / 255;
      return value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4;
    });
    const encode = Uint8ClampedArray.from({ length: 65536 }, (_, i) => Math.round(srgb(i / 65535) * 255));
    const round = value => Math.round(value * 1000) / 1000;
    const percentile = (values, fraction) => {
      const sorted = values.toSorted((a, b) => a - b);
      return sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))] : 0;
    };
    const timing = values => ({ median: round(percentile(values, .5)), p95: round(percentile(values, .95)) });
    const check = (condition, message) => { if (!condition) throw new Error(message); };

    // A fixed, colored, textured world plane. Unique colored cells give LK
    // spatial support without repeating one periodic calibration pattern.
    const textureWidth = 2048, textureHeight = 1024;
    const texture = new Float32Array(textureWidth * textureHeight * 3);
    const hash = (x, y, c = 0) => {
      let h = Math.imul(x + 314159, 374761393) ^ Math.imul(y + 271828, 668265263) ^ Math.imul(c + 1, 1274126177);
      h = Math.imul(h ^ h >>> 13, 1274126177);
      return ((h ^ h >>> 16) >>> 0) / 2 ** 32;
    };
    for (let y = 0; y < textureHeight; y++) for (let x = 0; x < textureWidth; x++) {
      const xx = (x + .5) * 6 / textureWidth - 3;
      const yy = (y + .5) * 3 / textureHeight - 1.5;
      const cellX = Math.floor((xx + 3) * 13), cellY = Math.floor((yy + 1.5) * 13);
      const fine = .018 * Math.sin(xx * 33 + yy * 18) + .012 * Math.cos(xx * 19 - yy * 27);
      for (let c = 0; c < 3; c++) texture[(y * textureWidth + x) * 3 + c] =
        .055 + .31 * hash(cellX, cellY, c) + fine;
    }
    const truth = (x, y, color) => {
      const u = clamp((x + 3) * textureWidth / 6 - .5, 0, textureWidth - 1.001);
      const v = clamp((y + 1.5) * textureHeight / 3 - .5, 0, textureHeight - 1.001);
      const left = Math.floor(u), top = Math.floor(v), dx = u - left, dy = v - top;
      const i = (top * textureWidth + left) * 3 + color;
      return (texture[i] * (1 - dx) + texture[i + 3] * dx) * (1 - dy) +
        (texture[i + textureWidth * 3] * (1 - dx) + texture[i + textureWidth * 3 + 3] * dx) * dy;
    };
    const path = [];
    for (let i = 0; i <= 60; i++) path.push(i * .008);
    for (let i = 59; i >= -60; i--) path.push(i * .008);

    function render(yaw, index, variant, hidePatch) {
      const data = new Uint8ClampedArray(width * height * 4);
      const a = Math.cos(yaw), b = Math.sin(yaw);
      const changingExposure = variant.startsWith("ae-");
      const deltaEV = changingExposure ? .45 * Math.sin(index * .04) : 0;
      const tint = !changingExposure ? [1, 1, 1]
        : [1 + .06 * Math.sin(index * .031), 1 - .025 * Math.sin(index * .037), 1 + .05 * Math.sin(index * .027)];
      const exposure = 2 ** -deltaEV;
      for (let y = 0; y < height; y++) {
        const v = (1 - 2 * (y + .5) / height) * source.y;
        for (let x = 0; x < width; x++) {
          const u = (2 * (x + .5) / width - 1) * source.x;
          const depth = a + b * u;
          const worldX = (a * u - b) / depth, worldY = v / depth;
          if (hidePatch && Math.abs(worldX - .5) < .04 && Math.abs(worldY - .3) < .04) continue;
          const at = (y * width + x) * 4;
          const shade = !changingExposure ? 1
            : 1 + .045 * Math.sin(index * .03) * u / source.x + .035 * Math.sin(index * .037) * v / source.y;
          for (let c = 0; c < 3; c++) {
            const value = truth(worldX, worldY, c) * exposure * tint[c] * shade;
            data[at + c] = encode[Math.round(clamp(value, 0, 1) * 65535)];
          }
          data[at + 3] = 255;
        }
      }
      // A moving foreground object occupies under 4% of the image. It must not
      // become the global transform or dictate the residual grid.
      if (variant === "ae-tint-occlusion" && index > 20 && index < 110) {
        const left = Math.round(width * .50 + 35 * Math.sin(index * .17)), top = Math.round(height * .49);
        for (let y = top; y < top + 90; y++) for (let x = left; x < left + 70; x++) {
          const at = (y * width + x) * 4;
          for (let c = 0; c < 3; c++) data[at + c] = 45 + Math.floor(hash(x >> 2, (y + index * 3) >> 2, c) * 180);
        }
      }
      if (variant === "weak-match-retry" && index === 21) {
        for (let i = 0; i < data.length; i += 4) {
          data[i] = data[i + 1] = data[i + 2] = 117;
        }
      }
      return { frame: { width, height, data }, cameraEV: 8 + deltaEV };
    }

    function metrics(panorama, owners) {
      let squared = 0, valid = 0;
      const seams = [];
      const ground = new Float32Array(panorama.width * panorama.height * 3);
      for (let y = 0; y < panorama.height; y++) for (let x = 0; x < panorama.width; x++) {
        const k = y * panorama.width + x;
        if (!panorama.pixels[k * 4 + 3]) continue;
        for (let c = 0; c < 3; c++) {
          const expected = truth(panorama.xs[x], panorama.ys[y], c);
          ground[k * 3 + c] = expected;
          squared += (linear[panorama.pixels[k * 4 + c]] - expected) ** 2;
          valid++;
        }
      }
      for (let y = 0; y < panorama.height; y++) for (let x = 0; x < panorama.width; x++) {
        const k = y * panorama.width + x;
        if (!owners[k]) continue;
        for (const next of [x + 1 < panorama.width ? k + 1 : k,
          y + 1 < panorama.height ? k + panorama.width : k]) {
          if (!owners[next] || owners[next] === owners[k]) continue;
          for (let c = 0; c < 3; c++) seams.push(255 * Math.abs(
            (linear[panorama.pixels[k * 4 + c]] - linear[panorama.pixels[next * 4 + c]]) -
            (ground[k * 3 + c] - ground[next * 3 + c])));
        }
      }
      return { rmseLinear255: round(Math.sqrt(squared / valid) * 255),
        seamLinearMae255: round(seams.reduce((a, b) => a + b, 0) / Math.max(1, seams.length)),
        seamLinearP95_255: round(percentile(seams, .95)), seamSamples: seams.length };
    }

    async function run(variant) {
      const started = performance.now();
      const options = { source, target, width: 720, height: 720 };
      const engine = await PanoramaEngine.create(options);
      const initializedMs = performance.now() - started;
      const jobs = [], viewTimes = [], processTimes = [], roundTrips = [], transfers = [];
      let panorama, index = 0, decoded = 0, trackingDecoded = 0, patchReleased = false, release;
      const owners = new Uint16Array(720 * Math.round(720 * target.y / target.x));
      panorama = new Panorama({ ...options, submit: async job => {
        const start = performance.now(), snapshot = await engine.submit(job);
        const roundTrip = performance.now() - start;
        processTimes.push(snapshot.processingMs); roundTrips.push(roundTrip);
        transfers.push(Math.max(0, roundTrip - snapshot.processingMs));
        const pixels = new Uint8ClampedArray(snapshot.pixels);
        for (let k = 0; k < owners.length; k++) if (pixels[k * 4 + 3] && !owners[k]) owners[k] = jobs.length + 1;
        jobs.push({ frame: index, yaw: round(path[index] ?? (-.48 + (index - path.length + 1) * .008)),
          coverage: round(snapshot.count / owners.length), accepted: snapshot.accepted,
          matched: !!snapshot.registration?.matched, inliers: snapshot.registration?.inliers ?? 0,
          gridNodes: snapshot.registration?.gridSupport?.filter(Boolean).length ?? 0,
          reason: snapshot.reason, requireVisual: job.requireVisual });
        return snapshot;
      } });
      check(panorama.pixels.length / 4 === owners.length, "Unexpected atlas geometry");
      panorama.setCenter(yawPitch(0));
      let settledDuringSweep = 0;
      try {
        const trajectory = [...path];
        // Continue moving through the final small missing patch; no stop or
        // special direct write is allowed even in this completion regression.
        if (variant === "late-gap") for (let i = 1; i <= 20; i++) trajectory.push(-.48 + i * .008);
        for (index = 0; index < trajectory.length; index++) {
          const yaw = trajectory[index], pose = yawPitch(yaw);
          const deltaEV = variant.startsWith("ae-") ? .45 * Math.sin(index * .04) : 0;
          panorama.capture({ pose, time: index * 50, cameraEV: 8 + deltaEV, exposureSeconds: .004,
            frame: () => { decoded++; return render(yaw, index, variant, variant === "late-gap" && !patchReleased).frame; },
            trackingFrame: () => { trackingDecoded++; throw new Error("Sensor trajectory decoded image-only tracking"); } });
          if (panorama.pending) await panorama.pending;
          check(!panorama.workerFailed, `${variant}: worker failed`);
          if (!panorama.settling) settledDuringSweep++;
          const viewStart = performance.now();
          panorama.view(pose, null, 720);
          viewTimes.push(performance.now() - viewStart);
          if (variant === "late-gap" && !patchReleased && panorama.coverage >= .98) {
            patchReleased = true;
            release = { frame: index, yaw: round(yaw), coverage: round(panorama.coverage) };
          }
        }
        const centerView = panorama.view(yawPitch(0), null, 720);
        const pixels = panorama.pixels.slice();
        const report = { variant, inputFrames: trajectory.length, decoded, trackingDecoded,
          atlas: [panorama.width, panorama.height], captures: panorama.captures,
          reliable: jobs.filter(job => job.matched && job.accepted).length,
          jobs: jobs.length, coverage: round(panorama.coverage), centerCoverage: round(centerView.coverage),
          initializedMs: round(initializedMs), workerMs: timing(processTimes),
          roundTripMs: timing(roundTrips), transferMs: timing(transfers), viewMs: timing(viewTimes),
          settledDuringSweep, ...metrics(panorama, owners),
          gap: variant === "late-gap" ? { release, finalMissing: panorama.pixels.length / 4 - panorama.count } : undefined,
          history: jobs };
        return { report, pixels };
      } finally { panorama.dispose(); engine.close(); }
    }

    const cases = [];
    let baseline;
    for (const variant of ["baseline", "ae-tint", "ae-tint-occlusion", "late-gap", "weak-match-retry"]) {
      const result = await run(variant);
      if (!baseline) baseline = result.pixels;
      let difference = 0, count = 0;
      for (let i = 0; i < baseline.length; i += 4) if (baseline[i + 3] && result.pixels[i + 3]) {
        for (let c = 0; c < 3; c++) { difference += Math.abs(linear[baseline[i + c]] - linear[result.pixels[i + c]]); count++; }
      }
      result.report.baselineDifferenceLinearMae255 = round(difference / count * 255);
      cases.push(result.report);
    }
    return { source, target, frame: [width, height], intervalMs: 50, exposureSeconds: .004, cases };
  }, base);
  console.log(JSON.stringify({ ...report, cases: report.cases.map(({ history, ...summary }) => {
    const failedIndex = history.findIndex(job => job.reason === "match");
    if (failedIndex >= 0 && history[failedIndex + 1]) summary.retryDelayMs =
      (history[failedIndex + 1].frame - history[failedIndex].frame) * report.intervalMs;
    return summary;
  }) }));
  for (const result of report.cases) {
    assert.ok(result.reliable >= 3, `${result.variant}: fewer than three reliable registrations`);
    assert.ok(result.coverage > .98, `${result.variant}: insufficient atlas coverage`);
    assert.equal(result.trackingDecoded, 0, "Sensor capture must not decode the image-only tracking frame");
    assert.equal(result.settledDuringSweep, 0, "Continuous scan must not depend on a stable pause");
    assert.ok(result.history.slice(1).every(job => job.requireVisual), "Moving frames must require visual registration");
    assert.ok(result.seamSamples > 100, "Seam metric must cover actual independently captured boundaries");
    // Units are linear-light error times 255, not gamma-encoded RGB distance.
    // These tolerances retain margin for interpolation/rounding across engines.
    assert.ok(result.rmseLinear255 < 4, `${result.variant}: excessive geometric/color error`);
    assert.ok(result.seamLinearMae255 < 1.5 && result.seamLinearP95_255 < 4,
      `${result.variant}: exposure/color seam regression`);
    assert.ok(result.baselineDifferenceLinearMae255 < 2,
      `${result.variant}: AE, tint or moving-object normalization regression`);
    if (result.variant === "late-gap") {
      assert.ok(result.gap.release, "Small hole must be released after 98% coverage");
      assert.ok(result.gap.finalMissing < 40, "Continuous late-stage scan did not repair the small hole");
    }
    if (result.variant === "weak-match-retry") {
      const failed = result.history.findIndex(job => job.reason === "match");
      assert.ok(failed >= 0, "Untextured continuous capture must be rejected");
      const before = result.history[failed], after = result.history[failed + 1];
      assert.ok(after?.matched && after.accepted, "Failed continuous capture must recover without stopping");
      assert.equal((after.frame - before.frame) * report.intervalMs, 150,
        "110 ms retry must fire at the first eligible 50 ms tick");
    }
  }
} finally {
  await browser?.close();
  if (server) await new Promise(resolve => server.close(resolve));
}
