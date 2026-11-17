"""Read cached manufacturer PDFs; emit auditable numeric sources without embedding manuals.
Requires Python 3, pymupdf, numpy and Pillow. Run with --cache pointing at silvergrain-research.
"""

import argparse, csv, hashlib, json, math, os
from pathlib import Path
import numpy as np
import pymupdf as fitz
from PIL import Image
from io import BytesIO

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tools/spektrafilm_sources"
OUT.mkdir(exist_ok=True)


def paths(page, index):
    result = []
    pts = []
    last = None
    for item in page.get_drawings()[index]["items"]:
        if last is not None and abs(item[1] - last) > 0.2:
            result.append(np.array(pts))
            pts = []
        a = np.array([list(q) for q in item[1:]])
        t = np.linspace(0, 1, 101)[:, None]
        if item[0] == "c":
            pts.extend(
                (1 - t) ** 3 * a[0]
                + 3 * t * (1 - t) ** 2 * a[1]
                + 3 * t * t * (1 - t) * a[2]
                + t**3 * a[3]
            )
        elif item[0] == "l":
            pts.extend(a)
        else:
            raise ValueError(item[0])
        last = item[-1]
    if pts:
        result.append(np.array(pts))
    return result


def sample(a, xmap, ymap, step):
    a = a[np.argsort(a[:, 0])]
    xx = xmap(a[:, 0])
    yy = ymap(a[:, 1])
    grid = np.unique(
        np.r_[xx[0], np.arange(math.ceil(xx[0] / step) * step, xx[-1], step), xx[-1]]
    )
    return [[round(x, 5), round(y, 5)] for x, y in zip(grid, np.interp(grid, xx, yy))]


def save(name, data):
    (OUT / name).write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def extract(cache):
    docs = cache / "docs"
    source = lambda doc, url: {
        "url": url,
        "sha256": hashlib.sha256((docs / (doc + ".pdf")).read_bytes()).hexdigest(),
    }
    d = fitz.open(docs / "0395e7b5.pdf")
    p = d[3]
    density = {
        k: sample(
            a,
            lambda x: (x - 231.70276) / 41.92262,
            lambda y: (389.7984 - y) / 40.47263,
            0.05,
        )
        for k, a in zip(["blue", "green", "red"], paths(p, 100))
    }
    # Chart log sensitivity has only a one-decade scale. Preserve common vertical normalization.
    specs = paths(p, 298) + paths(p, 300)
    peak = min(a[:, 1].min() for a in specs)
    spectral = {
        k: sample(
            a,
            lambda x: 400 + (x - 344.868) / 0.517245,
            lambda y: (peak - y) / 49.951,
            5,
        )
        for k, a in zip(["blue", "green", "red"], specs)
    }
    spectral["cyan"] = sample(
        paths(p, 299)[0],
        lambda x: 400 + (x - 344.868) / 0.517245,
        lambda y: (peak - y) / 49.951,
        5,
    )
    dye = {
        k: sample(
            a,
            lambda x: 400 + (x - 342.834) / 0.4205,
            lambda y: (628.929 - y) / 40.544,
            5,
        )
        for k, a in zip(["midscale", "minimum"], paths(p, 554))
    }
    fuji = dict(
        stock="SUPERIA X-TRA 800",
        iso=800,
        document_id="0395e7b5",
        page=4,
        source=source(
            "0395e7b5", "https://125px.com/docs/film/fuji/superia_xtra800_datasheet.pdf"
        ),
        gray_log10_H=math.log10(10 / 800),
        gray_reference="LightData placement assumption H=10/ISO lux seconds, consistent with existing Fuji digitizations; not a manufacturer absolute middle-gray calibration.",
        density_metadata={
            "figure": 15,
            "axes": ["log10 H (lux seconds)", "Status M density"],
            "exposure": "Daylight, 1/125 s",
            "process": "CN-16 / C-41",
            "method": "Native cubic paths, separate blue/green/red subpaths",
        },
        density_curves=density,
        sensitivity_metadata={
            "figure": 16,
            "axes": ["wavelength nm", "relative log10 sensitivity"],
            "definition": "Exposure reciprocal at Status M D-min + 1.0",
            "reference": "Shared zero at highest RGB peak; cyan recorded separately, not automatically summed into green. Outside drawn domains unreported.",
        },
        spectral_sensitivity=spectral,
        dye_metadata={
            "figure": 18,
            "description": "Total diffuse density for midscale neutral and D-min; individual dye extinction spectra unavailable.",
        },
        spectral_dye_density=dye,
        verification={
            "density_at_log10_H_minus_2": {
                k: float(np.interp(-2, np.array(v)[:, 0], np.array(v)[:, 1]))
                for k, v in density.items()
            },
            "method": "Axes independently checked on rendered page; digitized native vector paths.",
        },
    )
    save("superia-xtra800.json", fuji)
    d = fitz.open(docs / "11731318.pdf")
    im = np.asarray(Image.open(BytesIO(d.extract_image(48)["image"])).convert("L"))
    guide = np.array(
        [
            (12, 310),
            (100, 310),
            (145, 309),
            (180, 303),
            (220, 285),
            (260, 260),
            (300, 230),
            (340, 197),
            (380, 163),
            (400, 140),
        ]
    )
    pts = []
    for x in range(13, 400):
        if (
            min(abs(x - k) for k in [11, 56, 101, 146, 191, 236, 282, 327, 372, 417])
            < 2.5
        ):
            continue
        gy = np.interp(x, guide[:, 0], guide[:, 1])
        ys = np.arange(max(43, int(gy) - 6), min(316, int(gy) + 7))
        ys = ys[
            np.abs(ys[:, None] - np.array([42, 88, 133, 179, 225, 270, 317])).min(1) > 2
        ]
        ink = ys[im[ys, x] < 110]
        if len(ink):
            pts.append([x, float(np.median(ink))])
    a = np.array(pts)
    samples = sample(a, lambda x: (x - 11) / 90.25, lambda y: (317 - y) / 91.5, 0.05)
    q = np.array(samples)
    low = np.interp(q[0, 1] + 0.1, np.maximum.accumulate(q[:, 1]), q[:, 0])
    gray = low + math.log10(2) * 10 / 3
    panf = dict(
        stock="PAN F PLUS",
        iso=50,
        document_id="11731318",
        page=4,
        source=source(
            "11731318",
            "https://www.ilfordphoto.com/amfile/file/download/file/1905/product/700/",
        ),
        gray_log10_H=float(gray),
        gray_reference="Relative exposure axis. App placement: toe density + 0.10D, then +3 1/3 stops. Upper endpoint is graph extent, not proven saturation.",
        density_metadata={
            "axes": ["relative log10 exposure", "visual density"],
            "process": "ILFOTEC HC (1+31), 4 min at 20 C, intermittent agitation",
            "format": "Roll film, representative of 35mm according to manufacturer",
            "method": "Embedded image 48, 494x373 px; curve trace avoids grid lines; logH 0 at x11, scale90.25 px/decade; D0 y317, scale91.5 px/D",
        },
        density_curves={"visual": samples},
        verification={
            "method": "Rendered chart and processing caption checked; raster guide constrained to printed curve",
            "sample_count": len(samples),
            "density_at_relative_logH_3": float(np.interp(3, q[:, 0], q[:, 1])),
        },
    )
    save("panf-plus.json", panf)
    return [panf, fuji]


