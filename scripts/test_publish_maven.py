import hashlib
import io
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from publish_maven import merge_metadata, publish


class MissingKey(Exception):
    pass


class ConditionalConflict(Exception):
    response = {'ResponseMetadata': {'HTTPStatusCode': 412}}


class MemoryS3:
    class exceptions:
        NoSuchKey = MissingKey
        ClientError = ConditionalConflict

    def __init__(self):
        self.objects = {}
        self.writes = []
        self.fail_on = None

    def get_object(self, Bucket, Key):
        if Key not in self.objects:
            raise MissingKey(Key)
        data = self.objects[Key]
        return {'Body': io.BytesIO(data), 'ETag': hashlib.md5(data).hexdigest()}

    def put_object(self, Bucket, Key, Body, **options):
        if Key == self.fail_on:
            raise RuntimeError('Interrupted upload')
        if options.get('IfNoneMatch') and Key in self.objects:
            raise ConditionalConflict()
        if 'IfMatch' in options:
            if Key not in self.objects or hashlib.md5(self.objects[Key]).hexdigest() != options['IfMatch']:
                raise ConditionalConflict()
        self.objects[Key] = Body
        self.writes.append(Key)


def metadata(versions):
    return ('<metadata><groupId>dev.desiderium.retro</groupId><artifactId>test</artifactId>'
            '<versioning><versions>' + ''.join('<version>' + v + '</version>' for v in versions) +
            '</versions></versioning></metadata>').encode()


class PublicationTests(unittest.TestCase):
    def test_metadata_preserves_versions_and_does_not_downgrade_latest(self):
        result = ET.fromstring(merge_metadata(metadata(['0.1.0-alpha.1', '0.3.0']), metadata(['0.2.0']), '0.2.0'))
        self.assertEqual(result.findtext('versioning/latest'), '0.3.0')
        self.assertEqual(result.findtext('versioning/release'), '0.3.0')
        self.assertEqual([v.text for v in result.findall('versioning/versions/version')],
                         ['0.1.0-alpha.1', '0.3.0', '0.2.0'])

    def test_metadata_rejects_different_coordinate(self):
        with self.assertRaises(ValueError):
            merge_metadata(metadata(['0.1.0']).replace(b'<artifactId>test', b'<artifactId>other'),
                           metadata(['0.2.0']), '0.2.0')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.coordinate = 'dev/desiderium/retro/test/'
        directory = self.repo / self.coordinate / '0.1.0'
        directory.mkdir(parents=True)
        (directory / 'test-0.1.0.pom').write_bytes(b'pom')
        (directory / 'test-0.1.0.jar').write_bytes(b'jar')
        (directory.parent / 'maven-metadata.xml').write_bytes(metadata(['0.1.0']))
        self.client = MemoryS3()

    def test_conflict_is_detected_before_any_writes(self):
        self.client.objects['releases/' + self.coordinate + '0.1.0/test-0.1.0.pom'] = b'other'
        with self.assertRaises(ValueError):
            publish(self.client, 'bucket', 'releases/', self.repo, '0.1.0')
        self.assertEqual(self.client.writes, [])

    def test_interrupted_upload_can_resume_before_metadata_is_published(self):
        self.client.fail_on = 'releases/' + self.coordinate + '0.1.0/test-0.1.0.pom'
        with self.assertRaises(RuntimeError):
            publish(self.client, 'bucket', 'releases/', self.repo, '0.1.0')
        key = 'releases/' + self.coordinate + 'maven-metadata.xml'
        self.assertNotIn(key, self.client.objects)
        self.client.fail_on = None
        publish(self.client, 'bucket', 'releases/', self.repo, '0.1.0')
        self.assertEqual(self.client.objects[key + '.sha256'],
                         hashlib.sha256(self.client.objects[key]).hexdigest().encode())
        self.assertEqual(self.client.writes.count('releases/' + self.coordinate + '0.1.0/test-0.1.0.jar'), 1)


if __name__ == '__main__':
    unittest.main()
