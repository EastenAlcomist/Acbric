# Saved instance launcher / 已保存实例的启动入口，不将路径拼接为可执行代码。
$ErrorActionPreference = 'Stop'
try {
    $folder = Split-Path -Parent $MyInvocation.MyCommand.Path
    $config = Get-Content -LiteralPath (Join-Path $folder 'instance.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($config.schema -ne '1' -or $config.gameDir -isnot [string] -or $config.frameworkDir -isnot [string] -or $config.javaHome -isnot [string]) { throw 'INSTANCE_CONFIG_INVALID / 实例配置无效' }
    $instance = Split-Path -Parent $folder
    $script = Join-Path $config.frameworkDir 'start.ps1'
    if (-not (Test-Path -LiteralPath $script -PathType Leaf)) { throw 'FRAMEWORK_MOVED / 找不到框架，请在新框架包中运行 Setup.cmd，读取此实例后重新检查保存' }
    $arguments = @{ GameDir = $config.gameDir; InstanceDir = $instance }
    if ($config.javaHome) { $arguments.JavaHome = $config.javaHome }
    & $script @arguments
    exit $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message
    exit 2
}
