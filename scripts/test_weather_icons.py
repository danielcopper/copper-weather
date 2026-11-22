"""What weather_icons.py makes of an SVG, and what it refuses (D38)."""

import io
import re
import tempfile
import unittest
from pathlib import Path

import weather_icons
from weather_icons import Unsupported, convert


def svg(body, root='viewBox="0 0 64 64"'):
    return f'<svg xmlns="http://www.w3.org/2000/svg" {root}>{body}</svg>'


# An animated SVG's one animation, for a picture that needs one beside its still image.
PULSE = '<animate attributeName="opacity" dur="2s" repeatCount="indefinite" values="1;.5;1"/>'


def write_pair(folder, name, body):
    """The still SVG of name with body, and beside it the animated one, body with the first shape pulsing."""
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{name}-still.svg").write_text(svg(body))
    moving = body.replace("/>", f">{PULSE}</path>", 1) if body.startswith("<path") else body
    (folder / f"{name}.svg").write_text(svg(moving))


def paths(xml):
    """The <path> and <group> lines of a converted drawable, stripped, in order."""
    return [line.strip() for line in xml.splitlines() if line.strip().startswith(("<path", "android:", "<group", "</group"))]


class ConvertTest(unittest.TestCase):
    def test_a_circle_is_two_arcs_and_its_fill(self):
        xml = convert(svg('<circle cx="22" cy="21" r="7" fill="#f8af18"/>'), "t.svg")
        self.assertIn('android:pathData="M15,21 A7,7 0 1,0 29,21 A7,7 0 1,0 15,21 Z"', xml)
        self.assertIn('android:fillColor="#F8AF18"', xml)
        self.assertNotIn("stroke", xml)

    def test_a_rect_and_a_polygon_are_closed_paths(self):
        xml = convert(svg('<rect x="19" y="37" width="23" height="9" fill="#fff"/>'), "t.svg")
        self.assertIn('android:pathData="M19,37 H42 V46 H19 Z"', xml)
        self.assertIn('android:fillColor="#FFFFFF"', xml)
        xml = convert(svg('<polygon points="1,2 3,4 5,6" fill="#000"/>'), "t.svg")
        self.assertIn('android:pathData="M1,2 L3,4 L5,6 Z"', xml)

    def test_a_line_is_stroked_and_has_no_fill(self):
        xml = convert(svg('<line x1="22" y1="10.5" x2="22" y2="6.5" stroke="#f8af18" stroke-width="1.82" '
                          'stroke-linecap="round"/>'), "t.svg")
        self.assertIn('android:pathData="M22,10.5 L22,6.5"', xml)
        self.assertIn('android:strokeWidth="1.82"', xml)
        self.assertIn('android:strokeLineCap="round"', xml)
        self.assertNotIn("fillColor", xml)

    def test_a_shape_without_fill_is_filled_black_as_in_svg(self):
        self.assertIn('android:fillColor="#000000"', convert(svg('<path d="M0,0 L1,1 Z"/>'), "t.svg"))

    def test_paint_is_inherited_from_groups_and_none_overrides_it(self):
        xml = convert(svg('<g stroke="#123456" stroke-width="3" stroke-linejoin="round">'
                          '<path d="M0,0 L1,1" fill="none"/><path d="M2,2 L3,3" stroke-width="2"/></g>'), "t.svg")
        first, second = xml.split("<path")[1:]
        self.assertNotIn("fillColor", first)
        self.assertIn('android:strokeColor="#123456"', first)
        self.assertIn('android:strokeWidth="3"', first)
        self.assertIn('android:strokeLineJoin="round"', first)
        self.assertIn('android:strokeWidth="2"', second)
        self.assertIn('android:fillColor="#000000"', second)

    def test_each_transform_step_is_a_group_in_order(self):
        xml = convert(svg('<g transform="translate(6 3) scale(0.9 0.9)"><path d="M0,0 L1,1" fill="#000"/></g>'
                          '<line x1="0" y1="0" x2="1" y2="1" stroke="#000" transform="rotate(45 22 21)"/>'), "t.svg")
        self.assertEqual(
            [line for line in paths(xml) if "group" in line],
            [
                '<group android:translateX="6" android:translateY="3">',
                '<group android:scaleX="0.9" android:scaleY="0.9">',
                "</group>",
                "</group>",
                '<group android:rotation="45" android:pivotX="22" android:pivotY="21">',
                "</group>",
            ],
        )

    def test_the_opacity_of_a_shape_painted_once_goes_into_its_alpha(self):
        xml = convert(svg('<circle cx="1" cy="1" r="1" fill="#000" opacity=".18"/>'
                          '<line x1="0" y1="0" x2="1" y2="1" stroke="#000" opacity=".5"/>'), "t.svg")
        self.assertIn('android:fillAlpha="0.18"', xml)
        self.assertIn('android:strokeAlpha="0.5"', xml)
        self.assertTrue(xml.splitlines()[2].startswith("<vector"))

    def test_a_shape_with_fill_and_stroke_and_an_opacity_gets_its_own_layer(self):
        xml = convert(svg('<path d="M0,0 L1,1 Z" fill="#111"/>'
                          '<path d="M0,0 L1,1 Z" fill="#222" stroke="#222" stroke-width="3" opacity=".35"/>'
                          '<path d="M0,0 L1,1 Z" fill="#333"/>'), "t.svg")
        self.assertTrue(xml.splitlines()[2].startswith("<layer-list"))
        self.assertEqual(3, xml.count("<vector"))
        layers = xml.split("<vector")[1:]
        self.assertIn("#111111", layers[0])
        self.assertIn('android:alpha="0.35"', layers[1])
        self.assertNotIn("Alpha", layers[1])
        self.assertIn("#333333", layers[2])

    def test_an_invisible_shape_and_a_dash_that_covers_the_whole_shape_are_left_out(self):
        xml = convert(svg('<line x1="0" y1="0" x2="1" y2="1" stroke="#000" stroke-dasharray="18 100" '
                          'pathLength="100" opacity="0"/>'
                          '<path d="M6,33 H46" pathLength="100" fill="none" stroke="#000" '
                          'stroke-dasharray="100 100"/>'), "t.svg")
        self.assertEqual(1, xml.count("<path"))
        self.assertIn('android:pathData="M6,33 H46"', xml)
        self.assertNotIn("dash", xml.lower())

    def test_the_drawable_names_its_source(self):
        xml = convert(svg('<path d="M0,0 L1,1 Z"/>'), "light/fog-day-still.svg")
        self.assertIn("from icons/weather/light/fog-day-still.svg; do not edit", xml)


