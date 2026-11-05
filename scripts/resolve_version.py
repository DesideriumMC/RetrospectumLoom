"""Resolve CI version from gradle.properties and reject inconsistent release inputs."""
import os
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
version = properties['version'].strip()
requested = os.environ.get('REQUESTED_VERSION', '').strip()
if requested and requested != version:
    raise SystemExit(f'Release input {requested} differs from gradle.properties version {version}')
ref = os.environ.get('GITHUB_REF', '')
if requested and not re.fullmatch(r'\d+\.\d+\.\d+', version):
    raise SystemExit('Release requires a stable MAJOR.MINOR.PATCH version')
if ref.startswith('refs/tags/') and ref != 'refs/tags/v' + version:
    raise SystemExit(f'Tag {ref} does not match version {version}')
if os.environ.get('GITHUB_ENV'):
    with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as output:
        output.write(f'RELEASE_VERSION={version}\n')
print(version)
