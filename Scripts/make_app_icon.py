#!/usr/bin/env python3
"""
Rebuild the Android launcher icon from a single piece of square artwork.

Why this exists
---------------
Android does not take one 1024x1024 image the way an iOS asset catalog does. It
wants a density bucket for every screen, a circular variant for launchers that
ask for one, and — since API 26 — an *adaptive* icon split into a background and
a foreground layer that the launcher masks into whatever shape it likes.

The adaptive part is the one that is easy to get wrong. Both layers are 108dp
square, but only the middle 72dp is guaranteed to be visible: the outer ring is
there so the launcher can shift the layers during animations. Dropping full-bleed
artwork straight in as the foreground therefore crops about a third of it off.
This script scales the art down onto a transparent 108dp canvas so it lands
inside that safe zone, and fills the background layer with a flat colour.

Usage
-----
    python Scripts/make_app_icon.py path/to/artwork.png

Requires `pip install Pillow`.
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is required: pip install Pillow")
    raise SystemExit(1)

REPO_ROOT = Path(__file__).resolve().parent.parent
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res"

# Legacy launcher icon, one per density bucket, in dp-equivalent pixels.
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Adaptive layers are 108dp square regardless of the legacy size.
ADAPTIVE_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# The fraction of the 108dp canvas that is guaranteed visible. 72/108.
SAFE_ZONE = 72 / 108


def load_square(path: Path) -> Image.Image:
    """Open the artwork, flatten any transparency, and square it off."""
    image = Image.open(path)

    if image.mode in ("RGBA", "LA", "P"):
        image = image.convert("RGBA")
        flattened = Image.new("RGB", image.size, (255, 255, 255))
        flattened.paste(image, mask=image.split()[-1])
        image = flattened
    else:
        image = image.convert("RGB")

    width, height = image.size
    if width != height:
        side = min(width, height)
        left = (width - side) // 2
        top = (height - side) // 2
        image = image.crop((left, top, left + side, top + side))

    return image


def write_legacy(image: Image.Image) -> None:
    for bucket, size in LEGACY_SIZES.items():
        directory = RES_DIR / f"mipmap-{bucket}"
        directory.mkdir(parents=True, exist_ok=True)

        square = image.resize((size, size), Image.LANCZOS)
        square.save(directory / "ic_launcher.png", "PNG")

        # Circular variant for launchers that request android:roundIcon.
        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size * 4, size * 4), fill=255)
        mask = mask.resize((size, size), Image.LANCZOS)

        rounded = square.convert("RGBA")
        rounded.putalpha(mask)
        rounded.save(directory / "ic_launcher_round.png", "PNG")

        print(f"  mipmap-{bucket:<7} {size:>3}x{size:<3} ic_launcher.png, ic_launcher_round.png")


def write_adaptive(image: Image.Image) -> None:
    for bucket, canvas in ADAPTIVE_SIZES.items():
        directory = RES_DIR / f"mipmap-{bucket}"
        directory.mkdir(parents=True, exist_ok=True)

        inner = int(round(canvas * SAFE_ZONE))
        art = image.convert("RGBA").resize((inner, inner), Image.LANCZOS)

        # Rounded corners on the artwork itself, so it does not read as a hard
        # square floating inside a circular mask.
        radius = int(inner * 0.22)
        mask = Image.new("L", (inner * 4, inner * 4), 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            (0, 0, inner * 4, inner * 4), radius=radius * 4, fill=255
        )
        art.putalpha(mask.resize((inner, inner), Image.LANCZOS))

        foreground = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        offset = (canvas - inner) // 2
        foreground.paste(art, (offset, offset), art)
        foreground.save(directory / "ic_launcher_foreground.png", "PNG")

        print(f"  mipmap-{bucket:<7} {canvas:>3}x{canvas:<3} ic_launcher_foreground.png")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 1

    source = Path(sys.argv[1])
    if not source.exists():
        print(f"{source} does not exist.")
        return 1

    image = load_square(source)
    print(f"Source: {source} ({image.size[0]}x{image.size[1]})\n")

    write_legacy(image)
    write_adaptive(image)

    print(
        "\nDone. The adaptive icon XML in res/mipmap-anydpi-v26/ references these "
        "layers and does not need regenerating."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
