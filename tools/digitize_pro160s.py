"""Digitize Fuji AF3-203U page 8. Usage: python tools/digitize_pro160s.py 541caff9.pdf"""

import argparse
import hashlib
import json
import math
from pathlib import Path
from zlib import crc32

import numpy as np
import pymupdf


def axis(values, positions, labels):
    """Interpolate between printed ticks, extending only to the end of the actual ink."""
    slope0 = (labels[1] - labels[0]) / (positions[1] - positions[0])
    slope1 = (labels[-1] - labels[-2]) / (positions[-1] - positions[-2])
    return np.where(
        values < positions[0],
        labels[0] + slope0 * (values - positions[0]),
        np.where(
            values > positions[-1],
            labels[-1] + slope1 * (values - positions[-1]),
            np.interp(values, positions, labels),
        ),
    )


def trace(items):
    samples = []
    t = np.linspace(0, 1, 161)[:, None]
    for kind, p0, p1, p2, p3 in items:
        assert kind == "c"
        pts = [np.array([p.x, p.y]) for p in (p0, p1, p2, p3)]
        if samples:
            assert np.allclose(samples[-1][-1], pts[0], atol=0.01)
        samples.append(
            (1 - t) ** 3 * pts[0]
            + 3 * (1 - t) ** 2 * t * pts[1]
            + 3 * (1 - t) * t**2 * pts[2]
            + t**3 * pts[3]
        )
    xy = np.concatenate(samples)
    assert np.all(np.diff(xy[:, 0]) >= -1e-6)
    return xy.T


def sample(curve, x_axis, y_axis, step):
    px, py = trace(curve)
    x, y = x_axis(px), y_axis(py)
    grid = np.unique(
        np.r_[
            x[0],
            np.arange(math.ceil(x[0] / step), math.floor(x[-1] / step) + 1) * step,
            x[-1],
        ]
    )
    return [[round(a, 5), round(b, 5)] for a, b in zip(grid, np.interp(grid, x, y))]


def build(path):
    raw = path.read_bytes()
    assert crc32(raw) == 0x541CAFF9, "Expected Silvergrain AF3-203U (541caff9.pdf)"
    with pymupdf.open(path) as doc:
        shapes = doc[7].get_drawings()
    # Coordinates are PDF points from the page's upper-left corner.
    hx = lambda x: axis(
        x, [84.474, 129.310, 173.419, 217.529, 261.922], [-4, -3, -2, -1, 0]
    )
    density_y = lambda y: (283.52 - y) / 44.408
    sx = lambda x: axis(x, [344.138, 403.464, 461.714, 520.485], [400, 500, 600, 700])
    dx = lambda x: axis(x, [333.028, 395.35, 457.783, 520.69], [400, 500, 600, 700])
    dye_y = lambda y: (556.912 - y) / 65.616
    d = shapes[68]["items"]
    density = {
        ch: sample(d[start : start + 2], hx, density_y, 0.05)
        for ch, start in (("blue", 0), ("green", 2), ("red", 4))
    }
    s = shapes[218]["items"]
    traces = {
        "blue": s[:9],
        "green": s[9:16],
        "red": s[16:],
        "cyan": shapes[219]["items"],
    }
    # Absolute log sensitivity has no ordinate labels. The bracket on the
    # original measures one decade; use one common zero for all four curves.
    peak_y = min(trace(commands)[1].min() for commands in traces.values())
    sy = lambda y: (peak_y - y) / (223.485 - 164.582)
    spectra = {ch: sample(commands, sx, sy, 5) for ch, commands in traces.items()}
    dyes = shapes[553]["items"]
    dye = {
        ch: sample(commands, dx, dye_y, 5)
        for ch, commands in (("midscale", dyes[:8]), ("minimum", dyes[8:]))
    }

    def read(series, x):
        xx, yy = np.array(series).T
        return float(np.interp(x, xx, yy))

    # Independent visual readings from the labelled curves, deliberately
    # rounded to what can be resolved on the printed plot.
    for ch, near in (("red", 0.96), ("green", 1.51), ("blue", 1.79)):
        assert abs(read(density[ch], -1) - near) < 0.05
    for ch, nm in (("blue", 470), ("green", 555), ("red", 625), ("cyan", 520)):
        assert abs(max(spectra[ch], key=lambda pair: pair[1])[0] - nm) <= 5
    assert abs(read(dye["minimum"], 500) - 0.88) < 0.04
    assert abs(read(dye["midscale"], 500) - 1.65) < 0.04
    return {
        "stock": "PRO 160S",
        "iso": 160,
        "document_id": "541caff9",
        "page": 8,
        "source": {
            "manufacturer": "Fuji Photo Film Co., Ltd.",
            "bulletin": "AF3-203U",
            "title": "FUJICOLOR PRO 160 S PROFESSIONAL",
            "url": "https://125px.com/docs/film/fuji/AF3-203U_Pro160S_Product_Information_Bulletin.pdf",
            "sha256": hashlib.sha256(raw).hexdigest(),
        },
        "gray_log10_H": -1.1,
        "gray_reference": "LightData placement assumption; no manufacturer middle-gray exposure is marked. EV=(log10_H-gray_log10_H)/log10(2).",
        "density_metadata": {
            "figure": 19,
            "axes": ["log10 exposure H in lux seconds", "Status M density"],
            "exposure": "Daylight, 1/125 s",
            "process": "C-41",
            "grid_log10_H": 0.05,
        },
        "density_curves": density,
        "sensitivity_metadata": {
            "figure": 20,
            "axes": ["wavelength nm", "relative log10 sensitivity"],
            "definition": "Reciprocal exposure (J/cm^2) required for Status M density 1.0 above D-min; C-41.",
            "reference": "All four curves share a common zero at the highest continuous traced peak. The chart labels a one-decade interval but supplies no absolute ordinate.",
            "grid_nm": 5,
            "outside_domain": "Unreported. Samples stop at the printed stroke; do not continue endpoints as constant sensitivity.",
        },
        "spectral_sensitivity": spectra,
        "dye_metadata": {
            "figure": 22,
            "axes": ["wavelength nm", "spectral diffuse density"],
            "description": "Total density for a mid-scale neutral subject and D-min. Individual C/M/Y dye extinction spectra are unavailable in this figure.",
            "grid_nm": 5,
        },
        "spectral_dye_density": dye,
        "verification": {
            "method": "Digitized native cubic paths; axes and labels independently read from a page rendering.",
            "density_at_log10_H_minus_1_approx": {
                "red": 0.96,
                "green": 1.51,
                "blue": 1.79,
            },
            "sensitivity_peak_nm_approx": {
                "blue": 470,
                "green": 555,
                "red": 625,
                "cyan": 520,
            },
            "dye_density_at_500nm_approx": {"minimum": 0.88, "midscale": 1.65},
        },
    }


if __name__ == "__main__":
    cli = argparse.ArgumentParser()
    cli.add_argument("pdf", type=Path)
    cli.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parent
        / "spektrafilm_sources"
        / "pro160s.json",
    )
    args = cli.parse_args()
    args.out.write_text(
        json.dumps(build(args.pdf), ensure_ascii=False, indent=2) + "\n",
        encoding="utf8",
    )
