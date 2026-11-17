import test from "node:test";
import assert from "node:assert/strict";
import {
  Camera,
  FrameMotion,
  captureGeometry,
  captureExposure,
  parseTime,
  referenceSignature,
  trackExposure,
} from "./camera.js";
import { exposureEV } from "./core.js";

test("motion falls back immediately and waits for a new quiet interval", () => {
  const motion = new FrameMotion();
  function observe(time, value, region = false, width = 160) {
    const height = 90;
    const data = new Uint8Array(width * height * 4).fill(100);
    for (let y = 0; y < (region ? 30 : height); y++)
      for (let x = 0; x < (region ? 45 : width); x++)
        data.fill(value, 4 * (y * width + x), 4 * (y * width + x) + 3);
    return motion.observe({ width, height, data }, time);
  }
  assert.equal(observe(0, 100), false);
  for (let t = 150; t <= 750; t += 150) assert.equal(observe(t, 101), false);
  assert.equal(observe(900, 100), true);
  assert.equal(observe(1050, 160, true), false);
  for (let t = 1200; t < 2000; t += 150) observe(t, 160, true);
  assert.equal(observe(2100, 160, true), true);
  assert.equal(observe(3200, 160, true), false); // resumed after a capture gap
  assert.equal(observe(3350, 160, true, 90), false); // rotated stream
});

test("crop dimensions follow native pixels, partial fields and low-resolution video", () => {
  const source = { x: 1, y: .75 };
  const half = captureGeometry(1920, 1080, source, { x: .5, y: .375 }, .5);
  assert.deepEqual(half, { w: 480, h: 270, args: [480, 270, 960, 540, 0, 0, 480, 270] });
  const wide = captureGeometry(1920, 1080, source, { x: 2, y: .75 });
  assert.deepEqual(wide, { w: 1920, h: 540, args: [0, 0, 1920, 1080, 480, 0, 960, 540] });
  const low = captureGeometry(320, 240, null, null, 2);
  assert.deepEqual([low.w, low.h], [320, 240]);
});

test("video analysis stays small and native readbacks are on demand and reused", () => {
  const reads = [];
  const original = globalThis.document;
  globalThis.document = { createElement: () => ({ getContext: () => ({
    drawImage() {}, clearRect() {},
    getImageData(_x, _y, width, height) {
      reads.push([width, height]);
      return { width, height, data: new Uint8Array(width * height * 4) };
    },
  }) }) };
  try {
    const camera = new Camera({ videoWidth: 1920, videoHeight: 1080, readyState: 2 });
    camera.stream = {};
    camera.schedule = () => {};
    camera.frame(1);
    assert.deepEqual(reads, [[640, 360]]);
    camera.addEventListener("frame", () => {
      const a = camera.capturePreview();
      assert.equal(camera.capturePreview(), a);
      assert.equal(camera.capturePreview(null, null, .5).width, 960);
    }, { once: true });
    camera.frame(151);
    assert.deepEqual(reads.slice(1), [[640, 360], [1920, 1080], [960, 540]]);
    camera.previewCanvas(); // GPU source does not read pixels
    assert.equal(reads.length, 4);
  } finally {
    if (original === undefined) delete globalThis.document;
    else globalThis.document = original;
  }
});

test("time input accepts seconds and fractions, rejects invalid denominators", () => {
  assert.equal(parseTime("1/125"), 1 / 125);
  assert.equal(parseTime("12.5"), 12.5);
  for (const value of ["", "0", "-1", "1/0", "a", "1/2/3"])
    assert.ok(Number.isNaN(parseTime(value)));
});

