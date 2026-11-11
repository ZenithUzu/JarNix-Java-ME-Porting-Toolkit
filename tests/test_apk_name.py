"""APK file names carry the official game name and developer. Run: python -m unittest discover -s tests"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tool'))
from j2a_tool.generate import apk_file_name, groovy_string, official_package, official_vendor
from j2a_tool.jarinfo import MidletSuite


class Suite(MidletSuite):
    """Manifest-only stand-in for a JAR."""
    def __init__(self, vendor, name, classes):
        self.attrs = {'MIDlet-Vendor': vendor, 'MIDlet-Name': name}
        self.classes = list(classes)
        self.jar_path = 'renamed_240x320.jar'


# (vendor, MIDlet-Name, classes) -> applicationId; None = hashed fallback. Mirrored in OfflineCoreTest.
PACKAGES = [
    (('Gameloft S.A.', 'Asphalt 4: Elite Racing', []), 'com.gameloft.asphalt4eliteracing'),
    (('2K Games', 'Pokémon Café', []), 'com.g2kgames.pokemoncafe'),
    (('Digital Chocolate Inc.', 'Tower Bloxx', []), 'com.digitalchocolate.towerbloxx'),
    (('Sun', 'Class', []), 'com.sun.class_'),
    (('Unknown', 'Snake', []), None),
    (('ゲーム会社', 'Snake', []), None),
    (('EA Mobile', 'Tetris', ['com/eamobile/tetris/engine/Core.class']), None),
]

# Same cases are asserted for J2Droid in OfflineCoreTest (GameJar.apkFileName).
CASES = [
    (('Asphalt 4: Elite Racing', 'Gameloft S.A.', '1.0.2'), 'Asphalt 4 Elite Racing (Gameloft S.A) v1.0.2'),
    (('Gameloft Bowling', 'GAMELOFT', '2.1'), 'Gameloft Bowling v2.1'),
    (('Snake', 'Unknown', ''), 'Snake'),
    (('Snake', 'MIDlet Suite Vendor', '1.0'), 'Snake v1.0'),
    (('', 'Nokia', '1.0'), 'juego (Nokia) v1.0'),
    (('Bounce/Tales?', 'Rovio\tMobile', '1.0 beta'), 'Bounce Tales (Rovio Mobile) v1.0beta'),
]


class ApkNameTests(unittest.TestCase):
    def test_cases(self):
        for args, expected in CASES:
            self.assertEqual(apk_file_name(*args), expected, args)

    def test_long_names_keep_developer_and_version(self):
        name = apk_file_name('X' * 300, 'Digital Chocolate', '3.4.5')
        self.assertLessEqual(len(name), 120)
        self.assertTrue(name.endswith(' (Digital Chocolate) v3.4.5'))

    def test_placeholder_vendor_is_dropped(self):
        self.assertEqual(official_vendor('  vendor '), '')
        self.assertEqual(official_vendor('Glu Mobile'), 'Glu Mobile')

    def test_official_package(self):
        for (vendor, name, classes), expected in PACKAGES:
            got = official_package(Suite(vendor, name, classes), 'Main')
            if expected is None:
                self.assertTrue(got.startswith('com.j2a.game.g'), got)
            else:
                self.assertEqual(got, expected)

    def test_package_falls_back_to_first_midlet_name(self):
        suite = Suite('Glu', '', [])
        suite.attrs['MIDlet-1'] = 'Super KO Boxing, /i.png, Main'
        self.assertEqual(official_package(suite, 'Main'), 'com.glu.superkoboxing')

    def test_groovy_literal_is_escaped(self):
        self.assertEqual(groovy_string("Tom's"), "Tom\\'s")


if __name__ == '__main__':
    unittest.main()
