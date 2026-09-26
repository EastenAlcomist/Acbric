"""外部发行递归审查：允许清单、游戏内容指纹及 class 实际定义名；重命名和嵌套归档同样检查。"""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import struct
import zipfile


def defined_class(data):
    if not data.startswith(b'\xca\xfe\xba\xbe'):
        return None
    count = struct.unpack_from('>H', data, 8)[0]
    pool, pos, index = {}, 10, 1
    while index < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]; pos += 2
            pool[index] = data[pos:pos + length].decode('utf-8', errors='replace'); pos += length
        elif tag == 7:
            pool[index] = struct.unpack_from('>H', data, pos)[0]; pos += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): pos += 4
        elif tag in (5, 6): pos += 8; index += 1
        elif tag in (8, 16, 19, 20): pos += 2
        elif tag == 15: pos += 3
        else: raise ValueError(f'Invalid class constant tag: {tag}')
        index += 1
    return pool[pool[struct.unpack_from('>H', data, pos + 2)[0]]]


def inspect(data, label, forbidden, stats, depth=0):
    if depth > 8: raise ValueError(f'Archive nesting exceeds limit: {label}')
    stats['entries'] += 1
    if data and hashlib.sha256(data).hexdigest() in forbidden:
        raise ValueError(f'Game input content in distribution: {label}')
    name = defined_class(data)
    if name and name.startswith(('com/zarkonnen/', 'org/newdawn/', 'org/lwjgl/', 'com/codedisaster/steamworks/', 'sun/misc/FloatingDecimal')):
        raise ValueError(f'Game/library class definition: {name} in {label}')
    if zipfile.is_zipfile(io.BytesIO(data)):
        stats['archives'] += 1
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            seen = set()
            for item in archive.infolist():
                path = PurePosixPath(item.filename)
                if path.is_absolute() or '..' in path.parts or '\\' in item.filename or item.filename in seen:
                    raise ValueError(f'Unsafe/duplicate archive entry: {label}!{item.filename}')
                seen.add(item.filename)
                if item.is_dir(): continue
                if item.file_size > 256 * 1024 * 1024: raise ValueError(f'Oversized entry: {item.filename}')
                inspect(archive.read(item), label + '!' + item.filename, forbidden, stats, depth + 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--game-libs', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps({"status": "FAILED_OR_INCOMPLETE"}), encoding="utf-8")
    forbidden = set()
    # 只采集游戏资源/原生库/代码输入，排除旧运行目录的框架、用户数据和日志。
    roots = [args.game_dir / x for x in ('data', 'lib', 'expansions', 'default_ships', 'default_landships', 'default_buildings')]
    inputs = [p for root in roots if root.exists() for p in root.rglob('*') if p.is_file()]
    inputs += list(args.game_dir.glob('asplit-*.zip')) + list(args.game_libs.glob('asplit-*.zip'))
    if not inputs: raise ValueError('No local game inputs for fingerprint verification')
    for path in inputs:
        if path.stat().st_size:
            with path.open('rb') as stream: forbidden.add(hashlib.file_digest(stream, 'sha256').hexdigest())
    allowed = {'INSTALLER.md', 'INSTALLER.zh-CN.md', 'Setup.cmd', 'setup.ps1', 'Start Acbric.cmd', 'start-configured.ps1', 'mods/README.md', 'LICENSE', 'start.ps1', 'check-install.ps1', 'bundle.properties', 'EXTERNAL_INSTALL.md', 'EXTERNAL_INSTALL.zh-CN.md', 'EXTERNAL_START.md', 'EXTERNAL_START.zh-CN.md'}
    loader_names = {'Acbric-1.0-SNAPSHOT.jar', 'fabric-loader-0.19.3.jar', 'sponge-mixin-0.17.3+mixin.0.8.7.jar'}
    allowed |= {'Update Acbric.cmd', 'update.ps1', 'maintenance.ps1', 'QUICK_START.txt', '使用说明.txt', 'release-files.properties'}
    loader_names |= {f'asm{x}-9.8.jar' for x in ('', '-analysis', '-commons', '-tree', '-util')}
    with zipfile.ZipFile(args.archive) as archive:
        for item in archive.infolist():
            if item.is_dir(): continue
            parts = PurePosixPath(item.filename).parts
            if len(parts) < 2 or parts[0] != 'Acbric': raise ValueError(f'Unexpected root: {item.filename}')
            sub = '/'.join(parts[1:])
            valid = sub in allowed or sub == 'core/acbric-api.jar' or (len(parts) == 3 and parts[1] == 'loader-libs' and parts[2] in loader_names)
            valid |= parts[1] == 'docs' and len(parts) == 3 and sub.endswith('.md')
            valid |= parts[1] == 'runtime'
            valid |= parts[1] == 'update-baselines' and len(parts) == 3 and sub.endswith('.properties')
            if parts[1] == 'acbric-mod-template':
                t = '/'.join(parts[2:])
                valid |= t.startswith(('src/', 'gradle/wrapper/')) or t in {'gradlew', 'gradlew.bat', 'build.gradle', 'settings.gradle', 'gradle.properties', 'README.md', 'README.zh-CN.md', '.gitignore', 'local.properties.example'}
            if not valid: raise ValueError(f'File outside distribution allowlist: {item.filename}')
    stats = {'status': 'PASS', 'entries': 0, 'archives': 0, 'gameFingerprints': len(forbidden)}
    data = args.archive.read_bytes()
    inspect(data, args.archive.name, forbidden, stats)
    stats['sha256'] = hashlib.sha256(data).hexdigest()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(stats, indent=2), encoding='utf-8')
    print(json.dumps(stats, indent=2))


if __name__ == '__main__': main()
