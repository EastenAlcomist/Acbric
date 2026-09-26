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
    parser.add_argument('--installer', action='store_true', help='Create and rebind an instance through setup, then use its saved launcher')
    parser.add_argument('--upgrade-from', type=Path, help='Extract this previous release, upgrade with the current ZIP, then restore and launch it again')
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    if not args.tag.isalnum(): parser.error('Use a fresh alphanumeric tag')
    run = project / 'build/external-release-tests' / args.tag
    if run.exists(): parser.error('Test directory exists')
    run.mkdir(parents=True)
    install = args.game_dir.resolve()
    before = snapshot(install)
    bundle_zip = project / 'build/external-dist/Acbric-external.zip'
    unpack = run / '中文 & 空格发行'
    if args.upgrade_from and not args.installer: parser.error('--upgrade-from requires --installer')
    with zipfile.ZipFile(args.upgrade_from or bundle_zip) as archive: archive.extractall(unpack)
    bundle = unpack / 'Acbric'
    instance = run / '独立 中文实例'
    # Fresh root entry must explain setup requirements instead of guessing an instance.
    fresh = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start-configured.ps1')], capture_output=True, timeout=30)
    (run / 'unconfigured-entry.log').write_bytes(fresh.stdout + fresh.stderr)
    if fresh.returncode != 2 or b'SETUP_REQUIRED' not in fresh.stdout: raise AssertionError('Missing first-run setup instruction')
    if args.installer:
        setup_log = run / 'setup.log'
        with setup_log.open('w', encoding='utf-8') as output:
            result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'setup.ps1'),
                '-GameDir', str(install), '-InstanceDir', str(instance)], stdout=output, stderr=subprocess.STDOUT, timeout=60)
        if result.returncode != 0: raise AssertionError('Installer failed; see setup.log')
    mods = bundle / 'mods'
    mods.mkdir(parents=True, exist_ok=True)
    instance.mkdir(parents=True, exist_ok=True)
    (instance / 'config').mkdir(exist_ok=True)
    (instance / 'config/launch-settings.json').write_text(json.dumps({'useCustomWindow': True, 'customWindowW': 960,
        'customWindowH': 640, 'customWindowFullscreen': False, 'customWindowFullscreenWindow': False, 'customWindowBorderless': False}), encoding='utf-8')
    name = 'net/fabricacs/regression/fixtures/PublicLaunchFixture'
    with zipfile.ZipFile(mods / 'public-test.jar', 'w') as jar:
        jar.write(project / f'build/classes/java/regressionTest/{name}.class', name + '.class')
        jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'public_launch_test', 'version': '1', 'mixins': ['public-test.mixins.json']}))
        jar.writestr('acbric_vanilla/info.json', json.dumps({'id': 'public_launch_test', 'name': 'Bundled shared test'}))
        jar.writestr('public-test.mixins.json', json.dumps({'required': True, 'package': 'net.fabricacs.regression.fixtures',
            'compatibilityLevel': 'JAVA_21', 'mixins': ['PublicLaunchFixture'], 'injectors': {'defaultRequire': 1}}))
    (mods / 'native_test').mkdir()
    (mods / 'native_test/info.json').write_text(json.dumps({'id': 'native_test', 'name': 'Shared native test'}), encoding='utf-8')
    (instance / 'userdata/mods/old_path_test').mkdir(parents=True, exist_ok=True)
    (instance / 'userdata/mods/old_path_test/info.json').write_text(json.dumps({'id': 'old_path_test', 'name': 'Old path ignored'}), encoding='utf-8')
    if args.arc_jar: shutil.copy2(args.arc_jar, mods / 'arc.jar')
    (instance / 'userdata/prefs.json').write_text(json.dumps({'language': 'en', 'phoneHomeWithErrors': False,
        **{'mod_known_' + name: True for name in ['native_test', 'public_launch_test', 'installed_native', 'arc_overhaul']}}), encoding='utf-8')
    for directory in ['cwd', 'appdata', 'localappdata', 'home', 'tmp']: (run / directory).mkdir()
    env = dict(os.environ, APPDATA=str(run / 'appdata'), LOCALAPPDATA=str(run / 'localappdata'), USERPROFILE=str(run / 'home'),
        TEMP=str(run / 'tmp'), TMP=str(run / 'tmp'), JAVA_HOME=str(args.java_home.resolve()))
    for key in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']: env.pop(key, None)
    startup = subprocess.STARTUPINFO(); startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW; startup.wShowWindow = subprocess.SW_HIDE
    command = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start.ps1'),
        '-GameDir', str(install), '-InstanceDir', str(instance), '-JavaHome', str(args.java_home.resolve())]
    if args.installer:
        (run / 'invoke-entry.ps1').write_text('param([string]$Entry)\n& $Entry\nexit $LASTEXITCODE\n', encoding='utf-8-sig')
        command = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start-configured.ps1')]
    results = []
    def execute(label, cmd, expected=0):
        with (run / (label + '.log')).open('w', encoding='utf-8') as out:
            result = subprocess.run(cmd, cwd=run / 'cwd', env=env, stdout=out, stderr=subprocess.STDOUT, startupinfo=startup, timeout=90)
        if result.returncode != expected: raise AssertionError(f'{label} exit={result.returncode}; see log')
        results.append(label)
        return (run / (label + '.log')).read_text(encoding='utf-8', errors='replace')
    summary = {'status': 'FAILED', 'packageSha256': digest(bundle_zip)}
    updater = project / 'build/external-dist/Acbric/update.ps1'
    def update_command(restore=False):
        return ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(updater), '-TargetDir', str(bundle),
                *(['-Restore'] if restore else ['-PackagePath', str(bundle_zip)])]
    try:
        if args.upgrade_from:
            saved_user = snapshot(instance); saved_mods = snapshot(mods)
            binding_bytes = (bundle / '.acbric-active-instance.json').read_bytes()
            if 'UPDATE_OK' not in execute('update-previous-release', update_command()): raise AssertionError('Upgrade did not complete')
            if snapshot(instance) != saved_user or snapshot(mods) != saved_mods or (bundle / '.acbric-active-instance.json').read_bytes() != binding_bytes:
                raise AssertionError('Update modified player files')
            summary['baselineSha256'] = digest(args.upgrade_from)
        results.append('unconfigured-entry-diagnosed')
        if args.installer:
            results.append('setup-created-instance')
            binding = bundle / '.acbric-active-instance.json'
            saved_binding = binding.read_bytes()
            try:
                binding.write_text('{broken', encoding='utf-8')
                if 'LAUNCH_CONFIG_INVALID' not in execute('broken-root-binding', command, 2): raise AssertionError('Bad root config accepted')
                binding.write_text(json.dumps({'schema': '1', 'instanceDir': str(run / 'missing-instance')}), encoding='utf-8')
                if 'INSTANCE_NOT_FOUND' not in execute('missing-root-instance', command, 2): raise AssertionError('Missing instance accepted')
            finally: binding.write_bytes(saved_binding)
        for index in range(3 if args.upgrade_from else 2):
            if index == 2:
                saved_user = snapshot(instance); saved_mods = snapshot(mods)
                if 'RESTORED' not in execute('restore-previous-release', update_command(True)): raise AssertionError('Restore did not complete')
                with zipfile.ZipFile(args.upgrade_from) as old:
                    for item in old.infolist():
                        if not item.is_dir() and item.filename != 'Acbric/mods/README.md' and (bundle / item.filename[7:]).read_bytes() != old.read(item):
                            raise AssertionError('Restored release differs: ' + item.filename)
                if snapshot(instance) != saved_user or snapshot(mods) != saved_mods: raise AssertionError('Restore changed player data')
            if args.installer and index == 1:
                relocated = run / '重定位 & 框架' / 'Acbric'
                relocated.parent.mkdir()
                # Only move the exact freshly extracted bundle inside this test workspace.
                if not bundle.resolve().is_relative_to(run.resolve()) or not relocated.resolve().is_relative_to(run.resolve()): raise AssertionError('Unsafe test relocation')
                if not mods.resolve().is_relative_to(run.resolve()): raise AssertionError('Unsafe MOD relocation')
                bundle.rename(relocated)
                bundle = relocated; mods = relocated / 'mods'
                command = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start-configured.ps1')]
                if 'FRAMEWORK_MOVED' not in execute('moved-framework-diagnosed', command, 2): raise AssertionError('Missing relocation diagnosis')
                execute('rebind-instance', ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'setup.ps1'),
                    '-GameDir', str(install), '-InstanceDir', str(instance), '-Language', 'en'])
                if not (mods / 'public-test.jar').is_file(): raise AssertionError('Rebinding lost MOD')
            checkpoint = instance / 'public-checkpoint.txt'
            checkpoint.unlink(missing_ok=True)
            (instance / 'allow-test-exit').unlink(missing_ok=True)
            with (run / f'public-{index}.log').open('w', encoding='utf-8') as out:
                launch = command[:-2] if not args.installer and index == 1 and (bundle / 'runtime/bin/java.exe').is_file() else command
                if args.installer:
                    launch = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(run / 'invoke-entry.ps1'), '-Entry', str(bundle / 'Start Acbric.cmd')]
                child = subprocess.Popen(launch, cwd=run / 'cwd', env=env, stdout=out, stderr=subprocess.STDOUT, startupinfo=startup)
                try:
                    deadline = time.monotonic() + 90
                    while not checkpoint.exists():
                        if child.poll() is not None or time.monotonic() > deadline: raise AssertionError('Public menu did not reach checkpoint')
                        time.sleep(.25)
                    if index == 0:
                        if args.upgrade_from and 'FRAMEWORK_BUSY' not in execute('update-running-game-refused', update_command(), 2):
                            raise AssertionError('Update allowed while game running')
                        text = execute('busy-instance', command, 2)
                        if 'INSTANCE_BUSY' not in text: raise AssertionError('Missing busy diagnosis')
                        other = run / 'other-instance'
                        busy_command = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start.ps1'),
                            '-GameDir', str(install), '-InstanceDir', str(other)]
                        if 'MODS_BUSY' not in execute('busy-shared-mods', busy_command, 2): raise AssertionError('Missing shared MOD lock')
                    (instance / 'allow-test-exit').write_text('test completed', encoding='utf-8')
                    if child.wait(timeout=20) != 0: raise AssertionError('Public game did not exit successfully')
                finally:
                    if child.poll() is None:
                        # 只结束本测试启动器的子进程树，避免遗留测试游戏占用实例。
                        subprocess.run(['taskkill', '/PID', str(child.pid), '/T', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            results.append(f'public-menu-{index}: ' + checkpoint.read_text(encoding='utf-8').strip())
            if launch != command and str(bundle / 'runtime') not in checkpoint.read_text(encoding='utf-8'):
                raise AssertionError('Bundled runtime did not take precedence')
        if (mods / 'acbric-api.jar').exists(): raise AssertionError('Core was copied into mods')
        if (instance / 'mods').exists(): raise AssertionError('Unused instance mods folder created')
        if any((run / 'appdata').iterdir()): raise AssertionError('Global APPDATA fallback')
        logs = list((instance / 'logs/acbric/launcher').glob('*.log'))
        if len(logs) != (3 if args.upgrade_from else 2) or not all('ENTERING_MAIN' in p.read_text(encoding='utf-8') or 'Loading Airships' in p.read_text(encoding='utf-8') for p in logs):
            raise AssertionError('Missing captured child logs')
        if args.upgrade_from:
            if 'UPDATE_OK' not in execute('reapply-update', update_command()): raise AssertionError('Reapplying update failed')
        core = bundle / 'core/acbric-api.jar'
        original = core.read_bytes()
        try:
            core.write_bytes(original + b'corruption')
            if 'BUNDLE_CHANGED' not in execute('changed-core', command, 2): raise AssertionError('Changed core accepted')
        finally: core.write_bytes(original)
        bad = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(bundle / 'start.ps1'), '-GameDir', str(run / 'missing-game'), '-InstanceDir', str(instance)]
        if 'INSTALL_NOT_FOUND' not in execute('missing-game', bad, 2): raise AssertionError('Missing install accepted')
        template = bundle / 'acbric-mod-template'
        (template / 'local.properties').write_text('gameInstallDir=' + install.as_posix() + '\nframeworkDir=' + bundle.as_posix() + '\ninstanceDir=' + instance.as_posix() + '\n', encoding='utf-8')
        execute('independent-template', [str(args.java_home.resolve() / 'bin/java.exe'), '-cp', str(template / 'gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain', '-p', str(template), 'build'])
        execute('template-install', [str(args.java_home.resolve() / 'bin/java.exe'), '-cp', str(template / 'gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain', '-p', str(template), 'installMod'])
        if not (mods / 'acbric-template-mod.jar').is_file() or (instance / 'mods').exists(): raise AssertionError('Template installed to wrong MOD root')
        if (template / 'libs').exists(): raise AssertionError('Template copied game dependencies')
        summary.update(status='PASS', scenarios=results, logs=[str(p.relative_to(run)) for p in logs],
            limits='Actual production launcher and Main; optional installer uses setup CLI and saved entry, GUI behavior covered separately; test MOD observes 30 menu frames then exits. No child JVM write/network guard; isolated environment and before/after installation hashes. No full campaign in this harness.')
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
