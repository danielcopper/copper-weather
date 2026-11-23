"""Writes the warnings' symbols as Android VectorDrawables (D48).

The sources are copies of Google's Material Symbols, the Rounded style at its
defaults (weight 400, no fill, grade 0, 24 px), unchanged in icons/symbols/,
each named as Material Symbols names it, <name>.svg; their licence, Apache 2.0,
lies beside them in LICENSE. A Material Symbol is one path on a viewBox of
0 -960 960 960, which the drawables take, each path, UV's rays apart,
unchanged, on a viewport in the same units, 960 of them to 24 dp. Their fill is
the palette's text colour, a placeholder: the widget tints each symbol in its
warning's colour as it draws it (D47). The script writes
app/src/main/res/drawable/symbol_<kind>.xml for each kind of warning, in one of
three ways (D48):

- a symbol as it is, square, 24 dp large: thunderstorm, wind, rain, snow, fog,
  thaw and the warning sign;
- a composite: a main symbol at its size, and a second one at PART_SCALE of it
  at its upper right, reaching a little beyond its corner (D48); the drawable
  keeps the main's height, grown where the second reaches above it, and is as
  wide as what both draw, with the room a Material Symbol keeps at each side
  (MARGIN) before and after it: frost a thermometer with a snowflake, heat a
  thermometer with a sun, ice a falling person with a snowflake;
- UV: the rays of the sun, its disc left out (the subpaths that enclose its
  centre or sit on it), each ray's path starting at its absolute point, and the
  letters "UV" in their middle, drawn by this script as round-capped strokes,
  bold beside the rays, rather than taken from a font, so no font is needed when
  the widget draws.

For the short form's tiles (D47) it writes each kind's symbol again as
app/src/main/res/drawable/tile_<kind>.xml: a square of one side for all kinds,
centred on the centre of everything the symbol draws, a composite's second
symbol included, so each stands centred in its tile; half its side is the
farthest any symbol draws from its centre, and MARGIN.

Every copy must be used by one of them. The script takes exactly that shape of
SVG and fails, naming the file, on anything else: another root, viewBox, size
or element, a second path, a path with another attribute than d, an empty path,
or path data with more than commands and numbers; on a copy it does not use, or
a symbol it needs and has no copy of; and on a part whose bounds it cannot
find, so it cannot place it.

Usage: python3 scripts/warning_symbols.py          writes the drawables
       python3 scripts/warning_symbols.py --check  fails when the drawables in
                                                   the repo are not what it writes
"""

import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

# The weather icons' script beside this one reads path data; Python's -I leaves this folder off the path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import weather_icons  # noqa: E402
from weather_icons import fmt  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "icons" / "symbols"
RES = ROOT / "app" / "src" / "main" / "res"
DRAWABLE = "drawable"
PREFIX = "symbol_"
TILE_PREFIX = "tile_"
SVG = "{http://www.w3.org/2000/svg}"
# What path data may hold: its commands, numbers and separators, nothing that could leave the attribute it is put in.
PATH_GRAMMAR = re.compile(r"[MmLlHhVvCcSsQqTtAaZz0-9eE+\-.,\s]*")

# A Material Symbol's em, in its own units, and the room it keeps at each side.
EM = 960
MARGIN = 80
DP_PER_UNIT = 24 / EM

# The kinds drawn as a Material Symbol as it is, by the symbol's name.
PLAIN = {
    "thunderstorm": "thunderstorm",
    "wind": "air",
    "rain": "rainy",
    "snow": "weather_snowy",
    "fog": "foggy",
    "thaw": "water_drop",
    "other": "warning",
}

# The kinds drawn as a main symbol with a second one at its upper right.
COMPOSED = {
    "frost": ("thermometer", "ac_unit"),
    "heat": ("thermometer", "sunny"),
    "ice": ("falling", "ac_unit"),
}

