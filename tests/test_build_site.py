import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts' / 'build_site.py'
spec = importlib.util.spec_from_file_location('build_site', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SiteBuildTests(unittest.TestCase):
    def test_requires_valid_base_url(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(SystemExit):
                module.config('production')

    def test_rejects_http_for_production(self):
        with patch.dict(os.environ, {'SITE_BASE_URL': 'http://portfolio.example.net'}, clear=True):
            with self.assertRaises(SystemExit):
                module.config('production')

    def test_rejects_unowned_github_pages_root(self):
        with patch.dict(os.environ, {'SITE_BASE_URL': 'https://github.io'}, clear=True):
            with self.assertRaises(SystemExit):
                module.config('production')

    def test_accepts_github_pages_project_subpath(self):
        with patch.dict(os.environ, {
            'SITE_BASE_URL': 'https://mufaddalbatti53.github.io/MLB-Portfolio'
        }, clear=True):
            base_url, _ = module.config('production')
            self.assertEqual('https://mufaddalbatti53.github.io/MLB-Portfolio', base_url)

    def test_build_output_and_admin_noindex(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {
                'SITE_BASE_URL': 'https://portfolio.example.net/work'
            }, clear=True):
                module.build('production', Path(directory) / 'release')
            build = Path(directory) / 'release'
            index = (build / 'index.html').read_text()
            self.assertIn('https://portfolio.example.net/work/assets/og-preview.png', index)
            self.assertIn('Mufaddal Battiwala', index)
            self.assertIn('ProfilePage', index)
            self.assertIn('https://portfolio.example.net/work/#mufaddal-battiwala', index)
            self.assertRegex(index, r"script-src 'self' 'sha256-[A-Za-z0-9+/=]+'")
            self.assertIn('href="./assets/favicon.svg"', index)
            self.assertIn('noindex,nofollow', (build / 'admin.html').read_text())
            self.assertTrue((build / 'assets/logo.svg').exists())

    def test_html_escapes_optional_overrides(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(os.environ, {
                'SITE_BASE_URL': 'https://portfolio.example.net',
                'SITE_DISPLAY_NAME': '<script>alert(1)</script>'
            }, clear=True):
                module.build('production', Path(directory) / 'release')
            page = (Path(directory) / 'release' / 'index.html').read_text()
            self.assertNotIn('<script>alert(1)</script>', page)
            self.assertIn('&lt;script&gt;', page)


if __name__ == '__main__':
    unittest.main()
