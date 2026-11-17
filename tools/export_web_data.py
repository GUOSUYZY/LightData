"""Export the Android film catalogue and digitized data for the static web app.

Run from any directory with Python 3. No dependencies or network requests.
"""
import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main"
RESOURCES = SOURCE / "resources/studio/lightdata"

FAMILIES = {
    "BW": ("黑白负片", -4, 5, [[-5, .12], [-4, .20], [-2, .48], [0, .86], [3, 1.45], [6, 1.94]]),
    "COLOR": ("彩色负片", -4, 5, [[-5, .45], [-4, .53], [-2, .77], [0, 1.14], [3, 1.75], [6, 2.18]]),
    "SLIDE": ("反转片", -3, 2.5, [[-4, 3.3], [-3, 3], [-1, 1.59], [0, 1], [1, .56], [2.5, .23], [3.5, .15]]),
}


def rows(filename):
    with (RESOURCES / filename).open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def export():
    links = {}
    for line in (RESOURCES / "manual-links.tsv").read_text(encoding="utf-8").splitlines():
        document, kind, url = line.split("\t", 2)
        links[document] = (kind, url)
    films = {}
    for row in rows("density.csv"):
        name = row["stock"]
        if name not in films:
            document = row["document_id"]
            films[name] = dict(id=f"{document}:{name}", document=document,
                brand=row["brand"], iso=int(row["iso"]), name=name,
                low=float(row["low_ev"]), high=float(row["high_ev"]), dots=[],
                info=f"厂家图采样 · 第 {row['page']} 页 · {row['channel']} · {row['process']}",
                url=links[document][1], sourceKind=links[document][0], digital=False)
        films[name]["dots"].append([float(row["ev"]), float(row["density"])])
    measured_count = len(films)

    kotlin = (SOURCE / "java/studio/lightdata/FilmStocks.kt").read_text(encoding="utf-8")
    pattern = r'group\(\s*("[^"]+"|[IFO]),\s*Family\.(\w+),\s*"([^"]+)",\s*("""[\s\S]*?"""|"[^"\n]+")\s*,?\s*\)'
    for match in re.finditer(pattern, kotlin):
        brand, family, process, stocks = match.groups()
        brand = {"I": "ILFORD", "F": "Fujifilm", "O": "Foma"}.get(brand, brand.strip('"'))
        kind, low, high, dots = FAMILIES[family]
        for stock in stocks.strip('"').strip().splitlines():
            document, iso, name = stock.strip().split(" ", 2)
            if name not in films:
                films[name] = dict(id=f"{document}:{name}", document=document, brand=brand,
                    iso=int(iso), name=name, low=low, high=high, dots=dots,
                    info=f"{process} · {kind}通用近似曲线和预警范围。原资料用于型号、曝光指数和工艺。",
                    url=links[document][1], sourceKind=links[document][0], digital=False)

    for row in rows("layer-density.csv"):
        film = films[row["stock"]]
        color = film.setdefault("color", {"density": {}, "spectral": {}})
        color["density"].setdefault(row["channel"], []).append([float(row["ev"]), float(row["density"])])
    for row in rows("spectral.csv"):
        film = films[row["stock"]]
        color = film.setdefault("color", {"density": {}, "spectral": {}})
        color["spectral"].setdefault(row["channel"], []).append([float(row["wavelength_nm"]), float(row["log_sensitivity"])])
        color.setdefault("conditions", {})[row["channel"]] = row["conditions"]
    assert measured_count == 119, measured_count
    assert len(films) == 167, len(films)
    assert sum(len(points) for film in films.values() for points in film.get("color", {}).get("spectral", {}).values()) == 428
    assert sum(len(points) for film in films.values() for points in film.get("color", {}).get("density", {}).values()) == 477
    target = ROOT / "web/data/films.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(list(films.values()), ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Exported {len(films)} films ({measured_count} measured), {target.stat().st_size} bytes")


if __name__ == "__main__":
    export()
