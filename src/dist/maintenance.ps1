# 本地更新事务：严格校验发行文件、保留玩家数据、写入前备份，失败/中断通过日志恢复。
# Internal maintenance functions. No game or candidate-package code is executed during verification.
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$script:MaintenanceHome = $PSScriptRoot

function Assert-SafePath([string]$Root, [string]$Relative) {
    if ([string]::IsNullOrWhiteSpace($Relative) -or $Relative.Contains('\') -or $Relative.StartsWith('/') -or $Relative.Contains(':')) { throw "UNSAFE_PATH / Invalid path / 路径无效: $Relative" }
    foreach ($part in $Relative.Split('/')) {
        if ($part -eq '' -or $part -eq '.' -or $part -eq '..' -or $part -match '[<>"|?*\x00-\x1f]' -or $part -match '[. ]$' -or $part -match '^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)') { throw "UNSAFE_PATH / Invalid path / 路径无效: $Relative" }
    }
    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    $path = [IO.Path]::GetFullPath((Join-Path $rootPath $Relative))
    if (-not $path.StartsWith($rootPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'UNSAFE_PATH / 路径超出目标目录' }
    Assert-NoLinks $rootPath
    $cursor = $path
    while ($cursor.Length -gt $rootPath.Length) {
        if ([IO.File]::Exists($cursor) -or [IO.Directory]::Exists($cursor)) {
            if (([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "LINK_REFUSED / 链接路径不受支持: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
    return $path
}

function Assert-NoLinks([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (([IO.File]::Exists($cursor) -or [IO.Directory]::Exists($cursor)) -and (([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint) -ne 0)) { throw "LINK_REFUSED / 链接路径不受支持: $cursor" }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}

function Test-ReleasePath([string]$Name) {
    if ($Name -in @('start.ps1','check-install.ps1')) { return $true }
    if ($Name -in @('Setup.cmd','setup.ps1','Start Acbric.cmd','start-configured.ps1','Update Acbric.cmd','update.ps1','maintenance.ps1','LICENSE','bundle.properties','release-files.properties','QUICK_START.txt','使用说明.txt','EXTERNAL_INSTALL.md','EXTERNAL_INSTALL.zh-CN.md','EXTERNAL_START.md','EXTERNAL_START.zh-CN.md','INSTALLER.md','INSTALLER.zh-CN.md','mods/README.md','core/acbric-api.jar')) { return $true }
    if ($Name -match '^loader-libs/(Acbric-[\w.\-]+|fabric-loader-[\w.\-]+|sponge-mixin-[\w.+\-]+|asm(?:-analysis|-commons|-tree|-util)?-[\w.\-]+)\.jar$') { return $true }
    if ($Name -match '^docs/[^/]+\.md$' -or $Name -match '^update-baselines/[\w.\-]+\.properties$' -or $Name.StartsWith('runtime/')) { return $true }
    if ($Name -match '^acbric-mod-template/(src/.+|gradle/wrapper/.+|gradlew|gradlew.bat|build.gradle|settings.gradle|gradle.properties|README.md|README.zh-CN.md|\.gitignore|local.properties.example)$') { return $true }
    return $false
}

function Get-FileHashValue([string]$Path) {
    if ([IO.Directory]::Exists($Path)) { throw "PATH_IS_DIRECTORY / Expected file / 此路径应为文件: $Path" }
    if (-not [IO.File]::Exists($Path)) { return $null }
    $stream = [IO.File]::OpenRead($Path); $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}

function Read-ReleaseText([string]$Text) {
    $files = @{}; $version = $null; $schema = $null
    foreach ($line in ($Text -split '\r?\n')) {
        if ($line -eq '') { continue }
        if ($line -match '^schema=(.+)$' -and $null -eq $schema) { $schema = $Matches[1]; continue }
        if ($line -match '^api.version=([A-Za-z0-9.+_-]{1,100})$' -and $null -eq $version) { $version = $Matches[1]; continue }
        if ($line -notmatch '^sha256\.(.+)=([a-f0-9]{64})$') { throw 'RELEASE_INVALID / Invalid release manifest / 发行清单无效' }
        $name = $Matches[1]; $hash = $Matches[2]
        if ($files.ContainsKey($name) -or -not (Test-ReleasePath $name) -or $name -eq 'release-files.properties') { throw "RELEASE_INVALID / Duplicate or unsupported file / 重复或不支持的文件: $name" }
        $files[$name] = $hash
    }
    if ($schema -ne '1' -or -not $version -or $files.Count -eq 0 -or $files.Count -gt 10000 -or -not $files.ContainsKey('core/acbric-api.jar') -or -not $files.ContainsKey('bundle.properties') -or -not $files.ContainsKey('start.ps1')) {
        throw 'RELEASE_INVALID / Unsupported release manifest / 发行清单不完整或版本不支持'
    }
    return @{ Version = $version; Files = $files }
}

function Write-AtomicText([string]$Path, [string]$Text) {
    $temporary = $Path + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::WriteAllText($temporary, $Text, (New-Object Text.UTF8Encoding($false)))
        if ([IO.File]::Exists($Path)) { [IO.File]::Replace($temporary, $Path, [NullString]::Value) } else { [IO.File]::Move($temporary, $Path) }
    } finally { if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) } }
}

function Get-JsonFile([string]$Path) { return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json) }
function Write-JsonFile([string]$Path, $Value) { Write-AtomicText $Path ($Value | ConvertTo-Json -Depth 12) }
function Convert-FileMap($Value) {
    $result = @{}
    foreach ($p in $Value.PSObject.Properties) {
        if ($p.Value -isnot [string] -or $p.Value -notmatch '^[a-f0-9]{64}$' -or -not (Test-ReleasePath $p.Name)) { throw 'JOURNAL_INVALID / 恢复记录无效' }
        $result[$p.Name] = $p.Value
    }
    return $result
}

function Read-Pointer([string]$Target, [string]$Name) {
    $path = Assert-SafePath $Target ('.acbric-maintenance/' + $Name + '.json')
    if (-not [IO.File]::Exists($path)) { return $null }
    $value = Get-JsonFile $path
    if ($value.id -isnot [string] -or $value.id -notmatch '^[a-f0-9]{32}$') { throw 'JOURNAL_INVALID / 恢复记录无效，请保留维护目录' }
    return $value.id
}

function Open-MaintenanceLocks([string]$Target) {
    $handles = New-Object 'Collections.Generic.List[IDisposable]'
    try {
        foreach ($relative in @('.acbric-framework.lock','mods/.acbric-mods.lock')) {
            $file = Assert-SafePath $Target $relative
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($file)) | Out-Null
            $handles.Add([IO.File]::Open($file, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None))
        }
        return ,$handles
    } catch {
        foreach ($handle in $handles) { $handle.Dispose() }
        throw 'FRAMEWORK_BUSY / Close the game and Setup before updating / 请关闭游戏与 Setup 后再更新'
    }
}

function Assert-CurrentFiles([string]$Target, [hashtable]$Old, [hashtable]$New) {
    foreach ($name in $Old.Keys) {
        $path = Assert-SafePath $Target $name
        if ((Get-FileHashValue $path) -ne $Old[$name]) { throw "LOCAL_CHANGE / Framework file changed or missing; preserved / 框架文件已修改或缺失，未覆盖: $name" }
    }
    foreach ($name in $New.Keys) {
        $path = Assert-SafePath $Target $name
        if (-not $Old.ContainsKey($name) -and ([IO.File]::Exists($path) -or [IO.Directory]::Exists($path))) { throw "FILE_CONFLICT / Existing unowned file preserved / 保留未归属的同名文件: $name" }
    }
}

function New-UpdatePlan([string]$Target, [string]$Package) {
    $targetRoot = [IO.Path]::GetFullPath($Target)
    Assert-NoLinks $targetRoot
    if (-not [IO.File]::Exists((Join-Path $targetRoot 'bundle.properties'))) { throw 'TARGET_INVALID / Select the existing Acbric folder / 请选择现有 Acbric 框架目录' }
    if (Read-Pointer $targetRoot 'pending') { throw 'UPDATE_RECOVERY_REQUIRED / Recover the interrupted update first / 请先恢复未完成的更新' }
    $stage = Join-Path ([IO.Path]::GetTempPath()) ('acbric-update-stage-' + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($stage) | Out-Null
    $zip = $null
    try {
        $zip = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($Package))
        $seen = @{}; [long]$total = 0
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -eq 'Acbric/') { continue }
            if (-not $entry.FullName.StartsWith('Acbric/', [StringComparison]::Ordinal)) { throw 'PACKAGE_INVALID / Expected Acbric release ZIP / 请使用 Acbric 框架发行 ZIP' }
            $name = $entry.FullName.Substring(7)
            if ($name.EndsWith('/')) { $null = Assert-SafePath $stage $name.TrimEnd('/'); continue }
            if ($seen.ContainsKey($name) -or -not (Test-ReleasePath $name) -or (($entry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000) { throw "PACKAGE_INVALID / Duplicate, link or unsupported file / 重复、链接或不支持的文件: $name" }
            if ($entry.Length -gt 268435456 -or $entry.Length -lt 0 -or $seen.Count -ge 10000) { throw 'PACKAGE_LIMIT / 更新包超过大小限制' }
            $total += $entry.Length
            if ($total -gt 2147483648) { throw 'PACKAGE_LIMIT / 更新包超过总大小限制' }
            $dest = Assert-SafePath $stage $name
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
            $source = $entry.Open(); $output = [IO.File]::Open($dest, [IO.FileMode]::CreateNew)
            try {
                $buffer = New-Object byte[] 65536; [long]$written = 0
                while (($read = $source.Read($buffer, 0, $buffer.Length)) -gt 0) {
                    $written += $read
                    if ($written -gt $entry.Length) { throw 'PACKAGE_LIMIT / 解压数据超过声明长度' }
                    $output.Write($buffer, 0, $read)
                }
            } finally { $source.Dispose(); $output.Dispose() }
            if ((Get-Item -LiteralPath $dest).Length -ne $entry.Length) { throw 'PACKAGE_INVALID / 文件长度不匹配' }
            $seen[$name] = Get-FileHashValue $dest
        }
        $manifest = Assert-SafePath $stage 'release-files.properties'
        if (-not [IO.File]::Exists($manifest)) { throw 'PACKAGE_TOO_OLD / This ZIP has no update manifest / 此更新包缺少完整发行清单' }
        $next = Read-ReleaseText ([IO.File]::ReadAllText($manifest))
        if ($seen.Count -ne $next.Files.Count + 1) { throw 'PACKAGE_INVALID / 清单与更新包文件数量不一致' }
        foreach ($name in $next.Files.Keys) { if (-not $seen.ContainsKey($name) -or $seen[$name] -ne $next.Files[$name]) { throw "PACKAGE_CHANGED / Hash mismatch / 更新包校验失败: $name" } }
        $bundleText = [IO.File]::ReadAllText((Join-Path $stage 'bundle.properties'))
        if ($bundleText -notmatch ('(?m)^api.version=' + [regex]::Escape($next.Version) + '\r?$')) { throw 'PACKAGE_INVALID / 核心版本清单不一致' }
        $coreEntries = @{}
        foreach ($line in ($bundleText -split '\r?\n')) {
            if ($line -match '^sha256\.(.+)=([a-f0-9]{64})$') {
                $key = $Matches[1]; $hash = $Matches[2]
                if ($coreEntries.ContainsKey($key) -or -not $seen.ContainsKey($key) -or $seen[$key] -ne $hash) { throw 'PACKAGE_INVALID / 核心文件清单不一致' }
                $coreEntries[$key] = $hash
            }
        }
        foreach ($key in $seen.Keys) {
            if (($key -eq 'core/acbric-api.jar' -or $key.StartsWith('loader-libs/')) -and -not $coreEntries.ContainsKey($key)) { throw 'PACKAGE_INVALID / 核心文件清单缺项' }
        }
        $api = [IO.Compression.ZipFile]::OpenRead((Join-Path $stage 'core/acbric-api.jar'))
        try {
            $entry = $api.GetEntry('fabric.mod.json'); if ($null -eq $entry) { throw 'API metadata missing' }
            $reader = New-Object IO.StreamReader($entry.Open())
            try { $meta = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            if ($meta.id -ne 'acbric_api' -or $meta.version -ne $next.Version) { throw 'PACKAGE_INVALID / API 版本不一致' }
        } finally { $api.Dispose() }
        $oldManifest = Assert-SafePath $targetRoot 'release-files.properties'
        if ([IO.File]::Exists($oldManifest)) {
            $old = Read-ReleaseText ([IO.File]::ReadAllText($oldManifest))
            $old.Files['release-files.properties'] = Get-FileHashValue $oldManifest
        } else {
            $oldBundle = [IO.File]::ReadAllText((Assert-SafePath $targetRoot 'bundle.properties'))
            if ($oldBundle -notmatch '(?m)^api.version=([A-Za-z0-9.+_-]{1,100})\r?$') { throw 'TARGET_INVALID / 无法识别旧框架版本' }
            $baseline = Join-Path $script:MaintenanceHome ('update-baselines/' + $Matches[1] + '.properties')
            if (-not [IO.File]::Exists($baseline)) { throw 'BASELINE_UNAVAILABLE / Use a fresh release and reconfigure this older version / 此旧版本不支持原位更新，请解压新包后重新配置' }
            $old = Read-ReleaseText ([IO.File]::ReadAllText($baseline))
            if (-not [IO.Directory]::Exists((Join-Path $targetRoot 'runtime'))) {
                foreach ($key in @($old.Files.Keys)) { if ($key.StartsWith('runtime/')) { $old.Files.Remove($key) } }
            }
        }
        if (@($old.Files.Keys | Where-Object { $_.StartsWith('runtime/') }).Count -gt 0 -and @($next.Files.Keys | Where-Object { $_.StartsWith('runtime/') }).Count -eq 0) { throw 'RUNTIME_REQUIRED / Use a release with bundled Java / 当前框架带 Java，请选择同样包含 Java 的完整更新包' }
        # 用户可修改 mods 中的说明；与其他 MOD 一样保留，不作为框架维护目标。
        $old.Files.Remove('mods/README.md'); $next.Files.Remove('mods/README.md')
        $next.Files['release-files.properties'] = Get-FileHashValue $manifest
        Assert-CurrentFiles $targetRoot $old.Files $next.Files
        return @{ Target=$targetRoot; Stage=$stage; Old=$old.Files; New=$next.Files; OldVersion=$old.Version; NewVersion=$next.Version }
    } catch {
        Remove-UpdateStage $stage
        throw
    } finally { if ($null -ne $zip) { $zip.Dispose() } }
}

function Remove-UpdateStage([string]$Stage) {
    $root = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\','/')
    $path = [IO.Path]::GetFullPath($Stage)
    if ([IO.Path]::GetDirectoryName($path) -ne $root -or [IO.Path]::GetFileName($path) -notmatch '^acbric-update-stage-[a-f0-9]{32}$') { throw 'UNSAFE_CLEANUP / 暂存目录校验失败' }
    Assert-NoLinks $path
    if (Test-Path -LiteralPath $path) {
        foreach ($child in Get-ChildItem -LiteralPath $path -Recurse -Force) { if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'LINK_REFUSED / 暂存目录出现链接，保留现场' } }
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}

function Set-ManagedFile([string]$Target, [string]$Name, [string]$Source, [AllowNull()][string]$Hash) {
    $dest = Assert-SafePath $Target $Name
    if (-not $Hash) { if ([IO.File]::Exists($dest)) { [IO.File]::Delete($dest) }; return }
    if ((Get-FileHashValue $Source) -ne $Hash) { throw "BACKUP_CHANGED / Source hash changed / 备份或暂存文件校验失败: $Name" }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
    $temp = $dest + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::Copy($Source, $temp, $false)
        if ([IO.File]::Exists($dest)) { [IO.File]::Replace($temp, $dest, [NullString]::Value) } else { [IO.File]::Move($temp, $dest) }
    } finally { if ([IO.File]::Exists($temp)) { [IO.File]::Delete($temp) } }
}

function Read-Transaction([string]$Target, [string]$Id) {
    if ($Id -notmatch '^[a-f0-9]{32}$') { throw 'JOURNAL_INVALID / 事务编号无效' }
    $job = Assert-SafePath $Target ('.acbric-maintenance/transactions/' + $Id)
    $journal = Get-JsonFile (Assert-SafePath $job 'journal.json')
    # 文件归属均为相对路径；完整搬迁框架后仍可使用随目录保存的历史备份。
    if ($journal.schema -ne 1 -or $journal.id -ne $Id) { throw 'JOURNAL_INVALID / 事务记录与目标不匹配' }
    return @{ Folder=$job; Journal=$journal; Old=(Convert-FileMap $journal.old); New=(Convert-FileMap $journal.new) }
}

function Restore-Transaction([string]$Target, [string]$Id, [bool]$Interrupted) {
    $state = Read-Transaction $Target $Id
    $job = $state.Folder; $journal = $state.Journal; $old = $state.Old; $new = $state.New
    $names = @(@($old.Keys) + @($new.Keys) | Sort-Object -Unique)
    # 先验证所有备份与当前文件；不以恢复为理由覆盖更新完成后的用户修改。
    foreach ($name in $old.Keys) {
        if ((Get-FileHashValue (Assert-SafePath (Join-Path $job 'old') $name)) -ne $old[$name]) { throw "BACKUP_CHANGED / 保留现场，备份已损坏: $name" }
    }
    foreach ($name in $names) {
        $current = Get-FileHashValue (Assert-SafePath $Target $name)
        $allowed = if ($Interrupted) { $null -eq $current -or $current -eq $old[$name] -or $current -eq $new[$name] } else { $current -eq $new[$name] }
        if (-not $allowed) { throw "RECOVERY_CONFLICT / Preserve changed file and inspect backup / 当前文件被额外修改，请保留并检查: $name" }
    }
    Write-JsonFile (Assert-SafePath $Target '.acbric-maintenance/pending.json') @{id=$Id}
    $journal.phase = 'restoring'; Write-JsonFile (Join-Path $job 'journal.json') $journal
    foreach ($name in $names) {
        $current = Get-FileHashValue (Assert-SafePath $Target $name)
        if ($current -eq $old[$name]) { continue }
        if ($null -ne $current -and $current -ne $new[$name]) { throw "RECOVERY_CONFLICT / 文件在恢复期间改变: $name" }
        Set-ManagedFile $Target $name (Assert-SafePath (Join-Path $job 'old') $name) $old[$name]
    }
    $journal.phase = 'restored'; Write-JsonFile (Join-Path $job 'journal.json') $journal
    $latest = Assert-SafePath $Target '.acbric-maintenance/latest.json'
    if ($journal.previous) { Write-JsonFile $latest @{id=$journal.previous} } elseif ([IO.File]::Exists($latest)) { [IO.File]::Delete($latest) }
    [IO.File]::Delete((Assert-SafePath $Target '.acbric-maintenance/pending.json'))
    return "RESTORED / Restored previous framework / 已恢复更新前框架: $($journal.oldVersion); $job"
}

function Invoke-FrameworkUpdate($Plan, [int]$FailureAfter = -1, [int]$InterruptionAfter = -1) {
    $target = $Plan.Target; $handles = Open-MaintenanceLocks $target
    $job = $null; $started = $false
    try {
        if (Read-Pointer $target 'pending') { throw 'UPDATE_RECOVERY_REQUIRED / 请先恢复未完成更新' }
        Assert-CurrentFiles $target $Plan.Old $Plan.New
        $id = [guid]::NewGuid().ToString('N')
        $job = Assert-SafePath $target ('.acbric-maintenance/transactions/' + $id)
        [IO.Directory]::CreateDirectory($job) | Out-Null
        foreach ($side in @('old','new')) {
            $map = if ($side -eq 'old') { $Plan.Old } else { $Plan.New }
            $sourceRoot = if ($side -eq 'old') { $target } else { $Plan.Stage }
            foreach ($name in $map.Keys) {
                $dest = Assert-SafePath (Join-Path $job $side) $name
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
                [IO.File]::Copy((Assert-SafePath $sourceRoot $name), $dest, $false)
                if ((Get-FileHashValue $dest) -ne $map[$name]) { throw "SOURCE_CHANGED / 源文件在备份期间改变: $name" }
            }
        }
        $journal = @{schema=1; id=$id; target=$target; old=$Plan.Old; new=$Plan.New; oldVersion=$Plan.OldVersion; newVersion=$Plan.NewVersion; previous=(Read-Pointer $target 'latest'); phase='prepared'}
        Write-JsonFile (Join-Path $job 'journal.json') $journal
        Write-JsonFile (Assert-SafePath $target '.acbric-maintenance/pending.json') @{id=$id}
        $started = $true; $count = 0
        foreach ($name in @(@($Plan.Old.Keys) + @($Plan.New.Keys) | Sort-Object -Unique)) {
            if ((Get-FileHashValue (Assert-SafePath $target $name)) -ne $Plan.Old[$name]) { throw "LOCAL_CHANGE / 文件在更新期间改变: $name" }
            Set-ManagedFile $target $name (Assert-SafePath (Join-Path $job 'new') $name) $Plan.New[$name]
            $count++
            if ($count -eq $FailureAfter) { throw 'TEST_FAILURE / Injected write failure' }
            if ($count -eq $InterruptionAfter) { throw 'TEST_INTERRUPTION / Simulated process interruption' }
        }
        Assert-CurrentFiles $target $Plan.New @{}
        $journal.phase = 'complete'; Write-JsonFile (Join-Path $job 'journal.json') $journal
        Write-JsonFile (Assert-SafePath $target '.acbric-maintenance/latest.json') @{id=$id}
        [IO.File]::Delete((Assert-SafePath $target '.acbric-maintenance/pending.json'))
        return "UPDATE_OK / Framework updated / 框架更新完成: $($Plan.OldVersion) -> $($Plan.NewVersion); $job"
    } catch {
        $original = $_.Exception.Message
        if ($started -and -not $original.StartsWith('TEST_INTERRUPTION')) {
            try { $restored = Restore-Transaction $target $id $true }
            catch { throw "RECOVERY_REQUIRED / Automatic recovery could not finish; keep backup / 自动恢复未完成，请保留备份并再次恢复: $job`n$original`n$($_.Exception.Message)" }
            throw "UPDATE_FAILED_RESTORED / Update failed; previous files restored / 更新失败，已恢复原框架: $original`n$restored"
        }
        throw
    } finally { foreach ($handle in $handles) { $handle.Dispose() } }
}

function Invoke-FrameworkRestore([string]$Target) {
    $targetRoot = [IO.Path]::GetFullPath($Target)
    if (-not (Read-Pointer $targetRoot 'pending') -and -not (Read-Pointer $targetRoot 'latest')) { throw 'NO_BACKUP / No update backup available / 没有可恢复的更新备份' }
    $handles = Open-MaintenanceLocks $targetRoot
    try {
        $pending = Read-Pointer $targetRoot 'pending'
        $id = if ($pending) { $pending } else { Read-Pointer $targetRoot 'latest' }
        if (-not $id) { throw 'NO_BACKUP / No update backup available / 没有可恢复的更新备份' }
        return Restore-Transaction $targetRoot $id ([bool]$pending)
    } finally { foreach ($handle in $handles) { $handle.Dispose() } }
}
