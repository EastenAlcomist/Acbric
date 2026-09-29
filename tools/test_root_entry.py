"""隔离验证根启动脚本的相对/绝对实例选择与特殊字符传参；启动终点为记录参数的测试替身。"""
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    project = Path(__file__).resolve().parents[1]
    parent = project / 'build/root-entry-tests'
    parent.mkdir(parents=True, exist_ok=True)
    root = Path(tempfile.mkdtemp(prefix='中文 & ', dir=parent))
    bundle = root / 'Acbric'
    bundle.mkdir()
    for name in ['Start Acbric.cmd', 'start-configured.ps1']:
        shutil.copy2(project / 'src/dist' / name, bundle / name)
    (bundle / 'start.ps1').write_text(
        'param([string]$GameDir,[string]$InstanceDir,[string]$JavaHome)\n'
        '@{ game=$GameDir; instance=$InstanceDir; java=$JavaHome } | ConvertTo-Json | '
        'Set-Content -LiteralPath (Join-Path $PSScriptRoot "received.json") -Encoding UTF8\nexit 0\n', encoding='utf-8-sig')
    wrapper = root / 'invoke.ps1'
    wrapper.write_text('param([string]$Entry)\n& $Entry\nexit $LASTEXITCODE\n', encoding='utf-8-sig')
    cases = [('relative', bundle / 'instances/默认 & 实例'), ('absolute', root / '外置 & 实例')]
    for label, instance in cases:
        folder = instance / 'acbric-launcher'
        folder.mkdir(parents=True)
        game = str(root / '游戏 & 路径')
        java = str(root / 'Java & 路径')
        (folder / 'instance.json').write_text(json.dumps({'schema': '1', 'gameDir': game,
            'frameworkDir': str(bundle), 'javaHome': java, 'language': 'zh'}), encoding='utf-8')
        path = str(instance.relative_to(bundle)) if label == 'relative' else str(instance)
        (bundle / '.acbric-active-instance.json').write_text(json.dumps({'schema': '1', 'instanceDir': path}), encoding='utf-8')
        result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(wrapper),
            '-Entry', str(bundle / 'Start Acbric.cmd')], cwd=root, capture_output=True, timeout=20)
        (root / (label + '.log')).write_bytes(result.stdout + result.stderr)
        assert result.returncode == 0, (label, result.stdout, result.stderr)
        actual = json.loads((bundle / 'received.json').read_text(encoding='utf-8-sig'))
        assert actual == {'game': game, 'instance': str(instance), 'java': java}, actual
    summary = {'status': 'PASS', 'cases': [x[0] for x in cases],
               'limits': 'Real root CMD and PowerShell routing, test endpoint only; production JVM launch covered by test_external_release.py.'}
    (root / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary))
    print(root)


if __name__ == '__main__':
    main()
