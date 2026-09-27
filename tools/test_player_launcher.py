"""隔离解压真实 EXE：验证首次配置、再开、双语、游戏子进程和返回首页，不触碰玩家副本。"""
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess
import time
import zipfile
from test_external_install import snapshot, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    if not args.tag.isalnum(): parser.error('Use an alphanumeric fresh tag')
    project = Path(__file__).resolve().parents[1]
    run = project / 'build/player-launcher-tests' / args.tag
    run.mkdir(parents=True, exist_ok=False)
    game = args.game_dir.resolve()
    before = snapshot(game)
    version = json.loads((project / 'src/apiMod/resources/fabric.mod.json').read_text(encoding='utf-8'))['version']
    package = project / f'build/external-dist/Acbric-external-{version}.zip'
    with zipfile.ZipFile(package) as archive: archive.extractall(run / '中文 & path')
    bundle = run / '中文 & path/Acbric'
    binary = (bundle / 'Acbric.exe').read_bytes()
    pe = struct.unpack_from('<I', binary, 0x3c)[0]
    if struct.unpack_from('<H', binary, pe + 24 + 68)[0] != 2: raise AssertionError('EXE is not a Windows GUI application')
    agent = bundle / 'loader-libs/player-probe.jar'
    with zipfile.ZipFile(agent, 'w') as jar:
        for suffix in ['', '$Loader']:
            name = f'net/fabricacs/acbric/PlayerLauncherProbe{suffix}.class'
            jar.write(project / 'build/classes/java/regressionTest' / name, name)
    env = os.environ.copy()
    for name in ['APPDATA', 'LOCALAPPDATA']:
        folder = run / name.lower(); folder.mkdir(); env[name] = str(folder)
    for name in ['JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH']: env.pop(name, None)
    results = []
    try:
        for mode in ['setup', 'play']:
            if mode == 'play':
                instance = bundle / 'instances/default'
                (instance / 'config/launch-settings.json').write_text(json.dumps({'useCustomWindow': True, 'customWindowW': 960, 'customWindowH': 640, 'customWindowFullscreen': False, 'customWindowFullscreenWindow': False, 'customWindowBorderless': False}), encoding='utf-8')
                mods = bundle / 'mods'
                (mods / 'native_test').mkdir()
                (mods / 'native_test/info.json').write_text(json.dumps({'id': 'native_test', 'name': 'Test'}), encoding='utf-8')
                name = 'net/fabricacs/regression/fixtures/PublicLaunchFixture'
                with zipfile.ZipFile(mods / 'public-test.jar', 'w') as jar:
                    jar.write(project / f'build/classes/java/regressionTest/{name}.class', name + '.class')
                    jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'public_launch_test', 'version': '1', 'mixins': ['public-test.mixins.json']}))
                    jar.writestr('acbric_vanilla/info.json', json.dumps({'id': 'public_launch_test', 'name': 'Bundled test'}))
                    jar.writestr('public-test.mixins.json', json.dumps({'required': True, 'package': 'net.fabricacs.regression.fixtures', 'compatibilityLevel': 'JAVA_21', 'mixins': ['PublicLaunchFixture'], 'injectors': {'defaultRequire': 1}}))
            evidence = run / mode; evidence.mkdir()
            env['JAVA_TOOL_OPTIONS'] = f'-Djava.system.class.loader=net.fabricacs.acbric.PlayerLauncherProbe$Loader "-Dacbric.test.evidence={evidence}" "-Dacbric.test.game={game}" -Dacbric.test.mode={mode}'
            entry = subprocess.Popen([str(bundle / 'Acbric.exe')], cwd=run, env=env)
            try:
                end = time.monotonic() + 150
                while not (evidence / 'passed.txt').exists():
                    if (evidence / 'failed.txt').exists(): raise AssertionError((evidence / 'failed.txt').read_text(encoding='utf-8'))
                    if entry.poll() not in (None, 0) or time.monotonic() > end: raise AssertionError('Native launcher failed or timed out')
                    time.sleep(.2)
                if entry.wait(timeout=10) != 0: raise AssertionError('EXE bootstrap failed')
                results.append((evidence / 'passed.txt').read_text())
                # JVM may still be releasing its own locks after writing its completion marker.
                time.sleep(2)
            finally:
                if not (evidence / 'passed.txt').exists():
                    pidfile = evidence / 'java.pid'
                    owned_pid = pidfile.read_text().strip() if pidfile.exists() else str(entry.pid)
                    subprocess.run(['taskkill', '/PID', owned_pid, '/T', '/F'], capture_output=True)
        if list((run / 'appdata').iterdir()): raise AssertionError('Game wrote to global APPDATA')
        logs = list((bundle / 'instances/default/logs/acbric/launcher').glob('*.log'))
        if len(logs) != 1: raise AssertionError('Game output not captured')
        results.append((bundle / 'instances/default/public-checkpoint.txt').read_text(encoding='utf-8'))
        print('PASS: native EXE, first setup, saved English home, real game/menu/audio, return to launcher')
    finally:
        unchanged = snapshot(game) == before
        (run / 'result.json').write_text(json.dumps({'results': results, 'sourceUnchanged': unchanged, 'packageSha256': digest(package)}, indent=2), encoding='utf-8')
        if not unchanged: raise AssertionError('Source game was modified')


if __name__ == '__main__': main()