# The second symbol's size, and where its square starts, in the main symbol's ems.
PART_SCALE = 0.55
PART_LEFT = 0.75
PART_TOP = -0.05

# UV: the sun whose rays it keeps, and its letters' strokes, centred on the sun's centre.
UV_SUN = "sunny"
CENTRE = (480, -480)
LETTER_STROKE = 80
LETTER_HEIGHT = 200
U_WIDTH = 120
V_WIDTH = 140
LETTER_GAP = 110


# What the script refuses, the weather icons' script's own, so its runner reports it.
Unsupported = weather_icons.Unsupported


def path_of(text, name):
    """The path data of the Material Symbol text, from the file name."""
    try:
        root = ElementTree.fromstring(text)
    except ElementTree.ParseError as e:
        raise Unsupported(f"{name}: not XML ({e})") from e
    expected = {"viewBox": f"0 -{EM} {EM} {EM}", "width": "24", "height": "24"}
    if root.tag != SVG + "svg" or dict(root.attrib) != expected:
        raise Unsupported(f"{name}: the root is not a Material Symbol's svg ({dict(root.attrib)})")
    children = list(root)
    if len(children) != 1 or children[0].tag != SVG + "path" or set(children[0].attrib) != {"d"} or list(children[0]):
        raise Unsupported(f"{name}: a Material Symbol is one path with only d")
    data = children[0].attrib["d"]
    if not data.strip():
        raise Unsupported(f"{name}: its path is empty")
    if not PATH_GRAMMAR.fullmatch(data):
        raise Unsupported(f"{name}: its path data holds more than path commands and numbers")
    return data


def bounds(data, name):
    """The box (x0, y0, x1, y1) of the points the path data runs through, in its own units."""
    points = [p for line, _ in weather_icons.polylines(data, name) for p in line]
    if not points:
        raise Unsupported(f"{name}: the bounds of its path cannot be found, so it cannot be placed")
    xs = [x for x, _ in points]
    ys = [y for _, y in points]
    return min(xs), min(ys), max(xs), max(ys)


def vector(source, width, height, groups, change=None):
    """A VectorDrawable of viewport width × height in a symbol's units, its groups' XML given; what it changes."""
    changed = f", changed: {change}" if change else ""
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Generated by scripts/warning_symbols.py from {source}, Google's Material Symbols\n"
        f"     (Apache 2.0){changed}; do not edit (D48). -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{fmt(width * DP_PER_UNIT)}dp"\n'
        f'    android:height="{fmt(height * DP_PER_UNIT)}dp"\n'
        f'    android:viewportWidth="{fmt(width)}"\n'
        f'    android:viewportHeight="{fmt(height)}">\n'
        f"{groups}"
        "</vector>\n"
    )


def group(paths, translate_x=0.0, translate_y=float(EM), scale=1.0):
    """A group of paths, each (data, stroke), moved and scaled as given."""
    attributes = []
    if translate_x:
        attributes.append(f'android:translateX="{fmt(translate_x)}"')
    attributes.append(f'android:translateY="{fmt(translate_y)}"')
    if scale != 1.0:
        attributes += [f'android:scaleX="{fmt(scale)}"', f'android:scaleY="{fmt(scale)}"']
    lines = [f"    <group {' '.join(attributes)}>\n"]
    for data, stroke in paths:
        lines.append("        <path\n")
        if stroke:
            lines.append('            android:strokeColor="@color/palette_text"\n')
            lines.append(f'            android:strokeWidth="{fmt(stroke)}"\n')
            lines.append('            android:strokeLineCap="round"\n')
            lines.append('            android:strokeLineJoin="round"\n')
        else:
            lines.append('            android:fillColor="@color/palette_text"\n')
        lines.append(f'            android:pathData="{data}" />\n')
    lines.append("    </group>\n")
    return "".join(lines)


