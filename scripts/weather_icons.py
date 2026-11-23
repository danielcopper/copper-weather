"""Writes the widget's weather icons as Android VectorDrawables (D38) and AnimatedVectorDrawables (D43).

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
  stroke-linecap (butt or round; square only on a closed shape, whose stroke
  has no ends), stroke-linejoin, stroke-miterlimit, fill-rule, fill-opacity
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
found fails as an unsupported one does, naming the file and the element. The
drawables clip to that viewBox.

The animated icons (D43). Beside each still SVG lies the dashboard's animated
one, <condition>-<day|night>.svg, the same picture with SMIL animations; each
still image needs its animated icon and each animated icon its still image.
Each becomes the AnimatedVectorDrawable
weather_<condition>_<day|night>_animated.xml in drawable/ and drawable-night/,
showing the still images' frame, and the layout of the same name in layout/,
a ProgressBar with it as its indeterminate drawable (D6). The shapes, paint and
transforms are converted as above, with these differences:

- opacity on a group, which a VectorDrawable has none of, goes into the alpha
  of each shape under it; where those are all stroked alike and not filled, of
  straight lines, still and moved only by translations and rotations, they are
  drawn as one path, so their overlaps are not more opaque, as SVG draws them;
  others under a group's opacity below 1 add up where they overlap;
- a shape that paints a fill and a stroke at an opacity below 1 is drawn with
  that opacity in both, as an AnimatedVectorDrawable has one vector and no
  layers: where the stroke covers the fill, the two add up.

The animations it translates, and fails on anything else, naming the file and
the element:

- animate of opacity on a shape or group (fillAlpha and strokeAlpha, times
  fill-opacity and stroke-opacity); of r on a circle: one that draws a single
  disk, filled only or filled and stroked alike, becomes that disk in a group
  scaled about its centre (scaleX, scaleY), else its outline changes shape
  (pathData); of y2 on a vertical line, its end moving along it: the line to
  its farthest end, trimmed (trimPathEnd); of d on a path whose values keep
  their commands (pathData); of stroke-dashoffset on a shape with pathLength
  and stroke-dasharray of one dash and its gap, while no second dash reaches
  the path: the stroke trimmed to where that dash lies (trimPathStart,
  trimPathEnd), a key time added where the trim stops following the offset;
- animateTransform of translate, scale or rotate (about a fixed centre) on a
  group without a transform of its own: a named group's translateX and
  translateY, scaleX and scaleY, or rotation with pivotX and pivotY;
- timing: dur and begin in seconds, begin at 0 or before (a negative begin,
  the animation already that far on at the start), repeatCount indefinite,
  values with or without keyTimes, calcMode linear or spline with keySplines
  in 0..1.

Each animated number becomes an objectAnimator repeating endlessly: from its
first value to its last, or there and back as an animator played forwards and
in reverse where the two halves mirror each other, each with its spline as a
pathInterpolator, while it starts at its first value; else keyframes over its
whole duration, starting at its phase, the spline of an interval cut where the
phase or a trim's turn falls (de Casteljau) and each piece scaled to a
pathInterpolator, a jump back to its first value where it repeats. The
pathInterpolators are shared: one weather_spline_<x1>_<y1>_<x2>_<y2>.xml each
in interpolator/, its control points in ten-thousandths, for every spline the
drawables use. A shape's data takes only the first kind, since Android's
render thread plays only a path animator's first and last value; a changing
outline with a negative begin fails. Each number's animator is checked
against its SMIL animation at 400 points of two periods, within the
tolerance FOLLOW and SLIP set; one that does not follow it fails, naming the
file and the element. A shape's data is not sampled. The drawable's vector
shows each value at the start, so the first frame does not jump.

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
ANIMATED_NAME = re.compile(r"^(?P<condition>[a-z]+(?:-[a-z]+)*)-(?P<variant>day|night)\.svg$")
PREFIX = "weather_"
# The animated icon's drawable and its layout end in this.
ANIMATED = "_animated"
LAYOUT = "layout"

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


def fmt(value, digits=4):
    """A number as short as it reads: 22, 4.5, 0.1152 (at most 4 decimals, or digits)."""
    text = f"{value:.{digits}f}".rstrip("0").rstrip(".")
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


def tag_of(element):
    """An element's tag without the SVG namespace."""
    return element.tag[len(SVG):] if element.tag.startswith(SVG) else element.tag


def check_shape(element, tag, here):
    """Fails on a shape's attributes the converters do not take, and on a rect with rounded corners."""
    if tag == "rect" and ({"rx", "ry"} & set(element.attrib)):
        raise Unsupported(f"{here}: rounded corners (rx, ry) are not supported")
    unknown = set(element.attrib) - INHERITED - SHAPE_ONLY - GEOMETRY[tag]
    if unknown:
        raise Unsupported(f"{here}: attributes {sorted(unknown)} are not supported")


def svg_root(svg_text, source_name):
    """An SVG's root element and its viewBox's size; fails on anything else than an svg with a viewBox from 0 0."""
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
    return root, (box[2], box[3])


def frame_lines(frame, size, depth):
    """The group that moves the frame (x, y, side) into view and its clip to the viewBox of size, at depth; its
    closing tag is the caller's."""
    return [
        f'{" " * depth}<group android:translateX="{fmt(-frame[0])}" android:translateY="{fmt(-frame[1])}">',
        f'{" " * (depth + 4)}<clip-path android:pathData="M0,0 H{fmt(size[0])} V{fmt(size[1])} H0 Z" />',
    ]


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
    # A closed subpath that returns to its start before Z: its start is a corner, not a point drawn twice.
    for line in lines:
        if line[1] and len(line[0]) > 1 and line[0][-1] == line[0][0]:
            line[0].pop()
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


