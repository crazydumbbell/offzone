"""Build bounded static Rue v6 app resources from the versioned reference pack.

This is deterministic alpha *attenuation* and downsampling, not a professionally
retouched cutout. Do not mistake build success for art, rights or device QA.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / '.growth-design/mascots/2026-09-24-rue-statebook/assets'
IOS = ROOT / 'RoomDNS/Assets.xcassets'
ANDROID = ROOT / 'android/app/src/main/res/drawable-nodpi'
OWN = Path(__file__).resolve().parent

PORTRAITS = ('welcome', 'ready', 'focused', 'reflection', 'finished', 'recovered', 'needs-action', 'failed')
FULLBODY = ('welcome', 'ready', 'focused', 'recovered')


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def export(source: Path, target: Path, size: tuple[int, int]) -> dict:
    im = Image.open(source).convert('RGBA')
    # Remove transparent RGB, attenuate isolated low-alpha background wash;
    # preserve the artwork's original anti-aliased edges at UI scale.
    im.putalpha(im.getchannel('A').point(lambda a: 0 if a <= 60 else 255 if a >= 245 else round((a - 60) * 255 / 185)))
    im.thumbnail(size, Image.Resampling.LANCZOS)
    # RGB of fully transparent pixels must not encode a visible matte on resize.
    target.parent.mkdir(parents=True, exist_ok=True)
    im.save(target, 'PNG', optimize=True)
    return {'source': str(source.relative_to(ROOT)), 'sourceSha256': digest(source),
            'path': str(target.relative_to(ROOT)), 'sha256': digest(target), 'size': list(im.size)}


def main() -> None:
    manifest = {'kind': 'static preview assets, alpha attenuation only; not release-approved', 'portraits': {}, 'fullbody': {}}
    for group, names, maximum in (('portraits', PORTRAITS, (384, 512)), ('fullbody', FULLBODY, (512, 768))):
        for name in names:
            # The original chosen v6 pose is the large welcome identity anchor.
            source = (ROOT / '.growth-design/mascots/2026-09-24-rue-short-skirt/assets/anchor.png'
                      if group == 'fullbody' and name == 'welcome' else SOURCE / group / (name + '.png'))
            slug = f'rue_{group}_{name.replace("-", "_")}'
            apple = IOS / f'OffzoneRue-{group}-{name}.imageset'
            android = ANDROID / (slug + '.png')
            manifest[group][name] = export(source, apple / 'art.png', maximum)
            android.write_bytes((apple / 'art.png').read_bytes())
            (apple / 'Contents.json').write_text(json.dumps({'images': [{'filename': 'art.png', 'idiom': 'universal'}],
                                                               'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
            assert digest(android) == digest(apple / 'art.png')
    (OWN / 'manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n')
    print('Built', len(PORTRAITS) + len(FULLBODY), 'static iOS/Android Rue resources; not clean-alpha certified')

if __name__ == '__main__':
    main()