test("default automatic metering consumes continuous AE and follows shutter and ISO changes", () => {
  const settings = { exposureMode: "continuous", exposureTime: 100, iso: 100 };
  const profile = { mode: "auto", aperture: 2 };
  assert.equal(captureExposure({}, profile), null);
  const first = captureExposure(settings, profile);
  const second = captureExposure({ ...settings, exposureTime: 200 }, profile);
  assert.equal(first.t, 0.01);
  assert.ok(Math.abs(exposureEV(first) - exposureEV(second) - 1) < 1e-12);
  const higherISO = captureExposure({ ...settings, iso: 200 }, profile);
  assert.ok(Math.abs(exposureEV(first) - exposureEV(higherISO) - 1) < 1e-12);
  assert.deepEqual(
    captureExposure(settings, { ...profile, mode: "reported" }),
    first,
  );
  assert.equal(captureExposure(settings, { ...profile, mode: "fixed" }), null);
  assert.deepEqual(
    captureExposure(
      { ...settings, exposureMode: "manual" },
      { ...profile, mode: "fixed" },
    ),
    { ...first, source: "固定参数 · 预览估测" },
  );
  assert.equal(captureExposure(settings, { mode: "reported" }), null);
});

test("phone screenshot metadata uses fractional 100 microsecond units and acquisition ISO", () => {
  const settings = {
    exposureMode: "continuous",
    exposureTime: 299.94234,
    iso: 250,
  };
  const profile = {
    mode: "auto",
    aperture: 2.3,
    seconds: 1 / 1000,
    iso: 100,
    confirmed: false,
  };
  const capture = captureExposure(settings, profile);
  assert.ok(Math.abs(capture.t - 0.029994234) < 1e-12);
  assert.equal(capture.iso, 250);
  assert.equal(capture.a, 2.3);
  assert.match(capture.source, /AE/);
  assert.ok(
    Math.abs(
      exposureEV(capture) - Math.log2(((2.3 ** 2 / 0.029994234) * 100) / 250),
    ) < 1e-12,
  );
});

test("missing or nonfinite camera parameters cannot reuse stale manual inputs", () => {
  const profile = { mode: "auto", aperture: 2.3, seconds: 1 / 125, iso: 100 };
  for (const settings of [
    {},
    { exposureTime: 100 },
    { iso: 250 },
    ...[0, -1, NaN, Infinity, undefined].flatMap((value) => [
      { exposureTime: value, iso: 250 },
      { exposureTime: 100, iso: value },
    ]),
  ]) {
    assert.equal(trackExposure(settings), null);
    assert.equal(captureExposure(settings, profile), null);
  }
});

test("fixed metadata and confirmed manual camera exposure use acquisition ISO", () => {
  const settings = { exposureMode: "manual", exposureTime: 200, iso: 80 };
  assert.deepEqual(captureExposure(settings, { mode: "auto", aperture: 1.8 }), {
    t: 0.02,
    a: 1.8,
    iso: 80,
    source: "固定参数 · 预览估测",
  });
  assert.equal(
    captureExposure({}, { mode: "manual", aperture: 2, seconds: 0.1, iso: 80 }),
    null,
  );
  assert.equal(
    captureExposure(
      {},
      { mode: "manual", aperture: 2, seconds: 0.1, iso: 80, confirmed: true },
    ).iso,
    80,
  );
  const manual = {
    mode: "manual",
    aperture: 2,
    seconds: 0.1,
    iso: 80,
    confirmed: true,
  };
  assert.equal(captureExposure({ exposureMode: "continuous" }, manual), null);
  assert.equal(
    captureExposure(
      { exposureMode: "manual", exposureTime: 200, iso: 80 },
      manual,
    ),
    null,
  );
  assert.equal(
    captureExposure(
      { exposureMode: "manual", exposureTime: 1000, iso: 160 },
      manual,
    ),
    null,
  );
  assert.equal(
    captureExposure(
      { exposureMode: "manual", exposureTime: 1000, iso: 80 },
      manual,
    ).t,
    0.1,
  );
});

test("known-EV references expire when metadata or acquisition inputs change", () => {
  const settings = { exposureMode: "manual", exposureTime: 1000, iso: 80 };
  const profile = { aperture: 2, seconds: 0.1, iso: 80 };
  const signature = referenceSignature(settings, profile);
  assert.equal(
    referenceSignature({ ...settings, width: 640 }, { ...profile }),
    signature,
  );
  assert.notEqual(
    referenceSignature({ ...settings, exposureTime: 500 }, profile),
    signature,
  );
  assert.notEqual(
    referenceSignature(settings, { ...profile, aperture: 2.8 }),
    signature,
  );
  assert.notEqual(
    referenceSignature(settings, { ...profile, iso: 160 }),
    signature,
  );
});
