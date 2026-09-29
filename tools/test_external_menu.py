"""Windows/JDK 21 菜单、战役或媒体检查；隔离写入/网络，不替换 GPU、世界生成或 GIF 编码。"""
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
    parser.add_argument('--campaign', action='store_true', help='Create and save a real world, then load in a new process')
    parser.add_argument('--arc-jar', type=Path, help='Optional ARC build for campaign tests; no ARC source dependency')
    parser.add_argument('--media', action='store_true', help='Real GIF export and installed heroes/native MOD reload checks')
    parser.add_argument('--media-part', choices=['all', 'gif'], default='all', help='Isolate GIF diagnostics from resource reload checks')
    parser.add_argument('--strict-gl', action='store_true', help='Abort media checks on native GL errors; normal game settings otherwise')
    parser.add_argument('--legacy-control', action='store_true', help='Media GIF control on an isolated copied legacy layout')
    args = parser.parse_args()
    if args.legacy_control and (not args.media or args.media_part != 'gif'): parser.error('Legacy control requires --media --media-part gif')
    if args.media and args.campaign: parser.error('--media and --campaign are mutually exclusive')
    if os.name != 'nt' or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]*', args.tag):
        parser.error('Windows and a fresh simple tag required')
    if args.arc_jar and (not args.campaign or not args.arc_jar.is_file()): parser.error('--arc-jar requires --campaign and an existing JAR')
    if args.campaign and args.runs != 2: parser.error('Campaign testing requires create and restart/load phases')
    project = Path(__file__).resolve().parents[1]
    install = args.game_dir.resolve()
    run = project / ('build/external-media' if args.media else 'build/external-campaign' if args.campaign else 'build/external-menu') / args.tag
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
    if args.arc_jar: summary['arcSha256'] = digest(args.arc_jar)
    try:
        with (run / 'preflight.log').open('w', encoding='utf-8') as log:
            subprocess.run(base + ['-cp', str(loader / '*'), 'net.fabricacs.acbric.ExternalPreflight',
                '--game-dir', str(install), '--instance-dir', str(instance)], cwd=run / 'cwd', env=env,
                stdout=log, stderr=subprocess.STDOUT, timeout=60, check=True, startupinfo=startup)
        (instance / 'config/launch-settings.json').write_text(json.dumps({'useCustomWindow': True,
            'customWindowW': 960, 'customWindowH': 640, 'customWindowFullscreen': False,
            'customWindowFullscreenWindow': False, 'customWindowBorderless': False}), encoding='utf-8')
        control_install = install
        if args.legacy_control:
            for name in ['data', 'default_ships', 'default_landships', 'default_buildings', 'expansions', 'lib']:
                shutil.copytree(install / name, instance / name, dirs_exist_ok=True)
            shutil.copy2(install / 'Airships.json', instance / 'Airships.json')
            control_libs = run / 'libs'
            control_libs.mkdir()
            for file in list((install / 'lib').glob('*.jar')) + list(install.glob('asplit-*.zip')):
                shutil.copy2(file, control_libs / file.name)
            shutil.copytree(install / 'lib/native', control_libs / 'native')
            settings = json.loads((install / 'launch_settings.json').read_text(encoding='utf-8'))
            settings.update(json.loads((instance / 'config/launch-settings.json').read_text(encoding='utf-8')))
            settings.update(customDataDirectoryLocation=str(instance / 'userdata'), customGIFSaveDirectoryLocation=str(instance / 'userdata/gifs'))
            (instance / 'launch_settings.json').write_text(json.dumps(settings), encoding='utf-8')
            control_install = instance
        shutil.copy2(api, instance / 'mods/acbric-api.jar')
        if args.arc_jar:
            shutil.copy2(args.arc_jar, instance / 'mods/arc.jar')
            (instance / 'config/arc_overhaul').mkdir()
            (instance / 'config/arc_overhaul/conquest-start.json').write_text(json.dumps({'format': 1, 'version': 1,
                'data': {'cities': 3, 'towns': 3, 'cash': 12345}}), encoding='utf-8')
        with zipfile.ZipFile(run / 'guard.jar', 'w') as jar:
            jar.write(classes / (package + 'SmokeGuard.class'), package + 'SmokeGuard.class')
        with zipfile.ZipFile(instance / 'mods/menu-probe.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
            names = ['ExternalRuntimeProbe', 'ExternalMenuProbe', 'fixtures/ExternalMenuFixture']
            entries = ['net.fabricacs.regression.ExternalRuntimeProbe']
            mixins = ['ExternalMenuFixture']
            if args.campaign:
                names += ['ExternalCampaignProbe', 'fixtures/ExternalCampaignFixture', 'fixtures/ExternalCampaignFixture$Setup', 'fixtures/ExternalCampaignFixture$World']
                entries += ['net.fabricacs.regression.ExternalCampaignProbe']
                mixins += ['ExternalCampaignFixture$Setup', 'ExternalCampaignFixture$World']
            if args.media:
                names += ['ExternalMediaProbe', 'fixtures/ExternalMediaFixture', 'fixtures/ExternalMediaFixture$Mods', 'fixtures/ExternalMediaFixture$Replay', 'fixtures/ExternalMediaFixture$Tick']
                entries += ['net.fabricacs.regression.ExternalMediaProbe']
                if args.legacy_control: entries = ['net.fabricacs.regression.ExternalMediaProbe']
                mixins += ['ExternalMediaFixture$Mods', 'ExternalMediaFixture$Replay', 'ExternalMediaFixture$Tick']
            for name in names:
                jar.write(classes / (package + name + '.class'), package + name + '.class')
            jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'external_probe', 'version': '1',
                'entrypoints': {'acbric': entries}, 'mixins': ['menu-fixture.mixins.json']}))
            jar.writestr('menu-fixture.mixins.json', json.dumps({'required': True, 'package': 'net.fabricacs.regression.fixtures',
                'compatibilityLevel': 'JAVA_21', 'mixins': mixins, 'injectors': {'defaultRequire': 1}}))
            jar.writestr('acbric_vanilla/info.json', json.dumps({'id': 'external_probe', 'name': 'External menu probe'}))
            jar.writestr('acbric_vanilla/marker.txt', 'external instance only')
        cmd = base + [f'-Dacbric.external.install={install}', f'-Dacbric.external.instance={instance}',
            '-Dacbric.internal.externalProbeMain=net.fabricacs.regression.ExternalRuntimeProbe',
            f'-Dacbric.test.media={str(args.media).lower()}', f'-Dacbric.test.media.part={args.media_part}', f'-Dorg.lwjgl.util.Debug={str(args.strict_gl or not args.media).lower()}', f'-Dacbric.test.strictGL={str(args.strict_gl).lower()}', '-Dacbric.test.menu=true', f'-Dacbric.test.root={run}', '-Djava.security.manager=net.fabricacs.regression.SmokeGuard',
            '--add-opens=java.base/java.util=ALL-UNNAMED', '-cp', str(loader / '*') + ';' + str(run / 'guard.jar'),
            'net.fabricmc.loader.impl.launch.knot.KnotClient']
        if args.legacy_control:
            cmd = [c for c in cmd if not c.startswith(('-Dacbric.external.', '-Dacbric.internal.externalProbeMain='))]
            cmd[1:1] = [f'-Dacbric.test.instance={instance}', f'-Dacbric.test.install={control_install}', '-Dacbric.test.legacy=true', '-Ddev=true', '-Dsteam=false']
        summary['legacyControl'] = args.legacy_control
        summary['strictGL'] = args.strict_gl if args.media else None
        summary['runs'] = []
        for index in range(args.runs):
            previous_gifs = {f.name: digest(f) for f in (instance / 'userdata/gifs').glob('*.gif')} if args.media else {}
            log_file = run / ('menu.log' if index == 0 else 'menu-restart.log')
            phase = 'create' if index == 0 else 'load'
            checkpoint = instance / ('media-checkpoint.json' if args.media else f'campaign-{phase}.json' if args.campaign else 'menu-checkpoint.txt')
            checkpoint.unlink(missing_ok=True)
            phase_cmd = cmd
            if args.campaign:
                phase_cmd = cmd[:1] + [f'-Dacbric.test.campaign={phase}', f'-Dacbric.test.arc={str(bool(args.arc_jar)).lower()}'] + cmd[1:]
                if index == 1 and args.arc_jar:
                    (instance / 'config/arc_overhaul/conquest-start.json').write_text(json.dumps({'format': 1, 'version': 1,
                        'data': {'cities': 1, 'towns': 0, 'cash': 999}}), encoding='utf-8')
            with log_file.open('w', encoding='utf-8') as log:
                result = subprocess.run(phase_cmd, cwd=instance if args.legacy_control else run / 'cwd', env=env, stdout=log, stderr=subprocess.STDOUT,
                                        timeout=240 if args.campaign or args.media else 90, startupinfo=startup)
            text = log_file.read_text(encoding='utf-8')
            native_log = instance / 'userdata/log.txt'
            if native_log.is_file():
                shutil.copy2(native_log, run / f'native-{index}.log')
                text += native_log.read_text(encoding='utf-8', errors='replace')
            if result.returncode != 0 or not checkpoint.is_file() or 'SMOKE_WRITE_DENIED' in text:
                raise RuntimeError(f'External test failed: {log_file}\n{text[-8000:]}')
            result = {'exit': result.returncode, 'checkpoint': checkpoint.read_text(encoding='utf-8'),
                      'rawCacheFiles': len(list((instance / 'cache').rglob('*.tex')))}
            if args.media:
                gifs = {f.name: digest(f) for f in (instance / 'userdata/gifs').glob('*.gif')}
                if len(gifs) != len(previous_gifs) + 2 or any(gifs.get(k) != v for k, v in previous_gifs.items()):
                    raise AssertionError('Expected two new GIFs without overwriting earlier exports')
                result['gifs'] = gifs
            summary['runs'].append(result)
            print(f'External run {index + 1} passed / 外部测试第 {index + 1} 次通过', flush=True)
        summary['status'] = 'PASS_WITH_NATIVE_GL_ERRORS' if args.media and any(json.loads(r['checkpoint'])['glErrors'] for r in summary['runs']) else 'PASS'
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
