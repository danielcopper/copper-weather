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

Every drawable shows the same frame, the smallest square around what all the
SVGs draw, light and dark, centred on it, rather than the SVGs' whole viewBox,
so each motif fills more of its image while the icons keep their sizes and
places relative to each other (D38). What a shape draws is found from its
path: each segment's points, curves and arcs at many points along them,
through its transforms; a stroke adds half its width, scaled with the shape,
around every point and, at a miter join, the miter's tip; it is cut to the
SVG's viewBox, which the dashboard draws in. A shape whose bounds cannot be
found fails as an unsupported one does. The drawables clip to that viewBox.

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


# Points taken along each curve and arc for its bounds: along half a circle of radius 32, the largest an icon's viewBox
# holds, they miss its true extreme by under 0.01.
SAMPLES = 64


def numbers_of(data, where):
    """Path data as [(command, [numbers])]; an arc's flags are numbers 0 or 1."""
    tokens = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|[+\-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+\-]?\d+)?", data)
    commands = []
    for token in tokens:
        if token.isalpha():
            commands.append((token, []))
        elif not commands:
            raise Unsupported(f"{where}: path data {data!r} does not start with a command")
        else:
            commands[-1][1].append(float(token))
    return commands


def arc_points(start, rx, ry, angle, large, sweep, end):
    """Points along an SVG arc from start to end (SVG 1.1, F.6.5), the end included."""
    (x1, y1), (x2, y2) = start, end
    if (x1, y1) == (x2, y2):
        return []
    rx, ry = abs(rx), abs(ry)
    if rx == 0 or ry == 0:
        return [end]
    phi = math.radians(angle)
    cos, sin = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    xp, yp = cos * dx + sin * dy, -sin * dx + cos * dy
    scale = (xp * xp) / (rx * rx) + (yp * yp) / (ry * ry)
    if scale > 1:
        rx, ry = rx * math.sqrt(scale), ry * math.sqrt(scale)
    num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
    den = rx * rx * yp * yp + ry * ry * xp * xp
    root = math.sqrt(max(0.0, num / den)) if den else 0.0
    if large == sweep:
        root = -root
    cxp, cyp = root * rx * yp / ry, -root * ry * xp / rx
    cx = cos * cxp - sin * cyp + (x1 + x2) / 2
    cy = sin * cxp + cos * cyp + (y1 + y2) / 2

    def at(u, v):
        return math.atan2(v, u)

    theta = at((xp - cxp) / rx, (yp - cyp) / ry)
    delta = at((-xp - cxp) / rx, (-yp - cyp) / ry) - theta
    if sweep and delta < 0:
        delta += 2 * math.pi
    elif not sweep and delta > 0:
        delta -= 2 * math.pi
    out = []
    for i in range(1, SAMPLES + 1):
        t = theta + delta * i / SAMPLES
        u, v = rx * math.cos(t), ry * math.sin(t)
        out.append((cos * u - sin * v + cx, sin * u + cos * v + cy))
    return out


def bezier_points(controls):
    """Points along a quadratic or cubic Bezier curve given all its control points, the end included."""
    out = []
    for i in range(1, SAMPLES + 1):
        t = i / SAMPLES
        points_ = list(controls)
        while len(points_) > 1:
            points_ = [((1 - t) * a[0] + t * b[0], (1 - t) * a[1] + t * b[1]) for a, b in zip(points_, points_[1:])]
        out.append(points_[0])
    return out


ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def polylines(data, where):
    """The path as polylines, one per subpath, each [(x, y)], with whether it is closed."""
    lines = []
    current = start = (0.0, 0.0)
    control = None
    previous = ""
    for command, values in numbers_of(data, where):
        upper = command.upper()
        arity = ARITY[upper]
        if (arity == 0 and values) or (arity and (not values or len(values) % arity)):
            raise Unsupported(f"{where}: path command {command} has {len(values)} numbers")
        if upper == "Z":
            if lines:
                lines[-1][1] = True
            current = start
            previous = upper
            continue
        for index in range(0, len(values), arity):
            v = values[index:index + arity]
            relative = command.islower()
            x0, y0 = current

            def point(px, py):
                return (x0 + px, y0 + py) if relative else (px, py)

            if upper == "M" and index == 0:
                current = start = point(*v)
                lines.append([[current], False])
                control = None
                previous = "M"
                continue
            if not lines:
                raise Unsupported(f"{where}: path data does not start with M")
            segment = []
            if upper in ("M", "L"):
                segment = [point(*v)]
            elif upper == "H":
                segment = [(x0 + v[0] if relative else v[0], y0)]
            elif upper == "V":
                segment = [(x0, y0 + v[0] if relative else v[0])]
            elif upper == "C":
                c1, c2, end = point(v[0], v[1]), point(v[2], v[3]), point(v[4], v[5])
                segment = bezier_points([current, c1, c2, end])
                control = c2
            elif upper == "S":
                c1 = (2 * x0 - control[0], 2 * y0 - control[1]) if previous in ("C", "S") else current
                c2, end = point(v[0], v[1]), point(v[2], v[3])
                segment = bezier_points([current, c1, c2, end])
                control = c2
            elif upper == "Q":
                c, end = point(v[0], v[1]), point(v[2], v[3])
                segment = bezier_points([current, c, end])
                control = c
            elif upper == "T":
                c = (2 * x0 - control[0], 2 * y0 - control[1]) if previous in ("Q", "T") else current
                end = point(v[0], v[1])
                segment = bezier_points([current, c, end])
                control = c
            else:
                if v[3] not in (0, 1) or v[4] not in (0, 1):
                    raise Unsupported(f"{where}: arc flags {fmt(v[3])} {fmt(v[4])} are not 0 or 1")
                segment = arc_points(current, v[0], v[1], v[2], v[3], v[4], point(v[5], v[6]))
            if upper not in ("C", "S", "Q", "T"):
                control = None
            lines[-1][0].extend(segment)
            if segment:
                current = segment[-1]
            previous = upper
    for points_, _ in lines:
        for x, y in points_:
            if not (math.isfinite(x) and math.isfinite(y)):
                raise Unsupported(f"{where}: the bounds of path data {data!r} cannot be found")
    return [(points_, closed) for points_, closed in lines]


