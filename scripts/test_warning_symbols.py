"""What warning_symbols.py makes of a Material Symbol, and what it refuses (D48)."""

import io
import tempfile
import unittest
from pathlib import Path

import warning_symbols
from warning_symbols import Unsupported, convert

PATH = "M480-80q-137 0-228.5-94Z"


def symbol(body=f'<path d="{PATH}"/>', root='height="24" viewBox="0 -960 960 960" width="24"'):
    return f'<svg xmlns="http://www.w3.org/2000/svg" {root}>{body}</svg>'


class ConvertTest(unittest.TestCase):
    def test_the_path_stays_as_it_is_on_a_viewport_of_960_moved_down_by_960(self):
        xml = convert(symbol(), "water_drop.svg")
        self.assertIn('android:viewportWidth="960"', xml)
        self.assertIn('android:viewportHeight="960"', xml)
        self.assertIn('android:width="24dp"', xml)
        self.assertIn('<group android:translateY="960">', xml)
        self.assertIn(f'android:pathData="{PATH}"', xml)
        self.assertIn("from icons/symbols/water_drop.svg", xml)

    def test_its_fill_is_a_palette_colour_the_widget_tints(self):
        self.assertIn('android:fillColor="@color/palette_text"', convert(symbol(), "a.svg"))
        self.assertNotIn("#", convert(symbol(), "a.svg"))

    def test_anything_but_one_path_on_a_material_symbols_viewbox_is_refused(self):
        for text in [
            symbol(root='height="24" viewBox="0 0 24 24" width="24"'),
            symbol(root='height="48" viewBox="0 -960 960 960" width="48"'),
            symbol(body=f'<path d="{PATH}"/><path d="{PATH}"/>'),
            symbol(body='<circle r="3"/>'),
            symbol(body=f'<path d="{PATH}" fill="red"/>'),
            symbol(body=""),
            "<svg",
        ]:
            with self.subTest(text=text), self.assertRaises(Unsupported) as raised:
                convert(text, "odd.svg")
            self.assertIn("odd.svg", str(raised.exception))


class RunTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.sources = self.root / "symbols"
        self.res = self.root / "res"
        self.sources.mkdir()
        (self.sources / "air.svg").write_text(symbol())

    def run_script(self, check):
        err, out = io.StringIO(), io.StringIO()
        return warning_symbols.run(check, self.sources, self.res, err, out), err.getvalue()

    def test_a_check_fails_until_the_drawables_are_written_and_then_passes(self):
        self.assertEqual(1, self.run_script(check=True)[0])
        self.assertEqual(0, self.run_script(check=False)[0])
        self.assertTrue((self.res / "drawable" / "symbol_air.xml").exists())
        self.assertEqual((0, ""), self.run_script(check=True))

    def test_a_drawable_changed_by_hand_or_without_its_svg_fails_the_check(self):
        self.run_script(check=False)
        (self.res / "drawable" / "symbol_air.xml").write_text("changed")
        (self.res / "drawable" / "symbol_fog.xml").write_text("left over")
        code, err = self.run_script(check=True)
        self.assertEqual(1, code)
        self.assertIn("symbol_air.xml is not what the script writes", err)
        self.assertIn("symbol_fog.xml has no source SVG", err)

    def test_writing_removes_a_drawable_without_its_svg(self):
        (self.res / "drawable").mkdir(parents=True)
        (self.res / "drawable" / "symbol_fog.xml").write_text("left over")
        self.run_script(check=False)
        self.assertFalse((self.res / "drawable" / "symbol_fog.xml").exists())

    def test_without_any_svg_it_fails(self):
        (self.sources / "air.svg").unlink()
        code, err = self.run_script(check=True)
        self.assertEqual(1, code)
        self.assertIn("no SVG", err)


if __name__ == "__main__":
    unittest.main()
