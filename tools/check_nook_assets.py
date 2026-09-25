"""Verify Nook Cat state artwork and native resource parity."""

import hashlib
import json
from pathlib import Path

from PIL import Image


root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / ".growth-design/mascots/2026-09-25-nook-native/manifest.json").read_text())
assert hashlib.sha256((root / manifest["selectedConcept"]).read_bytes()).hexdigest() == manifest["selectedConceptSha256"]
for group, expected in (("portraits", 8), ("fullbody", 4)):
    assert len(manifest["assets"][group]) == expected
    for record in manifest["assets"][group].values():
        source, ios, android = (root / record[key] for key in ("source", "ios", "android"))
        original = source.read_bytes()
        assert hashlib.sha256(original).hexdigest() == record["sha256"]
        assert original == ios.read_bytes() == android.read_bytes()
        with Image.open(source) as image:
            assert image.mode == "RGBA" and list(image.size) == record["size"]
            assert all(image.getpixel(point)[3] <= 1 for point in (
                (0, 0), (image.width - 1, 0), (0, image.height - 1),
            ))
        catalog = json.loads((ios.parent / "Contents.json").read_text())
        assert catalog["images"][0]["filename"] == ios.name
assert not list((root / "RoomDNS/Assets.xcassets").glob("OffzoneRue-*"))
assert not list((root / "android/app/src/main/res/drawable-nodpi").glob("rue_*.png"))
print("PASS: 8 Nook portraits + 4 full-body assets, alpha, hashes and iOS/Android parity")
