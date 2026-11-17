import test from "node:test";
import assert from "node:assert/strict";
import { Panorama, yawPitch } from "./panorama.js";
import { PanoramaEngine } from "./panorama-engine.js";

const options = { target: { x: 1, y: .42 }, source: { x: .7, y: .53 }, width: 128 };
const identity = yawPitch(0).map(value => value || 0);
const never = () => assert.fail("No pixel readback is allowed here");
const fit = () => ({ a: 1, b: 0, x: 0, y: 0, matched: true });
function frame(gradient = false) {
  const width = 128, height = 96, data = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = (y * width + x) * 4, value = gradient ? 120 + 40 * y / height : 140;
    data[i] = data[i + 1] = data[i + 2] = value;
    data[i + 3] = 255;
  }
  return { width, height, data };
}
function snapshot(writer) {
  return { pixels: writer.pixels.slice().buffer, count: writer.count, captures: writer.captures,
    registration: writer.lastRegistration, gains: writer.lastGains, localGains: writer.lastLocalGains,
    disconnected: writer.disconnected, reason: writer.writeReason, processingMs: 1 };
}
const tick = (pano, time, angle, extra = {}) => pano.capture({
  pose: yawPitch(angle), time, cameraEV: 8, exposureSeconds: .004, frame: frame(), ...extra,
});

test("one in-flight job keeps its captured pose while the live view continues moving", async () => {
  let release, jobs = 0, captured, reads = 0;
  const gate = new Promise(resolve => { release = resolve; });
  const writer = new Panorama(options);
  const pano = new Panorama({ ...options, submit: async job => {
    jobs++; captured = job;
    await gate;
    writer.orientation = job.orientation;
    writer.cameraEV = job.baseEV;
    writer.writeFrame(job.frame, job.cameraEV);
    return snapshot(writer);
  } });
  pano.setCenter(identity);
  tick(pano, 0, 0, { frame: () => { reads++; return frame(); } });
  await Promise.resolve();
  for (let i = 1; i <= 10; i++) tick(pano, i * 50, i * .02, { frame: never });
  assert.equal(pano.busy, true);
  assert.equal(jobs, 1);
  assert.equal(reads, 1);
  assert.deepEqual(captured.orientation, identity);
  const current = [...pano.orientation];
  release(); await pano.pending;
  assert.equal(pano.busy, false);
  assert.deepEqual(pano.orientation, current, "Snapshot must not rewind the current sensor pose");
  assert.equal(pano.captures, 1);
  assert.equal(pano.cameraEV, 8);
});

test("disposed panorama ignores a pending atlas snapshot and callback", async () => {
  let release, updates = 0;
  const gate = new Promise(resolve => { release = resolve; });
  const writer = new Panorama(options);
  writer.cameraEV = 8; writer.writeFrame(frame(), 8);
  const pano = new Panorama({ ...options, submit: async () => { await gate; return snapshot(writer); },
    onUpdate: () => updates++ });
  tick(pano, 0, 0);
  pano.dispose();
  release(); await pano.pending;
  assert.equal(pano.count, 0);
  assert.equal(updates, 0);
});

test("worker failure clears busy state and requests the stationary fallback", async () => {
  let updates = 0;
  const pano = new Panorama({ ...options, submit: async () => { throw new Error("Worker unavailable"); },
    onUpdate: () => updates++ });
  tick(pano, 0, 0); await pano.pending;
  assert.equal(pano.busy, false);
  assert.equal(pano.workerFailed, true);
  assert.equal(pano.retryStationary, true);
  assert.equal(updates, 1);
  assert.equal(pano.count, 0);
});

test("unknown shutter waits for a pause; long shutter rejects excessive angular motion", () => {
  for (const exposureSeconds of [null, .25]) {
    const pano = new Panorama({ ...options, aligner: fit });
    assert.equal(tick(pano, 0, 0).accepted, true); // Center initialization is a deliberate exception.
    for (let i = 1; i <= 12; i++) {
      const result = tick(pano, i * 50, i * .02, { exposureSeconds, frame: never });
      assert.equal(result.accepted, false);
    }
    if (exposureSeconds == null) {
      for (const time of [650, 750, 850])
        assert.equal(tick(pano, time, .24, { exposureSeconds, frame: never }).accepted, false);
      assert.equal(tick(pano, 950, .24, { exposureSeconds }).accepted, true);
    } else assert.equal(tick(pano, 650, .24, { exposureSeconds }).accepted, true);
    assert.equal(pano.cameraEV, 8);
  }
});

test("failed clear moving frame retries at 110 ms without waiting 350 ms", () => {
  let calls = 0, reads = 0;
  const pano = new Panorama({ ...options, aligner: () => ++calls === 1 ? null : fit() });
  tick(pano, 0, 0);
  for (let i = 1; i <= 9; i++) tick(pano, i * 50, i * .02, {
    frame: () => { reads++; return frame(); },
  });
  assert.equal(calls, 1);
  assert.equal(pano.captures, 1);
  const before = pano.pixels.slice();
  assert.equal(tick(pano, 559, .18, { frame: never }).accepted, false);
  assert.deepEqual(pano.pixels, before, "Rejected fit cannot paint pixels");
  const retried = tick(pano, 560, .18);
  assert.equal(retried.settling, true);
  assert.equal(retried.accepted, true);
  assert.equal(calls, 2);
  assert.equal(reads, 1);
});

