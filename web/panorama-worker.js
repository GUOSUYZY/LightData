// A classic worker is intentional: the pinned OpenCV UMD bundle exposes cv via
// importScripts. Application modules remain ordinary ES modules loaded below.
let panorama;
let initialized;
self.onmessage = async ({ data: { id, type, payload } }) => {
  try {
    if (type === "init") {
      initialized = (async () => {
        importScripts("./vendor/opencv-4.12.0.js");
        // This pinned build has a legacy self-resolving Module.then, rather than
        // a native Promise. Wrap the value to avoid endless thenable assimilation.
        const { cv } = await new Promise((resolve, reject) => {
          const module = typeof self.cv === "function" ? self.cv() : self.cv;
          if (typeof module?.then === "function") module.then(cv => resolve({ cv }), reject);
          else resolve({ cv: module });
        });
        const [{ Panorama }, { alignOverlap }] = await Promise.all([
          import("./panorama.js"), import("./opencv-aligner.js"),
        ]);
        if (typeof cv?.calcOpticalFlowPyrLK !== "function") throw new Error("Optical flow unavailable");
        panorama = new Panorama({ ...payload, aligner: images => alignOverlap(cv,
          images.reference, images.incoming, images.mask, images.width, images.height) });
      })();
      await initialized;
      self.postMessage({ id, result: { ready: true } });
    } else if (type === "frame") {
      await initialized;
      if (!panorama) throw new Error("Panorama not initialized");
      const start = performance.now();
      // Never read a newer sensor pose after dispatch. This frame and exposure
      // metadata are a single captured observation of the scene.
      panorama.orientation = payload.orientation;
      panorama.cameraEV = payload.baseEV;
      const accepted = panorama.writeFrame(payload.frame, payload.cameraEV,
        { requireVisual: payload.requireVisual });
      const pixels = panorama.pixels.slice();
      self.postMessage({ id, result: { pixels: pixels.buffer, accepted,
        count: panorama.count, captures: panorama.captures,
        registration: panorama.lastRegistration, gains: panorama.lastGains,
        localGains: panorama.lastLocalGains,
        disconnected: panorama.disconnected, reason: panorama.writeReason,
        processingMs: performance.now() - start } }, [pixels.buffer]);
    } else throw new Error("Unknown panorama operation");
  } catch (error) {
    self.postMessage({ id, error: error?.message || "Panorama processing failed" });
  }
};
