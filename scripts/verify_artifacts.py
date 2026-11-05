"""Verify the plugin descriptor, Maven marker and release bundle."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
version = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line)['version']
version = os.environ.get('RELEASE_VERSION', version)
repo = root / 'build/maven'
artifact = repo / f'dev/desiderium/retro/retrospectum-loom/{version}/retrospectum-loom-{version}'
for suffix in ('.jar', '-sources.jar', '-javadoc.jar', '.pom'):
    assert Path(str(artifact) + suffix).is_file(), suffix
with zipfile.ZipFile(str(artifact) + '.jar') as jar:
    descriptors = [n for n in jar.namelist() if n.startswith('META-INF/gradle-plugins/') and n.endswith('.properties')]
    assert descriptors == ['META-INF/gradle-plugins/dev.desiderium.retro.loom.properties'], descriptors
    assert 'LICENSE' in jar.namelist() and 'UPSTREAM.md' in jar.namelist()
    for name in jar.namelist():
        if name.endswith('.class'):
            assert b'voldeloom' not in jar.read(name).lower(), name
marker = repo / f'dev/desiderium/retro/loom/dev.desiderium.retro.loom.gradle.plugin/{version}/dev.desiderium.retro.loom.gradle.plugin-{version}.pom'
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
dep = ET.parse(marker).find('m:dependencies/m:dependency', ns)
assert [dep.findtext('m:' + key, namespaces=ns) for key in ('groupId', 'artifactId', 'version')] == ['dev.desiderium.retro', 'retrospectum-loom', version]
with zipfile.ZipFile(root / f'build/distributions/retrospectum-loom-{version}-maven.zip') as bundle:
    assert any(n.endswith(f'retrospectum-loom-{version}.jar') for n in bundle.namelist())
    assert not any('voldeloom' in n.lower() or '.work/' in n for n in bundle.namelist())
print('Plugin identity, notices, Maven marker and distribution checks passed')
