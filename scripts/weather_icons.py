"""Writes the widget's weather icons as Android VectorDrawables (D38).

The sources are copies of the still SVGs of the Home Assistant dashboard's
weather icons in icons/weather/, light/ and dark/, named
<condition>-<day|night>-still.svg. Each becomes
app/src/main/res/drawable/weather_<condition>_<day|night>.xml from light/ and
the same name under drawable-night/ from dark/, a hyphen in the condition
written as an underscore.

The converter takes what those SVGs use and fails, naming the file and the
element, on anything else:

- elements: svg (a viewBox from 0 0), g, path, circle, rect (without rounded
  corners), line, polygon, polyline;
- transforms: translate, scale, rotate, each a nested group, in order;
- paint: fill and stroke as #rgb, #rrggbb or none, stroke-width,
  stroke-linecap, stroke-linejoin, stroke-miterlimit, fill-rule, fill-opacity
  and stroke-opacity, inherited from the groups as SVG inherits them;
- opacity on a shape: 0 leaves the shape out; else it goes into the shape's
  fill or stroke alpha when only one of them is painted, and a shape that
  paints both is drawn on its own layer at that opacity, as SVG composites
  it (the file is then a layer-list of vectors);
- stroke-dasharray only where it changes nothing: a dash at least as long as
  the shape's pathLength, so the whole stroke is drawn.

Usage: python3 scripts/weather_icons.py         writes the drawables
       python3 scripts/weather_icons.py --check fails when the drawables in
                                                the repo are not what it writes
"""

import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "icons" / "weather"
RES = ROOT / "app" / "src" / "main" / "res"
# The mode's SVGs and the resource folder their drawables go to.
MODES = {"light": "drawable", "dark": "drawable-night"}
SOURCE_NAME = re.compile(r"^(?P<condition>[a-z]+(?:-[a-z]+)*)-(?P<variant>day|night)-still\.svg$")
PREFIX = "weather_"

SVG = "{http://www.w3.org/2000/svg}"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"

