"""发行扫描的对抗夹具：实际 class 名、重命名资源、嵌套归档及路径穿越。"""
import hashlib
import io
import struct
import unittest
import zipfile
from verify_external_distribution import inspect


def clazz(name):
    encoded = name.encode()
    return b'\xca\xfe\xba\xbe\x00\x00\x00\x34\x00\x03\x01' + struct.pack('>H', len(encoded)) + encoded + b'\x07\x00\x01\x00\x21\x00\x02'


def archive(name, data):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as file: file.writestr(name, data)
    return output.getvalue()


class ScanTest(unittest.TestCase):
    def scan(self, data, hashes=None):
        inspect(data, 'fixture', hashes or set(), {'entries': 0, 'archives': 0})
    def test_renamed_class(self):
        with self.assertRaisesRegex(ValueError, 'class definition'): self.scan(archive('innocent.dat', clazz('com/zarkonnen/airships/AGame')))
    def test_nested_archive(self):
        with self.assertRaisesRegex(ValueError, 'class definition'): self.scan(archive('template.bin', archive('renamed.bin', clazz('com/zarkonnen/airships/Main'))))
    def test_renamed_resource(self):
        resource = b'private game resource bytes'
        with self.assertRaisesRegex(ValueError, 'Game input'): self.scan(archive('icon.bin', resource), {hashlib.sha256(resource).hexdigest()})
    def test_traversal(self):
        with self.assertRaisesRegex(ValueError, 'Unsafe'): self.scan(archive('../escape.txt', b'x'))
    def test_framework_class(self):
        self.scan(archive('net/fabricacs/Core.class', clazz('net/fabricacs/Core')))
    def test_depth(self):
        data = b'x'
        for _ in range(10): data = archive('nested', data)
        with self.assertRaisesRegex(ValueError, 'nesting'): self.scan(data)


if __name__ == '__main__': unittest.main()