def bounds_of(shape, box):
    """The box (x0, y0, x1, y1) of what the shape draws, cut to the SVG's viewBox; None when it draws nothing there."""
    where = shape.where
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
    for _, size, shapes_ in parsed:
        for shape in shapes_:
            box = bounds_of(shape, size)
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
    """One shape to draw: its path data, its paint, its own opacity and the transform steps above it; where names its
    file and element in a message."""

    def __init__(self, data, paint, opacity, steps, tag, where):
        self.where = where
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
    tag = tag_of(element)
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
    check_shape(element, tag, here)
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
    shape = Shape(path_data(tag, element.attrib, here), paint, opacity, steps + own, tag, here)
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
        lines += frame_lines(frame, size, base)
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
    root, size = svg_root(svg_text, source_name)
    found = []
    for child in root:
        shapes(child, {}, [], source_name, found)
    if not found:
        raise Unsupported(f"{source_name}: draws nothing")
    return size, found


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


# The animated icons (D43): the dashboard's animated SVGs, SMIL animations on the shapes and groups of a still icon, as
# AnimatedVectorDrawables.

ANIMATIONS = {"animate", "animateTransform"}
ANIMATION_ATTRIBUTES = {"attributeName", "type", "dur", "begin", "repeatCount", "calcMode", "keySplines", "values",
                        "keyTimes"}
# What an <animate> may animate, on which elements.
ANIMATABLE = {
    "g": {"opacity"},
    "circle": {"opacity", "r"},
    "line": {"opacity", "y2", "stroke-dashoffset"},
    "path": {"opacity", "d", "stroke-dashoffset"},
    "rect": {"opacity"},
    "polygon": {"opacity"},
    "polyline": {"opacity"},
}
SECONDS = re.compile(r"^(-?\d+(?:\.\d+)?)s$")
# Fractions of an animation's time: 6 decimals, so a keyframe of a 40 s animation is off by 0.02 ms at most.
FRACTION_DIGITS = 6
# How closely an emitted animator must follow its SMIL animation: a thousandth of the values' range, or of 1 where it is
# smaller, and a slip in time of a ten-thousandth of the duration, which the fractions' rounding stays within.
FOLLOW = 1e-3
SLIP = 1e-4
CHECK_POINTS = 400
LINEAR = "@android:interpolator/linear"
# The shared interpolators' names: this and each control point in ten-thousandths, the 4 decimals they are written in.
SPLINE = "weather_spline_"
SPLINE_SCALE = 10000
INTERPOLATOR = "interpolator"


def cubic(a, b, c, d, s):
    """A one-dimensional cubic Bezier from a to d with the control values b and c, at its parameter s."""
    m = 1 - s
    return m * m * m * a + 3 * m * m * s * b + 3 * m * s * s * c + s * s * s * d


def parameter(spline, value, axis):
    """The parameter at which the spline's x (axis 0) or y (axis 1), each rising from 0 to 1, reaches value."""
    low, high = 0.0, 1.0
    first, second = spline[axis], spline[axis + 2]
    for _ in range(60):
        middle = (low + high) / 2
        if cubic(0.0, first, second, 1.0, middle) < value:
            low = middle
        else:
            high = middle
    return (low + high) / 2


def progress(spline, u):
    """How far an interval has come at u of its time, by its spline (x1, y1, x2, y2) as SMIL's keySplines read; None
    is linear."""
    if spline is None:
        return u
    return cubic(0.0, spline[1], spline[3], 1.0, parameter(spline, u, 0))


def cut(spline, s):
    """The spline cut at its parameter s: its two pieces, each scaled to the unit square as an interval's spline (None
    where a piece does not move, which any interpolation draws alike), and the cut's time and progress."""
    points = [(0.0, 0.0), (spline[0], spline[1]), (spline[2], spline[3]), (1.0, 1.0)]

    def lerp(a, b):
        return (a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s)

    p01, p12, p23 = lerp(points[0], points[1]), lerp(points[1], points[2]), lerp(points[2], points[3])
    p012, p123 = lerp(p01, p12), lerp(p12, p23)
    at = lerp(p012, p123)

    def unit(first, second, origin, end):
        width, height = end[0] - origin[0], end[1] - origin[1]
        if width <= 0 or height <= 0:
            return None
        return ((first[0] - origin[0]) / width, (first[1] - origin[1]) / height,
                (second[0] - origin[0]) / width, (second[1] - origin[1]) / height)

    return unit(p01, p012, points[0], at), unit(p123, p23, at, points[3]), at


class Track:
    """One number an animation moves: its key times (fractions of its duration), its values there, each interval's
    spline (None for linear), its duration in seconds and the fraction of it already past at the start, from a negative
    begin. reference gives the value at a time in seconds as SMIL computes it, to check the animators against."""

    def __init__(self, times, values, splines, duration, phase, reference, where):
        self.times = tuple(times)
        self.values = tuple(values)
        self.splines = tuple(splines)
        self.duration = duration
        self.phase = phase
        self.reference = reference
        self.where = where

    def mapped(self, function):
        """The track with each value through function, which must be affine on each interval (a value split at its
        kinks first, split_at)."""
        reference = self.reference
        return Track(self.times, [function(v) for v in self.values], self.splines, self.duration, self.phase,
                     lambda t: function(reference(t)), self.where)

    def split_at_time(self, time, value=None):
        """The track with a key time added at time, inside an interval, the spline cut there, and its value there, or
        value when given; time must lie strictly inside an interval."""
        for i in range(len(self.times) - 1):
            start, end = self.times[i], self.times[i + 1]
            if start < time < end:
                u = (time - start) / (end - start)
                spline = self.splines[i]
                if spline is None:
                    pieces, moved = (None, None), u
                else:
                    left, right, at = cut(spline, parameter(spline, u, 0))
                    pieces, moved = (left, right), at[1]
                    for piece in pieces:
                        if piece is not None and not all(0 <= v <= 1 for v in piece):
                            raise Unsupported(f"{self.where}: the spline {' '.join(fmt(v) for v in spline)} cut at "
                                              f"{fmt(u)} of its interval leaves 0..1")
                if value is None:
                    value = self.values[i] + (self.values[i + 1] - self.values[i]) * moved
                return Track(self.times[:i + 1] + (time,) + self.times[i + 1:],
                             self.values[:i + 1] + (value,) + self.values[i + 1:],
                             self.splines[:i] + pieces + self.splines[i + 1:], self.duration, self.phase,
                             self.reference, self.where)
        raise Unsupported(f"{self.where}: no interval of the animation lies around {fmt(time, FRACTION_DIGITS)} of "
                          "its duration to cut")

    def split_at(self, value):
        """The track with a key time added wherever an interval of some length passes through value; one of no length,
        a jump, passes it at once."""
        track = self
        i = 0
        while i < len(track.values) - 1:
            a, b = track.values[i], track.values[i + 1]
            start, end = track.times[i], track.times[i + 1]
            if (a - value) * (b - value) < 0 and start < end:
                share = (value - a) / (b - a)
                spline = track.splines[i]
                u = share if spline is None else cubic(0.0, spline[0], spline[2], 1.0, parameter(spline, share, 1))
                # Kept off the interval's ends, where rounding could put it, so the cut falls inside it.
                track = track.split_at_time(start + (end - start) * min(max(u, 1e-9), 1 - 1e-9), value)
            i += 1
        return track

    def at(self, time):
        """The value at time, a fraction of the duration from the track's start, as SMIL interpolates it."""
        for i in range(len(self.times) - 1):
            if self.times[i] <= time <= self.times[i + 1]:
                span = self.times[i + 1] - self.times[i]
                u = 0.0 if span == 0 else (time - self.times[i]) / span
                return self.values[i] + (self.values[i + 1] - self.values[i]) * progress(self.splines[i], u)
        return self.values[-1]

    @property
    def start(self):
        """The value when the animation starts, at its phase: the base the drawable shows before it runs."""
        return self.at(self.phase)

    @property
    def still(self):
        return all(v == self.values[0] for v in self.values)