def integrate(sources):
    file = ROOT / "app/src/main/resources/studio/lightdata/density.csv"
    with file.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        fields = reader.fieldnames
        rows = list(reader)
    rows = [r for r in rows if r["stock"] not in [s["stock"] for s in sources]]
    for s in sources:
        channel = "visual" if s["stock"] == "PAN F PLUS" else "green"
        pts = np.array(s["density_curves"][channel])
        ev = (pts[:, 0] - s["gray_log10_H"]) / math.log10(2)
        den = np.maximum.accumulate(pts[:, 1])
        lo = float(np.interp(den[0] + 0.1, den, ev))
        hi = float(np.interp(den[-1] - 0.1, den, ev))
        assert lo < 0 < hi, (s["stock"], lo, hi)
        proc = (
            s["density_metadata"]["process"]
            + "；"
            + (
                "中灰估算：趾部+0.10D处上移3⅓档"
                if channel == "visual"
                else "中灰假设 H=10/ISO lx·s"
            )
            + "；界限取图内两端各收0.10D，亮端非实测饱和"
        )
        for x, y in zip(ev, pts[:, 1]):
            rows.append(
                dict(
                    zip(
                        fields,
                        [
                            s["stock"],
                            "ILFORD" if channel == "visual" else "Fujifilm",
                            s["iso"],
                            s["document_id"],
                            s["page"],
                            proc,
                            channel,
                            round(lo, 3),
                            round(hi, 3),
                            round(float(x), 5),
                            round(float(y), 5),
                        ],
                    )
                )
            )
    with file.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument(
        "--cache",
        type=Path,
        default=Path(os.environ.get("TEMP", ".")) / "silvergrain-research",
    )
    p.add_argument("--integrate", action="store_true")
    args = p.parse_args()
    sources = extract(args.cache)
    if args.integrate:
        integrate(sources)
    print(
        "Extracted",
        [
            (s["stock"], {k: len(v) for k, v in s["density_curves"].items()})
            for s in sources
        ],
    )
