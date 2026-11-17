import test from "node:test";
import assert from "node:assert/strict";
import { Camera } from "./camera.js";

function fakeCamera(t, width = 1920, height = 1080) {
  const previousDocument = globalThis.document;
  const reads = [], draws = [], callbacks = [];
  globalThis.document = {
    createElement(tag) {
      assert.equal(tag, "canvas");
      const canvas = { width: 300, height: 150 };
      const context = {
        drawImage(...args) { draws.push({ canvas, args }); },
        clearRect() {},
        getImageData(_x, _y, w, h) {
          reads.push([w, h]);
          return { width: w, height: h, data: new Uint8ClampedArray(w * h * 4) };
        },
      };
      canvas.getContext = () => context;
      return canvas;
    },
  };
  t.after(() => {
    if (previousDocument === undefined) delete globalThis.document;
    else globalThis.document = previousDocument;
  });
  const video = {
    readyState: 2, videoWidth: width, videoHeight: height,
    requestVideoFrameCallback(callback) {
      callbacks.push(callback);
      return callbacks.length;
    },
  };
  const camera = new Camera(video);
  camera.stream = {};
  return { camera, video, reads, draws, callbacks };
}

test("analysis remains enabled by default at the existing 140 ms cadence", (t) => {
  const { camera, reads, callbacks } = fakeCamera(t);
  const analyzed = [], previews = [];
  camera.settings = { iso: 125, exposureTime: 80 };
  camera.addEventListener("frame", (event) => analyzed.push(event.detail));
  camera.addEventListener("previewframe", (event) => previews.push(event.detail));
  assert.equal(camera.analysisEnabled, true);
  for (const time of [0, 16, 139, 140]) camera.frame(time);
  assert.deepEqual(reads, [[640, 360], [640, 360]]);
  assert.deepEqual(analyzed.map(({ time }) => time), [0, 140]);
  assert.deepEqual(previews.map(({ time }) => time), [0, 16, 139, 140]);
  assert.deepEqual(analyzed[0].settings, camera.settings);
  assert.notEqual(analyzed[0].settings, camera.settings);
  assert.equal(analyzed[0].still, false);
  assert.equal(callbacks.length, 4);
});

test("pose-only preview skips automatic analysis and all pixel readbacks", (t) => {
  const { camera, reads, draws, callbacks } = fakeCamera(t);
  camera.analysisEnabled = false;
  let analyses = 0, previews = 0;
  camera.captureAnalysis = () => { throw new Error("automatic analysis was called"); };
  camera.motion.observe = () => { throw new Error("visual motion was sampled"); };
  camera.addEventListener("frame", () => analyses++);
  camera.addEventListener("previewframe", () => {
    previews++;
    assert.equal(camera.previewCanvas(), camera.previewCanvas());
  });
  for (const time of [0, 16, 140, 1000]) camera.frame(time);
  assert.equal(analyses, 0);
  assert.equal(previews, 4);
  assert.equal(draws.length, 4);
  assert.deepEqual(reads, []);
  assert.equal(callbacks.length, 4);
  assert.equal(camera.inFrame, false);
});

test("analysis can resume without stopping the stream or suppressing preview", (t) => {
  const { camera, reads } = fakeCamera(t);
  const stream = camera.stream, times = [];
  let previews = 0;
  camera.addEventListener("frame", (event) => times.push(event.detail.time));
  camera.addEventListener("previewframe", () => previews++);
  camera.frame(0);
  camera.analysisEnabled = false;
  camera.frame(140);
  camera.frame(280);
  camera.analysisEnabled = true;
  camera.frame(420);
  assert.equal(camera.stream, stream);
  assert.deepEqual(times, [0, 420]);
  assert.deepEqual(reads, [[640, 360], [640, 360]]);
  assert.equal(previews, 4);
});

test("explicit tracking samples accept a smaller edge while automatic analysis is off", (t) => {
  const { camera, video, reads } = fakeCamera(t);
  camera.analysisEnabled = false;
  let events = 0;
  camera.addEventListener("frame", () => events++);
  const landscape = camera.captureAnalysis(160);
  assert.deepEqual([landscape.width, landscape.height], [160, 90]);
  video.videoWidth = 1080;
  video.videoHeight = 1920;
  const portrait = camera.captureAnalysis(320);
  assert.deepEqual([portrait.width, portrait.height], [180, 320]);
  video.videoWidth = 80;
  video.videoHeight = 60;
  const native = camera.captureAnalysis(160);
  assert.deepEqual([native.width, native.height], [80, 60]);
  assert.deepEqual(reads, [[160, 90], [180, 320], [80, 60]]);
  assert.equal(events, 0);
});

test("invalid sizes and unavailable frames cannot draw or read pixels", (t) => {
  const { camera, video, draws, reads } = fakeCamera(t);
  for (const edge of [0, -1, NaN, Infinity, "160", null])
    assert.equal(camera.captureAnalysis(edge), null);
  camera.stream = null;
  assert.equal(camera.captureAnalysis(), null);
  camera.stream = {};
  video.readyState = 1;
  assert.equal(camera.captureAnalysis(), null);
  video.readyState = 2;
  video.videoWidth = 0;
  assert.equal(camera.captureAnalysis(), null);
  assert.deepEqual(draws, []);
  assert.deepEqual(reads, []);
});
