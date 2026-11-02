#!/usr/bin/env python3
"""Create a static, dark 1920x1080 launcher background from the licensed source photo."""

import argparse
from pathlib import Path

from PIL import Image, ImageEnhance, ImageOps


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args()

    image = Image.open(args.source).convert("RGB")
    image = ImageOps.fit(image, (1920, 1080), method=Image.Resampling.LANCZOS, centering=(0.5, 0.52))
    image = ImageEnhance.Brightness(image).enhance(0.82)
    image = ImageEnhance.Color(image).enhance(0.88)

    blue_green = Image.new("RGB", image.size, (14, 30, 34))
    image = Image.blend(image, blue_green, 0.10)
    image = Image.blend(image, Image.new("RGB", image.size, (0, 0, 0)), 0.34)

    if args.preview:
        args.preview.parent.mkdir(parents=True, exist_ok=True)
        image.resize((960, 540), Image.Resampling.LANCZOS).save(
            args.preview,
            "JPEG",
            quality=82,
            optimize=True,
        )
    args.destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.destination, "JPEG", quality=83, optimize=True, progressive=True)


if __name__ == "__main__":
    main()
