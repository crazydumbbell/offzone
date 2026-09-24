"""Verify versioned Rue v6 static resources on both native platforms."""
import hashlib
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / '.growth-design/mascots/2026-09-24-rue-native/manifest.json').read_text())
for group, expected in (('portraits', 8), ('fullbody', 4)):
    assert len(manifest[group]) == expected
    for name, record in manifest[group].items():
        source = root / record['source']
        apple = root / record['path']
        android = root / f'android/app/src/main/res/drawable-nodpi/rue_{group}_{name.replace("-", "_")}.png'
        assert hashlib.sha256(source.read_bytes()).hexdigest() == record['sourceSha256']
        assert hashlib.sha256(apple.read_bytes()).hexdigest() == record['sha256']
        assert apple.read_bytes() == android.read_bytes()
        with Image.open(apple) as image:
            assert image.mode == 'RGBA' and image.size == tuple(record['size'])
            assert image.getpixel((0, 0))[3] == 0
        catalog = json.loads((apple.parent / 'Contents.json').read_text())
        assert catalog['images'][0]['filename'] == apple.name
assert not list((root / 'RoomDNS/Assets.xcassets').glob('OffzoneKiwi-*'))
assert not (root / 'RoomDNS/KiwiMotion').exists()
assert not list((root / 'android/app/src/main/res').rglob('kiwi*'))
print('PASS: 8 portrait + 4 full-body Rue resources per platform, source/derived hashes, alpha and no bundled Kiwi')