def apply(steps, point):
    """A point through the transform steps above a shape, the innermost first."""
    x, y = point
    for name, args in reversed(steps):
        if name == "translate":
            x, y = x + args[0], y + (args[1] if len(args) == 2 else 0.0)
        elif name == "scale":
            sx = args[0]
            sy = args[1] if len(args) == 2 else args[0]
            x, y = x * sx, y * sy
        else:
            angle, cx, cy = (args + [0.0, 0.0])[:3]
            a = math.radians(angle)
            dx, dy = x - cx, y - cy
            x, y = cx + dx * math.cos(a) - dy * math.sin(a), cy + dx * math.sin(a) + dy * math.cos(a)
    return x, y


def scale_of(steps):
    """How much the transform steps enlarge a length at most."""
    factor = 1.0
    for name, args in steps:
        if name == "scale":
            factor *= max(abs(args[0]), abs(args[1] if len(args) == 2 else args[0]))
    return factor


def miter_tips(points_, closed, half, limit):
    """The tips of a stroke's miter joins along a polyline, where the miter is within its limit."""
    tips = []
    count = len(points_)
    corners = range(count) if closed else range(1, count - 1)
    for i in corners:
        a, b, c = points_[i - 1], points_[i], points_[(i + 1) % count]
        u = (b[0] - a[0], b[1] - a[1])
        w = (c[0] - b[0], c[1] - b[1])
        lu, lw = math.hypot(*u), math.hypot(*w)
        if lu == 0 or lw == 0:
            continue
        u, w = (u[0] / lu, u[1] / lu), (w[0] / lw, w[1] / lw)
        cos_turn = max(-1.0, min(1.0, u[0] * w[0] + u[1] * w[1]))
        cos_half = math.sqrt((1 + cos_turn) / 2)
        if cos_half == 0 or 1 / cos_half > limit:
            continue
        bisector = (w[0] - u[0], w[1] - u[1])
        length = math.hypot(*bisector)
        if length == 0:
            continue
        reach = half / cos_half
        tips.append((b[0] - bisector[0] / length * reach, b[1] - bisector[1] / length * reach))
    return tips


def bounds_of(shape, box, where):
    """The box (x0, y0, x1, y1) of what the shape draws, cut to the SVG's viewBox; None when it draws nothing there."""
    found = []
    tips = []
    pad = 0.0
    if shape.stroke is not None:
        pad = shape.paint.get("stroke-width", 1.0) / 2 * scale_of(shape.steps)
    for points_, closed in polylines(shape.data, where):
        if shape.stroke is not None and shape.paint.get("stroke-linejoin", "miter") == "miter":
            half = shape.paint.get("stroke-width", 1.0) / 2
            limit = shape.paint.get("stroke-miterlimit", 4.0)
            tips += [apply(shape.steps, p) for p in miter_tips(points_, closed, half, limit)]
        if shape.stroke is not None and shape.paint.get("stroke-linecap", "butt") == "square" and not closed:
            raise Unsupported(f"{where}: stroke-linecap square is not supported")
        found += [apply(shape.steps, p) for p in points_]
    if not found:
        raise Unsupported(f"{where}: the bounds of path data {shape.data!r} cannot be found")
    # Every point grows by the stroke's half width; a miter's tip already lies where the stroke ends.
    xs = [x - pad for x, _ in found] + [x + pad for x, _ in found] + [x for x, _ in tips]
    ys = [y - pad for _, y in found] + [y + pad for _, y in found] + [y for _, y in tips]
    x0, y0 = max(min(xs), 0.0), max(min(ys), 0.0)
    x1, y1 = min(max(xs), box[0]), min(max(ys), box[1])
    return (x0, y0, x1, y1) if x0 < x1 and y0 < y1 else None


