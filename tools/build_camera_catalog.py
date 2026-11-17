"""Assemble the reviewed phone profiles; no network or scraping at app runtime.

Each fragment in tools/phone_catalog is curated from the linked specification
and launch-price references. Run this after editing a fragment, then run the web
optics tests. Existing profile IDs are stable because they are saved by browsers.
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "phone_catalog"
TARGET = ROOT / "web" / "data" / "cameras.json"


def build():
    devices = []
    for path in sorted(SOURCE.glob("*.json")):
        fragment = json.loads(path.read_text(encoding="utf-8"))
        devices.extend(fragment["devices"] if isinstance(fragment, dict) else fragment)
    if not devices:
        raise ValueError("No reviewed phone profiles")
    seen = set()
    for device in devices:
        if device["id"] in seen:
            raise ValueError(f"Duplicate phone profile: {device['id']}")
        seen.add(device["id"])
        device.setdefault("brand", device["name"].split()[0])
        if not device.get("lenses"):
            raise ValueError(f"No lenses: {device['id']}")
        if not device.get("legacy") and not 2022 <= device.get("launchYear", 0) <= 2026:
            raise ValueError(f"Outside reviewed launch dates: {device['id']}")
        if device.get("msrpCny", 0) <= 4000 or not device.get("priceSource"):
            raise ValueError(f"Missing qualifying launch price: {device['id']}")
        lens_ids = set()
        for lens in device["lenses"]:
            if lens["id"] in lens_ids:
                raise ValueError(f"Duplicate lens ID: {device['id']}/{lens['id']}")
            lens_ids.add(lens["id"])
    order = {brand: index for index, brand in enumerate(
        ["Samsung", "Apple", "HUAWEI", "HONOR", "Xiaomi", "OPPO", "vivo"])}
    devices.sort(key=lambda d: (
        order.get(d.get("brand", d["name"].split()[0]), 99),
        -d.get("launchYear", 0), d["name"].lower()))
    catalog = {
        "version": 2,
        "updated": "2026-11-17",
        "scope": "China mainland launches after 2021, with at least one launch configuration strictly above CNY 4000; previously supported legacy profiles retained.",
        "devices": devices,
    }
    TARGET.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(devices)} profiles, {sum(len(d['lenses']) for d in devices)} rear-lens entries")


if __name__ == "__main__":
    build()
