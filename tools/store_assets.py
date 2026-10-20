"""Play Store graphics from the app's own logo and font: a 512 x 512 icon and the 1024 x 500
feature graphic, written to docs/store/. The launcher foreground vector is turned into SVG and
rendered with ffmpeg's librsvg; the feature graphic is laid out with PIL in Roboto Flex.

Usage: python3 -I tools/store_assets.py   (from the repo root; needs ffmpeg with librsvg and Pillow)
"""
import io
import os
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

RES = "app/src/main/res"
OUT = "docs/store"
A = "{http://schemas.android.com/apk/res/android}"
INK0, INK2, TEXT, TEXT2, ACCENT = "#0E0E10", "#222226", "#F2F2F5", "#9A9AA3", "#FF4D6D"
LANES = ["#5A5A62", "#8A93A6", "#B8860B", "#E07A2E", "#A3C93A", "#137D6F", "#7B5BD6"]


def colour(argb):
    """Android #AARRGGBB to SVG colour and opacity."""
    v = argb.lstrip("#")
    return ("#" + v[2:], int(v[:2], 16) / 255) if len(v) == 8 else ("#" + v, 1.0)


def logo_svg(view_box):
    """The launcher foreground as SVG, cropped to [view_box] (x y w h in the 108 dp canvas)."""
    root = ET.parse(f"{RES}/drawable/ic_launcher_foreground.xml").getroot()
    group = root.find("group")
    scale = group.get(A + "scaleX")
    pivot = (group.get(A + "pivotX"), group.get(A + "pivotY"))
    defs, shapes = [], []
    for i, path in enumerate(group.findall("path")):
        d = path.get(A + "pathData")
        attrs = [f'd="{d}"']
        if path.get(A + "fillType") == "evenOdd":
            attrs.append('fill-rule="evenodd"')
        gradient = path.find("aapt:attr/gradient", {"aapt": "http://schemas.android.com/aapt"})
        if gradient is not None:
            stops = "".join(
                f'<stop offset="{s.get(A + "offset")}" stop-color="{colour(s.get(A + "color"))[0]}"/>' for s in gradient.findall("item")
            )
            g = lambda k: gradient.get(A + k)
            defs.append(f'<linearGradient id="g{i}" gradientUnits="userSpaceOnUse" x1="{g("startX")}" y1="{g("startY")}" '
                        f'x2="{g("endX")}" y2="{g("endY")}">{stops}</linearGradient>')
            attrs.append(f'fill="url(#g{i})"')
        else:
            attrs.append('fill="none"')
        if path.get(A + "strokeColor"):
            stroke, opacity = colour(path.get(A + "strokeColor"))
            attrs += [f'stroke="{stroke}"', f'stroke-opacity="{opacity}"', f'stroke-width="{path.get(A + "strokeWidth")}"',
                      f'stroke-linecap="{path.get(A + "strokeLineCap", "butt")}"']
        shapes.append(f"<path {' '.join(attrs)}/>")
    transform = f"translate({pivot[0]} {pivot[1]}) scale({scale}) translate(-{pivot[0]} -{pivot[1]})"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}">'
            f'<defs>{"".join(defs)}</defs><g transform="{transform}">{"".join(shapes)}</g></svg>')


def render(svg, size):
    """SVG to a PIL image at [size] px square, through ffmpeg's librsvg (from a file: piped SVG isn't probed)."""
    with tempfile.NamedTemporaryFile("w", suffix=".svg") as source:
        source.write(svg)
        source.flush()
        png = subprocess.run(
            ["ffmpeg", "-v", "error", "-f", "svg_pipe", "-width", str(size), "-height", str(size), "-i", source.name,
             "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-"],
            capture_output=True, check=True,
        ).stdout
    return Image.open(io.BytesIO(png)).convert("RGBA")


def font(size, weight):
    f = ImageFont.truetype(f"{RES}/font/roboto_flex.ttf", size)
    f.set_variation_by_axes([weight])
    return f


def icon():
    """512 x 512, full bleed (Play applies its own mask): the launcher's visible 72 dp middle."""
    image = Image.new("RGBA", (512, 512), INK0)
    image.alpha_composite(render(logo_svg("18 18 72 72"), 512))
    image.convert("RGB").save(f"{OUT}/icon-512.png")


def feature_graphic():
    image = Image.new("RGBA", (1024, 500), INK0)
    draw = ImageDraw.Draw(image)
    # A timeline strip along the bottom, in the app's lane colours.
    x = 0
    widths = [150, 110, 190, 90, 160, 130, 200]
    for i, w in enumerate(widths):
        draw.rounded_rectangle((x + 6, 404, x + w - 6, 452), radius=10, fill=LANES[i % len(LANES)])
        x += w
    draw.rectangle((618, 380, 622, 476), fill=TEXT)  # the playhead
    image.alpha_composite(render(logo_svg("18 18 72 72"), 300), (70, 60))
    draw.text((400, 120), "PreCut", font=font(124, 700), fill=TEXT)
    draw.text((404, 268), "Cut, layer and polish videos", font=font(40, 500), fill=TEXT2)
    draw.text((404, 318), "right on your phone", font=font(40, 500), fill=ACCENT)
    image.convert("RGB").save(f"{OUT}/feature-graphic.png")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon()
    feature_graphic()
    print(f"wrote {OUT}/icon-512.png and {OUT}/feature-graphic.png")