def frame_of(parsed):
    """The common frame (x, y, side) of parsed SVGs [(name, size, shapes)]: the smallest square around the union of
    what they draw, centred on it."""
    boxes = []
    for name, size, shapes_ in parsed:
        for shape in shapes_:
            box = bounds_of(shape, size, name)
            if box is not None:
                boxes.append(box)
    if not boxes:
        raise Unsupported("the SVGs draw nothing")
    x0, y0 = min(b[0] for b in boxes), min(b[1] for b in boxes)
    x1, y1 = max(b[2] for b in boxes), max(b[3] for b in boxes)
    side = max(x1 - x0, y1 - y0)
    return ((x0 + x1) / 2 - side / 2, (y0 + y1) / 2 - side / 2, side)


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


def vector_xml(shapes_, size, alpha, indent, into_paint, frame=None):
    """A <vector> of the shapes, each under one group per transform step; with a frame (x, y, side), the vector shows
    that square of the viewBox, clipped to the viewBox."""
    width, height = size if frame is None else (frame[2], frame[2])
    pad = " " * indent
    lines = [f'{pad}<vector xmlns:android="{ANDROID}"' if indent == 0 else f"{pad}<vector"]
    lines.append(f'{pad}    android:width="{fmt(width)}dp"')
    lines.append(f'{pad}    android:height="{fmt(height)}dp"')
    if alpha is not None:
        lines.append(f'{pad}    android:alpha="{fmt(alpha)}"')
    lines.append(f'{pad}    android:viewportWidth="{fmt(width)}"')
    lines.append(f'{pad}    android:viewportHeight="{fmt(height)}">')
    base = indent + 4
    if frame is not None:
        lines.append(f'{" " * base}<group android:translateX="{fmt(-frame[0])}" android:translateY="{fmt(-frame[1])}">')
        lines.append(f'{" " * (base + 4)}<clip-path android:pathData="M0,0 H{fmt(size[0])} V{fmt(size[1])} H0 Z" />')
        base += 4
    for shape in shapes_:
        depth = base
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
    if frame is not None:
        lines.append(f"{' ' * (indent + 4)}</group>")
    lines.append(f"{pad}</vector>")
    return lines


def parse(svg_text, source_name):
    """An SVG's viewBox size and its shapes; raises Unsupported, naming source_name, for anything not converted."""
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
    return (box[2], box[3]), found


def convert(svg_text, source_name, frame=None):
    """The drawable XML of one SVG, showing its whole viewBox or, given one, the frame (x, y, side); raises
    Unsupported, naming source_name, for anything not converted."""
    size, found = parse(svg_text, source_name)
    header = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- Generated by scripts/weather_icons.py from icons/weather/{source_name}; do not edit (D38). -->",
    ]
    parts = layers(found)
    if len(parts) == 1 and parts[0][0] is None:
        return "\n".join(header + vector_xml(found, size, None, 0, True, frame)) + "\n"
    lines = [f'<layer-list xmlns:android="{ANDROID}"', f'    xmlns:aapt="{AAPT}">']
    for alpha, layer in parts:
        lines.append("    <item>")
        lines.append('        <aapt:attr name="android:drawable">')
        lines.extend(vector_xml(layer, size, alpha, 12, alpha is None, frame))
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
    """Every drawable the sources make, each showing their common frame: {path: text}."""
    found = []
    for mode, folder in MODES.items():
        files = sorted((sources / mode).glob("*.svg"))
        if not files:
            raise Unsupported(f"{sources / mode}: no SVGs")
        for source in files:
            name = f"{mode}/{source.name}"
            text = source.read_text(encoding="utf-8")
            found.append((res / folder / drawable_name(source), name, text, *parse(text, name)))
    frame = frame_of([(name, size, shapes_) for _, name, _, size, shapes_ in found])
    return {target: convert(text, name, frame) for target, name, text, _, _ in found}


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
