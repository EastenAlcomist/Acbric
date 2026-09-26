"""外部安装真实加载检查：自有安装只读输入，全部运行数据放在新 build 夹具，禁止运行正常游戏。"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile


def digest(path):
    with path.open('rb') as file:
        return hashlib.file_digest(file, 'sha256').hexdigest()


def snapshot(root):
    return {str(path.relative_to(root)): digest(path) for path in sorted(root.rglob('*')) if path.is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*', args.tag):
        parser.error('Use a simple fresh tag / 请使用简单的新名称')
    project = Path(__file__).resolve().parents[1]
    install = args.game_dir.resolve()
    run = project / 'build/external-tests' / args.tag
    if run.exists():
        parser.error('Run already exists / 测试目录已经存在')
    if install.is_relative_to(run) or run.is_relative_to(install):
        parser.error('Separate installation and test output / 安装与输出须分开')
    java = args.java_home.resolve() / 'bin/java.exe'
    loader = project / 'build/preflight/loader-libs'
    api = project / 'build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar'
    probe = project / 'build/classes/java/regressionTest/net/fabricacs/regression/ExternalRuntimeProbe.class'
    for path in [install / 'Airships.json', java, api, probe, loader / 'Acbric-1.0-SNAPSHOT.jar']:
        if not path.is_file():
            parser.error(f'Build preflightTools apiModJar regressionTestClasses first; missing {path}')
    run.mkdir(parents=True)
    before = snapshot(install)
    (run / 'installation-before.json').write_text(json.dumps(before, indent=2), encoding='utf-8')
    instance = run / '中文 空格实例'
    for name in ['cwd', 'appdata', 'home', 'tmp']:
        (run / name).mkdir()
    env = dict(os.environ, APPDATA=str(run / 'appdata'))
    common = [str(java), '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
              f'-Djava.io.tmpdir={run / "tmp"}', f'-Duser.home={run / "home"}', '-cp', str(loader / '*')]
    results = []

    def command(name, command, expected=0):
        with (run / f'{name}.log').open('w', encoding='utf-8') as log:
            process = subprocess.run(command, cwd=run / 'cwd', env=env, stdout=log, stderr=subprocess.STDOUT, timeout=60)
        text = (run / f'{name}.log').read_text(encoding='utf-8')
        if process.returncode != expected:
            raise RuntimeError(f'{name} exit={process.returncode}\n{text[-10000:]}')
        results.append({'name': name, 'exit': process.returncode})
        return text

    try:
        command('preflight-valid', common + ['net.fabricacs.acbric.ExternalPreflight', '--game-dir', str(install), '--instance-dir', str(instance)])
        invalid = command('preflight-invalid', common + ['net.fabricacs.acbric.ExternalPreflight', '--game-dir', str(run / 'no-game'), '--instance-dir', str(instance)], 2)
        if 'INSTALL_NOT_FOUND' not in invalid or not list((run / 'tmp').glob('acbric-preflight-*/preflight.properties')):
            raise AssertionError('Invalid install must have a readable failure and early log')
        (instance / 'mods').mkdir(exist_ok=True)
        (instance / 'config/launch-settings.json').write_text(json.dumps({'customWindowW': 1111, 'customDataDirectoryLocation': str(run / 'forbidden-data'), 'customGIFSaveDirectoryLocation': str(run / 'forbidden-gifs')}), encoding='utf-8')
        shutil.copy2(api, instance / 'mods/acbric-api.jar')
        with zipfile.ZipFile(instance / 'mods/external-probe.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
            jar.write(probe, 'net/fabricacs/regression/ExternalRuntimeProbe.class')
            jar.write(probe.parent / 'TextureProbeImage.class', 'net/fabricacs/regression/TextureProbeImage.class')
            jar.write(probe.parent / 'fixtures/ExternalTextureFixture.class', 'net/fabricacs/regression/fixtures/ExternalTextureFixture.class')
            jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'external_probe', 'version': '1',
                'entrypoints': {'acbric': ['net.fabricacs.regression.ExternalRuntimeProbe']}, 'mixins': ['external-fixture.mixins.json']}))
            jar.writestr('external-fixture.mixins.json', json.dumps({'required': True, 'package': 'net.fabricacs.regression.fixtures', 'compatibilityLevel': 'JAVA_21', 'mixins': ['ExternalTextureFixture'], 'injectors': {'defaultRequire': 1}}))
            jar.writestr('acbric_vanilla/info.json', json.dumps({'id': 'external_probe', 'name': 'External path probe'}))
            jar.writestr('acbric_vanilla/marker.txt', 'external instance only')
        props = [f'-Dacbric.external.install={install}', f'-Dacbric.external.instance={instance}']
        blocked = command('normal-launch-blocked', common[:1] + props + common[1:] + ['net.fabricmc.loader.impl.launch.knot.KnotClient'], 1)
        if 'CORE_REQUIRED' not in blocked:
            raise AssertionError('Direct external launch without required core must fail before game/mod initialization')
        # 正式入口在 preLaunch 前拒绝缺失/解析成其他版本的核心；不触发 Main 或游戏图形。
        isolated = run / 'core-guard-instance'
        guarded = [f'-Dacbric.external.install={install}', f'-Dacbric.external.instance={isolated}', f'-Dacbric.external.core={api}']
        missing = command('resolved-core-missing', common[:1] + guarded + common[1:] + ['net.fabricmc.loader.impl.launch.knot.KnotClient'], 1)
        if 'CORE_MISSING' not in missing: raise AssertionError('Unloaded core was accepted')
        (isolated / 'mods').mkdir(exist_ok=True)
        with zipfile.ZipFile(isolated / 'mods/wrong-core.jar', 'w') as jar:
            jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'acbric_api', 'version': '99.0.0'}))
        mismatch = command('resolved-core-mismatch', common[:1] + guarded + common[1:] + ['net.fabricmc.loader.impl.launch.knot.KnotClient'], 1)
        if 'CORE_MISMATCH' not in mismatch: raise AssertionError('Wrong resolved core was accepted')
        if any(p.is_file() for p in (isolated / 'userdata').rglob('*')): raise AssertionError('Game data initialized before core validation')
        log = command('external-runtime', common[:1] + props + [
            '-Dacbric.internal.externalProbeMain=net.fabricacs.regression.ExternalRuntimeProbe',
            '-Djava.awt.headless=true', '--add-opens=java.base/java.util=ALL-UNNAMED'] + common[1:] +
            ['net.fabricmc.loader.impl.launch.knot.KnotClient'])
        match = re.search(r'EXTERNAL RUNTIME PASS: (\d+) checks', log)
        if not match:
            raise AssertionError(log[-10000:])
        if any((run / 'appdata').iterdir()) or any((run / 'home').iterdir()):
            raise AssertionError('Unexpected global fallback user data writes')
        if (run / 'forbidden-data').exists() or (run / 'forbidden-gifs').exists():
            raise AssertionError('Launch settings escaped instance')
        summary = {'status': 'PASS', 'runtimeChecks': int(match.group(1)), 'processes': results,
                   'apiSha256': digest(api), 'launcherSha256': digest(loader / 'Acbric-1.0-SNAPSHOT.jar'),
                   'limits': 'No Main.main, GPU, full assets/world, native loading, DLC, Workshop or multiplayer. Texture GPU construction replaced only in fixture; native file reads/writes/cache fallback exercised.'}
    finally:
        after = snapshot(install)
        changes = {'added': sorted(after.keys() - before.keys()), 'removed': sorted(before.keys() - after.keys()),
                   'modified': sorted(key for key in before.keys() & after.keys() if before[key] != after[key])}
        (run / 'installation-changes.json').write_text(json.dumps(changes, indent=2), encoding='utf-8')
        if any(changes.values()):
            raise AssertionError(f'Installation changed: {changes}')
    summary['installationFilesUnchanged'] = len(before)
    (run / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))
    print(f'Evidence / 证据: {run}')


if __name__ == '__main__':
    main()
