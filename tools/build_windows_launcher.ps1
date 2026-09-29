# 使用 Windows 自带 .NET Framework 编译器构建 GUI 入口；玩家不需要额外安装构建工具。
param([Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'Windows .NET Framework compiler is required to build Acbric.exe / 构建 EXE 需要 Windows .NET Framework 编译器' }
$source = Join-Path $PSScriptRoot '../src/launcher/windows/Acbric.cs'
$parent = Split-Path -Parent $Output
[IO.Directory]::CreateDirectory($parent) | Out-Null
& $compiler /nologo /target:winexe /platform:x64 /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "/out:$Output" $source
if ($LASTEXITCODE -ne 0) { throw 'Acbric.exe compilation failed' }