def seconds(text, what, where):
    match = SECONDS.match(text.strip()) if text is not None else None
    if not match:
        raise Unsupported(f"{where}: {what} {text!r} is not a time in seconds such as 4s or -1.98s")
    return float(match.group(1))


def numbers(text, where):
    return [number(v, where) for v in re.split(r"[\s,]+", text.strip()) if v]


def timing(element, count, where):
    """The SMIL timing of an animation with count values: its key times, splines, duration and phase."""
    unknown = set(element.attrib) - ANIMATION_ATTRIBUTES
    if unknown:
        raise Unsupported(f"{where}: attributes {sorted(unknown)} are not supported")
    if element.get("repeatCount") != "indefinite":
        raise Unsupported(f"{where}: repeatCount {element.get('repeatCount')!r} is not indefinite")
    duration = seconds(element.get("dur"), "dur", where)
    if duration <= 0:
        raise Unsupported(f"{where}: dur {element.get('dur')!r} is not above 0")
    begin = seconds(element.get("begin", "0s"), "begin", where)
    if begin > 0:
        raise Unsupported(f"{where}: begin {element.get('begin')!r} after the start is not supported")
    if count < 2:
        raise Unsupported(f"{where}: values need at least two")
    if "keyTimes" in element.attrib:
        times = numbers(element.get("keyTimes").replace(";", " "), f"{where}: keyTimes")
        if len(times) != count or times[0] != 0 or times[-1] != 1 or any(b < a for a, b in zip(times, times[1:])):
            raise Unsupported(f"{where}: keyTimes {element.get('keyTimes')!r} do not run from 0 to 1 by the values")
    else:
        times = [i / (count - 1) for i in range(count)]
    mode = element.get("calcMode", "linear")
    if mode == "linear":
        if "keySplines" in element.attrib:
            raise Unsupported(f"{where}: keySplines without calcMode spline")
        splines = [None] * (count - 1)
    elif mode == "spline":
        parts = [p for p in element.get("keySplines", "").split(";") if p.strip()]
        splines = [tuple(numbers(p, f"{where}: keySplines")) for p in parts]
        if len(splines) != count - 1 or any(len(s) != 4 or not all(0 <= v <= 1 for v in s) for s in splines):
            raise Unsupported(f"{where}: keySplines {element.get('keySplines')!r} are not one x1 y1 x2 y2 in 0..1 "
                              "per interval")
        splines = [None if s == (0.0, 0.0, 1.0, 1.0) else s for s in splines]
    else:
        raise Unsupported(f"{where}: calcMode {mode!r} is not supported")
    return times, splines, duration, (-begin % duration) / duration


def track_of(element, values, where):
    """The track of an animation's numbers values, with its timing."""
    times, splines, duration, phase = timing(element, len(values), where)
    original = Track(times, values, splines, duration, 0.0, None, where)
    return Track(times, values, splines, duration, phase, lambda t: original.at(((t / duration) + phase) % 1.0), where)


