"""Regression checks for candidate preservation and supported-host selection."""
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release_candidate as candidate


class ReleaseCandidateTest(unittest.TestCase):
    def test_latest_version_selects_newest_stable_supported_build(self):
        releases = {'GO': [
            {'type': 'release', 'build': '263.1', 'version': '2026.3'},
            {'type': 'eap', 'build': '262.999', 'version': '2026.2 EAP'},
            {'type': 'release', 'build': '262.9', 'version': '2026.2.1'},
            {'type': 'release', 'build': '262.10', 'version': '2026.2.2'},
        ]}
        with patch.object(candidate.urllib.request, 'urlopen', return_value=io.BytesIO(json.dumps(releases).encode())):
            self.assertEqual('2026.2.2', candidate.latest_version('goland'))

    def test_latest_version_rejects_catalog_without_supported_release(self):
        releases = {'GO': [{'type': 'release', 'build': '263.1', 'version': '2026.3'}]}
        with patch.object(candidate.urllib.request, 'urlopen', return_value=io.BytesIO(json.dumps(releases).encode())):
            with self.assertRaisesRegex(ValueError, 'No stable 2026.2'):
                candidate.latest_version('goland')

    def check_signed(self, source_payload, signed_payload, signed_comment=b''):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'candidate.zip'
            signed = root / 'signed.zip'
            for path, payload, comment in [(source, source_payload, b''), (signed, signed_payload, signed_comment)]:
                with zipfile.ZipFile(path, 'w') as archive:
                    for name, data in payload.items():
                        archive.writestr(name, data)
                    archive.comment = comment
            metadata = {'files': {'candidate.zip': candidate.digest(source)}}
            with patch.object(candidate, 'verify', return_value=metadata):
                return candidate.verify_signed(root, signed)

    def test_signed_payload_comparison_allows_non_entry_zip_metadata(self):
        # This validates payload preservation only. Gradle verifies the real signature.
        payload = {'spellixir/lib/plugin.jar': b'plugin'}
        self.assertTrue(self.check_signed(payload, payload, b'changed ZIP metadata')['payloadUnchanged'])

    def test_signed_payload_comparison_rejects_changed_content(self):
        with self.assertRaisesRegex(ValueError, 'changed release candidate contents'):
            self.check_signed({'plugin.jar': b'original'}, {'plugin.jar': b'changed'})

    def test_signed_payload_comparison_rejects_added_entries(self):
        with self.assertRaisesRegex(ValueError, 'changed the release candidate file set'):
            self.check_signed({'plugin.jar': b'original'}, {'plugin.jar': b'original', 'extra': b'unexpected'})

    def test_signed_payload_comparison_rejects_unsigned_candidate(self):
        payload = {'plugin.jar': b'original'}
        with self.assertRaisesRegex(ValueError, 'identical to unsigned'):
            self.check_signed(payload, payload)


if __name__ == '__main__':
    unittest.main()
