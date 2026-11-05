"""Exercise release version agreement without modifying the repository."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

class ResolveVersionTest(unittest.TestCase):
    def resolve(self, version='1.2.3', requested='', ref='refs/heads/main'):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'scripts').mkdir()
            script = root / 'scripts/resolve_version.py'
            script.write_bytes(Path(__file__).with_name('resolve_version.py').read_bytes())
            (root / 'gradle.properties').write_text('version=' + version + '\n')
            envfile = root / 'env'
            env = dict(os.environ, REQUESTED_VERSION=requested, GITHUB_REF=ref, GITHUB_ENV=str(envfile))
            result = subprocess.run([sys.executable, str(script)], env=env, capture_output=True, text=True)
            return result, envfile.read_text() if envfile.exists() else ''

    def test_normal_build_uses_project_version(self):
        result, output = self.resolve(version='1.2.4-SNAPSHOT')
        self.assertEqual(result.returncode, 0)
        self.assertEqual(output, 'RELEASE_VERSION=1.2.4-SNAPSHOT\n')

    def test_matching_stable_release_and_tag(self):
        result, output = self.resolve(requested='1.2.3', ref='refs/tags/v1.2.3')
        self.assertEqual(result.returncode, 0)
        self.assertEqual(output, 'RELEASE_VERSION=1.2.3\n')

    def test_rejects_input_tag_and_preview_mismatches(self):
        for kwargs in ({'requested': '1.2.4'}, {'ref': 'refs/tags/v9.0.0'},
                       {'version': '1.2.3-SNAPSHOT', 'requested': '1.2.3-SNAPSHOT'}):
            with self.subTest(kwargs=kwargs):
                result, output = self.resolve(**kwargs)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(output, '')
