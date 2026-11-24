#!/usr/bin/env python3
"""Build a canonical, owner-configured static MLB site with verified deployment values."""
import argparse
import base64
import hashlib
import html
import os
import re
import shutil
from pathlib import Path
from urllib.parse import urlsplit

SOURCE = Path(__file__).resolve().parents[1] / 'frontend'
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / 'dist'
SOURCE_SITE_BASE = 'https://mufaddalbatti53.github.io/MLB-Portfolio'
IDENTITY = {
    'SITE_DISPLAY_NAME': 'Mufaddal Battiwala',
    'SITE_EMAIL': 'mufaddal.techpro@gmail.com',
    'SITE_LOCATION': 'Kolkata, India',
    'SITE_GITHUB': 'https://github.com/mufaddalbatti53',
    'SITE_LINKEDIN': 'https://www.linkedin.com/in/mufaddal-tech/',
}
EMAIL_PATTERN = re.compile(r'^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$')


def refresh_jsonld_csp(text):
    match = re.search(r'<script type="application/ld\+json" id="seo-structured-data">(.*?)</script>', text, re.S)
    if not match:
        return text
    digest = base64.b64encode(hashlib.sha256(match.group(1).encode("utf-8")).digest()).decode("ascii")
    token = f"'sha256-{digest}'"
    meta = re.search(r'<meta http-equiv="Content-Security-Policy" content="([^"]+)">', text)
    if not meta:
        raise SystemExit('Structured data page is missing CSP')
    content = re.sub(r"\s+'sha256-[A-Za-z0-9+/=]+'", '', meta.group(1))
    content = content.replace("script-src 'self'", "script-src 'self' " + token, 1)
    return text[:meta.start(1)] + content + text[meta.end(1):]


def config(mode):
    base_url = os.environ.get('SITE_BASE_URL', '').strip().rstrip('/')
    parsed = urlsplit(base_url)
    local = mode == 'development' and parsed.hostname in {'localhost', '127.0.0.1'}
    valid_scheme = parsed.scheme == 'https' or (local and parsed.scheme == 'http')
    valid_path = parsed.path == '' or (parsed.path.startswith('/') and '//' not in parsed.path)
    if (not base_url or not valid_scheme or not parsed.hostname or not valid_path
            or parsed.query or parsed.fragment or parsed.username or parsed.password):
        raise SystemExit('SITE_BASE_URL must be an HTTPS site base URL; localhost HTTP is allowed only in development')
    if mode == 'production' and parsed.hostname == 'github.io':
        raise SystemExit('Use the owner-specific GitHub Pages host, not the github.io platform root')

    identity = {name: os.environ.get(name, value).strip() for name, value in IDENTITY.items()}
    if not EMAIL_PATTERN.fullmatch(identity['SITE_EMAIL']):
        raise SystemExit('Invalid SITE_EMAIL')
    if any(not value or len(value) > 180 or '\n' in value or '\r' in value for value in identity.values()):
        raise SystemExit('Invalid public profile field')
    for name in ('SITE_GITHUB', 'SITE_LINKEDIN'):
        url = urlsplit(identity[name])
        if url.scheme != 'https' or not url.hostname or url.username or url.password:
            raise SystemExit(f'{name} must be an HTTPS URL')
    return base_url, identity


def build(mode, output):
    base_url, identity = config(mode)
    output = output.resolve()
    if output == SOURCE.resolve() or SOURCE.resolve() in output.parents:
        raise SystemExit('Output cannot be within the frontend source')
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(SOURCE, output)

    for filename in ('index.html', '404.html', 'thank-you.html', 'privacy.html', 'terms.html',
                     'admin.html', 'robots.txt', 'sitemap.xml'):
        path = output / filename
        text = path.read_text(encoding='utf-8').replace(SOURCE_SITE_BASE, base_url)
        if filename.endswith('.html'):
            for name, default in IDENTITY.items():
                text = text.replace(default, html.escape(identity[name], quote=True))
            text = refresh_jsonld_csp(text)
        path.write_text(text, encoding='utf-8')
    print(f'Static site generated for {base_url} at {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=('production', 'development'), default='production')
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    build(args.mode, args.output)
