# 更新器不使用 Java，避免自身占用待替换的框架 JAR/运行时；只读取玩家选择的本地发行包。
param([string]$TargetDir, [string]$PackagePath, [switch]$Restore, [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'maintenance.ps1')
$interactive = -not $TargetDir -and -not $PackagePath -and -not $Restore -and -not $CheckOnly
$log = Join-Path ([IO.Path]::GetTempPath()) ('acbric-update-' + [guid]::NewGuid().ToString('N') + '.log')
$plan = $null
try {
    'Acbric update / 框架更新' | Out-File -LiteralPath $log -Encoding utf8
    if ($interactive) {
        Add-Type -AssemblyName System.Windows.Forms
        [Windows.Forms.Application]::EnableVisualStyles()
        $folder = New-Object Windows.Forms.FolderBrowserDialog
        $folder.Description = '选择需要更新的 Acbric 文件夹 / Select the existing Acbric folder'
        $folder.SelectedPath = $PSScriptRoot
        try { if ($folder.ShowDialog() -ne 'OK') { exit 0 }; $TargetDir = $folder.SelectedPath } finally { $folder.Dispose() }
        if (Read-Pointer $TargetDir 'pending') {
            $answer = [Windows.Forms.MessageBox]::Show("上次更新未完成，需要先恢复。现在恢复？`nAn update was interrupted. Restore it now?", 'Acbric', 'YesNo', 'Question')
            if ($answer -ne 'Yes') { exit 0 }; $Restore = $true
        } else {
            $answer = [Windows.Forms.MessageBox]::Show("是：选择本地 ZIP 更新框架`n否：恢复上一次更新前的框架`n取消：退出`n`nYes: update from a local ZIP`nNo: restore the previous framework`nCancel: exit", 'Acbric', 'YesNoCancel', 'Question')
            if ($answer -eq 'Cancel') { exit 0 }
            $Restore = $answer -eq 'No'
        }
        if (-not $Restore) {
            $chooser = New-Object Windows.Forms.OpenFileDialog
            $chooser.Title = '选择 Acbric 发行 ZIP / Select Acbric release ZIP'; $chooser.Filter = 'Acbric ZIP (*.zip)|*.zip'; $chooser.CheckFileExists = $true
            try { if ($chooser.ShowDialog() -ne 'OK') { exit 0 }; $PackagePath = $chooser.FileName } finally { $chooser.Dispose() }
        }
    }
    if (-not $TargetDir) { $TargetDir = $PSScriptRoot }
    if ($Restore) {
        if ($PackagePath -or $CheckOnly) { throw 'USAGE / Restore cannot be combined with a package or CheckOnly / 恢复不能同时指定更新包或检查模式' }
        $result = Invoke-FrameworkRestore $TargetDir
    } else {
        if (-not $PackagePath) { throw 'PACKAGE_REQUIRED / Select a local release ZIP / 请选择本地发行 ZIP' }
        $plan = New-UpdatePlan $TargetDir $PackagePath
        $description = "目标 / Target: $($plan.Target)`n版本 / Version: $($plan.OldVersion) -> $($plan.NewVersion)`n保留 MOD、实例、存档与设置。`nMODs, instances, saves and settings are preserved."
        $description | Out-File -LiteralPath $log -Append -Encoding utf8
        if ($CheckOnly) { $result = "CHECK_OK / 更新包检查通过`n$description" }
        else {
            if ($interactive -and [Windows.Forms.MessageBox]::Show("$description`n`n现在更新？ / Update now?", 'Acbric', 'YesNo', 'Question') -ne 'Yes') { exit 0 }
            $result = Invoke-FrameworkUpdate $plan
        }
    }
    $result | Out-File -LiteralPath $log -Append -Encoding utf8
    Write-Host $result
    Write-Host "Log / 日志: $log"
    if ($interactive) { [Windows.Forms.MessageBox]::Show("$result`n`n日志 / Log: $log", 'Acbric', 'OK', 'Information') | Out-Null }
    exit 0
} catch {
    $message = $_.Exception.Message
    ($_ | Out-String) | Out-File -LiteralPath $log -Append -Encoding utf8
    Write-Host $message; Write-Host "Log / 日志: $log"
    if ($interactive) { [Windows.Forms.MessageBox]::Show("$message`n`n日志 / Log: $log", 'Acbric', 'OK', 'Error') | Out-Null }
    exit 2
} finally {
    if ($null -ne $plan) { Remove-UpdateStage $plan.Stage }
}
