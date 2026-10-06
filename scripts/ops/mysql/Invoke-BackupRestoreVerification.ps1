[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Verify')][string] $Mode = 'Preflight',
    [string] $SourceContainer,
    [string[]] $WriterContainers = @(),
    [string] $Database = 'stock_agent_harness',
    [switch] $ConfirmSourceQuiescent,
    [string] $VerificationId = ('mysql-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
)

$ErrorActionPreference = 'Stop'
$script:Workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$script:OwnerLabel = 'com.stock.backup-verification'
$script:InspectFormat = '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Image}},"state":{{json .State}},"labels":{{json .Config.Labels}},"mounts":{{json .Mounts}},"networkMode":{{json .HostConfig.NetworkMode}},"portBindings":{{json .HostConfig.PortBindings}}}'

function Assert-SafeName([string] $Value, [string] $Kind) {
    $pattern = if ($Kind -eq 'container') { '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$' } else { '^[a-zA-Z][a-zA-Z0-9_]{0,63}$' }
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -cnotmatch $pattern) { throw "Invalid $Kind name." }
    if ($Kind -eq 'database' -and $Value -in @('mysql', 'sys', 'information_schema', 'performance_schema')) {
        throw 'System database backup is outside this tool scope.'
    }
}

function ConvertTo-NativeArgument([string] $Value) {
    # Windows CommandLineToArgvW quoting, including quotes and trailing backslashes.
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Invoke-NativeProcess {
    param([string] $FileName, [string[]] $Arguments, [string] $OutputPath, [int] $TimeoutSeconds = 60)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $FileName
    $start.Arguments = ($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $start.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $output = $null
    try {
        if ($OutputPath) { $output = [IO.File]::Open($OutputPath, [IO.FileMode]::CreateNew) }
        if (-not $process.Start()) { throw 'Process start failed.' }
        $stdout = if ($output) { $process.StandardOutput.BaseStream.CopyToAsync($output) } else { $process.StandardOutput.ReadToEndAsync() }
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill()
            [void] $process.WaitForExit(5000)
            throw 'External command timed out; inspect server-side activity before retrying.'
        }
        if (-not $stdout.Wait(10000) -or -not $stderr.Wait(10000)) { throw 'External command stream did not finish.' }
        # Do not print native stderr: SQL errors can contain private row contents.
        if ($process.ExitCode -ne 0) { throw "External command failed (exit=$($process.ExitCode)); no native output was published." }
        if (-not $output) { return $stdout.GetAwaiter().GetResult().Trim() }
    } finally {
        if ($output) { $output.Dispose() }
        $process.Dispose()
    }
}

function Invoke-Docker {
    param([string[]] $Arguments, [string] $OutputPath, [int] $TimeoutSeconds = 60)
    $docker = (Get-Command docker -CommandType Application -ErrorAction Stop).Source
    Invoke-NativeProcess -FileName $docker -Arguments $Arguments -OutputPath $OutputPath -TimeoutSeconds $TimeoutSeconds
}

function Read-Container([string] $Name) {
    return (Invoke-Docker -Arguments @('container', 'inspect', '--format', $script:InspectFormat, $Name) | ConvertFrom-Json)
}

function Get-PublicContainer($Container) {
    return [ordered]@{
        id = $Container.id; name = $Container.name; image = $Container.image
        status = $Container.state.Status; running = $Container.state.Running
        startedAt = $Container.state.StartedAt; finishedAt = $Container.state.FinishedAt
        volumes = @($Container.mounts | ForEach-Object { [ordered]@{ type = $_.Type; name = $_.Name; destination = $_.Destination } })
    }
}

function Invoke-MySql {
    param([string] $ContainerId, [string] $Sql, [switch] $Target)
    $shell = if ($Target) { 'test "$(cat /proc/1/comm)" = mysqld || exit 66; unset MYSQL_PWD; exec "$@"' } else {
        'test -n "$MYSQL_ROOT_PASSWORD" || exit 65; export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec "$@"'
    }
    return Invoke-Docker -Arguments @('exec', $ContainerId, 'sh', '-c', $shell, '_', 'mysql', '--no-defaults', '--no-login-paths',
        '--protocol=socket', '--user=root', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names',
        '--connect-timeout=5', '--init-command=SET SESSION MAX_EXECUTION_TIME=15000', '--execute', $Sql)
}

function Read-DatabaseState {
    param([string] $ContainerId, [string] $Db, [switch] $Target)
    Assert-SafeName $Db 'database'
    $server = Invoke-MySql $ContainerId @'
SELECT JSON_OBJECT('version', VERSION(), 'lowerCaseTableNames', @@lower_case_table_names,
 'eventScheduler', @@event_scheduler,
 'otherClients', (SELECT COUNT(*) FROM information_schema.PROCESSLIST
  WHERE ID <> CONNECTION_ID() AND USER NOT IN ('system user', 'event_scheduler')));
'@ -Target:$Target | ConvertFrom-Json
    if ($server.version -notmatch '^8\.4\.' -or $server.lowerCaseTableNames -ne 0) { throw 'Expected MySQL 8.4 with lower_case_table_names=0.' }
    if (-not $Target -and ($server.eventScheduler -ne 'OFF' -or $server.otherClients -ne 0)) {
        throw 'Source event scheduler or other DB clients are active; quiescence is not confirmed.'
    }
    if ($Target -and $server.eventScheduler -ne 'OFF') { throw 'Target event scheduler must remain off.' }
    if (-not $Target -and (Invoke-MySql $ContainerId 'SHOW REPLICA STATUS;')) { throw 'Replication configuration is outside this local backup verification scope.' }
    $tools = [ordered]@{}
    foreach ($tool in @('mysql', 'mysqldump')) {
        $tools[$tool] = Invoke-Docker -Arguments @('exec', $ContainerId, $tool, '--no-defaults', '--no-login-paths', '--version')
        if ($tools[$tool] -notmatch '\bVer\s+8\.4\.') { throw 'Expected MySQL 8.4 client tools.' }
    }
    $tableSql = @"
SELECT JSON_OBJECT('name', t.TABLE_NAME, 'engine', t.ENGINE, 'type', t.TABLE_TYPE,
 'hasPrimaryKey', EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS c
 WHERE c.TABLE_SCHEMA=t.TABLE_SCHEMA AND c.TABLE_NAME=t.TABLE_NAME AND c.CONSTRAINT_TYPE='PRIMARY KEY'))
FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA='$Db' ORDER BY t.TABLE_NAME;
"@
    $rows = Invoke-MySql $ContainerId $tableSql -Target:$Target
    $tables = @($rows -split '\r?\n' | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
    if ($tables.Count -eq 0 -or 'flyway_schema_history' -notin $tables.name) { throw 'Application database or Flyway history is missing.' }
    foreach ($table in $tables) {
        Assert-SafeName $table.name 'table'
        if ($table.type -ne 'BASE TABLE' -or $table.engine -ne 'InnoDB' -or $table.hasPrimaryKey -ne 1) {
            throw 'Only InnoDB base tables with a primary key are supported; unordered or nontransactional content is refused.'
        }
    }
    $countSql = ($tables | ForEach-Object {
        "SELECT JSON_OBJECT('table', '$($_.name)', 'rows', COUNT(*)) FROM ``$Db``.``$($_.name)``"
    }) -join ' UNION ALL '
    $counts = Invoke-MySql $ContainerId ($countSql + ';') -Target:$Target
    $flywaySql = "SELECT JSON_OBJECT('rank', installed_rank, 'version', version, 'description', description, 'script', script, 'checksum', checksum, 'success', success) FROM ``$Db``.flyway_schema_history ORDER BY installed_rank;"
    $flyway = Invoke-MySql $ContainerId $flywaySql -Target:$Target
    $history = @($flyway -split '\r?\n' | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
    if ($history.Count -eq 0 -or @($history | Where-Object { $_.success -ne 1 }).Count -gt 0) { throw 'Missing or unsuccessful Flyway history.' }
    return [ordered]@{
        version = $server.version
        tools = $tools
        tables = $tables
        counts = @($counts -split '\r?\n' | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json } | Sort-Object -Property table -CaseSensitive)
        flyway = $history
    }
}

function Assert-SourceAndWriters {
    param($Source, [string[]] $Writers)
    if (-not $Source.state.Running -or $Source.state.Paused -or $Source.state.Restarting) { throw 'Source MySQL must already be running, not paused or restarting.' }
    if ($Source.image -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Source image ID is not immutable.' }
    $dataMount = @($Source.mounts | Where-Object { $_.Destination -eq '/var/lib/mysql' })
    if ($dataMount.Count -ne 1 -or $dataMount[0].Type -ne 'volume' -or -not $dataMount[0].Name) { throw 'Expected a named source MySQL data volume.' }
    if ($Source.labels.'com.docker.compose.service' -ne 'mysql' -or -not $Source.labels.'com.docker.compose.project') {
        throw 'This tool requires the repository Compose MySQL service.'
    }
    if ($Writers.Count -eq 0) { throw 'Specify every known application container with -WriterContainers.' }
    $states = @()
    foreach ($name in $Writers) {
        Assert-SafeName $name 'container'
        $writer = Read-Container $name
        if ($writer.id -eq $Source.id -or $writer.state.Running -or $writer.state.Restarting -or
            $writer.state.Status -notin @('exited', 'created')) { throw 'An expected writer is running or is not a stopped application container.' }
        $states += ,(Get-PublicContainer $writer)
    }
    $project = $Source.labels.'com.docker.compose.project'
    $composeApps = Invoke-Docker -Arguments @('ps', '-a', '--filter', "label=com.docker.compose.project=$project", '--filter', 'label=com.docker.compose.service=app', '--format', '{{.ID}}')
    if (-not $composeApps) { throw 'No Compose application container found; writer inventory cannot be verified.' }
    foreach ($id in ($composeApps -split '\r?\n')) {
        if (@($states | Where-Object { $_.id.StartsWith($id) }).Count -ne 1) { throw 'A Compose application container is missing from -WriterContainers.' }
    }
    return $states
}

function Assert-SourceUnchanged {
    param($Before, [string[]] $Writers, $WriterStates)
    $current = Read-Container $Before.id
    $states = @(Assert-SourceAndWriters $current $Writers)
    if ((Get-PublicContainer $Before | ConvertTo-Json -Depth 8 -Compress) -cne (Get-PublicContainer $current | ConvertTo-Json -Depth 8 -Compress) -or
        ($WriterStates | ConvertTo-Json -Depth 8 -Compress) -cne ($states | ConvertTo-Json -Depth 8 -Compress)) {
        throw 'Source or writer container identity/lifecycle changed during verification.'
    }
}

function New-EvidenceDirectory([string] $Id) {
    if ($Id -cnotmatch '^mysql-[a-zA-Z0-9-]{1,60}$') { throw 'Invalid verification ID; use mysql- followed by letters, digits or hyphens.' }
    $path = $script:Workspace
    if (-not (Test-Path -LiteralPath $path -PathType Container) -or
        ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Workspace must be a real directory, not a symlink or junction.' }
    foreach ($part in @('data', 'backups', 'mysql')) {
        $path = Join-Path $path $part
        if (Test-Path -LiteralPath $path) {
            if (-not (Test-Path -LiteralPath $path -PathType Container) -or
                ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Backup path must not be a file, symlink or junction.' }
        } else { [void] [IO.Directory]::CreateDirectory($path) }
    }
    $path = Join-Path $path $Id
    if (Test-Path -LiteralPath $path) { throw 'Evidence directory exists; never overwrite or resume a partial verification.' }
    [void] [IO.Directory]::CreateDirectory($path)
    return $path
}

function Write-EvidenceJson([string] $Path, $Value) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 15))
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
}

function Export-Database {
    param([string] $ContainerId, [string] $Db, [string] $Path, [switch] $Target)
    $shell = if ($Target) { 'unset MYSQL_PWD; exec "$@"' } else {
        'test -n "$MYSQL_ROOT_PASSWORD" || exit 65; export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec "$@"'
    }
    $arguments = @('exec', $ContainerId, 'sh', '-c', $shell, '_', 'mysqldump', '--no-defaults', '--no-login-paths',
        '--user=root', '--protocol=socket', '--default-character-set=utf8mb4', '--single-transaction', '--quick',
        '--order-by-primary', '--hex-blob', '--tz-utc', '--routines', '--events', '--triggers', '--set-gtid-purged=OFF', '--no-tablespaces',
        '--column-statistics=0', '--skip-comments', '--skip-dump-date', '--skip-add-locks', '--skip-disable-keys',
        '--skip-extended-insert', '--skip-lock-tables', '--databases', $Db)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $partial = $Path + '.partial'
    Invoke-Docker -Arguments $arguments -OutputPath $partial -TimeoutSeconds 300
    if ((Get-Item -LiteralPath $partial).Length -eq 0) { throw 'Empty dump is not a backup.' }
    [IO.File]::Move($partial, $Path)
    return [ordered]@{ file = [IO.Path]::GetFileName($Path); bytes = (Get-Item -LiteralPath $Path).Length
        sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash; durationMs = $watch.ElapsedMilliseconds
        startedAt = $startedAt; finishedAt = [DateTimeOffset]::UtcNow.ToString('o') }
}

function Assert-IsolatedTarget {
    param($Target, $Source, [string] $Id, [string] $Volume)
    if ($Target.id -eq $Source.id -or $Target.image -ne $Source.image -or $Target.labels.($script:OwnerLabel) -cne $Id -or
        $Target.networkMode -ne 'none' -or @($Target.portBindings.PSObject.Properties).Count -ne 0 -or
        $Target.mounts.Count -ne 1 -or $Target.mounts[0].Type -ne 'volume' -or
        $Target.mounts[0].Name -cne $Volume -or $Target.mounts[0].Destination -ne '/var/lib/mysql' -or
        $Volume -in $Source.mounts.Name) { throw 'Target ownership, image, network, ports or volume isolation check failed.' }
    $volumeInfo = Invoke-Docker -Arguments @('volume', 'inspect', '--format', '{{json .Labels}}', $Volume) | ConvertFrom-Json
    if ($volumeInfo.($script:OwnerLabel) -cne $Id) { throw 'Target volume ownership label is missing.' }
}

function Assert-DatabaseEqual($Left, $Right) {
    if (($Left | ConvertTo-Json -Depth 12 -Compress) -cne ($Right | ConvertTo-Json -Depth 12 -Compress)) {
        throw 'DB version, table metadata, row counts or Flyway history differ.'
    }
}

function Invoke-BackupRestoreVerification {
    param([ValidateSet('Preflight', 'Verify')][string] $Mode, [string] $SourceContainer, [string[]] $WriterContainers, [string] $Database,
        [bool] $ConfirmSourceQuiescent, [string] $VerificationId)
    Assert-SafeName $SourceContainer 'container'
    Assert-SafeName $Database 'database'
    if ($Mode -eq 'Verify' -and -not $ConfirmSourceQuiescent) { throw 'Verify requires -ConfirmSourceQuiescent after all writers and schema changes have been stopped.' }
    $startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()
    $source = Read-Container $SourceContainer
    $writers = @(Assert-SourceAndWriters $source $WriterContainers)
    $state = Read-DatabaseState $source.id $Database
    $preflight = [ordered]@{ observedAt = [DateTimeOffset]::UtcNow.ToString('o'); database = $Database
        source = (Get-PublicContainer $source); writers = $writers; state = $state }
    if ($Mode -eq 'Preflight') { return $preflight }

    $root = New-EvidenceDirectory $VerificationId
    $targetName = 'stock-restore-' + $VerificationId
    $volume = $targetName + '-data'
    $report = [ordered]@{ verificationId = $VerificationId; status = 'FAILED'; stage = 'preflight'
        startedAt = $startedAt; finishedAt = $null; durationMs = $null; source = $preflight.source
        targetName = $targetName; targetId = $null; targetVolume = $volume; targetStopped = $false
        backup = $null; restoreStartedAt = $null; restoreFinishedAt = $null; restoreDurationMs = $null
        restoredDump = $null; sourceAfterDump = $null; failureStage = $null; failureReason = $null }
    $targetId = $null
    $failure = $false
    try {
        Write-EvidenceJson (Join-Path $root 'preflight.json') $preflight
        $report.stage = 'resource-check'
        $names = Invoke-Docker -Arguments @('ps', '-a', '--format', '{{.Names}}')
        $volumes = Invoke-Docker -Arguments @('volume', 'ls', '--format', '{{.Name}}')
        if ($targetName -in ($names -split '\r?\n') -or $volume -in ($volumes -split '\r?\n')) { throw 'Target resources already exist.' }
        $report.stage = 'backup'
        $backupPath = Join-Path $root 'backup.sql'
        $report.backup = Export-Database $source.id $Database $backupPath
        Assert-SourceUnchanged $source $WriterContainers $writers
        $report.stage = 'create-target'
        [void] (Invoke-Docker -Arguments @('volume', 'create', '--label', "$($script:OwnerLabel)=$VerificationId", $volume))
        $targetId = Invoke-Docker -Arguments @('create', '--name', $targetName, '--pull=never', '--network', 'none',
            '--label', "$($script:OwnerLabel)=$VerificationId", '--env', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes',
            '--mount', "type=volume,src=$volume,dst=/var/lib/mysql", $source.image, '--event-scheduler=OFF', '--skip-log-bin')
        if ($targetId -notmatch '^[a-f0-9]{64}$') { throw 'Invalid new target container ID.' }
        $report.targetId = $targetId
        Assert-IsolatedTarget (Read-Container $targetId) $source $VerificationId $volume
        [void] (Invoke-Docker -Arguments @('start', $targetId))
        $report.stage = 'wait-target'
        $ready = $false
        $watch = [Diagnostics.Stopwatch]::StartNew()
        while ($watch.Elapsed.TotalSeconds -lt 120) {
            $target = Read-Container $targetId
            if (-not $target.state.Running) { throw 'Target MySQL exited during startup.' }
            try {
                $response = Invoke-MySql $targetId 'SELECT 1;' -Target
                if ($response -eq '1') { $ready = $true; break }
            } catch { }
            Start-Sleep -Seconds 2
        }
        if (-not $ready) { throw 'Target MySQL was not ready within the startup deadline.' }
        $report.stage = 'restore'
        Assert-IsolatedTarget (Read-Container $targetId) $source $VerificationId $volume
        [void] (Invoke-Docker -Arguments @('cp', $backupPath, ($targetId + ':/tmp/stock-backup.sql')))
        $report.restoreStartedAt = [DateTimeOffset]::UtcNow.ToString('o')
        $restoreWatch = [Diagnostics.Stopwatch]::StartNew()
        [void] (Invoke-Docker -Arguments @('exec', $targetId, 'sh', '-c',
            'unset MYSQL_PWD; exec mysql --no-defaults --no-login-paths --protocol=socket --user=root --default-character-set=utf8mb4 --comments --binary-mode=1 < /tmp/stock-backup.sql') -TimeoutSeconds 300)
        $report.restoreDurationMs = $restoreWatch.ElapsedMilliseconds
        $report.restoreFinishedAt = [DateTimeOffset]::UtcNow.ToString('o')
        $report.stage = 'compare'
        $restored = Read-DatabaseState $targetId $Database -Target
        Write-EvidenceJson (Join-Path $root 'restored-state.json') $restored
        Assert-DatabaseEqual $state $restored
        $report.restoredDump = Export-Database $targetId $Database (Join-Path $root 'restored.sql') -Target
        $report.sourceAfterDump = Export-Database $source.id $Database (Join-Path $root 'source-after.sql')
        Assert-DatabaseEqual $state (Read-DatabaseState $source.id $Database)
        Assert-SourceUnchanged $source $WriterContainers $writers
        if ($report.backup.sha256 -cne $report.restoredDump.sha256 -or $report.backup.sha256 -cne $report.sourceAfterDump.sha256 -or
            $report.backup.sha256 -cne (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash) {
            throw 'Logical schema/content differs after restore or source changed during verification.'
        }
        $report.status = 'PASSED'
    } catch {
        $failure = $true
        $report.failureStage = $report.stage
        $report.failureReason = $_.Exception.Message
    } finally {
        if ($targetId -match '^[a-f0-9]{64}$') {
            try {
                Assert-IsolatedTarget (Read-Container $targetId) $source $VerificationId $volume
                [void] (Invoke-Docker -Arguments @('stop', '--time', '20', $targetId))
                $report.targetStopped = -not (Read-Container $targetId).state.Running
                if (-not $report.targetStopped) { throw 'Target is still running.' }
            } catch {
                $report.status = 'FAILED'
                if (-not $report.failureStage) { $report.failureStage = 'stop-target'; $report.failureReason = $_.Exception.Message }
                $failure = $true
            }
        }
        $report.finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
        $report.durationMs = $operationWatch.ElapsedMilliseconds
        Write-EvidenceJson (Join-Path $root 'result.json') $report
    }
    if ($failure) { throw "Verification failed at $($report.failureStage). Evidence: $root. Inspect owned resources; do not retry with the same ID." }
    return $report
}

if ($MyInvocation.InvocationName -ne '.') {
    Invoke-BackupRestoreVerification -Mode $Mode -SourceContainer $SourceContainer -WriterContainers $WriterContainers `
        -Database $Database -ConfirmSourceQuiescent $ConfirmSourceQuiescent.IsPresent -VerificationId $VerificationId | ConvertTo-Json -Depth 15
}
