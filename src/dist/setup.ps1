# 实例配置向导：先检查 Java；无路径参数打开中英文窗口，显式路径执行配置。
param(
    [string]$GameDir,
    [string]$InstanceDir,
    [string]$JavaHome,
    [ValidateSet("zh", "en")][string]$Language = "zh"
)
$ErrorActionPreference = 'Stop'
$bootstrapLog = Join-Path ([System.IO.Path]::GetTempPath()) ('acbric-bootstrap-' + [guid]::NewGuid().ToString() + '.log')
try {
    'Acbric external launcher / 外部启动器' | Out-File -LiteralPath $bootstrapLog -Encoding utf8
    Write-Host "Bootstrap log / 引导日志: $bootstrapLog"
    $bundleRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
    $LoaderDir = Join-Path $bundleRoot 'loader-libs'
    if (-not $JavaHome) {
        if (Test-Path -LiteralPath (Join-Path $bundleRoot 'runtime/bin/java.exe')) { $JavaHome = Join-Path $bundleRoot 'runtime' }
        else { $JavaHome = $env:JAVA_HOME }
    }
    if (-not $JavaHome) { throw 'JAVA_REQUIRED: Set JAVA_HOME or -JavaHome / 请指定独立 Java 21 路径' }
    $javaExe = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) { throw "JAVA_MISSING / Java 不存在: $javaExe" }
    # Java 8 无法运行框架字节码，必须在进入 Java 主类前给出可读诊断。
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $runtimeText = (& $javaExe -XshowSettings:properties -version 2>&1 | Out-String)
    $runtimeExit = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    $runtimeText | Out-File -LiteralPath $bootstrapLog -Append -Encoding utf8
    if ($runtimeExit -ne 0) { throw 'JAVA_CHECK_FAILED / Java 无法运行，请查看引导日志' }
    if ($runtimeText -notmatch 'java.specification.version\s*=\s*(\S+)') { throw 'JAVA_VERSION_UNKNOWN / 无法识别 Java 版本' }
    $runtimeVersion = $Matches[1]
    if ($runtimeVersion.StartsWith('1.') -or [int]$runtimeVersion -lt 21) { throw "JAVA_TOO_OLD: Java 21+ required / 需要 Java 21 或更新版本，当前为 $runtimeVersion" }
    if ($runtimeText -notmatch 'os.arch\s*=\s*(amd64|x86_64)\s') { throw 'PLATFORM_UNSUPPORTED: Windows x64 required / 需要 Windows x64 运行时' }
    if (-not (Test-Path -LiteralPath $LoaderDir -PathType Container)) { throw "LOADER_MISSING / 启动依赖不存在: $LoaderDir" }
    $resolvedLoader = (Resolve-Path -LiteralPath $LoaderDir).Path
    $installerArguments = @()
    if ($GameDir -or $InstanceDir) {
        if (-not $GameDir -or -not $InstanceDir) { throw 'PATHS_REQUIRED / 请同时指定游戏和实例路径' }
        $installerArguments = @('--game-dir', $GameDir, '--instance-dir', $InstanceDir, '--language', $Language)
    }
    & $javaExe '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-cp' (Join-Path $resolvedLoader '*') net.fabricacs.acbric.ExternalInstaller @installerArguments
    $result = $LASTEXITCODE
    "Launcher exit code: $result" | Out-File -LiteralPath $bootstrapLog -Append -Encoding utf8
    exit $result
} catch {
    $_.Exception.Message | Out-File -LiteralPath $bootstrapLog -Append -Encoding utf8
    Write-Host $_.Exception.Message
    Write-Host "Log / 日志: $bootstrapLog"
    exit 2
}