class FromTo:
    """An objectAnimator of one property from a value to another with a spline, played forwards again and again, or
    forwards and backwards (reverse); its values are numbers, or a path's data (path)."""

    def __init__(self, prop, duration_ms, start, end, spline, reverse=False, path=False):
        self.prop = prop
        self.duration_ms = duration_ms
        self.start = start
        self.end = end
        self.spline = spline
        self.reverse = reverse
        self.path = path

    def at(self, ms):
        """The number at ms after the start, as Android's animator computes it."""
        period = self.duration_ms
        u = (ms % period) / period
        if self.reverse and int(ms // period) % 2 == 1:
            u = 1 - u
        return self.start + (self.end - self.start) * progress(self.spline, u)

    def xml(self, depth):
        pad = " " * depth
        inner = " " * (depth + 4)
        shown = (lambda v: v) if self.path else fmt
        lines = [
            f"{pad}<objectAnimator",
            f'{inner}android:propertyName="{self.prop}"',
            f'{inner}android:valueType="{"pathType" if self.path else "floatType"}"',
            f'{inner}android:valueFrom="{shown(self.start)}"',
            f'{inner}android:valueTo="{shown(self.end)}"',
            f'{inner}android:duration="{self.duration_ms}"',
            f'{inner}android:repeatCount="infinite"',
        ]
        if self.reverse:
            lines.append(f'{inner}android:repeatMode="reverse"')
        lines.append(f'{inner}android:interpolator="{interpolator(self.spline)}" />')
        return lines


class Keyframes:
    """An objectAnimator of one number over its whole duration, played again and again, by keyframes [(fraction,
    value, spline of the interval before it)], which follow a negative begin."""

    def __init__(self, prop, duration_ms, keyframes):
        self.prop = prop
        self.duration_ms = duration_ms
        self.keyframes = keyframes

    def at(self, ms):
        """The value at ms after the start, as Android's animator computes it."""
        u = (ms % self.duration_ms) / self.duration_ms
        frames = self.keyframes
        for i in range(1, len(frames)):
            if u < frames[i][0]:
                previous = frames[i - 1]
                span = frames[i][0] - previous[0]
                local = (u - previous[0]) / span
                return previous[1] + (frames[i][1] - previous[1]) * progress(frames[i][2], local)
        return frames[-1][1]

    def xml(self, depth):
        pad = " " * depth
        inner = " " * (depth + 4)
        lines = [
            f"{pad}<objectAnimator",
            f'{inner}android:duration="{self.duration_ms}"',
            f'{inner}android:repeatCount="infinite"',
            f'{inner}android:interpolator="{LINEAR}">',
        ]
        lines.append(f'{inner}<propertyValuesHolder android:propertyName="{self.prop}" '
                     'android:valueType="floatType">')
        for fraction, value, spline in self.keyframes:
            frame = (f'{inner}    <keyframe android:fraction="{fmt(fraction, FRACTION_DIGITS)}" '
                     f'android:value="{fmt(value)}"')
            if spline is not None:
                frame += f' android:interpolator="{interpolator(spline)}"'
            lines.append(frame + " />")
        lines.append(f"{inner}</propertyValuesHolder>")
        lines.append(f"{pad}</objectAnimator>")
        return lines


def interpolator(spline):
    """The interpolator of a spline: linear for none, else the shared pathInterpolator resource named by its control
    points, which outputs writes for every name the drawables use."""
    if spline is None:
        return LINEAR
    return "@interpolator/" + SPLINE + "_".join(f"{round(v * SPLINE_SCALE):05d}" for v in spline)


def interpolator_xml(name):
    """The pathInterpolator resource of a name interpolator gives."""
    x1, y1, x2, y2 = (int(v) / SPLINE_SCALE for v in name[len(SPLINE):].split("_"))
    return "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by scripts/weather_icons.py; do not edit (D43): a spline of the",
        "     animated icons' SMIL timing, x1 y1 x2 y2 as its name says. -->",
        f'<pathInterpolator xmlns:android="{ANDROID}"',
        f'    android:controlX1="{fmt(x1)}"',
        f'    android:controlY1="{fmt(y1)}"',
        f'    android:controlX2="{fmt(x2)}"',
        f'    android:controlY2="{fmt(y2)}" />',
    ]) + "\n"


def rounded(spline):
    return None if spline is None else tuple(float(fmt(v)) for v in spline)


def mirrored(spline):
    """The spline played backwards, as an animator in reverse plays its interpolator."""
    return None if spline is None else (1 - spline[2], 1 - spline[3], 1 - spline[0], 1 - spline[1])


def animator(prop, track, shapes_=None):
    """The animator of track: from and to while it starts at its first value and either runs from one value to another
    or there and back the same way in two equal halves; else keyframes from its phase on, which a path's shape cannot
    take (Android's render thread plays only a path animator's first and last value). Given shapes_, the track's values
    are their indices, and the animator changes the path's data between them."""
    duration_ms = round(track.duration * 1000)
    values, splines, times = track.values, track.splines, track.times
    path = shapes_ is not None

    def shown(v):
        return shapes_[int(v)] if path else v

    if track.phase == 0 and len(values) == 2:
        return FromTo(prop, duration_ms, shown(values[0]), shown(values[1]), rounded(splines[0]), path=path)
    if (track.phase == 0 and len(values) == 3 and values[0] == values[2] and times[1] == 0.5
            and rounded(splines[1]) == rounded(mirrored(splines[0])) and duration_ms % 2 == 0):
        return FromTo(prop, duration_ms // 2, shown(values[0]), shown(values[1]), rounded(splines[0]), reverse=True,
                      path=path)
    if path:
        raise Unsupported(f"{track.where}: a changing shape is supported only from its start, from one value to "
                          "another or there and back alike")
    if track.phase not in track.times:
        track = track.split_at_time(track.phase)
    times, values, splines = track.times, track.values, track.splines
    k = times.index(track.phase)
    frames = [(0.0, values[k], None)]
    frames += [(times[j] - track.phase, values[j], splines[j - 1]) for j in range(k + 1, len(times))]
    if track.phase > 0:
        if values[-1] != values[0]:
            # The animation's jump back to its first value, where it repeats, within the keyframes.
            frames.append((1 - track.phase, values[0], None))
        frames += [(times[j] + 1 - track.phase, values[j], splines[j - 1]) for j in range(1, k + 1)]
    frames = [(float(fmt(f, FRACTION_DIGITS)), float(fmt(v)), rounded(s)) for f, v, s in frames]
    return Keyframes(prop, duration_ms, frames)


def follows(animator_, track):
    """Fails unless animator_ follows track's SMIL timing, at many points of two periods (D43)."""
    low, high = min(track.values), max(track.values)
    tolerance = FOLLOW * max(1.0, high - low)
    span = 2 * track.duration
    slip = SLIP * track.duration
    for i in range(CHECK_POINTS):
        t = (i + 0.5) * span / CHECK_POINTS
        got = animator_.at(t * 1000)
        if not any(abs(got - track.reference(t + d)) <= tolerance for d in (0.0, -slip, slip)):
            raise Unsupported(f"{track.where}: the animator of {animator_.prop} does not follow the SMIL animation "
                              f"at {t:.3f} s ({fmt(got)} instead of {fmt(track.reference(t))})")


