#!/usr/bin/env python3
"""Render legacy mipmaps and a preview from the adaptive foreground; needs CairoSVG/Pillow."""

from io import BytesIO
from pathlib import Path
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image


def main():
    root = Path(__file__).resolve().parents[1]
    res = root / "app/src/main/res"
    android = "{http://schemas.android.com/apk/res/android}"
    background = ET.parse(res / "values/ic_launcher_colors.xml").find("color").text.strip()
    vector = ET.parse(res / "drawable/ic_launcher_foreground.xml").getroot()
    # Adaptive launchers show the central 72dp of the 108dp layers at rest.
    svg = ET.Element("svg", xmlns="http://www.w3.org/2000/svg", viewBox="18 18 72 72")
    ET.SubElement(svg, "rect", x="0", y="0", width="108", height="108", fill=background)
    for path in vector.findall("path"):
        ET.SubElement(svg, "path", {
            "d": path.attrib[android + "pathData"],
            "fill": path.attrib[android + "fillColor"],
            "fill-rule": path.get(android + "fillType", "nonZero").replace("nonZero", "nonzero").replace("evenOdd", "evenodd"),
        })
    source = ET.tostring(svg)
    destinations = [(res / f"mipmap-{density}/ic_launcher.png", size) for density, size in (
        ("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192),
    )]
    destinations.append((root / ".scratch/screenshots/icon-preview.png", 512))
    for destination, size in destinations:
        rendered = cairosvg.svg2png(bytestring=source, output_width=size, output_height=size)
        with Image.open(BytesIO(rendered)).convert("RGBA") as image:
            if image.getchannel("A").getextrema() != (255, 255):
                raise ValueError("Launcher artwork must have a fully opaque background")
            destination.parent.mkdir(parents=True, exist_ok=True)
            image.convert("RGB").save(destination)
        print(f"{destination.relative_to(root)} ({size}×{size}, opaque)")


if __name__ == "__main__":
    main()
