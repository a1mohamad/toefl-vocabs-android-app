#!/usr/bin/env python3
"""
Turn raw CI screenshots into the small set the README displays.

Why this exists
---------------
The emulator captures at the device's native 1080x2400, which is 100-400 KB per
image. Seven of those is well over a megabyte of binaries in a repository whose
entire source is a few hundred KB, and GitHub renders them at a fraction of that
size anyway.

This resizes to the same 460px width the iOS repository uses, so the two READMEs
show screenshots at matching scale, and strips the PNG metadata that varies
between runs — otherwise re-running CI produces a diff on every image even when
nothing on screen changed.

Usage
-----
    # after downloading the emulator-screenshots artifact
    python Scripts/prepare_screenshots.py path/to/downloaded/artifact

Requires `pip install Pillow`.
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow is required: pip install Pillow")
    raise SystemExit(1)

REPO_ROOT = Path(__file__).resolve().parent.parent
DESTINATION = REPO_ROOT / "assets" / "screenshots"

# Matches the width the iOS repository's screenshots use.
TARGET_WIDTH = 460

# Only the images the README actually shows. Capturing every screen on every run
# is useful for spotting regressions in the CI artifact; committing all eighteen
# is not.
WANTED = [
    "practice-dark.png",
    "library-dark.png",
    "section-dark.png",
    "practice-revealed-dark.png",
    "summary-dark.png",
    "reports-light.png",
    "reports-dark.png",
]


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 1

    source = Path(sys.argv[1])
    if not source.is_dir():
        print(f"{source} is not a directory.")
        return 1

    DESTINATION.mkdir(parents=True, exist_ok=True)

    missing: list[str] = []
    for name in WANTED:
        origin = source / name
        if not origin.exists():
            missing.append(name)
            continue

        image = Image.open(origin).convert("RGB")
        width, height = image.size
        target_height = round(height * TARGET_WIDTH / width)
        resized = image.resize((TARGET_WIDTH, target_height), Image.LANCZOS)

        target = DESTINATION / name
        # `optimize` plus no metadata: two runs of the same screen produce
        # byte-identical files, so an unchanged screen is not a diff.
        resized.save(target, "PNG", optimize=True)

        print(f"  {name:<30} {width}x{height} -> {TARGET_WIDTH}x{target_height}"
              f"  ({target.stat().st_size // 1024} KB)")

    if missing:
        print(f"\nMissing from {source}: {', '.join(missing)}")
        return 1

    print(f"\nWrote {len(WANTED)} images to {DESTINATION.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