SHAPES = {"path", "circle", "rect", "line", "polygon", "polyline"}
INHERITED = {
    "fill",
    "stroke",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "stroke-miterlimit",
    "fill-rule",
    "fill-opacity",
    "stroke-opacity",
}
GEOMETRY = {
    "path": {"d"},
    "circle": {"cx", "cy", "r"},
    "rect": {"x", "y", "width", "height"},
    "line": {"x1", "y1", "x2", "y2"},
    "polygon": {"points"},
    "polyline": {"points"},
}
SHAPE_ONLY = {"opacity", "transform", "stroke-dasharray", "pathLength"}
GROUP_ATTRIBUTES = INHERITED | {"transform"}
COLOR = re.compile(r"^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
PATH_DATA = re.compile(r"^[MmLlHhVvCcSsQqTtAaZz0-9eE.,+\-\s]+$")
TRANSFORM = re.compile(r"\s*([a-zA-Z]+)\s*\(([^)]*)\)\s*,?")


class Unsupported(Exception):
    """Something in an SVG the converter does not convert."""


def number(text, what):
    try:
        value = float(text)
    except (TypeError, ValueError):
        raise Unsupported(f"{what} is not a number: {text!r}") from None
    if not math.isfinite(value):
        raise Unsupported(f"{what} is not a finite number: {text!r}")
    return value


def fmt(value):
    """A number as short as it reads: 22, 4.5, 0.1152 (at most 4 decimals)."""
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


def transforms(text, where):
    """The transform list as [(name, [numbers])], each checked."""
    steps = []
    position = 0
    for match in TRANSFORM.finditer(text):
        if match.start() != position:
            break
        position = match.end()
        name = match.group(1)
        args = [number(a, f"{where}: {name}") for a in re.split(r"[\s,]+", match.group(2).strip()) if a]
        allowed = {"translate": (1, 2), "scale": (1, 2), "rotate": (1, 3)}
        if name not in allowed:
            raise Unsupported(f"{where}: transform {name} is not supported")
        if len(args) not in allowed[name]:
            raise Unsupported(f"{where}: {name} takes {' or '.join(map(str, allowed[name]))} numbers, not {len(args)}")
        steps.append((name, args))
    if position != len(text) or not steps:
        raise Unsupported(f"{where}: transform {text!r} is not a list of translate, scale and rotate")
    return steps


def group_attributes(name, args):
    """The attributes of the VectorDrawable group that does one transform step."""
    if name == "translate":
        x, y = args[0], args[1] if len(args) == 2 else 0.0
        return {"translateX": x, "translateY": y}
    if name == "scale":
        x, y = args[0], args[1] if len(args) == 2 else args[0]
        return {"scaleX": x, "scaleY": y}
    angle, cx, cy = (args + [0.0, 0.0])[:3]
    return {"rotation": angle, "pivotX": cx, "pivotY": cy}


def color(text, where):
    if text == "none":
        return None
    if not COLOR.match(text):
        raise Unsupported(f"{where}: colour {text!r} is not #rgb, #rrggbb or none")
    digits = text[1:]
    if len(digits) == 3:
        digits = "".join(c * 2 for c in digits)
    return "#" + digits.upper()


def points(text, where):
    values = [number(v, f"{where}: points") for v in re.split(r"[\s,]+", text.strip()) if v]
    if len(values) < 4 or len(values) % 2:
        raise Unsupported(f"{where}: points {text!r} are not pairs of numbers")
    pairs = [f"{fmt(values[i])},{fmt(values[i + 1])}" for i in range(0, len(values), 2)]
    return "M" + " L".join(pairs)


def path_data(tag, attributes, where):
    """The shape as VectorDrawable path data."""

    def get(name):
        return number(attributes.get(name), f"{where}: {name}")

    if tag == "path":
        data = attributes.get("d", "")
        if not data.strip() or not PATH_DATA.match(data):
            raise Unsupported(f"{where}: path data {data!r} is not plain SVG path commands")
        return " ".join(data.split())
    if tag == "circle":
        cx, cy, r = get("cx"), get("cy"), get("r")
        return (
            f"M{fmt(cx - r)},{fmt(cy)} A{fmt(r)},{fmt(r)} 0 1,0 {fmt(cx + r)},{fmt(cy)} "
            f"A{fmt(r)},{fmt(r)} 0 1,0 {fmt(cx - r)},{fmt(cy)} Z"
        )
    if tag == "rect":
        x, y, w, h = get("x"), get("y"), get("width"), get("height")
        return f"M{fmt(x)},{fmt(y)} H{fmt(x + w)} V{fmt(y + h)} H{fmt(x)} Z"
    if tag == "line":
        return f"M{fmt(get('x1'))},{fmt(get('y1'))} L{fmt(get('x2'))},{fmt(get('y2'))}"
    if tag == "polygon":
        return points(attributes.get("points", ""), where) + " Z"
    return points(attributes.get("points", ""), where)


def check_dashes(attributes, where):
    """A dash pattern is allowed only where its first dash covers the whole shape (pathLength)."""
    dashes = attributes.get("stroke-dasharray")
    if dashes is None or dashes == "none":
        return
    length = attributes.get("pathLength")
    first = re.split(r"[\s,]+", dashes.strip())[0]
    if length is None or number(first, f"{where}: stroke-dasharray") < number(length, f"{where}: pathLength"):
        raise Unsupported(f"{where}: stroke-dasharray {dashes!r} leaves gaps, which VectorDrawables do not draw")


def fill_of(paint, tag):
    """The fill colour: none for a line, which has no inside; SVG's default black where nothing sets one."""
    if tag == "line":
        return None
    return paint["fill"] if "fill" in paint else "#000000"


class Shape:
    """One shape to draw: its path data, its paint, its own opacity and the transform steps above it."""

    def __init__(self, data, paint, opacity, steps, tag):
        self.data = data
        self.paint = paint
        self.opacity = opacity
        self.steps = steps
        self.fill = fill_of(paint, tag)
        self.stroke = paint.get("stroke")

    @property
    def painted(self):
        return [p for p in (self.fill, self.stroke) if p is not None]

    def attributes(self, alpha_into_paint):
        """The path's android attributes; the shape's opacity goes into its paint's alpha when told to."""
        out = {"pathData": self.data}
        opacity = self.opacity if alpha_into_paint else 1.0
        if self.fill is not None:
            out["fillColor"] = self.fill
            alpha = self.paint.get("fill-opacity", 1.0) * opacity
            if alpha != 1.0:
                out["fillAlpha"] = alpha
            if self.paint.get("fill-rule") == "evenodd":
                out["fillType"] = "evenOdd"
        if self.stroke is not None:
            out["strokeColor"] = self.stroke
            out["strokeWidth"] = self.paint.get("stroke-width", 1.0)
            alpha = self.paint.get("stroke-opacity", 1.0) * opacity
            if alpha != 1.0:
                out["strokeAlpha"] = alpha
            cap = self.paint.get("stroke-linecap", "butt")
            if cap != "butt":
                out["strokeLineCap"] = cap
            join = self.paint.get("stroke-linejoin", "miter")
            if join != "miter":
                out["strokeLineJoin"] = join
            limit = self.paint.get("stroke-miterlimit", 4.0)
            if limit != 4.0:
                out["strokeMiterLimit"] = limit
        return out


KEYWORDS = {
    "stroke-linecap": ("butt", "round", "square"),
    "stroke-linejoin": ("miter", "round", "bevel"),
    "fill-rule": ("nonzero", "evenodd"),
}


def paint_of(element, inherited, where):
    """The inherited paint with this element's own presentation attributes on top, each checked; a colour of None
    is "none"."""
    paint = dict(inherited)
    for name in sorted(INHERITED & set(element.attrib)):
        value = element.attrib[name].strip()
        if name in ("fill", "stroke"):
            paint[name] = color(value, where)
        elif name in KEYWORDS:
            if value not in KEYWORDS[name]:
                raise Unsupported(f"{where}: {name} {value!r} is not one of {', '.join(KEYWORDS[name])}")
            paint[name] = value
        else:
            paint[name] = number(value, f"{where}: {name}")
    return paint


def shapes(element, inherited, steps, where, out):
    """Collects the shapes under element in document order."""
    tag = element.tag[len(SVG):] if element.tag.startswith(SVG) else element.tag
    here = f"{where} <{tag}>"
    if tag == "g":
        unknown = set(element.attrib) - GROUP_ATTRIBUTES
        if unknown:
            raise Unsupported(f"{here}: attributes {sorted(unknown)} are not supported on a group")
        paint = paint_of(element, inherited, here)
        own = transforms(element.attrib["transform"], here) if "transform" in element.attrib else []
        for child in element:
            shapes(child, paint, steps + own, where, out)
        return
    if tag not in SHAPES:
        raise Unsupported(f"{here}: element is not supported")
    if tag == "rect" and ({"rx", "ry"} & set(element.attrib)):
        raise Unsupported(f"{here}: rounded corners (rx, ry) are not supported")
    unknown = set(element.attrib) - INHERITED - SHAPE_ONLY - GEOMETRY[tag]
    if unknown:
        raise Unsupported(f"{here}: attributes {sorted(unknown)} are not supported")
    if len(element):
        raise Unsupported(f"{here}: a shape with children (an animation?) is not supported")
    opacity = number(element.attrib.get("opacity", "1"), f"{here}: opacity")
    if opacity > 1:
        raise Unsupported(f"{here}: opacity {fmt(opacity)} is above 1")
    if opacity <= 0:
        return
    check_dashes(element.attrib, here)
    paint = paint_of(element, inherited, here)
    own = transforms(element.attrib["transform"], here) if "transform" in element.attrib else []
    shape = Shape(path_data(tag, element.attrib, here), paint, opacity, steps + own, tag)
    if shape.painted:
        out.append(shape)


def layers(shapes_):
    """The shapes as layers: runs drawn straight into one vector, and each shape that needs its own opacity apart."""
    out = []
    for shape in shapes_:
        own = shape.opacity < 1 and len(shape.painted) > 1
        if own:
            out.append((shape.opacity, [shape]))
        elif out and out[-1][0] is None:
            out[-1][1].append(shape)
        else:
            out.append((None, [shape]))
    return out


def vector_xml(shapes_, size, alpha, indent, into_paint):
    """A <vector> of the shapes, each under one group per transform step."""
    width, height = size
    pad = " " * indent
    lines = [f'{pad}<vector xmlns:android="{ANDROID}"' if indent == 0 else f"{pad}<vector"]
    lines.append(f'{pad}    android:width="{fmt(width)}dp"')
    lines.append(f'{pad}    android:height="{fmt(height)}dp"')
    if alpha is not None:
        lines.append(f'{pad}    android:alpha="{fmt(alpha)}"')
    lines.append(f'{pad}    android:viewportWidth="{fmt(width)}"')
    lines.append(f'{pad}    android:viewportHeight="{fmt(height)}">')
    for shape in shapes_:
        depth = indent + 4
        for name, args in shape.steps:
            attributes = " ".join(f'android:{k}="{fmt(v)}"' for k, v in group_attributes(name, args).items())
            lines.append(f"{' ' * depth}<group {attributes}>")
            depth += 4
        attributes = shape.attributes(into_paint)
        text = [f'{" " * depth}<path']
        for key, value in attributes.items():
            shown = fmt(value) if isinstance(value, float) else value
            text.append(f'{" " * depth}    android:{key}="{shown}"')
        text[-1] += " />"
        lines.extend(text)
        for _ in shape.steps:
            depth -= 4
            lines.append(f"{' ' * depth}</group>")
    lines.append(f"{pad}</vector>")
    return lines


def convert(svg_text, source_name):
    """The drawable XML of one SVG; raises Unsupported, naming source_name, for anything not converted."""
    try:
        root = ET.fromstring(svg_text)
    except ET.ParseError as e:
        raise Unsupported(f"{source_name}: not XML: {e}") from None
    if root.tag != SVG + "svg":
        raise Unsupported(f"{source_name}: the root is not an SVG svg element")
    unknown = set(root.attrib) - {"viewBox"}
    if unknown:
        raise Unsupported(f"{source_name} <svg>: attributes {sorted(unknown)} are not supported")
    box = [number(v, f"{source_name}: viewBox") for v in root.attrib.get("viewBox", "").split()]
    if len(box) != 4 or box[0] != 0 or box[1] != 0 or box[2] <= 0 or box[3] <= 0:
        raise Unsupported(f"{source_name}: viewBox must be 0 0 width height")
    found = []
    for child in root:
        shapes(child, {}, [], source_name, found)
    if not found:
        raise Unsupported(f"{source_name}: draws nothing")
    size = (box[2], box[3])
    header = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- Generated by scripts/weather_icons.py from icons/weather/{source_name}; do not edit (D38). -->",
    ]
    parts = layers(found)
    if len(parts) == 1 and parts[0][0] is None:
        return "\n".join(header + vector_xml(found, size, None, 0, True)) + "\n"
    lines = [f'<layer-list xmlns:android="{ANDROID}"', f'    xmlns:aapt="{AAPT}">']
    for alpha, layer in parts:
        lines.append("    <item>")
        lines.append('        <aapt:attr name="android:drawable">')
        lines.extend(vector_xml(layer, size, alpha, 12, alpha is None))
        lines.append("        </aapt:attr>")
        lines.append("    </item>")
    lines.append("</layer-list>")
    return "\n".join(header + lines) + "\n"


