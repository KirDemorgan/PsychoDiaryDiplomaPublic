"""Renders the app icon variants and splash images from code.

Usage: python3 scripts/render-icons.py [output_dir]
Requires Pillow (pip install pillow). Default output is assets/images.
"""

import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps

CANVAS = 2048
FINAL = 1024


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def diagonal_gradient(size, stops):
    ramp = Image.new("L", (size, size))
    scale = 255 / (2 * (size - 1))
    ramp.putdata([int((x + y) * scale) for y in range(size) for x in range(size)])
    if len(stops) == 2:
        return ImageOps.colorize(ramp, hex_rgb(stops[0]), hex_rgb(stops[1])).convert("RGBA")
    return ImageOps.colorize(
        ramp, black=hex_rgb(stops[0]), white=hex_rgb(stops[2]), mid=hex_rgb(stops[1])
    ).convert("RGBA")


def radial_glow(size, center, radius, strength):
    glow = Image.radial_gradient("L").resize((radius * 2, radius * 2))
    glow = glow.point(lambda value: int(max(0, 255 - value * 1.42) * strength))
    layer = Image.new("L", (size, size), 0)
    layer.paste(glow, (center[0] - radius, center[1] - radius))
    return layer


def wave_points(count=1400, amplitude=300, phase=0.55, lift=0):
    points = []
    for index in range(count + 1):
        t = index / count
        x = 300 + (CANVAS - 600) * t
        envelope = 0.62 + 0.38 * math.sin(math.pi * t)
        y = CANVAS * 0.5 + lift - amplitude * math.sin(2 * math.pi * (t * 0.95) + phase) * envelope
        points.append((x, y))
    return points


def stroke_mask(points, width, dy=0):
    mask = Image.new("L", (CANVAS, CANVAS), 0)
    draw = ImageDraw.Draw(mask)
    radius = width / 2
    for x, y in points:
        draw.ellipse((x - radius, y + dy - radius, x + radius, y + dy + radius), fill=255)
    return mask


def composite_color(base, color, mask, opacity=1.0):
    layer = Image.new("RGBA", base.size, color + (255,))
    alpha = mask.point(lambda value: int(value * opacity))
    base.paste(layer, (0, 0), alpha)


def paste_gradient(canvas, mask, stops, opacity=1.0):
    layer = diagonal_gradient(CANVAS, list(stops))
    alpha = mask.point(lambda value: int(value * opacity))
    canvas.paste(layer, (0, 0), alpha)


def draw_ribbon(canvas, points, width, tone, strength):
    mask = stroke_mask(points, width)
    shadow = mask.filter(ImageFilter.GaussianBlur(width * 0.3)).transform(
        (CANVAS, CANVAS), Image.AFFINE, (1, 0, 0, 0, 1, -width * 0.22)
    )
    composite_color(canvas, tone["shadow"], shadow, tone["shadow_opacity"] * strength)
    paste_gradient(canvas, mask, tone["body"], strength)
    inner = mask.filter(ImageFilter.MinFilter(int(width * 0.16) | 1))
    edge = ImageChops.subtract(mask, inner).filter(ImageFilter.GaussianBlur(3))
    composite_color(canvas, tone["edge"], edge, tone["edge_opacity"] * strength)
    gloss = stroke_mask(points, width * 0.3, dy=-width * 0.22)
    gloss = ImageChops.multiply(gloss, mask).filter(ImageFilter.GaussianBlur(width * 0.07))
    composite_color(canvas, (255, 255, 255), gloss, tone["gloss_opacity"] * strength)
    return mask


def draw_wave(canvas, tone):
    back = wave_points(amplitude=260, phase=1.35, lift=130)
    draw_ribbon(canvas, back, 120, tone, 0.45)
    front = wave_points()
    draw_ribbon(canvas, front, 170, tone, 1.0)

    x, y = front[int(len(front) * 0.84)]
    radius = 104
    ring = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(ring).ellipse((x - radius, y - radius, x + radius, y + radius), fill=255)
    composite_color(canvas, tone["shadow"], ring.filter(ImageFilter.GaussianBlur(26)), 0.35)
    composite_color(canvas, tone["dot"], ring, 1.0)
    hole = radius - 44
    core = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(core).ellipse((x - hole, y - hole, x + hole, y + hole), fill=255)
    composite_color(canvas, tone["dot_core"], core, 1.0)


LIGHT = {
    "background": ["#2FC9BC", "#16A0A4", "#7C5CE8"],
    "glow": 0.45,
    "body": ("#FFFFFF", "#E7F7FB"),
    "shadow": (8, 44, 70),
    "shadow_opacity": 0.35,
    "edge": (255, 255, 255),
    "edge_opacity": 0.9,
    "gloss_opacity": 0.55,
    "dot": (255, 255, 255),
    "dot_core": (22, 160, 164),
}

DARK = {
    "background": ["#0E3B48", "#17294F", "#3D2A82"],
    "glow": 0.22,
    "body": ("#9FF1E6", "#B9A7FF"),
    "shadow": (0, 0, 0),
    "shadow_opacity": 0.5,
    "edge": (255, 255, 255),
    "edge_opacity": 0.55,
    "gloss_opacity": 0.3,
    "dot": (226, 255, 250),
    "dot_core": (24, 36, 80),
}

TINTED = {
    "background": ["#000000", "#0A0A0A", "#1A1A1A"],
    "glow": 0.0,
    "body": ("#FFFFFF", "#D6D6D6"),
    "shadow": (0, 0, 0),
    "shadow_opacity": 0.6,
    "edge": (255, 255, 255),
    "edge_opacity": 0.6,
    "gloss_opacity": 0.35,
    "dot": (255, 255, 255),
    "dot_core": (20, 20, 20),
}


def render_icon(tone):
    canvas = diagonal_gradient(CANVAS, tone["background"])
    if tone["glow"]:
        sheen = radial_glow(CANVAS, (int(CANVAS * 0.28), int(CANVAS * 0.2)), int(CANVAS * 0.75), tone["glow"])
        composite_color(canvas, (255, 255, 255), sheen, 1.0)
    draw_wave(canvas, tone)
    return canvas.convert("RGB").resize((FINAL, FINAL), Image.LANCZOS)


def render_splash(tone, size=480):
    tile = render_icon(tone).convert("RGBA").resize((size * 2, size * 2), Image.LANCZOS)
    mask = Image.new("L", tile.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, tile.width - 1, tile.height - 1), radius=int(tile.width * 0.225), fill=255)
    tile.putalpha(mask)
    return tile.resize((size, size), Image.LANCZOS)


def main():
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "assets" / "images"
    output.mkdir(parents=True, exist_ok=True)
    render_icon(LIGHT).save(output / "icon.png")
    render_icon(DARK).save(output / "icon-dark.png")
    render_icon(TINTED).save(output / "icon-tinted.png")
    render_splash(LIGHT).save(output / "splash-icon.png")
    render_splash(DARK).save(output / "splash-icon-dark.png")


if __name__ == "__main__":
    main()
