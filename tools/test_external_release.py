"""解压正式发行包后从无关工作目录启动，观测真实 Main 菜单、锁、坏包和模板独立构建。"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import zipfile
from test_external_install import snapshot, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--arc-jar', type=Path)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    if not args.tag.isalnum(): parser.error('Use a fresh alphanumeric tag')
    run = project / 'build/external-release-tests' / args.tag
    if run.exists(): parser.error('Test directory exists')
    run.mkdir(parents=True)
    install = args.game_dir.resolve()
    before = snapshot(install)
    bundle_zip = project / 'build/external-dist/Acbric-external.zip'
    unpack = run / '中文 空格发行'
    with zipfile.ZipFile(bundle_zip) as archive: archive.extractall(unpack)
    bundle = unpack / 'Acbric'
    instance = run / '独立 中文实例'
    (instance / 'mods').mkdir(parents=True)
    (instance / 'config').mkdir()
    (instance / 'config/launch-settings.json').write_text(json.dumps({'useCustomWindow': True, 'customWindowW': 960,
        'customWindowH': 640, 'customWindowFullscreen': False, 'customWindowFullscreenWindow': False, 'customWindowBorderless': False}), encoding='utf-8')
    name = 'net/fabricacs/regression/fixtures/PublicLaunchFixture'
    with zipfile.ZipFile(instance / 'mods/public-test.jar', 'w') as jar:
        jar.write(project / f'build/classes/java/regressionTest/{name}.class', name + '.class')
        jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'public_launch_test', 'version': '1', 'mixins': ['public-test.mixins.json']}))
        jar.writestr('public-test.mixins.json', json.dumps({'required': True, 'package': 'net.fabricacs.regression.fixtures',
            'compatibilityLevel': 'JAVA_21', 'mixins': ['PublicLaunchFixture'], 'injectors': {'defaultRequire': 1}}))
    if args.arc_jar: shutil.copy2(args.arc_jar, instance / 'mods/arc.jar')
    for directory in ['cwd', 'appdata', 'localappdata', 'home', 'tmp']: (run / directory).mkdir()
    env = dict(os.environ, APPDATA=str(run / 'appdata'), LOCALAPPDATA=str(run / 'localappdata'), USERPROFILE=str(run / 'home'),
        TEMP=str(run / 'tmp'), TMP=str(run / 'tmp'), JAVA_HOME=str(args.java_home.resolve()))
    for key in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']: env.pop(key, None)
    startup = subprocess.STARTUPINFO(); startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW; startup.wShowWindow = subprocess.SW_HIDE
    command = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start.ps1'),
        '-GameDir', str(install), '-InstanceDir', str(instance), '-JavaHome', str(args.java_home.resolve())]
    results = []
    def execute(label, cmd, expected=0):
        with (run / (label + '.log')).open('w', encoding='utf-8') as out:
            result = subprocess.run(cmd, cwd=run / 'cwd', env=env, stdout=out, stderr=subprocess.STDOUT, startupinfo=startup, timeout=90)
        if result.returncode != expected: raise AssertionError(f'{label} exit={result.returncode}; see log')
        results.append(label)
        return (run / (label + '.log')).read_text(encoding='utf-8', errors='replace')
    summary = {'status': 'FAILED', 'packageSha256': digest(bundle_zip)}
    try:
        for index in range(2):
            checkpoint = instance / 'public-checkpoint.txt'
            checkpoint.unlink(missing_ok=True)
            (instance / 'allow-test-exit').unlink(missing_ok=True)
            with (run / f'public-{index}.log').open('w', encoding='utf-8') as out:
                launch = command[:-2] if index == 1 and (bundle / 'runtime/bin/java.exe').is_file() else command
                child = subprocess.Popen(launch, cwd=run / 'cwd', env=env, stdout=out, stderr=subprocess.STDOUT, startupinfo=startup)
                try:
                    deadline = time.monotonic() + 90
                    while not checkpoint.exists():
                        if child.poll() is not None or time.monotonic() > deadline: raise AssertionError('Public menu did not reach checkpoint')
                        time.sleep(.25)
                    if index == 0:
                        text = execute('busy-instance', command, 2)
                        if 'INSTANCE_BUSY' not in text: raise AssertionError('Missing busy diagnosis')
                    (instance / 'allow-test-exit').write_text('test completed', encoding='utf-8')
                    if child.wait(timeout=20) != 0: raise AssertionError('Public game did not exit successfully')
                finally:
                    if child.poll() is None:
                        # 只结束本测试启动器的子进程树，避免遗留测试游戏占用实例。
                        subprocess.run(['taskkill', '/PID', str(child.pid), '/T', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            results.append(f'public-menu-{index}: ' + checkpoint.read_text(encoding='utf-8').strip())
            if launch != command and str(bundle / 'runtime') not in checkpoint.read_text(encoding='utf-8'):
                raise AssertionError('Bundled runtime did not take precedence')
        if (instance / 'mods/acbric-api.jar').exists(): raise AssertionError('Core was copied into mods')
        if any((run / 'appdata').iterdir()): raise AssertionError('Global APPDATA fallback')
        logs = list((instance / 'logs/acbric/launcher').glob('*.log'))
        if len(logs) != 2 or not all('ENTERING_MAIN' in p.read_text(encoding='utf-8') or 'Loading Airships' in p.read_text(encoding='utf-8') for p in logs):
            raise AssertionError('Missing captured child logs')
        core = bundle / 'core/acbric-api.jar'
        original = core.read_bytes()
        try:
            core.write_bytes(original + b'corruption')
            if 'BUNDLE_CHANGED' not in execute('changed-core', command, 2): raise AssertionError('Changed core accepted')
        finally: core.write_bytes(original)
        bad = command.copy(); bad[bad.index('-GameDir') + 1] = str(run / 'missing-game')
        if 'INSTALL_NOT_FOUND' not in execute('missing-game', bad, 2): raise AssertionError('Missing install accepted')
        template = bundle / 'acbric-mod-template'
        (template / 'local.properties').write_text('gameInstallDir=' + install.as_posix() + '\nframeworkDir=' + bundle.as_posix() + '\ninstanceDir=' + instance.as_posix() + '\n', encoding='utf-8')
        execute('independent-template', [str(args.java_home.resolve() / 'bin/java.exe'), '-cp', str(template / 'gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain', '-p', str(template), 'build'])
        if (template / 'libs').exists(): raise AssertionError('Template copied game dependencies')
        summary.update(status='PASS', scenarios=results, logs=[str(p.relative_to(run)) for p in logs],
            limits='Actual production launcher and Main; test MOD observes 30 menu frames then exits. No child JVM write/network guard; isolated environment and before/after installation hashes. No full campaign in this harness.')
    finally:
        after = snapshot(install)
        changes = {'added': sorted(after.keys() - before.keys()), 'removed': sorted(before.keys() - after.keys()),
                   'modified': sorted(k for k in before.keys() & after.keys() if before[k] != after[k])}
        summary['installationChanges'] = changes
        summary['installationFileCount'] = len(before)
        if any(changes.values()): summary['status'] = 'FAILED_INSTALL_CHANGED'
        (run / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding='utf-8')
        if any(changes.values()): raise AssertionError(changes)
    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == '__main__': main()
