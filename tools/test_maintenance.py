"""隔离验证真实 PowerShell 更新事务；所有输入仅为生成的小型发行夹具，不含游戏。"""
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / 'build/maintenance-tests' / uuid.uuid4().hex
ROOT.mkdir(parents=True)
checks = []


def digest(data): return hashlib.sha256(data).hexdigest()


def release(version, extra):
    api = io.BytesIO()
    with zipfile.ZipFile(api, 'w') as z:
        z.writestr('fabric.mod.json', json.dumps({'id': 'acbric_api', 'version': version}))
    files = {'core/acbric-api.jar': api.getvalue(), 'start.ps1': b'# fixture ' + version.encode(), **extra}
    files['bundle.properties'] = f'schema=1\napi.version={version}\nsha256.core/acbric-api.jar={digest(api.getvalue())}\n'.encode()
    manifest = f'schema=1\napi.version={version}\n' + ''.join(f'sha256.{n}={digest(v)}\n' for n, v in sorted(files.items()))
    files['release-files.properties'] = manifest.encode()
    return files


OLD = release('1.0', {'docs/obsolete.md': b'old', 'mods/README.md': b'old mods instructions'})
NEW = release('2.0', {'docs/new.md': b'new', 'mods/README.md': b'new mods instructions'})
USER = {'mods/example.jar': b'player MOD', 'mods/README.md': b'player notes', 'instances/default/userdata/save.bin': b'save',
        'instances/default/config/settings.json': b'{}', '.acbric-active-instance.json': b'{"binding":"preserve"}'}


def write_files(folder, files):
    for n, b in files.items():
        p = folder / n; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(b)


def package(files):
    p = ROOT / (uuid.uuid4().hex + '.zip')
    with zipfile.ZipFile(p, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, data in files.items(): z.writestr('Acbric/' + n, data)
    return p


ZIP = package(NEW)
DRIVER = ROOT / 'invoke.ps1'
DRIVER.write_text('''param($HomeDir, $TargetDir, $PackagePath, $Action, [int]$After = 1)
$ErrorActionPreference = 'Stop'
. (Join-Path $HomeDir 'maintenance.ps1')
$plan = $null
try {
    if ($Action -eq 'restore') { Invoke-FrameworkRestore $TargetDir }
    else {
        $plan = New-UpdatePlan $TargetDir $PackagePath
        switch ($Action) {
            'update' { Invoke-FrameworkUpdate $plan }
            'fail' { Invoke-FrameworkUpdate $plan -FailureAfter $After }
            'interrupt' { Invoke-FrameworkUpdate $plan -InterruptionAfter $After }
            'busy' {
                $lock = [IO.File]::Open((Join-Path $TargetDir '.acbric-framework.lock'), 'OpenOrCreate', 'ReadWrite', 'ReadWrite')
                try { Invoke-FrameworkUpdate $plan } finally { $lock.Dispose() }
            }
            'mods-busy' {
                $lock = [IO.File]::Open((Join-Path $TargetDir 'mods/.acbric-mods.lock'), 'OpenOrCreate', 'ReadWrite', 'ReadWrite')
                try { Invoke-FrameworkUpdate $plan } finally { $lock.Dispose() }
            }
            'check' { 'CHECK_OK' }
        }
    }
    exit 0
} catch { Write-Output $_; Write-Output $_.ScriptStackTrace; exit 2 }
finally { if ($null -ne $plan) { Remove-UpdateStage $plan.Stage } }
''', encoding='utf-8-sig')


def invoke(target, action='update', archive=ZIP, expect='UPDATE_OK', after=1):
    result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(DRIVER),
                             '-HomeDir', str(REPO / 'src/dist'), '-TargetDir', str(target), '-PackagePath', str(archive),
                             '-Action', action, '-After', str(after)], capture_output=True)
    # PowerShell redirects in the active Windows code page; failure codes are ASCII.
    out = (result.stdout + result.stderr).decode('utf-8', errors='replace')
    (ROOT / f'log-{len(list(ROOT.glob("log-*"))):03d}.txt').write_text(out, encoding='utf-8')
    if expect not in out: raise AssertionError(f'{action}: expected {expect}\n{out}')
    if expect in ('UPDATE_OK', 'RESTORED', 'CHECK_OK') and result.returncode: raise AssertionError(out)
    return out


def fresh(name, files=OLD):
    p = ROOT / ('框架 & ' + name); write_files(p, files); write_files(p, USER); return p


def check(ok, label):
    if not ok: raise AssertionError(label)
    checks.append(label); print('PASS:', label, flush=True)


def preserved(p): return all((p / n).read_bytes() == data for n, data in USER.items())
def matches(p, files): return all((p / n).read_bytes() == data for n, data in files.items() if n != 'mods/README.md')
def pending(p): return p / '.acbric-maintenance/pending.json'