class Animated:
    """What an animated SVG becomes: its vector's lines and the animators of each named target."""

    def __init__(self, where):
        self.where = where
        self.lines = []
        self.targets = []
        self.names = 0

    def name(self, kind):
        self.names += 1
        return f"{kind}{self.names}"

    def animate(self, target, animators):
        if animators:
            self.targets.append((target, animators))


def float_animator(prop, track):
    """The animator of a number's track, checked against its SMIL animation."""
    result = animator(prop, track)
    follows(result, track)
    return result


def own_animations(element, where):
    """An element's animations, {attribute or transform: element}, and its other children."""
    found = {}
    rest = []
    for child in element:
        tag = tag_of(child)
        if tag not in ANIMATIONS:
            rest.append(child)
            continue
        here = f"{where} <{tag}>"
        unknown = set(child.attrib) - ANIMATION_ATTRIBUTES
        if unknown:
            raise Unsupported(f"{here}: attributes {sorted(unknown)} are not supported")
        if not child.get("values", "").strip():
            raise Unsupported(f"{here}: an animation without values is not supported")
        key = "transform" if tag == "animateTransform" else child.get("attributeName")
        if tag == "animateTransform" and child.get("attributeName") != "transform":
            raise Unsupported(f"{here}: attributeName {child.get('attributeName')!r} is not transform")
        if key in found:
            raise Unsupported(f"{here}: a second animation of {key} on one element is not supported")
        found[key] = (child, here)
    return found, rest


def opacity_track(own, static, inherited, where):
    """A shape's or group's opacity: a number, or a track where it or a group above it animates it."""
    if own is None:
        if isinstance(inherited, Track):
            return inherited.mapped(lambda v: v * static)
        return inherited * static
    element, here = own
    track = track_of(element, numbers(element.get("values").replace(";", " "), f"{here}: values"), here)
    if any(not 0 <= v <= 1 for v in track.values):
        raise Unsupported(f"{here}: opacity values outside 0..1")
    if isinstance(inherited, Track):
        raise Unsupported(f"{here}: an opacity animated here and on a group above is not supported")
    return track.mapped(lambda v: v * inherited)


def animated_shapes(element, inherited, opacity, icon, depth):
    """Writes the vector's groups and paths for element and what is under it, and the animators of what moves."""
    where = icon.where
    tag = tag_of(element)
    here = f"{where} <{tag}>"
    if tag in ANIMATIONS:
        raise Unsupported(f"{here}: an animation outside a shape or group is not supported")
    if tag != "g" and tag not in SHAPES:
        raise Unsupported(f"{here}: element is not supported")
    animations, children = own_animations(element, here)
    for key, (_, animation) in animations.items():
        allowed = ANIMATABLE.get(tag, set()) | ({"transform"} if tag == "g" else set())
        if key not in allowed:
            raise Unsupported(f"{animation}: an animation of {key} on <{tag}> is not supported")
    static = number(element.attrib.get("opacity", "1"), f"{here}: opacity")
    if not 0 <= static <= 1:
        raise Unsupported(f"{here}: opacity {fmt(static)} is outside 0..1")
    alpha = opacity_track(animations.get("opacity"), static, opacity, here)
    if tag == "g":
        unknown = set(element.attrib) - GROUP_ATTRIBUTES - {"opacity"}
        if unknown:
            raise Unsupported(f"{here}: attributes {sorted(unknown)} are not supported on a group")
        paint = paint_of(element, inherited, here)
        steps = transforms(element.attrib["transform"], here) if "transform" in element.attrib else []
        opened = open_groups(steps, icon, depth)
        if "transform" in animations:
            if steps:
                raise Unsupported(f"{here}: an animated transform on a group with a transform of its own is not "
                                  "supported")
            opened += moving_group(animations["transform"], icon, depth + 4 * opened)
        one = joined(children, paint, here) if isinstance(alpha, Track) or alpha < 1 else None
        if one is not None:
            path(one, alpha, {}, None, icon, depth + 4 * opened)
        for child in children if one is None else []:
            animated_shapes(child, paint, alpha, icon, depth + 4 * opened)
        close_groups(opened, icon, depth)
        return
    if children:
        raise Unsupported(f"{here}: a shape with children other than animations is not supported")
    check_shape(element, tag, here)
    if not isinstance(alpha, Track) and alpha <= 0:
        return
    paint = paint_of(element, inherited, here)
    shape = Shape(path_data(tag, element.attrib, here), paint, 1.0, [], tag, here)
    if not shape.painted:
        return
    geometry = [key for key in animations if key != "opacity"]
    if len(geometry) > 1:
        raise Unsupported(f"{here}: animations of {' and '.join(sorted(geometry))} on one shape are not supported")
    if "stroke-dashoffset" not in animations:
        check_dashes(element.attrib, here)
    steps = transforms(element.attrib["transform"], here) if "transform" in element.attrib else []
    opened = open_groups(steps, icon, depth)
    inner = depth + 4 * opened
    kind = geometry[0] if geometry else None
    if kind == "r":
        disk_or_circle(element, shape, alpha, animations["r"], icon, inner)
    else:
        trims = {}
        morph = None
        if kind == "y2":
            trims = ray(element, shape, animations["y2"], here)
        elif kind == "stroke-dashoffset":
            trims = dashes(element, shape, animations["stroke-dashoffset"], here)
        elif kind == "d":
            morph = morphing(element, shape, animations["d"])
        path(shape, alpha, trims, morph, icon, inner)
    close_groups(opened, icon, depth)