def drawable_name(source):
    match = SOURCE_NAME.match(source.name)
    if not match:
        raise Unsupported(f"{source}: not named <condition>-<day|night>-still.svg")
    return f"{PREFIX}{match.group('condition').replace('-', '_')}_{match.group('variant')}.xml"


def outputs(sources=SOURCES, res=RES):
    """Every drawable the sources make: {path: text}."""
    out = {}
    for mode, folder in MODES.items():
        files = sorted((sources / mode).glob("*.svg"))
        if not files:
            raise Unsupported(f"{sources / mode}: no SVGs")
        for source in files:
            target = res / folder / drawable_name(source)
            out[target] = convert(source.read_text(encoding="utf-8"), f"{mode}/{source.name}")
    return out


def existing(res=RES):
    """The weather drawables in the repo now."""
    return {path for folder in MODES.values() for path in (res / folder).glob(PREFIX + "*.xml")}


def shown(path):
    """A path for a message: relative to the repository where it lies inside it."""
    try:
        return path.relative_to(ROOT)
    except ValueError:
        return path


def run(check, sources=SOURCES, res=RES, err=None, out=None):
    """Writes the drawables of sources into res, or with check only compares them; the exit code."""
    err = err or sys.stderr
    out = out or sys.stdout
    try:
        written = outputs(sources, res)
    except Unsupported as e:
        print(f"weather_icons: {e}", file=err)
        return 1
    stray = existing(res) - set(written)
    if check:
        differ = [p for p, text in written.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
        for path in sorted(differ):
            print(f"weather_icons: {shown(path)} is not what the script writes", file=err)
        for path in sorted(stray):
            print(f"weather_icons: {shown(path)} has no source SVG", file=err)
        return 1 if differ or stray else 0
    for path in stray:
        path.unlink()
    for path, text in written.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(f"weather_icons: wrote {len(written)} drawables", file=out)
    return 0


def main(argv):
    check = argv[1:] == ["--check"]
    if argv[1:] and not check:
        print(__doc__, file=sys.stderr)
        return 2
    return run(check)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
