"""Audit the catalog and read mono spectral curves from the cited manufacturer PDF vectors.

Usage: python tools/prepare_derived_materials.py --docs PATH_TO_SILVERGRAIN_PDFS
The monoSensitivity points are (nm, log10 sensitivity). Outside the measured support,
the baking model can assume zero sensitivity and must identify that choice as an approximation.
"""

import argparse
import json
import math
from pathlib import Path

import numpy as np
import pymupdf

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "tools/spektrafilm_sources/derived_materials.json"

# page (1-based), vector path, x at 400 nm, points per 100 nm,
# y at zero log sensitivity, points per decade, conditions.
APX = (
    2,
    45,
    85.267,
    56.55,
    206.382,
    56.56,
    "Agfapan spectral sensitivity; lg sensitivity versus wavelength",
)
SPECTRA = {
    "APX 100": APX,
    "APX 25": APX,
    "APX 400": APX,
    "T-MAX 100": (
        8,
        78,
        134.890,
        40.192,
        574.228,
        37.867,
        "F-4016; 0.3 above D-min; 1.4 s; D-76 20 °C; diffuse visual",
    ),
    "T-MAX 400": (
        7,
        106,
        421.744,
        40.133,
        621.676,
        37.965,
        "F-4043; 0.3 above D-min; D-76 20 °C; diffuse visual",
    ),
}


# Crops in PDF points, rendered at 3x. Guide marks isolate the film's trace
# from ticks/labels and the "conventional film" comparison on the SFX chart.
# Wedge plots do not specify a radiometrically calibrated sensitivity scale.
# Preserve their relative shape with an explicitly approximate normalized-height model.
WEDGES = {
    "PAN F PLUS": (
        (50, 645, 231, 730),
        91,
        148,
        235,
        [
            (57, 234),
            (72, 204),
            (92, 179),
            (112, 145),
            (138, 124),
            (149, 117),
            (166, 128),
            (182, 120),
            (198, 116),
            (219, 135),
            (239, 140),
            (261, 125),
            (277, 116),
            (300, 116),
            (322, 109),
            (355, 103),
            (378, 98),
            (394, 105),
            (411, 131),
            (431, 128),
            (441, 140),
            (451, 169),
            (459, 208),
            (461, 230),
        ],
    ),
    "ORTHO PLUS": (
        (310, 650, 505, 745),
        100,
        154,
        250,
        [
            (31, 248),
            (55, 175),
            (75, 132),
            (96, 106),
            (120, 96),
            (165, 95),
            (210, 99),
            (245, 113),
            (270, 122),
            (296, 106),
            (321, 94),
            (337, 114),
            (347, 150),
            (352, 195),
            (354, 246),
        ],
    ),
    "SFX 200": (
        (50, 530, 335, 652),
        117,
        164,
        340,
        [
            (41, 338),
            (75, 271),
            (103, 228),
            (132, 202),
            (171, 182),
            (220, 177),
            (250, 183),
            (282, 199),
            (306, 203),
            (350, 202),
            (406, 196),
            (435, 185),
            (476, 190),
            (508, 192),
            (550, 187),
            (605, 172),
            (641, 164),
            (655, 177),
            (669, 224),
            (683, 273),
            (704, 317),
            (729, 339),
        ],
    ),
    "HP5 PLUS": (
        (55, 570, 272, 675),
        101,
        166,
        272,
        [
            (24, 270),
            (55, 220),
            (87, 173),
            (108, 154),
            (147, 142),
            (202, 135),
            (270, 143),
            (309, 133),
            (369, 111),
            (403, 112),
            (444, 122),
            (477, 118),
            (487, 128),
            (508, 211),
            (526, 270),
        ],
    ),
    "FP4 PLUS": (
        (52, 610, 289, 724),
        118,
        186,
        303,
        [
            (53, 301),
            (64, 265),
            (82, 242),
            (100, 226),
            (118, 205),
            (144, 195),
            (170, 185),
            (199, 164),
            (228, 154),
            (265, 152),
            (311, 159),
            (334, 153),
            (356, 141),
            (391, 140),
            (439, 118),
            (469, 101),
            (497, 98),
            (525, 104),
            (547, 105),
            (573, 169),
            (597, 247),
            (613, 302),
        ],
    ),
}