def joined(children, inherited, where):
    """The shapes of a group as one path, where they are all stroked alike and not filled, still, of straight lines,
    and moved only by translations and rotations: one stroked path covers where its parts overlap once, as SVG's
    opacity on their group does, where a path each would show the overlaps more opaque. None otherwise."""
    paints = []
    parts = []
    for child in children:
        tag = tag_of(child)
        here = f"{where} <{tag}>"
        if tag not in SHAPES or len(child) or set(child.attrib) - INHERITED - {"transform"} - GEOMETRY[tag]:
            return None
        if tag == "rect" and ({"rx", "ry"} & set(child.attrib)):
            return None
        paint = paint_of(child, inherited, here)
        if fill_of(paint, tag) is not None or paint.get("stroke") is None:
            return None
        steps = transforms(child.attrib["transform"], here) if "transform" in child.attrib else []
        if any(name not in ("translate", "rotate") for name, _ in steps):
            return None
        data = path_data(tag, child.attrib, here)
        if set(re.findall(r"[A-Za-z]", data)) - set("MLHVZmlhvz"):
            return None
        for points_, closed in polylines(data, here):
            moved = [apply(steps, point) for point in points_]
            parts.append("M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in moved) + (" Z" if closed else ""))
        paints.append({**paint, "fill": None})
    if len(parts) < 2 or any(p != paints[0] for p in paints):
        return None
    return Shape(" ".join(parts), paints[0], 1.0, [], "path", where)


def open_groups(steps, icon, depth):
    for i, (name, args) in enumerate(steps):
        attributes = " ".join(f'android:{k}="{fmt(v)}"' for k, v in group_attributes(name, args).items())
        icon.lines.append(f"{' ' * (depth + 4 * i)}<group {attributes}>")
    return len(steps)


def close_groups(count, icon, depth):
    for i in reversed(range(count)):
        icon.lines.append(f"{' ' * (depth + 4 * i)}</group>")


def moving_group(animation, icon, depth):
    """Opens a named group that moves as the animateTransform animation says; 1, the groups it opened."""
    element, here = animation
    kind = element.get("type")
    rows = [numbers(v, f"{here}: values") for v in element.get("values", "").split(";") if v.strip()]
    sizes = {"translate": (1, 2), "scale": (1, 2), "rotate": (1, 3)}
    if kind not in sizes:
        raise Unsupported(f"{here}: type {kind!r} is not translate, scale or rotate")
    if not rows or any(len(r) not in sizes[kind] for r in rows):
        raise Unsupported(f"{here}: values {element.get('values')!r} are not {kind} values")
    if kind == "translate":
        columns = {"translateX": [r[0] for r in rows], "translateY": [r[1] if len(r) == 2 else 0.0 for r in rows]}
        fixed = {}
    elif kind == "scale":
        columns = {"scaleX": [r[0] for r in rows], "scaleY": [r[1] if len(r) == 2 else r[0] for r in rows]}
        fixed = {}
    else:
        columns = {"rotation": [r[0] for r in rows]}
        pivots = {tuple((r + [0.0, 0.0])[1:3]) for r in rows}
        if len(pivots) != 1:
            raise Unsupported(f"{here}: a rotation about a moving centre is not supported")
        fixed = dict(zip(("pivotX", "pivotY"), pivots.pop()))
    name = icon.name("g")
    tracks = {prop: track_of(element, values, here) for prop, values in columns.items()}
    attributes = [f'android:name="{name}"']
    attributes += [f'android:{k}="{fmt(v)}"' for k, v in fixed.items()]
    attributes += [f'android:{prop}="{fmt(track.start)}"' for prop, track in tracks.items()]
    icon.lines.append(f"{' ' * depth}<group {' '.join(attributes)}>")
    icon.animate(name, [float_animator(prop, t) for prop, t in tracks.items() if not t.still])
    return 1


def values_of(animation):
    element, here = animation
    return element, here, numbers(element.get("values", "").replace(";", " "), f"{here}: values")


def disk_or_circle(element, shape, alpha, animation, icon, depth):
    """A circle whose radius changes. Where it draws as one disk, filled only or filled and stroked alike, it is that
    disk in a group scaled about its centre, which takes keyframes and so a negative begin; else its outline changes
    shape, which only an animation from its start can do (animator)."""
    animate, here, radii = values_of(animation)
    if any(r < 0 for r in radii):
        raise Unsupported(f"{here}: a negative radius")
    cx, cy = number(element.get("cx"), f"{here}: cx"), number(element.get("cy"), f"{here}: cy")
    track = track_of(animate, radii, here)
    paint = shape.paint
    one = shape.stroke is None or (shape.stroke == shape.fill and paint.get("stroke-opacity", 1.0) ==
                                   paint.get("fill-opacity", 1.0))
    if shape.fill is not None and one:
        grow = paint.get("stroke-width", 1.0) / 2 if shape.stroke is not None else 0.0
        base = number(element.get("r"), f"{here}: r") + grow
        if base <= 0:
            raise Unsupported(f"{here}: a circle of radius 0")
        name = icon.name("g")
        scale = track.mapped(lambda r: (r + grow) / base)
        icon.lines.append(f'{" " * depth}<group android:name="{name}" android:pivotX="{fmt(cx)}" '
                          f'android:pivotY="{fmt(cy)}" android:scaleX="{fmt(scale.start)}" '
                          f'android:scaleY="{fmt(scale.start)}">')
        icon.animate(name, [float_animator(p, scale) for p in ("scaleX", "scaleY")] if not scale.still else [])
        disk = Shape(circle_path(cx, cy, base), {**paint, "stroke": None}, 1.0, [], "circle", here)
        path(disk, alpha, {}, None, icon, depth + 4)
        icon.lines.append(f"{' ' * depth}</group>")
        return
    morph = (circle_path(cx, cy, radii[0]), [circle_path(cx, cy, r) for r in radii], animate, here)
    path(shape, alpha, {}, morph, icon, depth)


def circle_path(cx, cy, r):
    return path_data("circle", {"cx": str(cx), "cy": str(cy), "r": str(r)}, "")


def ray(element, shape, animation, here):
    """A vertical line whose end moves along it (y2): the line to its farthest end, trimmed to the end's place."""
    animate, here, ends = values_of(animation)
    x1, y1 = number(element.get("x1"), f"{here}: x1"), number(element.get("y1"), f"{here}: y1")
    if number(element.get("x2"), f"{here}: x2") != x1:
        raise Unsupported(f"{here}: a moving y2 is supported only on a vertical line")
    far = max(ends, key=lambda y: abs(y - y1))
    if far == y1 or any((y - y1) * (far - y1) < 0 for y in ends):
        raise Unsupported(f"{here}: the line's end crosses its start")
    shape.data = f"M{fmt(x1)},{fmt(y1)} L{fmt(x1)},{fmt(far)}"
    return {"trimPathEnd": track_of(animate, ends, here).mapped(lambda y: (y - y1) / (far - y1))}


def dashes(element, shape, animation, here):
    """A stroke drawn by one dash moving along it (stroke-dashoffset): the stroke trimmed to where that dash lies."""
    animate, here, offsets = values_of(animation)
    if "pathLength" not in element.attrib or "stroke-dasharray" not in element.attrib:
        raise Unsupported(f"{here}: a moving dash needs pathLength and stroke-dasharray")
    length = number(element.get("pathLength"), f"{here}: pathLength")
    pattern = numbers(element.get("stroke-dasharray"), f"{here}: stroke-dasharray")
    if len(pattern) == 1:
        pattern = pattern * 2
    if len(pattern) != 2 or length <= 0 or pattern[0] <= 0 or pattern[0] > length:
        raise Unsupported(f"{here}: stroke-dasharray {element.get('stroke-dasharray')!r} is not one dash and its gap "
                          "on the path")
    dash, gap = pattern
    if len(re.findall(r"[Mm]", shape.data)) != 1:
        raise Unsupported(f"{here}: a moving dash on more than one subpath is not supported")
    # Only the one dash meets the path while the offset stays within these bounds.
    if min(offsets) < -gap or max(offsets) > dash + gap - length:
        raise Unsupported(f"{here}: offsets {element.get('values')!r} bring a second dash onto the path")
    track = track_of(animate, offsets, here)
    for kink in (0.0, -length, dash, dash - length):
        track = track.split_at(kink)

    def clamp(v):
        return min(1.0, max(0.0, v))

    return {
        "trimPathStart": track.mapped(lambda o: clamp(-o / length)),
        "trimPathEnd": track.mapped(lambda o: clamp((dash - o) / length)),
    }


def morphing(element, shape, animation):
    """A path whose data changes (d): every value the same commands with the same numbers of numbers."""
    animate, here = animation
    shapes_ = [" ".join(v.split()) for v in animate.get("values", "").split(";") if v.strip()]
    for data in shapes_:
        if not PATH_DATA.match(data):
            raise Unsupported(f"{here}: path data {data!r} is not plain SVG path commands")
    signature = [[(c, len(v)) for c, v in numbers_of(d, here)] for d in shapes_]
    if any(s != signature[0] for s in signature):
        raise Unsupported(f"{here}: the path data changes its commands, which a path cannot morph")
    return (shapes_[0], shapes_, animate, here)


def path(shape, alpha, trims, morph, icon, depth):
    """Writes the path of shape at its opacity alpha (a number or a track), trimmed by trims {property: track} and
    changing shape by morph, and its animators."""
    pad = " " * depth
    animators = []
    base = alpha.start if isinstance(alpha, Track) else alpha
    attributes = {"pathData": morph[0] if morph else shape.data}
    if shape.fill is not None:
        attributes["fillColor"] = shape.fill
        factor = shape.paint.get("fill-opacity", 1.0)
        if base * factor != 1.0:
            attributes["fillAlpha"] = base * factor
        if shape.paint.get("fill-rule") == "evenodd":
            attributes["fillType"] = "evenOdd"
        if isinstance(alpha, Track) and not alpha.still:
            animators.append(float_animator("fillAlpha", alpha.mapped(lambda v, f=factor: v * f)))
    if shape.stroke is not None:
        attributes["strokeColor"] = shape.stroke
        attributes["strokeWidth"] = shape.paint.get("stroke-width", 1.0)
        factor = shape.paint.get("stroke-opacity", 1.0)
        if base * factor != 1.0:
            attributes["strokeAlpha"] = base * factor
        if isinstance(alpha, Track) and not alpha.still:
            animators.append(float_animator("strokeAlpha", alpha.mapped(lambda v, f=factor: v * f)))
        cap = shape.paint.get("stroke-linecap", "butt")
        if cap != "butt":
            attributes["strokeLineCap"] = cap
        join = shape.paint.get("stroke-linejoin", "miter")
        if join != "miter":
            attributes["strokeLineJoin"] = join
        limit = shape.paint.get("stroke-miterlimit", 4.0)
        if limit != 4.0:
            attributes["strokeMiterLimit"] = limit
    for prop, track in trims.items():
        if track.start != (0.0 if prop == "trimPathStart" else 1.0):
            attributes[prop] = track.start
        if not track.still:
            animators.append(float_animator(prop, track))
    if morph:
        _, shapes_, animate, here = morph
        track = track_of(animate, [float(shapes_.index(s)) for s in shapes_], here)
        if len(set(shapes_)) > 1:
            if track.phase != 0:
                raise Unsupported(f"{here}: a changing shape is supported only from its start")
            animators.append(animator("pathData", track, shapes_))
    name = icon.name("p") if animators else None
    text = [f"{pad}<path"]
    if name:
        text.append(f'{pad}    android:name="{name}"')
    for key, value in attributes.items():
        shown = fmt(value) if isinstance(value, float) else value
        text.append(f'{pad}    android:{key}="{shown}"')
    text[-1] += " />"
    icon.lines.extend(text)
    if name:
        icon.animate(name, animators)


def convert_animated(svg_text, source_name, frame, still=None):
    """The AnimatedVectorDrawable XML of one animated SVG, showing the frame (x, y, side) of the still images; raises
    Unsupported, naming source_name and the element, for anything not converted, and, given the viewBox size of its
    still image, when its own differs, since the frame and the clip place it as its still image."""
    root, size = svg_root(svg_text, source_name)
    if still is not None and size != still:
        raise Unsupported(f"{source_name}: viewBox 0 0 {fmt(size[0])} {fmt(size[1])} differs from its still "
                          f"image's, 0 0 {fmt(still[0])} {fmt(still[1])}")
    icon = Animated(source_name)
    for child in root:
        animated_shapes(child, {}, 1.0, icon, 16)
    if not any(line.strip().startswith("<path") for line in icon.lines):
        raise Unsupported(f"{source_name}: draws nothing")
    if not icon.targets:
        raise Unsupported(f"{source_name}: moves nothing")
    side = frame[2]
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- Generated by scripts/weather_icons.py from icons/weather/{source_name}; do not edit (D38, D43). -->",
        f'<animated-vector xmlns:android="{ANDROID}"',
        f'    xmlns:aapt="{AAPT}">',
        '    <aapt:attr name="android:drawable">',
        "        <vector",
        f'            android:width="{fmt(side)}dp"',
        f'            android:height="{fmt(side)}dp"',
        f'            android:viewportWidth="{fmt(side)}"',
        f'            android:viewportHeight="{fmt(side)}">',
        *frame_lines(frame, size, 12),
    ]
    lines += icon.lines
    lines += ["            </group>", "        </vector>", "    </aapt:attr>"]
    for target, animators in icon.targets:
        lines.append(f'    <target android:name="{target}">')
        lines.append('        <aapt:attr name="android:animation">')
        if len(animators) == 1:
            lines += animators[0].xml(12)
        else:
            lines.append("            <set>")
            for one in animators:
                lines += one.xml(16)
            lines.append("            </set>")
        lines.append("        </aapt:attr>")
        lines.append("    </target>")
    lines.append("</animated-vector>")
    return "\n".join(lines) + "\n"


