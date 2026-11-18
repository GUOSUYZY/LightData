# Rebuilding the preview tables

LightData ships baked tables from [spektrafilm 0.3.4](https://github.com/andreavolpato/spektrafilm/tree/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc). Each inventory entry identifies its film stock, output medium, source profile, modeling assumptions and SHA-256 digest. Tables derived from additional datasheets carry the manual link. See [the coverage inventory](coverage.json), [profile license](SPEKTRAFILM_LICENSE.txt) and [change history](CHANGELOG.txt).

From the LightData repository root, use Python with the upstream core runtime dependencies and a checkout of that exact revision.

```sh
git clone https://github.com/andreavolpato/spektrafilm.git ../spektrafilm
git -C ../spektrafilm checkout 3bb2c2d2801ff68b92019cf1dbcbb133d60832bc
python tools/export_web_data.py
python tools/bake_spektrafilm.py --upstream ../spektrafilm --derived
```

The versioned source data in `tools/spektrafilm_sources/` are sufficient for the bake. To digitize from scratch, the separate extraction scripts require the cited original manuals. No PDFs, Python runtime or full spectral simulation engine are shipped in the app.

Each file contains 33³ RGB8 triplets, with red varying fastest, then green, then blue. Input grid coordinates use `u = log2(1 + max(0, x) / 0.18) / 9` per linear sRGB component, clamped to [0, 1]. Output is sRGB encoded. Both clients interpolate the cube and independently apply the film exposure placement and clipping limits.

The shipped set contains 80 upstream combinations and 104 derived entries. All 184 tables passed the increasing neutral-axis check. Against 33 off-grid direct pipeline samples per table, the mean per-channel absolute interpolation error was 0.389 on an 8-bit scale, and the largest was 8.810 (KODACHROME 64). This comparison measures lookup interpolation only. The 46 stocks without a usable density source keep their existing tone preview. See the [additional manual audit](https://github.com/RayforLoy/LightData/blob/main/tools/spektrafilm_sources/manual-audit.json).
