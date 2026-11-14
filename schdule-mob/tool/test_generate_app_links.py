import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from generate_app_links import certificate_fingerprint, application_identifier_prefix, generate


FINGERPRINT = hashlib.sha256(b"unit-test certificate input").hexdigest()
SECOND_FINGERPRINT = hashlib.sha256(b"unit-test rotation certificate input").hexdigest()
PREFIX = "Q9M8H7K6L5"
SCRIPT = Path(__file__).with_name("generate_app_links.py")


class AppLinksTests(unittest.TestCase):
    def test_real_input_formats_are_required(self):
        normalized = certificate_fingerprint(FINGERPRINT)
        self.assertEqual(normalized, certificate_fingerprint(normalized.lower()))
        self.assertEqual(len(normalized.split(":")), 32)
        for invalid in ("", "<SHA256>", "AA:" * 31 + "AA", "0" * 64, "12:34", FINGERPRINT + "ab"):
            with self.subTest(invalid=invalid), self.assertRaises(argparse.ArgumentTypeError):
                certificate_fingerprint(invalid)
        for invalid in ("", "YOURTEAMID", "ABCDE12345", "TEAMID1234", "X" * 10, "q9m8h7k6l5", PREFIX + ".net.anissia.schedule"):
            with self.subTest(invalid=invalid), self.assertRaises(argparse.ArgumentTypeError):
                application_identifier_prefix(invalid)
        self.assertEqual(application_identifier_prefix(PREFIX), PREFIX)

    def test_missing_or_invalid_cli_input_creates_nothing(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root) / "not-created"
            for args in ([], ["--android-sha256", "not-a-fingerprint", "--apple-app-id-prefix", PREFIX]):
                result = subprocess.run([sys.executable, str(SCRIPT), "--output-dir", str(output), *args], capture_output=True, text=True)
                self.assertEqual(result.returncode, 2)
                self.assertFalse(output.exists())

    def test_exact_paths_and_browser_exclusion_are_generated_in_order(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root) / "review"
            android_path, apple_path = generate(output, [FINGERPRINT, SECOND_FINGERPRINT, FINGERPRINT], PREFIX)
            android = json.loads(android_path.read_text())
            self.assertEqual(android[0]["relation"], ["delegate_permission/common.handle_all_urls"])
            self.assertEqual(android[0]["target"]["package_name"], "anissia.android.schedule")
            self.assertEqual(android[0]["target"]["sha256_cert_fingerprints"], [certificate_fingerprint(FINGERPRINT), certificate_fingerprint(SECOND_FINGERPRINT)])
            details = json.loads(apple_path.read_text())["applinks"]["details"]
            self.assertEqual(details[0]["appIDs"], [PREFIX + ".net.anissia.schedule"])
            self.assertEqual(details[0]["defaults"], {"caseSensitive": True, "percentEncoded": True})
            rules = details[0]["components"]
            self.assertEqual(rules[0], {"?": {"_anissia_browser": "1"}, "exclude": True})
            self.assertEqual(rules[1:], [{"/": path} for path in (
                "/schedule", "/schedule/", "/schedule/2026", "/schedule/2026/", "/schedule/2015", "/schedule/2015/"
            )])
            original = {path: path.read_bytes() for path in (android_path, apple_path)}
            generate(output, [FINGERPRINT, SECOND_FINGERPRINT], PREFIX)
            self.assertEqual({path: path.read_bytes() for path in original}, original)
            self.assertEqual(sorted(path.name for path in output.iterdir()), ["apple-app-site-association", "assetlinks.json"])

    def test_other_apps_and_other_apple_services_are_preserved(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root)
            other_android = {"relation": ["delegate_permission/common.handle_all_urls"], "target": {"namespace": "android_app", "package_name": "another.app", "sha256_cert_fingerprints": [certificate_fingerprint(FINGERPRINT)]}}
            other_apple = {"appID": "M1N2B3V4C5.another.app", "paths": ["/other/*"]}
            original_apple = {"applinks": {"apps": [], "details": [other_apple]}, "webcredentials": {"apps": ["M1N2B3V4C5.another.app"]}, "activitycontinuation": {"apps": ["M1N2B3V4C5.another.app"]}}
            (output / "assetlinks.json").write_text(json.dumps([other_android]))
            (output / "apple-app-site-association").write_text(json.dumps(original_apple))
            android_path, apple_path = generate(output, [FINGERPRINT], PREFIX)
            self.assertEqual(json.loads(android_path.read_text())[0], other_android)
            apple = json.loads(apple_path.read_text())
            self.assertEqual(apple["applinks"]["details"][0], other_apple)
            self.assertEqual(apple["applinks"]["apps"], [])
            self.assertEqual(apple["webcredentials"], original_apple["webcredentials"])
            self.assertEqual(apple["activitycontinuation"], original_apple["activitycontinuation"])

    def test_conflicting_managed_entries_leave_both_files_unchanged(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root)
            paths = generate(output, [FINGERPRINT], PREFIX)
            original = {path: path.read_bytes() for path in paths}
            for fingerprints, prefix in (([SECOND_FINGERPRINT], PREFIX), ([FINGERPRINT], "M1N2B3V4C5")):
                with self.assertRaisesRegex(ValueError, "충돌"):
                    generate(output, fingerprints, prefix)
                self.assertEqual({path: path.read_bytes() for path in paths}, original)

    def test_invalid_existing_apple_file_does_not_write_android_file(self):
        invalid_contents = ("{broken", '{"applinks":{"details":{}}}', '{"applinks":{},"applinks":{}}')
        for content in invalid_contents:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as root:
                output = Path(root)
                apple_path = output / "apple-app-site-association"
                apple_path.write_text(content)
                with self.assertRaises(ValueError):
                    generate(output, [FINGERPRINT], PREFIX)
                self.assertEqual(apple_path.read_text(), content)
                self.assertFalse((output / "assetlinks.json").exists())

    def test_symlink_output_is_not_followed(self):
        with tempfile.TemporaryDirectory() as root:
            output = Path(root)
            target = output / "original.json"
            target.write_text("[]")
            (output / "assetlinks.json").symlink_to(target)
            with self.assertRaisesRegex(ValueError, "심볼릭"):
                generate(output, [FINGERPRINT], PREFIX)
            self.assertEqual(target.read_text(), "[]")
            self.assertFalse((output / "apple-app-site-association").exists())


if __name__ == "__main__":
    unittest.main()