def layout_xml(drawable):
    """The layout that shows the animated drawable as a ProgressBar's indeterminate drawable (D6, D43)."""
    return "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by scripts/weather_icons.py; do not edit (D43). The animated",
        f"     icon {drawable}, light or dark with the system, as the",
        "     indeterminate drawable of a ProgressBar, which runs it while the widget",
        "     is seen and stops it when it is not (D6). -->",
        f'<ProgressBar xmlns:android="{ANDROID}"',
        '    android:id="@+id/weather_animated"',
        '    style="@style/WeatherWidget.AnimatedIcon"',
        f'    android:indeterminateDrawable="@drawable/{drawable}" />',
    ]) + "\n"


def drawable_name(source):
    """The name of the still image or the animated icon a source SVG makes, by its name."""
    match = SOURCE_NAME.match(source.name) or ANIMATED_NAME.match(source.name)
    if not match:
        raise Unsupported(f"{source}: not named <condition>-<day|night>-still.svg or <condition>-<day|night>.svg")
    animated = ANIMATED if match.re is ANIMATED_NAME else ""
    return f"{PREFIX}{match.group('condition').replace('-', '_')}_{match.group('variant')}{animated}.xml"


def outputs(sources=SOURCES, res=RES):
    """Every drawable the sources make, each showing the still images' common frame, the layout of each animated
    icon and the interpolators they share: {path: text}. Each still image needs its animated icon and each animated
    icon its still image."""
    stills = []
    animated = []
    for mode, folder in MODES.items():
        files = sorted((sources / mode).glob("*.svg"))
        if not files:
            raise Unsupported(f"{sources / mode}: no SVGs")
        names = {drawable_name(source) for source in files}
        for source in files:
            name = f"{mode}/{source.name}"
            text = source.read_text(encoding="utf-8")
            target = res / folder / drawable_name(source)
            if target.name.endswith(ANIMATED + ".xml"):
                if target.name.replace(ANIMATED, "") not in names:
                    raise Unsupported(f"{name}: an animated icon without its still image")
                animated.append((target, name, text))
            else:
                if target.name.replace(".xml", ANIMATED + ".xml") not in names:
                    raise Unsupported(f"{name}: a still image without its animated icon")
                stills.append((target, name, text, *parse(text, name)))
    frame = frame_of([(name, size, shapes_) for _, name, _, size, shapes_ in stills])
    written = {target: convert(text, name, frame) for target, name, text, _, _ in stills}
    sizes = {target: size for target, _, _, size, _ in stills}
    for target, name, text in animated:
        still = target.with_name(target.name.replace(ANIMATED, ""))
        written[target] = convert_animated(text, name, frame, sizes[still])
        written[res / LAYOUT / target.name] = layout_xml(target.stem)
    splines = {n for text in list(written.values()) for n in re.findall(r"@interpolator/(" + SPLINE + r"[\d_]+)", text)}
    for spline in splines:
        written[res / INTERPOLATOR / f"{spline}.xml"] = interpolator_xml(spline)
    return written