class Figure:
    """A symbol's groups, each (paths, translate x, translate y, scale) in the main symbol's em, y from 0 at its top;
    the box of what they draw there; its source and what it changes."""

    def __init__(self, groups, box, source, change=None):
        self.groups = groups
        self.box = box
        self.source = source
        self.change = change

    def drawable(self, left, top, width, height):
        """The VectorDrawable of the viewport from (left, top), width × height in the em's units."""
        xml = "".join(group(paths, x - left, y - top, scale) for paths, x, y, scale in self.groups)
        return vector(self.source, width, height, xml, self.change)


def placed_box(data, name, x, y, scale):
    """The bounds of the path data moved and scaled as a group does it."""
    x0, y0, x1, y1 = bounds(data, name)
    return x + scale * x0, y + scale * y0, x + scale * x1, y + scale * y1


def union(*boxes):
    return min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes)


def centre_of(box):
    return (box[0] + box[2]) / 2, (box[1] + box[3]) / 2


def plain(paths, name):
    """A Material Symbol as it is."""
    box = placed_box(paths[name], f"{name}.svg", 0.0, float(EM), 1.0)
    return Figure([([(paths[name], None)], 0.0, float(EM), 1.0)], box, f"icons/symbols/{name}.svg")


def composed(paths, main, part):
    """The main symbol with the part at its upper right, at PART_SCALE of its size."""
    # Where the part's own coordinates land, in the main's.
    move_x = PART_LEFT * EM
    move_y = PART_TOP * EM + PART_SCALE * EM
    main_box = placed_box(paths[main], f"{main}.svg", 0.0, float(EM), 1.0)
    part_box = placed_box(paths[part], f"{part}.svg", move_x, move_y, PART_SCALE)
    groups = [([(paths[main], None)], 0.0, float(EM), 1.0), ([(paths[part], None)], move_x, move_y, PART_SCALE)]
    change = f"{part} at {fmt(PART_SCALE)} of the size at the upper right of {main}"
    source = f"icons/symbols/{main}.svg and {part}.svg"
    return Figure(groups, union(main_box, part_box), source, change)


def symbol_drawable(figure):
    """The symbol: the em's height, grown where a part reaches above it, as wide as what it draws with MARGIN at each
    side where it is wider than the em, else the em."""
    x0, y0, x1, _ = figure.box
    if len(figure.groups) == 1:
        return figure.drawable(0.0, 0.0, EM, EM)
    left, right, top = x0 - MARGIN, x1 + MARGIN, min(0.0, y0)
    return figure.drawable(left, top, right - left, EM - top)


def tile_half(figures):
    """Half a tile's side: the farthest any symbol draws from the centre of all it draws, and MARGIN beyond."""
    return MARGIN + max(max(x1 - x0, y1 - y0) / 2 for x0, y0, x1, y1 in (figure.box for figure in figures))


def tile_drawable(figure, half):
    """The tile's symbol: a square of the common side around the centre of all it draws, so each stands centred."""
    cx, cy = centre_of(figure.box)
    return figure.drawable(cx - half, cy - half, 2 * half, 2 * half)


def subpaths(data, name):
    """The path data split into its subpaths, each (data starting with an absolute moveto, its points)."""
    lines = weather_icons.polylines(data, name)
    pieces = []
    for command, numbers in weather_icons.numbers_of(data, name):
        if command in "Mm":
            pieces.append([(command, numbers)])
        elif not pieces:
            raise Unsupported(f"{name}: its path does not start with a moveto")
        else:
            pieces[-1].append((command, numbers))
    if len(pieces) != len(lines):
        raise Unsupported(f"{name}: its subpaths cannot be told apart")
    out = []
    for piece, (points, _) in zip(pieces, lines, strict=True):
        (move, numbers), rest = piece[0], piece[1:]
        x, y = points[0]
        # Pairs after a moveto's first are linetos, relative after a relative one, as SVG reads them.
        lines_to = [("l" if move == "m" else "L", numbers[2:])] if len(numbers) > 2 else []
        text = "".join(command + " ".join(map(fmt, values)) for command, values in lines_to + rest)
        out.append((f"M{fmt(x)},{fmt(y)}{text}", points))
    return out


