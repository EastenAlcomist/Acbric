"""Windows/JDK 21 真实图形菜单检查；测试专用写入/网络拦截，30 帧后退出，不替换 GPU。"""
from pathlib import Path
import argparse
import json
import os
import re
import shutil
import subprocess
import zipfile
from test_external_install import digest, snapshot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--runs', type=int, choices=[1, 2], default=2, help='First launch plus optional warm-cache restart')
    args = parser.parse_args()
    if os.name != 'nt' or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*', args.tag):
        parser.error('Windows and a fresh simple tag required')
    project = Path(__file__).resolve().parents[1]
    install = args.game_dir.resolve()
    run = project / 'build/external-menu' / args.tag
    if run.exists() or run.is_relative_to(install) or install.is_relative_to(run):
        parser.error('Use a new isolated output directory')
    java = args.java_home.resolve() / 'bin/java.exe'
    loader = project / 'build/preflight/loader-libs'
    api = project / 'build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar'
    classes = project / 'build/classes/java/regressionTest'
    package = 'net/fabricacs/regression/'
    for path in [java, api, install / 'Airships.json', classes / (package + 'SmokeGuard.class')]:
        if not path.is_file(): parser.error(f'Missing input: {path}')
    run.mkdir(parents=True)
    for name in ['cwd', 'appdata', 'localappdata', 'home', 'tmp']:
        (run / name).mkdir()
    instance = run / 'instance'
    before = snapshot(install)
    (run / 'installation-before.json').write_text(json.dumps(before, indent=2), encoding='utf-8')
    env = dict(os.environ, APPDATA=str(run / 'appdata'), LOCALAPPDATA=str(run / 'localappdata'),
               USERPROFILE=str(run / 'home'), TEMP=str(run / 'tmp'), TMP=str(run / 'tmp'))
    base = [str(java), '-Xmx2G', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8',
            f'-Djava.io.tmpdir={run / "tmp"}', f'-Duser.home={run / "home"}']
    startup = subprocess.STARTUPINFO()
    startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    startup.wShowWindow = subprocess.SW_HIDE
    summary = {'status': 'FAILED', 'apiSha256': digest(api), 'launcherSha256': digest(loader / 'Acbric-1.0-SNAPSHOT.jar')}
    try:
        with (run / 'preflight.log').open('w', encoding='utf-8') as log:
            subprocess.run(base + ['-cp', str(loader / '*'), 'net.fabricacs.acbric.ExternalPreflight',
                '--game-dir', str(install), '--instance-dir', str(instance)], cwd=run / 'cwd', env=env,
                stdout=log, stderr=subprocess.STDOUT, timeout=60, check=True, startupinfo=startup)
        (instance / 'config/launch-settings.json').write_text(json.dumps({'useCustomWindow': True,
            'customWindowW': 960, 'customWindowH': 640, 'customWindowFullscreen': False,
            'customWindowFullscreenWindow': False, 'customWindowBorderless': False}), encoding='utf-8')
        shutil.copy2(api, instance / 'mods/acbric-api.jar')
        with zipfile.ZipFile(run / 'guard.jar', 'w') as jar:
            jar.write(classes / (package + 'SmokeGuard.class'), package + 'SmokeGuard.class')
        with zipfile.ZipFile(instance / 'mods/menu-probe.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
            for name in ['ExternalRuntimeProbe', 'ExternalMenuProbe', 'fixtures/ExternalMenuFixture']:
                jar.write(classes / (package + name + '.class'), package + name + '.class')
            jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'external_probe', 'version': '1',
                'entrypoints': {'acbric': ['net.fabricacs.regression.ExternalRuntimeProbe']}, 'mixins': ['menu-fixture.mixins.json']}))
            jar.writestr('menu-fixture.mixins.json', json.dumps({'required': True, 'package': 'net.fabricacs.regression.fixtures',
                'compatibilityLevel': 'JAVA_21', 'mixins': ['ExternalMenuFixture'], 'injectors': {'defaultRequire': 1}}))
            jar.writestr('acbric_vanilla/info.json', json.dumps({'id': 'external_probe', 'name': 'External menu probe'}))
            jar.writestr('acbric_vanilla/marker.txt', 'external instance only')
        cmd = base + [f'-Dacbric.external.install={install}', f'-Dacbric.external.instance={instance}',
            '-Dacbric.internal.externalProbeMain=net.fabricacs.regression.ExternalRuntimeProbe',
            '-Dorg.lwjgl.util.Debug=true', '-Dacbric.test.menu=true', f'-Dacbric.test.root={run}', '-Djava.security.manager=net.fabricacs.regression.SmokeGuard',
            '--add-opens=java.base/java.util=ALL-UNNAMED', '-cp', str(loader / '*') + ';' + str(run / 'guard.jar'),
            'net.fabricmc.loader.impl.launch.knot.KnotClient']
        summary['runs'] = []
        for index in range(args.runs):
            log_file = run / ('menu.log' if index == 0 else 'menu-restart.log')
            checkpoint = instance / 'menu-checkpoint.txt'
            checkpoint.unlink(missing_ok=True)
            with log_file.open('w', encoding='utf-8') as log:
                result = subprocess.run(cmd, cwd=run / 'cwd', env=env, stdout=log, stderr=subprocess.STDOUT,
                                        timeout=90, startupinfo=startup)
            text = log_file.read_text(encoding='utf-8')
            native_log = instance / 'userdata/log.txt'
            if native_log.is_file():
                shutil.copy2(native_log, run / f'native-{index}.log')
                text += native_log.read_text(encoding='utf-8', errors='replace')
            if result.returncode != 0 or not checkpoint.is_file() or 'SMOKE_WRITE_DENIED' in text:
                raise RuntimeError(f'Menu test failed: {log_file}\n{text[-8000:]}')
            result = {'exit': result.returncode, 'checkpoint': checkpoint.read_text(encoding='utf-8'),
                      'rawCacheFiles': len(list((instance / 'cache').rglob('*.tex')))}
            summary['runs'].append(result)
            print(f'Menu run {index + 1} passed / 菜单第 {index + 1} 次通过', flush=True)
        summary['status'] = 'PASS'
    except Exception as error:
        summary['error'] = str(error)
        raise
    finally:
        after = snapshot(install)
        changes = {'added': sorted(after.keys() - before.keys()), 'removed': sorted(before.keys() - after.keys()),
                   'modified': sorted(k for k in before.keys() & after.keys() if before[k] != after[k])}
        summary['installationChanges'] = changes
        summary['installationFileCount'] = len(before)
        if any(changes.values()): summary['status'] = 'FAILED_INSTALL_CHANGED'
        (run / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding='utf-8')
        if any(changes.values()): raise AssertionError(f'Installation changed: {changes}')
    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
