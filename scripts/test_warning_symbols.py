"""What warning_symbols.py makes of the Material Symbols, and what it refuses (D48)."""

import io
import re
import tempfile
import unittest
from pathlib import Path

import warning_symbols
from warning_symbols import Unsupported

# A square from 280 to 680 across and from -880 to -80 down, a stand-in for a symbol.
PATH = "M280-80V-880H680V-80Z"
# A sun: a ray above its centre, and its disc as an outer and an inner ring and a point at the centre.
SUN = "M440-920h80v160h-80ZM240-480a240 240 0 1 0 480 0a240 240 0 1 0-480 0Zm80 0a160 160 0 1 0 320 0Zm160 0Z"


def symbol(body=f'<path d="{PATH}"/>', root='height="24" viewBox="0 -960 960 960" width="24"'):
    return f'<svg xmlns="http://www.w3.org/2000/svg" {root}>{body}</svg>'


def needed():
    """Every copy the script needs."""
    names = set(warning_symbols.PLAIN.values()) | {warning_symbols.UV_SUN}
    return names | {name for pair in warning_symbols.COMPOSED.values() for name in pair}


def numbers(xml, attribute):
    return float(re.search(rf'android:{attribute}="([-0-9.]+)', xml).group(1))


class ConvertTest(unittest.TestCase):
    def setUp(self):
        self.paths = {name: PATH for name in needed()}
        self.paths[warning_symbols.UV_SUN] = SUN

    def test_a_symbol_as_it_is_keeps_its_path_on_a_viewport_of_960_moved_down_by_960(self):
        xml = warning_symbols.plain(self.paths, "air")
        self.assertIn('android:viewportWidth="960"', xml)
        self.assertIn('android:viewportHeight="960"', xml)
        self.assertIn('android:width="24dp"', xml)
        self.assertIn('<group android:translateY="960">', xml)
        self.assertIn(f'android:pathData="{PATH}"', xml)
        self.assertIn("from icons/symbols/air.svg", xml)

    def test_its_fill_is_a_palette_colour_the_widget_tints(self):
        xml = warning_symbols.plain(self.paths, "air")
        self.assertIn('android:fillColor="@color/palette_text"', xml)
        self.assertNotIn("#", xml)

    def test_a_composite_puts_the_second_symbol_at_055_at_the_upper_right_reaching_beyond_the_corner(self):
        xml = warning_symbols.composed(self.paths, "thermometer", "ac_unit")
        groups = re.findall(r"<group ([^>]*)>", xml)
        self.assertEqual(2, len(groups))
        self.assertIn('android:scaleX="0.55"', groups[1])
        # The second's square starts at 0.75 of the main's width and at -0.05 of its height; it draws from 280 of its
        # 960 and from 80 of them below its top, so it reaches 4 above the main's top, and the drawable grows by 4.
        self.assertEqual(964, numbers(xml, "viewportHeight"))
        self.assertEqual(964, float(re.search(r'translateY="([-0-9.]+)"', groups[0]).group(1)))
        self.assertEqual(484, float(re.search(r'translateY="([-0-9.]+)"', groups[1]).group(1)))
        # As wide as both draw, 280 to 720 + 0.55 × 680, with 80 before and after.
        self.assertEqual(200, -float(re.search(r'translateX="([-0-9.]+)"', groups[0]).group(1)))
        self.assertEqual(round(720 + 0.55 * 680 + 80 - 200, 2), numbers(xml, "viewportWidth"))
        self.assertEqual(round(numbers(xml, "viewportWidth") / 40, 2), numbers(xml, "width"))

    def test_uv_keeps_the_sun_s_rays_without_its_disc_and_strokes_the_letters_in_their_middle(self):
        xml = warning_symbols.uv(self.paths)
        data = re.findall(r'android:pathData="([^"]*)"', xml)
        self.assertEqual(["M440,-920h80v160h-80Z"], data[:1])
        self.assertNotIn("a240", data[0])
        self.assertIn('android:strokeLineCap="round"', xml)
        self.assertIn('android:strokeWidth="80"', xml)
        # The letters centred on the sun's centre: the U from 295 and the V to 665, from -580 to -380.
        self.assertTrue(data[1].startswith("M295,-580 V-440 A60,60 0 0,0 415,-440 V-580 M525,-580 L595,-380 L665,-580"))

    def test_a_moveto_with_more_points_keeps_them_as_lines(self):
        pieces = warning_symbols.subpaths("M10-10 20-20ZM480-480m5 5 1 1Z", "t.svg")
        self.assertEqual(["M10,-10L20 -20Z", "M480,-480", "M485,-475l1 1Z"], [data for data, _ in pieces])

    def test_anything_but_one_path_on_a_material_symbols_viewbox_is_refused(self):
        for text in [
            symbol(root='height="24" viewBox="0 0 24 24" width="24"'),
            symbol(root='height="48" viewBox="0 -960 960 960" width="48"'),
            symbol(body=f'<path d="{PATH}"/><path d="{PATH}"/>'),
            symbol(body='<circle r="3"/>'),
            symbol(body=f'<path d="{PATH}" fill="red"/>'),
            symbol(body=""),
            symbol(body='<path d="M0 0&quot; android:fillColor=&quot;red"/>'),
            "<svg",
        ]:
            with self.subTest(text=text), self.assertRaises(Unsupported) as raised:
                warning_symbols.path_of(text, "odd.svg")
            self.assertIn("odd.svg", str(raised.exception))

    def test_a_part_whose_bounds_cannot_be_found_is_refused(self):
        self.paths["ac_unit"] = "Z"
        with self.assertRaises(Unsupported) as raised:
            warning_symbols.composed(self.paths, "thermometer", "ac_unit")
        self.assertIn("ac_unit.svg", str(raised.exception))


class RunTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.sources = self.root / "symbols"
        self.res = self.root / "res"
        self.sources.mkdir()
        for name in needed():
            (self.sources / f"{name}.svg").write_text(symbol(f'<path d="{SUN if name == "sunny" else PATH}"/>'))

    def run_script(self, check):
        err, out = io.StringIO(), io.StringIO()
        return warning_symbols.run(check, self.sources, self.res, err, out), err.getvalue()

    def test_a_check_fails_until_the_drawables_are_written_and_then_passes(self):
        self.assertEqual(1, self.run_script(check=True)[0])
        self.assertEqual(0, self.run_script(check=False)[0])
        written = sorted(p.name for p in (self.res / "drawable").iterdir())
        kinds = [*warning_symbols.PLAIN, *warning_symbols.COMPOSED, "uv"]
        self.assertEqual(sorted(f"symbol_{kind}.xml" for kind in kinds), written)
        self.assertEqual((0, ""), self.run_script(check=True))

    def test_a_drawable_changed_by_hand_or_without_its_source_fails_the_check(self):
        self.run_script(check=False)
        (self.res / "drawable" / "symbol_wind.xml").write_text("changed")
        (self.res / "drawable" / "symbol_mist.xml").write_text("left over")
        code, err = self.run_script(check=True)
        self.assertEqual(1, code)
        self.assertIn("symbol_wind.xml is not what the script writes", err)
        self.assertIn("symbol_mist.xml has no source SVG", err)

    def test_writing_removes_a_drawable_without_its_source(self):
        (self.res / "drawable").mkdir(parents=True)
        (self.res / "drawable" / "symbol_mist.xml").write_text("left over")
        self.run_script(check=False)
        self.assertFalse((self.res / "drawable" / "symbol_mist.xml").exists())

    def test_a_copy_no_symbol_uses_or_a_missing_one_fails(self):
        (self.sources / "mist.svg").write_text(symbol())
        code, err = self.run_script(check=True)
        self.assertEqual(1, code)
        self.assertIn("mist.svg: not used by any symbol", err)
        (self.sources / "mist.svg").unlink()
        (self.sources / "falling.svg").unlink()
        code, err = self.run_script(check=True)
        self.assertEqual(1, code)
        self.assertIn("no copy of falling.svg", err)

    def test_without_any_svg_it_fails(self):
        for svg in self.sources.glob("*.svg"):
            svg.unlink()
        code, err = self.run_script(check=True)
        self.assertEqual(1, code)
        self.assertIn("no SVG", err)


if __name__ == "__main__":
    unittest.main()