def existing(res=RES):
    """The weather drawables, the animated icons' layouts and their interpolators in the repo now."""
    folders = [*MODES.values(), LAYOUT, INTERPOLATOR]
    return {path for folder in folders for path in (res / folder).glob(PREFIX + "*.xml")}


def shown(path):
    """A path for a message: relative to the repository where it lies inside it."""
    try:
        return path.relative_to(ROOT)
    except ValueError:
        return path


def run(check, sources=SOURCES, res=RES, err=None, out=None, script="weather_icons", make=None, present=None):
    """Writes the drawables of sources into res, or with check only compares them; the exit code.

    script names the script in its messages; make and present, by default this script's outputs and existing, give
    the drawables it writes and those of its in the repo now, so the warning symbols' script runs the same way.
    """
    err = err or sys.stderr
    out = out or sys.stdout
    make = make or outputs
    present = present or existing
    try:
        written = make(sources, res)
    except Unsupported as e:
        print(f"{script}: {e}", file=err)
        return 1
    stray = present(res) - set(written)
    if check:
        differ = [p for p, text in written.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
        for path in sorted(differ):
            print(f"{script}: {shown(path)} is not what the script writes", file=err)
        for path in sorted(stray):
            print(f"{script}: {shown(path)} has no source SVG", file=err)
        return 1 if differ or stray else 0
    for path in stray:
        path.unlink()
    for path, text in written.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(f"{script}: wrote {len(written)} drawables", file=out)
    return 0


def main(argv, runner=None, usage=__doc__):
    """Runs runner, by default this script's run, as the command line says: --check, or nothing to write."""
    check = argv[1:] == ["--check"]
    if argv[1:] and not check:
        print(usage, file=sys.stderr)
        return 2
    return (runner or run)(check)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