class RefuseTest(unittest.TestCase):
    def refused(self, body, says, root='viewBox="0 0 64 64"'):
        with self.assertRaises(Unsupported) as caught:
            convert(svg(body, root), "light/x-day-still.svg")
        self.assertIn("light/x-day-still.svg", str(caught.exception))
        self.assertIn(says, str(caught.exception))

    def test_elements_it_does_not_know(self):
        self.refused('<ellipse cx="1" cy="1" rx="1" ry="2"/>', "<ellipse>: element is not supported")
        self.refused('<text>x</text>', "<text>: element is not supported")
        self.refused('<g><use href="#a"/></g>', "<use>: element is not supported")

    def test_an_animation_inside_a_shape(self):
        self.refused('<circle cx="1" cy="1" r="1"><animate attributeName="r"/></circle>', "with children")

    def test_attributes_it_does_not_know(self):
        self.refused('<path d="M0,0 L1,1" filter="url(#f)"/>', "['filter'] are not supported")
        self.refused('<g opacity=".5"><path d="M0,0 L1,1"/></g>', "['opacity'] are not supported on a group")
        self.refused('<rect x="0" y="0" width="1" height="1" rx="2"/>', "rounded corners")
        self.refused('<path d="M0,0 L1,1"/>', "<svg>: attributes ['width']", root='viewBox="0 0 64 64" width="64"')

    def test_colours_that_are_not_hex(self):
        self.refused('<path d="M0,0 L1,1" fill="red"/>', "colour 'red'")
        self.refused('<path d="M0,0 L1,1" fill="url(#g)"/>', "colour 'url(#g)'")
        self.refused('<path d="M0,0 L1,1" stroke="currentColor"/>', "colour 'currentColor'")

    def test_transforms_it_does_not_know(self):
        self.refused('<path d="M0,0 L1,1" transform="matrix(1 0 0 1 0 0)"/>', "transform matrix")
        self.refused('<path d="M0,0 L1,1" transform="skewX(10)"/>', "transform skewX")
        self.refused('<path d="M0,0 L1,1" transform="rotate(1 2)"/>', "rotate takes 1 or 3 numbers")

    def test_a_dash_pattern_with_gaps(self):
        self.refused('<path d="M0,0 L1,1" stroke="#000" stroke-dasharray="4 2"/>', "leaves gaps")
        self.refused('<path d="M0,0 L1,1" stroke="#000" pathLength="100" stroke-dasharray="50 100"/>',
                     "leaves gaps")

    def test_a_view_box_that_does_not_start_at_0(self):
        self.refused('<path d="M0,0 L1,1"/>', "viewBox must be 0 0 width height", root='viewBox="1 0 64 64"')

    def test_path_data_that_is_not_plain_commands(self):
        self.refused('<path d="M0,0 L1,1; x"/>', "is not plain SVG path commands")