def letters():
    """The strokes of "UV", centred on the sun's centre: a U with a round bottom, and a V."""
    cx, cy = CENTRE
    total = U_WIDTH + LETTER_GAP + V_WIDTH
    top, bottom = cy - LETTER_HEIGHT / 2, cy + LETTER_HEIGHT / 2
    u0 = cx - total / 2
    u1 = u0 + U_WIDTH
    radius = U_WIDTH / 2
    v0 = u1 + LETTER_GAP
    v1 = v0 + V_WIDTH
    u = (
        f"M{fmt(u0)},{fmt(top)} V{fmt(bottom - radius)} "
        f"A{fmt(radius)},{fmt(radius)} 0 0,0 {fmt(u1)},{fmt(bottom - radius)} V{fmt(top)}"
    )
    v = f"M{fmt(v0)},{fmt(top)} L{fmt((v0 + v1) / 2)},{fmt(bottom)} L{fmt(v1)},{fmt(top)}"
    return u + " " + v


def uv(paths):
    """The sun's rays without its disc, and "UV" in their middle."""
    cx, cy = CENTRE
    rays = []
    for data, points in subpaths(paths[UV_SUN], f"{UV_SUN}.svg"):
        xs = [x for x, _ in points]
        ys = [y for _, y in points]
        if not (min(xs) <= cx <= max(xs) and min(ys) <= cy <= max(ys)):
            rays.append(data)
    if not rays:
        raise Unsupported(f"{UV_SUN}.svg: no rays outside its disc")
    box = placed_box(" ".join(rays), f"{UV_SUN}.svg", 0.0, float(EM), 1.0)
    return Figure(
        [([(" ".join(rays), None), (letters(), LETTER_STROKE)], 0.0, float(EM), 1.0)],
        box,
        f"icons/symbols/{UV_SUN}.svg",
        "its disc left out, and the letters UV, drawn by the script, in its middle",
    )


def outputs(sources=SOURCES, res=RES):
    """Each drawable's path and the text the script writes there, from the SVGs in sources."""
    svgs = sorted(sources.glob("*.svg"))
    if not svgs:
        raise Unsupported(f"no SVG in {sources}")
    paths = {svg.stem: path_of(svg.read_text(encoding="utf-8"), svg.name) for svg in svgs}
    needed = set(PLAIN.values()) | {name for pair in COMPOSED.values() for name in pair} | {UV_SUN}
    missing = sorted(needed - set(paths))
    if missing:
        raise Unsupported(f"no copy of {', '.join(n + '.svg' for n in missing)} in {sources}")
    unused = sorted(set(paths) - needed)
    if unused:
        raise Unsupported(f"{', '.join(n + '.svg' for n in unused)}: not used by any symbol")
    figures = {kind: plain(paths, name) for kind, name in PLAIN.items()}
    figures |= {kind: composed(paths, *pair) for kind, pair in COMPOSED.items()}
    figures["uv"] = uv(paths)
    half = tile_half(figures.values())
    written = {res / DRAWABLE / f"{PREFIX}{kind}.xml": symbol_drawable(f) for kind, f in figures.items()}
    written |= {res / DRAWABLE / f"{TILE_PREFIX}{kind}.xml": tile_drawable(f, half) for kind, f in figures.items()}
    return written


def existing(res=RES):
    """The symbols' and the tiles' drawables in the repo now."""
    return set((res / DRAWABLE).glob(PREFIX + "*.xml")) | set((res / DRAWABLE).glob(TILE_PREFIX + "*.xml"))


def run(check, sources=SOURCES, res=RES, err=None, out=None):
    """Writes the drawables of sources into res, or with check only compares them; the exit code."""
    return weather_icons.run(check, sources, res, err, out, "warning_symbols", outputs, existing)


def main(argv):
    return weather_icons.main(argv, run, __doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
