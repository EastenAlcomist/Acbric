# 玩家维护交接：在临时目录等待启动器退出，再调用原有更新/恢复事务；日志保留，默认只显示可读结果。
param([Parameter(Mandatory=$true)][string]$Job)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$zh = $true
$logRoot = Join-Path $env:LOCALAPPDATA 'Acbric/launcher-logs'
[IO.Directory]::CreateDirectory($logRoot) | Out-Null
$log = Join-Path $logRoot ('maintenance-' + [guid]::NewGuid().ToString('N') + '.log')
$plan = $null
try {
    $request = Get-Content -LiteralPath (Join-Path $Job 'request.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $zh = $request.language -eq 'zh'
    . (Join-Path $Job 'maintenance.ps1')
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while (Get-Process -Id $request.parent -ErrorAction SilentlyContinue) {
        if ($watch.Elapsed.TotalSeconds -gt 120) { throw 'LAUNCHER_BUSY / Launcher has not exited' }
        Start-Sleep -Milliseconds 200
    }
    if ($request.restore) { $result = Invoke-FrameworkRestore $request.target }
    else {
        $plan = New-UpdatePlan $request.target $request.archive
        $result = Invoke-FrameworkUpdate $plan
    }
    $result | Out-File -LiteralPath $log -Encoding utf8
    $exe = Join-Path $request.target 'Acbric.exe'
    if (Test-Path -LiteralPath $exe -PathType Leaf) {
        Start-Process -FilePath $exe -WorkingDirectory $request.target -WindowStyle Hidden
    } else {
        $message = if ($zh) { '已恢复旧版框架。请使用旧版的 Start Acbric.cmd 启动。' } else { 'The previous framework was restored. Use its Start Acbric.cmd to launch.' }
        [Windows.Forms.MessageBox]::Show($message, 'Acbric') | Out-Null
    }
} catch {
    ($_ | Out-String) | Out-File -LiteralPath $log -Encoding utf8
    $message = if ($zh) { '维护没有完成。原有文件受到保护；请重新打开 Acbric，从帮助中导出诊断信息。' } else { 'Maintenance could not finish. Existing files are protected. Reopen Acbric and export diagnostics from Help.' }
    [Windows.Forms.MessageBox]::Show($message, 'Acbric') | Out-Null
} finally { if ($null -ne $plan) { Remove-UpdateStage $plan.Stage } }