test("90 percent coverage enables a 0.45-degree moving edge keyframe", () => {
  const pano = new Panorama({ ...options, aligner: fit });
  pano.setCenter(identity);
  const pixels = new Uint8ClampedArray(pano.pixels.length);
  let count = 0;
  for (let p = 0; p < pixels.length / 4; p++) {
    if (p % 11 === 0) continue;
    pixels.set([140, 140, 140, 255], p * 4); count++;
  }
  pano.applySnapshot({ pixels: pixels.buffer, count, captures: 1, disconnected: false });
  pano.cameraEV = 8; pano.lastAttempt = identity; pano.anchor = identity; pano.since = 0; pano.lastTime = 0;
  let reads = 0;
  const result = tick(pano, 50, .01, { frame: () => { reads++; return frame(); } });
  assert.equal(result.settling, true);
  assert.equal(reads, 1, "0.57 degrees must pass the late-stage threshold");
  assert.equal(result.accepted, true);
});

test("quadrant gains compensate a smooth vertical brightness gradient in linear RGB", () => {
  const pano = new Panorama(options);
  pano.setCenter(identity);
  pano.cameraEV = 8;
  pano.writeFrame(frame(), 8);
  pano.orientation = yawPitch(.43);
  pano.writeFrame(frame(true), 8);
  assert.ok(pano.lastLocalGains[0][0] > pano.lastLocalGains[2][0]);
  const image = pano.view(identity).image;
  for (const y of [.2, .8]) {
    const at = (Math.floor(image.height * y) * image.width + Math.floor(image.width * .06)) * 4;
    assert.equal(image.data[at + 3], 255);
    assert.ok(Math.abs(image.data[at] - 140) < 8, `Corrected code ${image.data[at]}`);
  }
});

test("visibility resume preserves the original sensor origin, including a stale first event", () => {
  const pano = new Panorama(options);
  pano.setCenter(identity);
  tick(pano, 0, .175, { collect: false, frame: never, trackingFrame: never });
  pano.resumeTracking({ preserveSensorOrigin: true });
  pano.capture({ time: 2000, pose: null, frame: never, trackingFrame: never });
  assert.deepEqual(pano.sensorOrigin, identity);
  const pose = yawPitch(.61).map(value => value || 0);
  tick(pano, 2050, .61, { collect: false, frame: never, trackingFrame: never });
  assert.deepEqual(pano.orientation, pose, "Background movement must not redefine zero orientation");
  const aim = pano.guide().aim;
  assert.ok(Math.abs(aim.x - (.5 - Math.tan(.61) / (2 * pano.bounds.x))) < 1e-10);
});

test("visibility wait times out into visual tracking if the sensor disappears", () => {
  const pano = new Panorama(options);
  pano.setCenter(identity);
  pano.resumeTracking({ preserveSensorOrigin: true });
  pano.capture({ time: 0, collect: false, frame: never, trackingFrame: never });
  pano.capture({ time: 999, collect: false, frame: never, trackingFrame: never });
  let reads = 0;
  pano.capture({ time: 1000, collect: false, frame: never,
    trackingFrame: () => { reads++; return frame(); } });
  assert.equal(reads, 1);
  assert.equal(pano.sensorOrigin, null);
});

test("engine preserves non-enumerable ImageData dimensions and transfers a copy", async () => {
  let message, transfer;
  const worker = { postMessage(data, transfers) {
    message = data; transfer = transfers;
    queueMicrotask(() => this.onmessage({ data: { id: data.id, result: { done: true } } }));
  }, terminate() {} };
  const engine = new PanoramaEngine(worker), data = new Uint8ClampedArray(8 * 4);
  const image = Object.defineProperties({ data }, {
    width: { get: () => 4 }, height: { get: () => 2 },
  });
  const result = await engine.submit({ frame: image, orientation: identity });
  assert.equal(result.done, true);
  assert.deepEqual([message.payload.frame.width, message.payload.frame.height], [4, 2]);
  assert.notEqual(message.payload.frame.data, data);
  assert.equal(data.byteLength, 32);
  assert.equal(transfer[0], message.payload.frame.data.buffer);
  engine.close();
});

test("aborting preparation terminates its worker and rejects the initialization", async () => {
  const previous = globalThis.Worker;
  let instance;
  globalThis.Worker = class {
    constructor() { instance = this; }
    postMessage() {}
    terminate() { this.terminated = true; }
  };
  try {
    const controller = new AbortController();
    const pending = PanoramaEngine.create(options, 2000, controller.signal);
    controller.abort();
    await assert.rejects(pending, /closed/);
    assert.equal(instance.terminated, true);
  } finally {
    if (previous === undefined) delete globalThis.Worker;
    else globalThis.Worker = previous;
  }
});
