"""Publish one release to R2, preserving existing versions and immutable files.

Requires boto3. The release workflow serializes updates for this repository.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def read_object(client, bucket, key):
    try:
        response = client.get_object(Bucket=bucket, Key=key)
        return response['Body'].read(), response['ETag']
    except client.exceptions.NoSuchKey:
        return None, None


def release_key(version):
    match = re.fullmatch(r'(\d+)\.(\d+)\.(\d+)', version)
    if not match:
        raise ValueError('Release versions must use MAJOR.MINOR.PATCH')
    return tuple(map(int, match.groups()))


def merge_metadata(remote, local, version):
    source = ET.fromstring(local)
    root = ET.fromstring(remote) if remote is not None else ET.fromstring(local)
    for name in ('groupId', 'artifactId'):
        if root.findtext(name) != source.findtext(name):
            raise ValueError('Remote Maven metadata coordinate mismatch')
    versioning = root.find('versioning')
    if versioning is None:
        versioning = ET.SubElement(root, 'versioning')
    versions = versioning.find('versions')
    if versions is None:
        versions = ET.SubElement(versioning, 'versions')
    existing = [element.text for element in versions.findall('version')]
    if version not in existing:
        ET.SubElement(versions, 'version').text = version
    # Preserve preview/legacy entries, but choose the highest stable release.
    stable = [element.text for element in versions.findall('version')
              if re.fullmatch(r'\d+\.\d+\.\d+', element.text or '')]
    latest = max(stable, key=release_key)
    for name, value in (('latest', latest), ('release', latest),
                        ('lastUpdated', datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S'))):
        element = versioning.find(name)
        if element is None:
            element = ET.SubElement(versioning, name)
        element.text = value
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def publish(client, bucket, prefix, repository, version):
    release_key(version)
    metadata = sorted(repository.rglob('maven-metadata.xml'))
    if not metadata:
        raise ValueError('No Maven publications found')
    uploads = []
    publications = []
    for path in metadata:
        local = path.read_bytes()
        model = ET.fromstring(local)
        if model.findtext('groupId') != 'dev.desiderium.retro' and model.findtext('groupId') != 'dev.desiderium.retro.loom':
            raise ValueError('Unexpected publication group')
        directory = path.parent / version
        files = sorted(p for p in directory.iterdir() if p.is_file())
        if not any(p.suffix == '.pom' for p in files):
            raise ValueError('Missing version POM: ' + str(directory))
        for file in files:
            key = prefix + file.relative_to(repository).as_posix()
            data = file.read_bytes()
            previous, _ = read_object(client, bucket, key)
            if previous is not None and previous != data:
                raise ValueError('Refusing to replace published file: ' + key)
            if previous is None:
                uploads.append((key, data))
        key = prefix + path.relative_to(repository).as_posix()
        previous, etag = read_object(client, bucket, key)
        merged = merge_metadata(previous, local, version)
        publications.append((key, merged, etag))
    # Preflight every coordinate before writing. Identical existing files allow retries.
    for key, data in uploads:
        try:
            client.put_object(Bucket=bucket, Key=key, Body=data,
                              IfNoneMatch='*', CacheControl='public, max-age=86400')
        except client.exceptions.ClientError as error:
            if error.response['ResponseMetadata']['HTTPStatusCode'] != 412:
                raise
            previous, _ = read_object(client, bucket, key)
            if previous != data:
                raise ValueError('Concurrent conflicting publication: ' + key) from error
    for key, data, etag in publications:
        condition = {'IfMatch': etag} if etag else {'IfNoneMatch': '*'}
        client.put_object(Bucket=bucket, Key=key, Body=data, ContentType='application/xml',
                          CacheControl='no-cache', **condition)
        for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
            checksum = hashlib.new(algorithm, data).hexdigest().encode('ascii')
            client.put_object(Bucket=bucket, Key=key + '.' + algorithm,
                              Body=checksum, ContentType='text/plain', CacheControl='no-cache')
    print(f'Published {len(publications)} coordinates at version {version}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, default=Path('build/maven'))
    parser.add_argument('--version', default=os.environ.get('RELEASE_VERSION'), required=not os.environ.get('RELEASE_VERSION'))
    parser.add_argument('--bucket', default='desiderium-maven')
    parser.add_argument('--prefix', default='releases/')
    parser.add_argument('--endpoint', default=os.environ.get('R2_ENDPOINT'), required=not os.environ.get('R2_ENDPOINT'))
    args = parser.parse_args()
    import boto3
    from botocore.config import Config
    client = boto3.client('s3', endpoint_url=args.endpoint, region_name='auto',
                          config=Config(request_checksum_calculation='when_required',
                                        response_checksum_validation='when_required'))
    publish(client, args.bucket, args.prefix.rstrip('/') + '/', args.repository, args.version)


if __name__ == '__main__':
    main()
