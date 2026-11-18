"""Extract the green sensitometric curve from Kodak H-1-5206 (April 2026), page 4.

Usage: python tools/digitize_verita.py path/to/technical-information.pdf
Requires PyMuPDF. The source URL is stored in manual-links.tsv.
"""

import csv
import sys
from pathlib import Path

import pymupdf


def green_curve(page):
    # The chart labels the middle of the three black Bezier paths "Green".
    paths = [
        p
        for p in page.get_drawings()
        if p["rect"].x1 < 250
        and 100 < p["rect"].y0 < p["rect"].y1 < 255
        and len(p["items"]) == 20
        and all(s[0] == "c" for s in p["items"])
    ]
    assert len(paths) == 3 and "Camera Stops" in page.get_text()
    middle = sorted(paths, key=lambda p: p["rect"].y0)[1]
    samples = []
    for _, a, b, c, d in middle["items"]:
        for i in range(101):
            t = i / 100
            point = (
                a * (1 - t) ** 3
                + b * (3 * t * (1 - t) ** 2)
                + c * (3 * t * t * (1 - t))
                + d * t**3
            )
            # PDF vector axis ticks at -8 / +8 stops and 0 / 3 density.
            ev = (point.x - 78.43883) * 16 / (243.03362 - 78.43883) - 8
            density = (258.09534 - point.y) * 3 / (258.09534 - 120.99353)
            samples.append((ev, density))
    return samples


def interpolate(points, x):
    for (a, b), (c, d) in zip(points, points[1:]):
        if a <= x <= c and c > a:
            return b + (x - a) * (d - b) / (c - a)
    raise ValueError(x)


if __name__ == "__main__":
    with pymupdf.open(sys.argv[1]) as pdf:
        curve = green_curve(pdf[3])
    points = [(i / 4, round(interpolate(curve, i / 4), 4)) for i in range(-32, 33)]
    inverse = [(y, x) for x, y in points]
    low = round(interpolate(inverse, points[0][1] + 0.1), 2)
    high = round(interpolate(inverse, points[-1][1] - 0.1), 2)
    output = (
        Path(__file__).resolve().parents[1]
        / "app/src/main/resources/studio/lightdata/density.csv"
    )
    with output.open(encoding="utf-8", newline="") as f:
        rows = [r for r in csv.reader(f) if r[0] != "VERITA 200D"]
    with output.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerows(rows)
        for x, y in points:
            writer.writerow(
                [
                    "VERITA 200D",
                    "柯达",
                    200,
                    "h1-5206",
                    4,
                    "H-1-5206 · 5500 K / 1⁄50 s / ECN-2 / Status M。图示 Camera Stops 中灰；预警取图内绿层两端各内收 0.10 D。",
                    "绿层",
                    low,
                    high,
                    x,
                    y,
                ]
            )
