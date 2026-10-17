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
    def plugin_archive(self, root, jar_location='spellixir/lib/plugin.jar'):
        jar_bytes = io.BytesIO()
        with zipfile.ZipFile(jar_bytes, 'w') as jar:
            jar.writestr('META-INF/plugin.xml',
                         '<idea-plugin><id>com.cimere.spellixir</id><version>0.1.7</version>'
                         '<idea-version since-build="261.26222.65" until-build="262.*"/>'
                         '<depends>com.intellij.modules.platform</depends></idea-plugin>')
            for suffix in ('lang/ElixirFileType.class', 'lang/ElixirLexer.class',
                           'lang/parser/ElixirParser.class', 'mix/MixProjectService.class'):
                jar.writestr('com/cimere/spellixir/' + suffix, bytes.fromhex('cafebabe00000041'))
        archive = root / 'candidate.zip'
        with zipfile.ZipFile(archive, 'w') as package:
            package.writestr(jar_location, jar_bytes.getvalue())
            package.writestr('spellixir/resources/settings.txt', 'external resource')
            package.writestr('spellixir/empty/', '')
        return archive

    def test_install_preserves_full_zip_and_removes_development_plugin(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.plugin_archive(root)
            installed = root / 'plugins_packagedSmoke' / 'spellixir'
            installed.mkdir(parents=True)
            (installed / 'development.jar').write_bytes(b'stale development plugin')
            probe = installed.parent / 'spellixir-smoke-probe'
            probe.mkdir()
            (probe / 'probe.jar').write_bytes(b'separate probe')
            original_zip = archive.read_bytes()
            candidate.install_smoke_archive(archive, installed)
            self.assertEqual('external resource', (installed / 'resources/settings.txt').read_text())
            self.assertTrue((installed / 'empty').is_dir())
            self.assertFalse((installed / 'development.jar').exists())
            self.assertEqual(b'separate probe', (probe / 'probe.jar').read_bytes())
            self.assertEqual(original_zip, archive.read_bytes())
            candidate.verify_installed_archive(archive, installed)

    def test_install_rejects_misplaced_jar_without_replacing_existing_plugin(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.plugin_archive(root, 'spellixir/wrong/place/plugin.jar')
            installed = root / 'plugins_packagedSmoke' / 'spellixir'
            installed.mkdir(parents=True)
            (installed / 'existing').write_text('keep')
            with self.assertRaisesRegex(ValueError, 'must be in the plugin lib directory'):
                candidate.install_smoke_archive(archive, installed)
            self.assertEqual('keep', (installed / 'existing').read_text())

    def test_installed_verification_rejects_missing_changed_and_extra_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.plugin_archive(root)
            installed = root / 'plugins_packagedSmoke' / 'spellixir'
            for mutation in ('missing', 'changed', 'extra'):
                with self.subTest(mutation=mutation):
                    candidate.install_smoke_archive(archive, installed)
                    resource = installed / 'resources/settings.txt'
                    if mutation == 'missing':
                        resource.unlink()
                    elif mutation == 'changed':
                        resource.write_text('changed')
                    else:
                        (installed / 'extra').write_text('extra')
                    with self.assertRaisesRegex(ValueError, 'differs|differ'):
                        candidate.verify_installed_archive(archive, installed)

    def test_install_preserves_signed_zip_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.plugin_archive(root)
            # Outer ZIP metadata must not change which payload is installed.
            # The real Marketplace signature is verified separately by Gradle.
            with zipfile.ZipFile(archive, 'a') as package:
                package.comment = b'signed archive metadata'
            candidate.install_smoke_archive(archive, root / 'plugins_packagedSmoke' / 'spellixir')

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
