# OpenCV.js 4.12.0

`opencv-4.12.0.js` is the unmodified, self-contained JavaScript / embedded WASM distribution from `@techstark/opencv-js@4.12.0-release.1`.

| Item | Value |
| --- | --- |
| Downloaded | 2026-11-18 |
| Distribution URL | https://cdn.jsdelivr.net/npm/@techstark/opencv-js@4.12.0-release.1/dist/opencv.js |
| Package source | https://github.com/TechStark/opencv-js/tree/v4.12.0-release.1 |
| OpenCV source | https://github.com/opencv/opencv/tree/4.12.0 |
| Bytes | 10,872,779 |
| SHA-256 | `bd0c3e6448043de04f6a64a12cb7b759f78c3ab8f7c35c9f2e0f71c88bb17103` |
| Modifications | None |
| OpenCV license | Apache-2.0; [unmodified text](LICENSE-OpenCV.txt), [source](https://raw.githubusercontent.com/opencv/opencv/4.12.0/LICENSE) |
| Distribution license | Apache-2.0; [unmodified text](LICENSE-TechStark-opencv-js.txt), [source](https://raw.githubusercontent.com/TechStark/opencv-js/v4.12.0-release.1/LICENSE) |

The OpenCV distribution and its upstream dependencies retain their respective copyrights. The checked-in license texts are upstream copies, not the LightData application license. The binary is served locally; alignment does not contact the CDN at runtime. It includes WASM in the JavaScript, so no additional `.wasm` download is required.

## Runtime contract

The classic Worker `importScripts()` path was verified in Chromium using `node web/opencv-aligner-check.mjs`. `Mat`, `erode`, `goodFeaturesToTrack`, `calcOpticalFlowPyrLK`, and `findHomography` are available; `estimateAffinePartial2D` is **not** exported by this build. `opencv-aligner.js` therefore uses homography RANSAC only to select inlier points and independently fits a strict four-parameter similarity. It never applies a projective transformation to the image.

The global `self.cv` is a legacy Emscripten thenable, not a native Promise: its `.then()` callback receives the same Module object. **Do not directly `await self.cv`**, which repeatedly assimilates that self-resolving thenable. Wrap the callback result in a plain object:

```js
importScripts("./vendor/opencv-4.12.0.js");
const ready = await new Promise(resolve => self.cv.then(module => resolve({ cv: module })));
const cv = ready.cv;
const { alignOverlap } = await import("./opencv-aligner.js");
```

Resolve these URLs relative to the actual Worker location; the check script uses absolute same-origin URLs for its Blob Worker. Wait for initialization before calling alignment.

Inputs are equally sized single-channel `Uint8Array` images with a maximum edge of 256 pixels, plus an equally sized common valid-region mask. Invalid / transparent projection regions must have a zero mask. Returned parameters map **reference to incoming** pixels:

```text
incomingX = a * referenceX - b * referenceY + x
incomingY = b * referenceX + a * referenceY + y
```

The caller owns all input buffers. The function deletes every temporary CV Mat, including on errors. Browser checks include real-WASM translation, rotation/scale, inverse mapping, occlusion, independent moving outliers, rejection of flat/localized scenes and excessive transforms, and forced-exception allocation cleanup. Performance numbers are reported by the check script for the current machine rather than treated as a mobile-device guarantee.
