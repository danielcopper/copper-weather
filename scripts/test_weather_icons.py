"""What weather_icons.py makes of an SVG, and what it refuses (D38)."""

import io
import tempfile
import unittest
from pathlib import Path

import weather_icons
from weather_icons import Unsupported, convert


def svg(body, root='viewBox="0 0 64 64"'):
    return f'<svg xmlns="http://www.w3.org/2000/svg" {root}>{body}</svg>'


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


class OutputsTest(unittest.TestCase):
    def test_light_goes_to_drawable_and_dark_to_drawable_night_under_one_name(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for mode in ("light", "dark"):
                (root / "svg" / mode).mkdir(parents=True)
                (root / "svg" / mode / "lightning-rainy-night-still.svg").write_text(svg('<path d="M0,0 L1,1 Z"/>'))
            written = weather_icons.outputs(root / "svg", root / "res")
            self.assertEqual(
                {root / "res" / "drawable" / "weather_lightning_rainy_night.xml",
                 root / "res" / "drawable-night" / "weather_lightning_rainy_night.xml"},
                set(written),
            )

    def test_a_source_named_otherwise_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for mode in ("light", "dark"):
                (root / "svg" / mode).mkdir(parents=True)
                (root / "svg" / mode / "sunny-day.svg").write_text(svg('<path d="M0,0 L1,1 Z"/>'))
            with self.assertRaises(Unsupported):
                weather_icons.outputs(root / "svg", root / "res")


class CheckTest(unittest.TestCase):
    """--check, on a folder of SVGs and one of resources made for the test."""

    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        root = Path(folder.name)
        self.sources = root / "svg"
        self.res = root / "res"
        for mode in ("light", "dark"):
            (self.sources / mode).mkdir(parents=True)
            (self.sources / mode / "fog-day-still.svg").write_text(svg('<path d="M0,0 L1,1 Z"/>'))
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


if __name__ == "__main__":
    unittest.main()
