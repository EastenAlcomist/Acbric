# Root launcher / 与 Setup 同层的日常启动入口；路径只作为数据传递。
$ErrorActionPreference = 'Stop'
try {
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.acbric-maintenance/pending.json')) { throw 'UPDATE_RECOVERY_REQUIRED / Run Update Acbric.cmd to restore / 更新未完成，请运行 Update Acbric.cmd 恢复' }
    $folder = [IO.Path]::GetFullPath($PSScriptRoot)
    $bindingFile = Join-Path $folder '.acbric-active-instance.json'
    if (-not (Test-Path -LiteralPath $bindingFile -PathType Leaf)) {
        throw 'SETUP_REQUIRED / Run Setup.cmd and save an instance first / 请先运行同目录的 Setup.cmd 并保存配置'
    }
    try {
        $binding = Get-Content -LiteralPath $bindingFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($binding.schema -ne '1' -or $binding.instanceDir -isnot [string] -or [string]::IsNullOrWhiteSpace($binding.instanceDir)) { throw 'Invalid fields' }
        if ([IO.Path]::IsPathRooted($binding.instanceDir)) {
            $instance = [IO.Path]::GetFullPath($binding.instanceDir)
        } else {
            $instance = [IO.Path]::GetFullPath((Join-Path $folder $binding.instanceDir))
            if (-not $instance.StartsWith($folder + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid relative path' }
        }
    } catch { throw 'LAUNCH_CONFIG_INVALID / Launch configuration is invalid; preserve it and run Setup.cmd / 启动配置无效，请保留文件并检查后重新配置' }
    $configFile = Join-Path $instance 'acbric-launcher/instance.json'
    if (-not (Test-Path -LiteralPath $configFile -PathType Leaf)) { throw 'INSTANCE_NOT_FOUND / Instance moved or missing; select it again in Setup.cmd / 实例已移动或缺失，请在 Setup.cmd 重新选择' }
    try {
        $config = Get-Content -LiteralPath $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($config.schema -ne '1' -or $config.gameDir -isnot [string] -or [string]::IsNullOrWhiteSpace($config.gameDir) -or $config.frameworkDir -isnot [string] -or $config.javaHome -isnot [string]) { throw 'Invalid fields' }
    } catch { throw 'INSTANCE_CONFIG_INVALID / Invalid instance configuration / 实例配置无效，请保留文件并检查' }
    if ([IO.Path]::GetFullPath($config.frameworkDir) -ne $folder) { throw 'FRAMEWORK_MOVED / Run Setup.cmd here, load this instance, check and save / 请运行当前位置的 Setup.cmd，读取此实例并重新检查保存' }
    $arguments = @{ GameDir = $config.gameDir; InstanceDir = $instance }
    if ($config.javaHome) { $arguments.JavaHome = $config.javaHome }
    & (Join-Path $folder 'start.ps1') @arguments
    exit $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message
    exit 2
}
