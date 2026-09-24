"""After convert_kiwi_video.swift, package its existing alpha frames without creative edits.

swift android/scripts/convert_kiwi_video.swift RoomDNS/KiwiMotion/kiwi-pingpong.mov
python3 android/scripts/package_kiwi_video.py
Requires macOS AVFoundation for decoding and Pillow with WebP support for encoding.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
frames = [Image.open(p).convert('RGBA') for p in sorted(Path('/tmp/offzone-kiwi-frames').glob('*.png'))]
assert len(frames) == 191 and all(frame.size == (360, 282) for frame in frames)
assert all(frame.getchannel('A').getextrema() == (0, 255) for frame in frames)
target = root/'android/app/src/main/res/drawable-nodpi/kiwi_loop.webp'
target.parent.mkdir(parents=True, exist_ok=True)
frames[0].save(target, save_all=True, append_images=frames[1:], duration=[83,83,84]*63+[83,84], loop=0, quality=85, method=4)
assert target.stat().st_size < 4 * 1024 * 1024
result = Image.open(target)
assert result.n_frames == 191
print(f'{result.n_frames} frames, alpha preserved, {target.stat().st_size} bytes')