def wedge_spectrum(path, specification):
    rect, x400, dx100, baseline, guide = specification
    with pymupdf.open(path) as pdf:
        image = pdf[0].get_pixmap(
            matrix=pymupdf.Matrix(3, 3),
            clip=pymupdf.Rect(rect),
            colorspace=pymupdf.csGRAY,
        )
    pixels = np.frombuffer(image.samples, dtype=np.uint8).reshape(
        image.height, image.width
    )
    xs, ys = np.transpose(guide)
    low = math.ceil((400 + (xs[0] - x400) * 100 / dx100) / 5) * 5
    high = math.floor((400 + (xs[-1] - x400) * 100 / dx100) / 5) * 5
    samples = []
    for nm in range(low, high + 1, 5):
        x = round(x400 + (nm - 400) * dx100 / 100)
        expected = np.interp(x, xs, ys)
        candidates = np.where(pixels[:, x] < 128)[0]
        nearby = candidates[np.abs(candidates - expected) <= 10]
        assert len(nearby), (path, nm, x, expected)
        samples.append([nm, max(1, baseline - float(np.median(nearby)))])
    peak = max(y for _, y in samples)
    return {
        "page": 1,
        "scale": "log10 of normalized relative wedge height (approximation)",
        "conditions": "2850 K tungsten wedge spectrogram. The ordinate is treated as a relative response index; its radiometric transfer and lamp correction are unspecified. Visible-RGB approximation only.",
        "points": [[nm, round(math.log10(y / peak), 4)] for nm, y in samples],
    }


def vector_spectrum(path, specification):
    page, index, x400, dx100, y0, dy, conditions = specification
    with pymupdf.open(path) as pdf:
        items = pdf[page - 1].get_drawings()[index]["items"]
    xy = []
    for kind, *control in items:
        pts = np.array([tuple(p) for p in control])
        if kind == "l":
            xy.extend(pts)
        elif kind == "c":
            t = np.linspace(0, 1, 65)[:, None]
            xy.extend(
                (1 - t) ** 3 * pts[0]
                + 3 * t * (1 - t) ** 2 * pts[1]
                + 3 * t * t * (1 - t) * pts[2]
                + t**3 * pts[3]
            )
        else:
            raise ValueError(f"Unexpected PDF path {kind} in {path}")
    points = np.array(sorted(xy, key=lambda p: p[0]))
    nm = 400 + (points[:, 0] - x400) * 100 / dx100
    logs = (y0 - points[:, 1]) / dy
    grid = np.arange(
        math.ceil((nm.min() - 0.03) / 5) * 5,
        math.floor((nm.max() + 0.03) / 5) * 5 + 1,
        5,
    )
    assert len(grid) > 40 and np.ptp(logs) > 1
    return {
        "page": page,
        "scale": "log10 relative sensitivity",
        "conditions": conditions,
        "points": [
            [int(x), round(float(v), 4)]
            for x, v in zip(grid, np.interp(grid, nm, logs))
        ],
    }


def classify(f):
    name, info = f["name"], f["info"]
    measured = info.startswith("厂家图采样")
    mono = any(tag in info for tag in ("视觉密度", "BW neutral", "· visual ·", "黑白"))
    positive = f["dots"][0][1] > f["dots"][-1][1]
    if measured:
        assert mono or any(tag in info for tag in ("绿层", "green", "RGB均值")), name
    if positive:
        if name.startswith("KODACHROME"):
            donor = "kodak_kodachrome_64"
        elif f["brand"] == "Fujifilm":
            donor = (
                "fujifilm_velvia_100"
                if name.startswith("VELVIA")
                else "fujifilm_provia_100f"
            )
        else:
            donor = "kodak_ektachrome_100"
    elif f["brand"] == "Fujifilm" and not mono:
        donor = (
            "fujifilm_pro_400h"
            if name.startswith(("PRO ", "FUJICOLOR PRO", "New PRO"))
            else "fujifilm_c200"
        )
    else:
        donor = "kodak_portra_400"
    return {
        "document": f["document"],
        "bw": mono,
        "polarity": "positive" if positive else "negative",
        "measuredDensity": measured,
        "donor": donor if measured else None,
        "evidence": info,
    }


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--docs", type=Path, required=True)
    docs = args.parse_args().docs
    films = json.loads((ROOT / "web/data/films.json").read_text(encoding="utf8"))
    result = {}
    supplements = {}
    for file in OUTPUT.parent.glob("*.json"):
        if file != OUTPUT:
            source = json.loads(file.read_text(encoding="utf8"))
            if source.get("density_curves"):
                supplements[source["stock"]] = (file, source)
    for film in films:
        stock = film["name"]
        entry = classify(film)
        if stock in supplements:
            file, source = supplements[stock]
            assert film["document"] == source["document_id"]
            entry["measuredDensity"] = True
            entry["densitySourceFile"] = file.name
            if not entry["donor"]:
                entry["donor"] = (
                    "kodak_portra_400" if entry["bw"] else "fujifilm_xtra_400"
                )
                entry["evidence"] = (
                    f"Manufacturer density chart, p. {source['page']}, {file.name}"
                )
        if stock in SPECTRA:
            entry["monoSensitivity"] = vector_spectrum(
                docs / (film["document"] + ".pdf"), SPECTRA[stock]
            )
        if stock in WEDGES:
            entry["monoSensitivity"] = wedge_spectrum(
                docs / (film["document"] + ".pdf"), WEDGES[stock]
            )
        result[stock] = entry
    OUTPUT.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8"
    )


if __name__ == "__main__":
    main()
