"""Writes the warnings' symbols as Android VectorDrawables (D48).

The sources are copies of Google's Material Symbols, the Rounded style at its
defaults (weight 400, no fill, grade 0, 24 px), unchanged in icons/symbols/,
each named as Material Symbols names it, <name>.svg; their licence, Apache 2.0,
lies beside them in LICENSE. A Material Symbol is one path on a viewBox of
0 -960 960 960, which the drawables take unchanged on a viewport in the same
units, 960 of them to 24 dp. Their fill is the palette's text colour, a
placeholder: the widget tints each symbol in its warning's colour as it draws
it (D47). The script writes app/src/main/res/drawable/symbol_<kind>.xml for
each kind of warning, in one of three ways (D48):

- a symbol as it is, square, 24 dp large: thunderstorm, wind, rain, snow, fog,
  thaw and the warning sign;
- a composite, as the DWD and MeteoSwiss draw frost and heat: a main symbol at
  its size, and a second one at 0.55 of it at its upper right, the second's
  square from 0.75 to 1.30 of the main's width and from -0.05 to 0.50 of its
  height, so it reaches a little beyond the main symbol's corner; the
  drawable keeps the main's height, grown where the second reaches above it,
  and is as wide as what both draw, with the room a Material Symbol keeps at
  each side (80 of 960) before and after it: frost a thermometer with a
  snowflake, heat a thermometer with a sun, ice a falling person with a
  snowflake;
- UV: the rays of the sun, its disc left out (the subpaths that enclose its
  centre or sit on it), and the letters "UV" in their middle, drawn by this
  script as round-capped strokes, bold beside the rays, rather than taken from
  a font, so no font is needed when the widget draws.

Every copy must be used by one of them. The script takes exactly that shape of
SVG and fails, naming the file, on anything else: another root, viewBox, size
or element, a second path, or a path with another attribute than d; on a copy
it does not use, or a symbol it needs and has no copy of; and on a part whose
bounds it cannot find, so it cannot place it.

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

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "icons" / "symbols"
RES = ROOT / "app" / "src" / "main" / "res"
DRAWABLE = "drawable"
PREFIX = "symbol_"
SVG = "{http://www.w3.org/2000/svg}"
NUMBER = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"

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


class Unsupported(Exception):
    """An SVG that is not a Material Symbol as the script takes it, or a symbol it cannot build."""


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
    return children[0].attrib["d"]


def bounds(data, name):
    """The box (x0, y0, x1, y1) of the points the path data runs through, in its own units."""
    try:
        lines = weather_icons.polylines(data, name)
    except weather_icons.Unsupported as e:
        raise Unsupported(str(e)) from e
    points = [p for line, _ in lines for p in line]
    if not points:
        raise Unsupported(f"{name}: the bounds of its path cannot be found, so it cannot be placed")
    xs = [x for x, _ in points]
    ys = [y for _, y in points]
    return min(xs), min(ys), max(xs), max(ys)


def fmt(value):
    return weather_icons.fmt(value)


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


def plain(paths, name):
    """A Material Symbol as it is."""
    return vector(f"icons/symbols/{name}.svg", EM, EM, group([(paths[name], None)]))


def composed(paths, main, part):
    """The main symbol with the part at its upper right, at PART_SCALE of its size."""
    main_box = bounds(paths[main], f"{main}.svg")
    part_box = bounds(paths[part], f"{part}.svg")
    # Where the part's own coordinates land, in the main's, with y from 0 at the top of the main's em.
    move_x = PART_LEFT * EM
    move_y = PART_TOP * EM + PART_SCALE * EM
    part_left = move_x + PART_SCALE * part_box[0]
    part_right = move_x + PART_SCALE * part_box[2]
    part_top = move_y + PART_SCALE * part_box[1]
    left = min(main_box[0], part_left) - MARGIN
    right = max(main_box[2], part_right) + MARGIN
    top = min(0.0, part_top)
    groups = group([(paths[main], None)], translate_x=-left, translate_y=EM - top) + group(
        [(paths[part], None)], translate_x=move_x - left, translate_y=move_y - top, scale=PART_SCALE
    )
    change = f"{part} at {fmt(PART_SCALE)} of the size at the upper right of {main}"
    return vector(f"icons/symbols/{main}.svg and {part}.svg", right - left, EM - top, groups, change)


def subpaths(data, name):
    """The path data split into its subpaths, each (data starting with an absolute moveto, its points)."""
    try:
        lines = weather_icons.polylines(data, name)
    except weather_icons.Unsupported as e:
        raise Unsupported(str(e)) from e
    starts = [i for i, c in enumerate(data) if c in "Mm"]
    pieces = [data[a:b] for a, b in zip(starts, starts[1:] + [len(data)], strict=True)]
    if len(pieces) != len(lines):
        raise Unsupported(f"{name}: its subpaths cannot be told apart")
    out = []
    for piece, (points, _) in zip(pieces, lines, strict=True):
        end = next((i for i, c in enumerate(piece) if i > 0 and c.isalpha()), len(piece))
        numbers = re.findall(NUMBER, piece[1:end])
        # Pairs after a moveto's first are linetos, relative after a relative one, as SVG reads them.
        lines_to = f"{'l' if piece[0] == 'm' else 'L'}{' '.join(numbers[2:])}" if len(numbers) > 2 else ""
        x, y = points[0]
        out.append((f"M{fmt(x)},{fmt(y)}{lines_to}{piece[end:]}", points))
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
    return vector(
        f"icons/symbols/{UV_SUN}.svg",
        EM,
        EM,
        group([(" ".join(rays), None), (letters(), LETTER_STROKE)]),
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
    drawables = {kind: plain(paths, name) for kind, name in PLAIN.items()}
    drawables |= {kind: composed(paths, *pair) for kind, pair in COMPOSED.items()}
    drawables["uv"] = uv(paths)
    return {res / DRAWABLE / f"{PREFIX}{kind}.xml": text for kind, text in drawables.items()}


def existing(res=RES):
    """The symbols' drawables in the repo now."""
    return set((res / DRAWABLE).glob(PREFIX + "*.xml"))


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
        print(f"warning_symbols: {e}", file=err)
        return 1
    stray = existing(res) - set(written)
    if check:
        differ = [p for p, text in written.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
        for path in sorted(differ):
            print(f"warning_symbols: {shown(path)} is not what the script writes", file=err)
        for path in sorted(stray):
            print(f"warning_symbols: {shown(path)} has no source SVG", file=err)
        return 1 if differ or stray else 0
    for path in stray:
        path.unlink()
    for path, text in written.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    print(f"warning_symbols: wrote {len(written)} drawables", file=out)
    return 0


def main(argv):
    check = argv[1:] == ["--check"]
    if argv[1:] and not check:
        print(__doc__, file=sys.stderr)
        return 2
    return run(check)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