def run():
    p = fresh('success'); invoke(p)
    check(matches(p, NEW) and not (p / 'docs/obsolete.md').exists() and preserved(p), 'update adds/replaces/removes owned files, preserves MODs/settings/saves/default binding')
    invoke(p, 'restore', expect='RESTORED')
    check(matches(p, OLD) and not (p / 'docs/new.md').exists() and preserved(p), 'manual restore returns exact previous framework and preserves players')
    p = fresh('failure'); invoke(p, 'fail', expect='UPDATE_FAILED_RESTORED', after=3)
    check(matches(p, OLD) and not pending(p).exists() and preserved(p), 'injected mid-write failure automatically restores all previous files')
    p = fresh('interrupt'); invoke(p, 'interrupt', expect='TEST_INTERRUPTION', after=3)
    check(pending(p).exists(), 'interruption retains recovery journal')
    invoke(p, expect='UPDATE_RECOVERY_REQUIRED')
    invoke(p, 'restore', expect='RESTORED')
    check(matches(p, OLD) and preserved(p) and not pending(p).exists(), 'new process recovers interrupted update')
    p = fresh('modified'); (p / 'start.ps1').write_text('user edit')
    invoke(p, expect='LOCAL_CHANGE'); check((p / 'start.ps1').read_text() == 'user edit' and preserved(p), 'modified framework files are preserved')
    p = fresh('collision'); write_files(p, {'docs/new.md': b'private'})
    invoke(p, expect='FILE_CONFLICT'); check((p / 'docs/new.md').read_bytes() == b'private', 'unowned collision preserved')
    for action in ('busy', 'mods-busy'):
        p = fresh(action); invoke(p, action, expect='FRAMEWORK_BUSY')
        check(matches(p, OLD) and not pending(p).exists(), action + ' blocks before replacing files')
    p = fresh('restore-conflict'); invoke(p); (p / 'start.ps1').write_text('new user edit')
    invoke(p, 'restore', expect='RECOVERY_CONFLICT'); check((p / 'start.ps1').read_text() == 'new user edit', 'rollback refuses post-update edits')
    p = fresh('corrupt-backup'); invoke(p)
    id_ = json.loads((p / '.acbric-maintenance/latest.json').read_text())['id']
    (p / f'.acbric-maintenance/transactions/{id_}/old/start.ps1').write_text('broken')
    invoke(p, 'restore', expect='BACKUP_CHANGED'); check(matches(p, NEW), 'backup damage detected before restore writes')
    p = fresh('history'); invoke(p)
    third = release('3.0', {'docs/third.md': b'third'})
    invoke(p, archive=package(third)); invoke(p, 'restore', expect='RESTORED')
    check(matches(p, NEW), 'latest restore returns immediately previous version')
    invoke(p, 'restore', expect='RESTORED'); check(matches(p, OLD), 'earlier rollback history remains usable')
    p = fresh('restore-interrupted'); invoke(p)
    latest = (p / '.acbric-maintenance/latest.json').read_bytes()
    id_ = json.loads(latest)['id']
    journal_path = p / f'.acbric-maintenance/transactions/{id_}/journal.json'
    journal = json.loads(journal_path.read_text()); journal['phase'] = 'restoring'
    journal_path.write_text(json.dumps(journal), encoding='utf-8')
    pending(p).write_bytes(latest)
    (p / 'start.ps1').write_bytes(OLD['start.ps1'])
    (p / 'docs/new.md').unlink()
    invoke(p, 'restore', expect='RESTORED')
    check(matches(p, OLD) and preserved(p), 'recovery resumes a partially completed restoration')
    p = fresh('interrupted-user-edit'); invoke(p, 'interrupt', expect='TEST_INTERRUPTION', after=3)
    (p / 'start.ps1').write_text('preserve my edit')
    invoke(p, 'restore', expect='RECOVERY_CONFLICT')
    check(pending(p).exists() and (p / 'start.ps1').read_text() == 'preserve my edit', 'interrupted recovery preserves unknown edits and keeps startup blocked')
    variants = [({**NEW, 'start.ps1': b'corrupt'}, 'PACKAGE_CHANGED'),
                ({**NEW, '../escape.txt': b'escape'}, 'PACKAGE_INVALID'),
                ({**NEW, 'instances/default/userdata/save.bin': b'overwrite'}, 'PACKAGE_INVALID'),
                ({**NEW, 'START.PS1': b'case collision'}, 'PACKAGE_INVALID'),
                ({n: b for n, b in NEW.items() if n != 'release-files.properties'}, 'PACKAGE_TOO_OLD')]
    for i, (files, code) in enumerate(variants):
        p = fresh(f'invalid-{i}'); invoke(p, archive=package(files), expect=code)
        check(matches(p, OLD) and preserved(p) and not pending(p).exists(), code + f' package {i} rejected without changes')
    p = fresh('runtime', release('1.0', {'runtime/bin/java.exe': b'fixture'}))
    invoke(p, expect='RUNTIME_REQUIRED'); check((p / 'runtime/bin/java.exe').exists(), 'runtime cannot be silently removed by a minimal ZIP')
    p = ROOT / 'nonexistent'; invoke(p, 'restore', expect='NO_BACKUP'); check(not p.exists(), 'invalid restore target creates no directories')
    p = fresh('early-start'); write_files(p, {'.acbric-maintenance/pending.json': b'{}'})
    # Guards must run before Java, even without valid instance/default-binding configuration.
    for name in ('setup.ps1', 'start-configured.ps1', 'start.ps1'):
        shutil.copyfile(REPO / 'src/dist' / name, p / name)
        args = ['-GameDir', str(ROOT), '-InstanceDir', str(ROOT / 'unused')] if name == 'start.ps1' else []
        result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(p / name), *args], capture_output=True)
        check(b'UPDATE_RECOVERY_REQUIRED' in result.stdout + result.stderr and result.returncode != 0, name + ' blocks before Java bootstrap')
    (ROOT / 'result.json').write_text(json.dumps({'status': 'PASS', 'checks': checks}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'{len(checks)} checks passed: {ROOT}', flush=True)


if __name__ == '__main__': run()