class FrameTest(unittest.TestCase):
    """The frame every drawable shows: the smallest square around what all the SVGs draw (D38)."""

    @staticmethod
    def bounds(body):
        size, found = weather_icons.parse(svg(body), "t.svg")
        boxes = [weather_icons.bounds_of(shape, size) for shape in found]
        return [tuple(round(v, 3) for v in box) for box in boxes if box is not None]

    def test_every_drawable_shows_the_square_around_what_all_the_svgs_draw(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            bodies = {
                "light": '<rect x="10" y="10" width="10" height="20" fill="#000"/>',
                "dark": '<circle cx="40" cy="20" r="5" fill="#000"/>',
            }
            for mode, body in bodies.items():
                (root / "svg" / mode).mkdir(parents=True)
                (root / "svg" / mode / "fog-day-still.svg").write_text(svg(body))
                tag = body[1:body.index(" ")]
                (root / "svg" / mode / "fog-day.svg").write_text(svg(body.replace("/>", f">{PULSE}</{tag}>")))
            written = weather_icons.outputs(root / "svg", root / "res")
        # x from 10 to 45 and y from 10 to 30: a square of 35, centred on y 20; the animated icons show it too.
        drawables = [xml for path, xml in written.items() if path.parent.name != "layout"]
        self.assertEqual(4, len(drawables))
        for xml in drawables:
            self.assertIn('android:viewportWidth="35"', xml)
            self.assertIn('android:viewportHeight="35"', xml)
            self.assertIn('android:width="35dp"', xml)
            self.assertIn('<group android:translateX="-10" android:translateY="-2.5">', xml)
            self.assertIn('<clip-path android:pathData="M0,0 H64 V64 H0 Z" />', xml)

    def test_a_stroke_adds_half_its_width_scaled_with_the_shape(self):
        self.assertEqual(
            [(18, 18, 42, 22)],
            self.bounds('<g transform="scale(2)"><line x1="10" y1="10" x2="20" y2="10" stroke="#000" '
                        'stroke-width="2" stroke-linecap="round"/></g>'),
        )

    def test_a_rotation_and_an_arc_are_followed(self):
        # A quarter turn about (32, 32) moves the circle at (42, 32) to (32, 42).
        self.assertEqual(
            [(27, 37, 37, 47)],
            self.bounds('<circle cx="42" cy="32" r="5" fill="#000" transform="rotate(90 32 32)"/>'),
        )

    def test_a_miter_join_reaches_past_half_the_width(self):
        # The peak's miter points up by 1 / cos(half the angle) = sqrt(5), past the stroke's half width of 1.
        self.assertEqual(
            [(9, 7.764, 31, 31)],
            self.bounds('<path d="M10,30 L20,10 L30,30" fill="none" stroke="#000" stroke-width="2"/>'),
        )

    def test_a_miter_beyond_its_limit_is_cut_flat(self):
        # The peak's miter would reach about 10 past it, more than 4 times the stroke's half width: it is bevelled.
        self.assertEqual(
            [(9, 9, 15, 31)],
            self.bounds('<path d="M10,30 L12,10 L14,30" fill="none" stroke="#000" stroke-width="2"/>'),
        )

    def test_a_closed_path_back_at_its_start_before_z_has_its_start_as_a_corner(self):
        box = self.bounds('<path d="M20,10 L30,30 L10,30 L20,10 Z" fill="none" stroke="#000" stroke-width="2"/>')
        self.assertEqual(7.764, box[0][1])

    def test_what_lies_outside_the_view_box_is_cut_off(self):
        self.assertEqual([(50, 22, 64, 42)], self.bounds('<circle cx="60" cy="32" r="10" fill="#000"/>'))

    def refused(self, body, says):
        with self.assertRaises(Unsupported) as caught:
            self.bounds(body)
        self.assertIn(says, str(caught.exception))

    def test_path_data_whose_bounds_cannot_be_found_is_refused_naming_the_file_and_the_element(self):
        self.refused('<path d="M0,0 L1"/>', "t.svg <path>: path command L has 1 numbers")
        self.refused('<path d="M0,0 L1e400,1"/>', "t.svg <path>: the bounds of path data")
        self.refused('<path d="Z"/>', "t.svg <path>: the bounds of path data 'Z' cannot be found")

    def test_a_square_cap_on_an_open_line_is_refused(self):
        self.refused('<line x1="0" y1="0" x2="1" y2="1" stroke="#000" stroke-linecap="square"/>',
                     "t.svg <line>: stroke-linecap square is not supported")


class OutputsTest(unittest.TestCase):
    def test_light_goes_to_drawable_and_dark_to_drawable_night_under_one_name(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for mode in ("light", "dark"):
                write_pair(root / "svg" / mode, "lightning-rainy-night", '<path d="M0,0 L1,1 Z"/>')
            written = weather_icons.outputs(root / "svg", root / "res")
            self.assertEqual(
                {root / "res" / "drawable" / "weather_lightning_rainy_night.xml",
                 root / "res" / "drawable-night" / "weather_lightning_rainy_night.xml",
                 root / "res" / "drawable" / "weather_lightning_rainy_night_animated.xml",
                 root / "res" / "drawable-night" / "weather_lightning_rainy_night_animated.xml",
                 root / "res" / "layout" / "weather_lightning_rainy_night_animated.xml"},
                set(written),
            )

    def test_the_layout_shows_the_animated_icon_as_a_progress_bars_indeterminate_drawable(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for mode in ("light", "dark"):
                write_pair(root / "svg" / mode, "fog-day", '<path d="M0,0 L1,1 Z"/>')
            layout = weather_icons.outputs(root / "svg", root / "res")[root / "res" / "layout" /
                                                                        "weather_fog_day_animated.xml"]
        self.assertIn("<ProgressBar", layout)
        self.assertIn('android:id="@+id/weather_animated"', layout)
        self.assertIn('style="@style/WeatherWidget.AnimatedIcon"', layout)
        self.assertIn('android:indeterminateDrawable="@drawable/weather_fog_day_animated"', layout)

    def test_the_interpolators_the_animated_icons_name_are_written_once_each(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            pulse = ('<animate attributeName="opacity" dur="2s" repeatCount="indefinite" calcMode="spline" '
                     'keySplines=".42 0 .58 1; .42 0 .58 1" values="1;.5;1"/>')
            for mode in ("light", "dark"):
                (root / "svg" / mode).mkdir(parents=True)
                for name in ("fog-day", "rainy-day"):
                    (root / "svg" / mode / f"{name}-still.svg").write_text(svg('<path d="M0,0 L1,1 Z"/>'))
                    (root / "svg" / mode / f"{name}.svg").write_text(svg(f'<path d="M0,0 L1,1 Z">{pulse}</path>'))
            written = weather_icons.outputs(root / "svg", root / "res")
        interpolators = {path.name: text for path, text in written.items() if path.parent.name == "interpolator"}
        self.assertEqual(["weather_spline_04200_00000_05800_10000.xml"], list(interpolators))
        text = interpolators["weather_spline_04200_00000_05800_10000.xml"]
        for part in ("<pathInterpolator", 'android:controlX1="0.42"', 'android:controlY1="0"',
                     'android:controlX2="0.58"', 'android:controlY2="1" />'):
            self.assertIn(part, text)

    def test_an_animated_icon_whose_view_box_differs_from_its_still_images_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for mode in ("light", "dark"):
                write_pair(root / "svg" / mode, "fog-day", '<path d="M0,0 L1,1 Z"/>')
            moving = root / "svg" / "dark" / "fog-day.svg"
            moving.write_text(moving.read_text().replace('viewBox="0 0 64 64"', 'viewBox="0 0 128 128"'))
            with self.assertRaises(Unsupported) as caught:
                weather_icons.outputs(root / "svg", root / "res")
            self.assertIn("dark/fog-day.svg: viewBox 0 0 128 128 differs from its still image's, 0 0 64 64",
                          str(caught.exception))

    def test_a_source_named_otherwise_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for mode in ("light", "dark"):
                write_pair(root / "svg" / mode, "fog-day", '<path d="M0,0 L1,1 Z"/>')
                (root / "svg" / mode / "sunny-dusk.svg").write_text(svg('<path d="M0,0 L1,1 Z"/>'))
            with self.assertRaises(Unsupported) as caught:
                weather_icons.outputs(root / "svg", root / "res")
            self.assertIn("sunny-dusk.svg: not named", str(caught.exception))

    def test_a_still_image_without_its_animated_icon_and_the_other_way_round_are_refused(self):
        for alone, says in (("fog-day-still.svg", "a still image without its animated icon"),
                            ("fog-day.svg", "an animated icon without its still image")):
            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                for mode in ("light", "dark"):
                    write_pair(root / "svg" / mode, "rainy-day", '<path d="M0,0 L1,1 Z"/>')
                    body = '<path d="M0,0 L1,1 Z">' + PULSE + "</path>"
                    (root / "svg" / mode / alone).write_text(svg(body))
                with self.assertRaises(Unsupported) as caught:
                    weather_icons.outputs(root / "svg", root / "res")
                self.assertIn(f"{alone}: {says}", str(caught.exception))


class CheckTest(unittest.TestCase):
    """--check, on a folder of SVGs and one of resources made for the test."""

    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        root = Path(folder.name)
        self.sources = root / "svg"
        self.res = root / "res"
        for mode in ("light", "dark"):
            write_pair(self.sources / mode, "fog-day", '<path d="M0,0 L1,1 Z"/>')
        self.assertEqual(0, weather_icons.run(False, self.sources, self.res, out=io.StringIO()))

    def check(self):
        err = io.StringIO()
        return weather_icons.run(True, self.sources, self.res, err), err.getvalue()

    def test_drawables_as_written_pass(self):
        self.assertEqual((0, ""), self.check())

    def test_a_drawable_changed_by_hand_fails_and_is_named(self):
        changed = self.res / "drawable-night" / "weather_fog_day.xml"
        changed.write_text(changed.read_text().replace("#000000", "#111111"))
        code, said = self.check()
        self.assertEqual(1, code)
        self.assertIn(f"{changed} is not what the script writes", said)

    def test_a_drawable_without_a_source_fails_and_is_named(self):
        stray = self.res / "drawable" / "weather_sandstorm_day.xml"
        stray.write_text("<vector />")
        code, said = self.check()
        self.assertEqual(1, code)
        self.assertIn(f"{stray} has no source SVG", said)

    def test_a_missing_drawable_fails(self):
        (self.res / "drawable" / "weather_fog_day.xml").unlink()
        self.assertEqual(1, self.check()[0])

    def test_an_animated_icon_or_its_layout_changed_by_hand_or_without_a_source_fails(self):
        changed = self.res / "drawable" / "weather_fog_day_animated.xml"
        changed.write_text(changed.read_text().replace('android:duration="1000"', 'android:duration="900"'))
        stray = self.res / "layout" / "weather_sandstorm_day_animated.xml"
        stray.write_text("<ProgressBar />")
        interpolator = self.res / "interpolator" / "weather_spline_00000_00000_10000_10000.xml"
        interpolator.parent.mkdir(parents=True, exist_ok=True)
        interpolator.write_text("<pathInterpolator />")
        code, said = self.check()
        self.assertEqual(1, code)
        self.assertIn(f"{changed} is not what the script writes", said)
        self.assertIn(f"{stray} has no source SVG", said)
        self.assertIn(f"{interpolator} has no source SVG", said)


EASE = 'calcMode="spline" keySplines=".42 0 .58 1; .42 0 .58 1"'


def animated(body):
    """The animated drawable of an SVG with body, showing its whole viewBox."""
    return weather_icons.convert_animated(svg(body), "light/x-day.svg", (0, 0, 64))


def target(xml, name):
    """The animation of the target name in an animated drawable."""
    start = xml.index(f'<target android:name="{name}">')
    return xml[start:xml.index("</target>", start)]


def keyframes(xml):
    """The (fraction, value) of each keyframe, in order."""
    return [(float(f), float(v)) for f, v in re.findall(r'<keyframe android:fraction="([^"]+)" android:value="([^"]+)"',
                                                        xml)]


class AnimatedTest(unittest.TestCase):
    """What weather_icons.py makes of an animated SVG (D43)."""

    def test_an_opacity_there_and_back_is_one_animator_played_forwards_and_in_reverse(self):
        xml = animated(f'<circle cx="22" cy="21" r="7" fill="#f0a000" opacity=".18"><animate attributeName="opacity" '
                       f'dur="4s" repeatCount="indefinite" {EASE} values=".12;.28;.12"/></circle>')
        self.assertIn('android:fillAlpha="0.12" />', xml)
        animation = target(xml, "p1")
        for part in ('android:propertyName="fillAlpha"', 'android:valueFrom="0.12"', 'android:valueTo="0.28"',
                     'android:duration="2000"', 'android:repeatCount="infinite"', 'android:repeatMode="reverse"',
                     'android:interpolator="@interpolator/weather_spline_04200_00000_05800_10000" />'):
            self.assertIn(part, animation)

    def test_a_negative_begin_starts_keyframes_at_its_phase_with_the_spline_cut_there(self):
        xml = animated(f'<line x1="22" y1="10.5" x2="22" y2="6.5" stroke="#f0a000" stroke-width="2"><animate '
                       f'attributeName="y2" dur="4s" begin="-1s" repeatCount="indefinite" {EASE} '
                       f'values="6.5;5;6.5"/></line>')
        # The line to its farthest end, 5, trimmed to y2: 6.5 is (10.5 - 6.5) / 5.5 of it; a quarter on, half way up.
        self.assertIn('android:pathData="M22,10.5 L22,5"', xml)
        self.assertIn('android:trimPathEnd="0.8636" />', xml)
        self.assertEqual([(0, 0.8636), (0.25, 1), (0.75, 0.7273), (1, 0.8636)], keyframes(target(xml, "p1")))
        # The ease cut in half: its second half before the peak, its first half at the end.
        self.assertIn('android:interpolator="@interpolator/weather_spline_02900_05000_05800_10000"', xml)
        self.assertIn('android:interpolator="@interpolator/weather_spline_04200_00000_07100_05000"', xml)

    def test_a_disk_whose_radius_changes_is_scaled_about_its_centre(self):
        xml = animated(f'<circle cx="22" cy="21" r="9.5" fill="#f0a000"><animate attributeName="r" dur="4s" '
                       f'repeatCount="indefinite" {EASE} values="8.5;10.5;8.5"/></circle>')
        self.assertIn('<group android:name="g1" android:pivotX="22" android:pivotY="21" android:scaleX="0.8947" '
                      'android:scaleY="0.8947">', xml)
        self.assertIn('android:pathData="M12.5,21 A9.5,9.5 0 1,0 31.5,21 A9.5,9.5 0 1,0 12.5,21 Z"', xml)
        for axis in ("scaleX", "scaleY"):
            self.assertIn(f'android:propertyName="{axis}"', target(xml, "g1"))
        self.assertIn('android:valueTo="1.1053"', target(xml, "g1"))

    def test_a_circle_filled_and_stroked_alike_is_one_disk_half_its_stroke_larger(self):
        xml = animated(f'<circle cx="19" cy="39" r="7" fill="#8d99a8" stroke="#8d99a8" stroke-width="3"><animate '
                       f'attributeName="r" dur="6s" begin="-1s" repeatCount="indefinite" {EASE} '
                       f'values="7;8.1;7"/></circle>')
        self.assertIn('android:pathData="M10.5,39 A8.5,8.5 0 1,0 27.5,39 A8.5,8.5 0 1,0 10.5,39 Z"', xml)
        self.assertNotIn("strokeColor", xml)
        # From 8.5, the base, to 9.6, the largest radius and half the stroke.
        scales = [v for _, v in keyframes(target(xml, "g1"))]
        self.assertEqual((1, 1.1294), (min(scales), max(scales)))

    def test_a_circle_stroked_in_another_colour_changes_its_outline(self):
        xml = animated(f'<circle cx="19" cy="39" r="7" fill="#fff" stroke="#000" stroke-width="3"><animate '
                       f'attributeName="r" dur="6s" repeatCount="indefinite" {EASE} values="7;8;7"/></circle>')
        animation = target(xml, "p1")
        self.assertIn('android:valueType="pathType"', animation)
        self.assertIn('android:valueFrom="M12,39 A7,7 0 1,0 26,39 A7,7 0 1,0 12,39 Z"', animation)
        self.assertIn('android:valueTo="M11,39 A8,8 0 1,0 27,39 A8,8 0 1,0 11,39 Z"', animation)
        self.assertIn('android:repeatMode="reverse"', animation)

    def test_a_paths_changing_data_morphs_between_its_values(self):
        wave, other = "M12,40 C20,37 24,43 32,40", "M12,40 C20,43 24,37 32,40"
        xml = animated(f'<path d="{wave}" fill="none" stroke="#000"><animate attributeName="d" dur="4s" '
                       f'repeatCount="indefinite" {EASE} values="{wave};{other};{wave}"/></path>')
        animation = target(xml, "p1")
        self.assertIn('android:valueFrom="M12,40 C20,37 24,43 32,40"', animation)
        self.assertIn('android:valueTo="M12,40 C20,43 24,37 32,40"', animation)

    def test_a_moving_dash_trims_the_stroke_to_where_the_dash_lies(self):
        xml = animated('<path d="M8,24 H36" pathLength="100" fill="none" stroke="#000" stroke-dasharray="100 100">'
                       '<animate attributeName="stroke-dashoffset" dur="4s" repeatCount="indefinite" '
                       'keyTimes="0;.45;.6;1" values="100;0;0;-100"/></path>')
        animation = target(xml, "p1")
        start, end = animation.split('android:propertyName="trimPathEnd"')
        self.assertEqual([(0, 0), (0.45, 0), (0.6, 0), (1, 1)], keyframes(start))
        self.assertEqual([(0, 0), (0.45, 1), (0.6, 1), (1, 1)], keyframes(end))
        self.assertIn('android:trimPathEnd="0"', xml)

    def test_a_dash_shorter_than_the_path_gets_a_key_time_where_its_start_leaves_the_path_start(self):
        xml = animated('<line x1="54" y1="7.75" x2="27" y2="20.5" pathLength="100" stroke="#000" '
                       'stroke-dasharray="18 100"><animate attributeName="stroke-dashoffset" dur="17s" '
                       'repeatCount="indefinite" keyTimes="0;.82;.84;.9;1" values="18;18;10;-82;-82"/></line>')
        start = target(xml, "p1").split('android:propertyName="trimPathEnd"')[0]
        # The offset, falling from 10 to -82 between .84 and .9, passes 0 10/92 of the way, at .846522.
        self.assertEqual([(0, 0), (0.82, 0), (0.84, 0), (0.846522, 0), (0.9, 0.82), (1, 0.82)], keyframes(start))

    def test_a_translation_with_key_times_and_a_negative_begin_jumps_back_where_it_repeats(self):
        xml = animated('<g><path d="M0,0 L1,1 Z"/><animateTransform attributeName="transform" type="translate" '
                       'dur="4s" begin="-1s" repeatCount="indefinite" keyTimes="0;.5;1" values="0 0;10 2;20 4"/></g>')
        self.assertIn('<group android:name="g1" android:translateX="5" android:translateY="1">', xml)
        x, y = target(xml, "g1").split('android:propertyName="translateY"')
        self.assertEqual([(0, 5), (0.25, 10), (0.75, 20), (0.75, 0), (1, 5)], keyframes(x))
        self.assertEqual([(0, 1), (0.25, 2), (0.75, 4), (0.75, 0), (1, 1)], keyframes(y))

    def test_a_rotation_about_a_fixed_centre_turns_a_group_about_its_pivot(self):
        xml = animated('<g><path d="M0,0 L1,1 Z"/><animateTransform attributeName="transform" type="rotate" '
                       'dur="6s" repeatCount="indefinite" values="0 3 4;360 3 4"/></g>')
        self.assertIn('<group android:name="g1" android:pivotX="3" android:pivotY="4" android:rotation="0">', xml)
        animation = target(xml, "g1")
        for part in ('android:propertyName="rotation"', 'android:valueFrom="0"', 'android:valueTo="360"',
                     'android:duration="6000"', 'android:interpolator="@android:interpolator/linear" />'):
            self.assertIn(part, animation)
        self.assertNotIn("repeatMode", animation)

    def test_a_scale_animates_both_axes(self):
        xml = animated(f'<g><path d="M0,0 L1,1 Z"/><animateTransform attributeName="transform" type="scale" '
                       f'dur="2s" repeatCount="indefinite" {EASE} values=".35;1;.35"/></g>')
        animation = target(xml, "g1")
        self.assertIn('android:propertyName="scaleX"', animation)
        self.assertIn('android:propertyName="scaleY"', animation)

    def test_a_groups_opacity_goes_into_each_shape_under_it(self):
        xml = animated('<g opacity="0"><path d="M0,0 L1,1 Z" fill="#111"/><line x1="0" y1="0" x2="1" y2="1" '
                       'stroke="#222" opacity=".5"/><animate attributeName="opacity" dur="2s" '
                       'repeatCount="indefinite" keyTimes="0;.5;1" values="0;1;0"/></g>')
        self.assertIn('android:valueTo="1"', target(xml, "p1"))
        self.assertIn('android:propertyName="fillAlpha"', target(xml, "p1"))
        self.assertIn('android:valueTo="0.5"', target(xml, "p2"))
        self.assertIn('android:propertyName="strokeAlpha"', target(xml, "p2"))

    def test_strokes_alike_under_a_groups_opacity_are_one_path_so_their_overlaps_are_not_more_opaque(self):
        xml = animated('<g><g stroke="#000" stroke-width="1"><line x1="0" y1="-3" x2="0" y2="3"/>'
                       '<line x1="0" y1="-3" x2="0" y2="3" transform="rotate(90 0 0)"/>'
                       '<path d="M1,1 L2,2" fill="none"/></g>'
                       '<animate attributeName="opacity" dur="2s" repeatCount="indefinite" values="1;0;1"/></g>')
        self.assertEqual(1, xml.count("<path"))
        self.assertIn('android:pathData="M0,-3 L0,3 M3,0 L-3,0 M1,1 L2,2"', xml)

    def test_the_drawable_names_its_source(self):
        xml = animated(f'<path d="M0,0 L1,1 Z">{PULSE}</path>')
        self.assertIn("from icons/weather/light/x-day.svg; do not edit", xml)

    def test_a_jump_passes_a_value_at_once_and_a_split_leaves_its_track_as_it_was(self):
        track = weather_icons.Track([0, .5, .5, 1], [-1, -1, 5, 5], [None] * 3, 2.0, 0.0, None, "t.svg")
        self.assertEqual((-1, -1, 5, 5), track.split_at(0.0).values)
        moving = weather_icons.Track([0, 1], [-1, 5], [None], 2.0, 0.0, None, "t.svg")
        split = moving.split_at(0.0)
        self.assertEqual(((0, 1 / 6, 1), (-1, 0.0, 5)), (split.times, split.values))
        self.assertEqual(((0, 1), (-1, 5)), (moving.times, moving.values))

    def test_an_animator_that_does_not_follow_its_animation_is_refused(self):
        track = weather_icons.Track([0.0, 1.0], [0.0, 1.0], [None], 2.0, 0.0, lambda t: (t / 2.0) % 1.0,
                                    "light/x-day.svg <path>")
        weather_icons.follows(weather_icons.animator("fillAlpha", track), track)
        late = weather_icons.FromTo("fillAlpha", 2100, 0.0, 1.0, None)
        with self.assertRaises(Unsupported) as caught:
            weather_icons.follows(late, track)
        self.assertIn("light/x-day.svg <path>: the animator of fillAlpha does not follow the SMIL animation",
                      str(caught.exception))


class AnimatedRefuseTest(unittest.TestCase):
    """What weather_icons.py refuses in an animated SVG, naming the file and the element (D43)."""

    def refused(self, body, says):
        with self.assertRaises(Unsupported) as caught:
            animated(body)
        self.assertIn("light/x-day.svg", str(caught.exception))
        self.assertIn(says, str(caught.exception))

    def pulse(self, attributes, says, shape='<path d="M0,0 L1,1 Z">', end="</path>"):
        self.refused(f'{shape}<animate attributeName="opacity" {attributes}/>{end}', says)

    def test_timing_it_does_not_translate(self):
        self.pulse('dur="2s" repeatCount="indefinite" calcMode="discrete" values="1;0"', "calcMode 'discrete'")
        self.pulse('dur="2s" repeatCount="indefinite" calcMode="paced" values="1;0"', "calcMode 'paced'")
        self.pulse('dur="2s" begin="1s" repeatCount="indefinite" values="1;0"', "begin '1s' after the start")
        self.pulse('dur="2s" repeatCount="2" values="1;0"', "repeatCount '2' is not indefinite")
        self.pulse('dur="500ms" repeatCount="indefinite" values="1;0"', "dur '500ms' is not a time in seconds")
        self.pulse('dur="2s" repeatCount="indefinite" from="1" to="0"', "attributes ['from', 'to'] are not supported")
        self.pulse('dur="2s" repeatCount="indefinite" keyTimes="0;.5" values="1;0"', "keyTimes '0;.5' do not run")
        self.pulse('dur="2s" repeatCount="indefinite" calcMode="spline" keySplines="0 0 2 1" values="1;0"',
                   "keySplines '0 0 2 1' are not one x1 y1 x2 y2 in 0..1")

    def test_animations_of_what_it_does_not_translate(self):
        self.refused('<circle cx="1" cy="1" r="1"><animate attributeName="fill" dur="2s" repeatCount="indefinite" '
                     'values="#000;#fff"/></circle>', "<animate>: an animation of fill on <circle> is not supported")
        self.refused('<circle cx="1" cy="1" r="1"><animateTransform attributeName="transform" type="rotate" '
                     'dur="2s" repeatCount="indefinite" values="0;90"/></circle>',
                     "an animation of transform on <circle> is not supported")
        self.refused('<circle cx="1" cy="1" r="1"><animateMotion dur="2s" path="M0,0 L1,1"/></circle>',
                     "a shape with children other than animations")
        self.refused('<g><path d="M0,0 L1,1 Z"/><animateTransform attributeName="transform" type="skewX" dur="2s" '
                     'repeatCount="indefinite" values="0;10"/></g>', "type 'skewX' is not translate, scale or rotate")
        self.refused('<g><path d="M0,0 L1,1 Z"/><animateTransform attributeName="transform" type="rotate" dur="2s" '
                     'repeatCount="indefinite" values="0 1 1;90 2 2"/></g>', "a rotation about a moving centre")
        self.refused('<g transform="translate(1 1)"><path d="M0,0 L1,1 Z"/><animateTransform '
                     'attributeName="transform" type="rotate" dur="2s" repeatCount="indefinite" values="0;90"/></g>',
                     "an animated transform on a group with a transform of its own")
        self.pulse('dur="2s" repeatCount="indefinite" values="1;0"/><animate attributeName="opacity" dur="2s" '
                   'repeatCount="indefinite" values="1;0"', "a second animation of opacity on one element")

    def test_shapes_it_cannot_move_as_animated(self):
        self.refused('<path d="M0,0 L1,1"><animate attributeName="d" dur="2s" repeatCount="indefinite" '
                     'values="M0,0 L1,1;M0,0 C1,1 2,2 3,3"/></path>', "the path data changes its commands")
        self.refused('<path d="M0,0 L1,1"><animate attributeName="d" dur="2s" begin="-1s" repeatCount="indefinite" '
                     'values="M0,0 L1,1;M0,0 L2,2"/></path>', "a changing shape is supported only from its start")
        self.refused('<line x1="0" y1="0" x2="1" y2="5" stroke="#000"><animate attributeName="y2" dur="2s" '
                     'repeatCount="indefinite" values="5;6"/></line>', "a moving y2 is supported only on a vertical")
        self.refused('<line x1="0" y1="0" x2="0" y2="5" pathLength="100" stroke="#000" stroke-dasharray="20 30">'
                     '<animate attributeName="stroke-dashoffset" dur="2s" repeatCount="indefinite" values="0;-50"/>'
                     '</line>', "bring a second dash onto the path")
        self.refused('<g><path d="M0,0 L1,1 Z"><animate attributeName="opacity" dur="2s" repeatCount="indefinite" '
                     'values="1;0"/></path><animate attributeName="opacity" dur="2s" repeatCount="indefinite" '
                     'values="1;0"/></g>', "an opacity animated here and on a group above")

    def test_a_spline_whose_cut_at_a_negative_begin_leaves_the_unit_square(self):
        self.pulse('dur="4s" begin="-1s" repeatCount="indefinite" calcMode="spline" keySplines="0 1 1 0" '
                   'values="0;1"', "<animate>: the spline 0 1 1 0 cut at 0.25 of its interval leaves 0..1")

    def test_an_svg_that_moves_nothing(self):
        self.refused('<path d="M0,0 L1,1 Z"/>', "moves nothing")

if __name__ == "__main__":
    unittest.main()
