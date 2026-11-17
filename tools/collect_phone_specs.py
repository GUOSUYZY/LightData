"""Cache readable manufacturer specifications for the curated camera catalog.

Usage: python tools/collect_phone_specs.py urls.json OUTPUT_DIRECTORY
The input is a JSON object mapping a stable reference key to an HTTPS URL.
Only readable text is cached; the shipped application uses cameras.json.
"""

import concurrent.futures
import hashlib
import gzip
import json
from html.parser import HTMLParser
from pathlib import Path
import sys
import time
from urllib.request import Request, urlopen


class ReadableHTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.skip = 0
        self.parts = []

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "noscript"):
            self.skip += 1

    def handle_endtag(self, tag):
        if tag in ("script", "style", "noscript"):
            self.skip = max(0, self.skip - 1)

    def handle_data(self, data):
        if not self.skip and data.strip():
            self.parts.append(data.strip())


def collect(key, url, output):
    if not url.startswith("https://"):
        raise ValueError("Manufacturer references must use HTTPS")
    destination = output / (key + ".json")
    if destination.exists():
        cached = json.loads(destination.read_text(encoding="utf-8"))
        if cached.get("collectorVersion") == 1:
            return key, "cached"
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urlopen(request, timeout=25) as response:
                raw = response.read()
                resolved = response.url
            if raw.startswith(b"\x1f\x8b"):
                raw = gzip.decompress(raw)
            parser = ReadableHTML()
            parser.feed(raw.decode("utf-8", errors="replace"))
            destination.write_text(json.dumps({
                "collectorVersion": 1,
                "url": url,
                "resolvedUrl": resolved,
                "sha256": hashlib.sha256(raw).hexdigest(),
                "text": "\n".join(parser.parts),
            }, ensure_ascii=False, indent=2), encoding="utf-8")
            return key, "saved"
        except Exception as error:
            if attempt == 2:
                return key, str(error)
            time.sleep(0.5 * (attempt + 1))


if __name__ == "__main__":
    references = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    output = Path(sys.argv[2])
    output.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        pending = [pool.submit(collect, key, url, output) for key, url in references.items()]
        for future in concurrent.futures.as_completed(pending):
            key, status = future.result()
            print(f"{key}: {status}", flush=True)
