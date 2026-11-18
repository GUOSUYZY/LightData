"""Read the middle Microphen curve separately from three Foma page-one vector plots."""
import argparse
import csv
from pathlib import Path

import numpy as np
import pymupdf


# Anchor drawing is the plot frame. The next three paths mark -3, -2, -1 log H.
# The two upper density traces are filled outlines in a single compound path.
CHARTS = {
    "3e3cf3a4": (141, 194, 7),
    "bc0e7dc0": (148, 167, 6),
    "0969a427": (120, 158, 9),
}


def middle_density(outlines, x, bottom, two):
    crossings = []
    for kind, a, b in outlines:
        assert kind == "l"
        if min(a.x, b.x) <= x < max(a.x, b.x):
            crossings.append(a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x))
    # Each trace has two edges. Increasing page Y goes down, so the
    # lower of these two traces is the middle of the three plot curves.
    assert len(crossings) >= 4, (x, crossings)
    heights = np.sort(crossings)
    boundary = np.argmax(np.diff(heights)) + 1
    # Adjacent filled subpaths can produce coincident outline intersections.
    center = (heights[boundary] + heights[-1]) / 2
    return 2 * (bottom - center) / (bottom - two)


def digitize(documents, source, metadata):
    frame, curve, minutes = CHARTS[source]
    with pymupdf.open(documents / (source + ".pdf")) as doc:
        paths = doc[0].get_drawings()
    neg3, neg2 = (paths[i]["rect"].x0 for i in (frame+1, frame+2))
    bottom = paths[frame+1]["rect"].y1
    two = paths[frame+4]["rect"].y0
    log_h = np.arange(-3.15, -.39, .05)
    density = [middle_density(paths[curve]["items"], neg3 + (h+3)*(neg2-neg3), bottom, two)
               for h in log_h]
    ev = (log_h + 2.45) / np.log10(2)
    low, high = np.interp([density[0]+.1, density[-1]-.1], density, ev)
    process = (f"5500 K，1/20 秒，Microphen 20°C {minutes} 分钟（曲线 2）。三份原图的中线形状近同，各自采样；"
               "相对曝光轴 −2.45 作中灰参照，预警为采样两端内缩 0.10 D。")
    return [dict(metadata, process=process, low_ev=round(low,5), high_ev=round(high,5),
                 ev=round(x,5), density=round(y,5)) for x,y in zip(ev,density)]


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("documents", type=Path)
    source_dir = parser.parse_args().documents
    target = Path("app/src/main/resources/studio/lightdata/density.csv")
    with target.open(encoding="utf8", newline="") as f:
        reader = csv.DictReader(f)
        fields = reader.fieldnames
        old_rows = list(reader)
    rows, done = [], set()
    for row in old_rows:
        key = row["document_id"]
        if key not in CHARTS:
            rows.append(row)
        elif key not in done:
            rows.extend(digitize(source_dir, key, row))
            done.add(key)
    assert done == set(CHARTS)
    with target.open("w", encoding="utf8", newline="") as f:
        writer = csv.DictWriter(f, fields, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
