$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Invoke-BackupRestoreVerification.ps1')
$productionWorkspace = $script:Workspace
$script:Workspace = Join-Path $productionWorkspace ('build\mysql-backup-tool-tests-' + [Guid]::NewGuid().ToString('N'))
[void] [IO.Directory]::CreateDirectory($script:Workspace)
$script:Passed = 0
$nativeFixture = Join-Path $script:Workspace 'NativeFixture.exe'
Add-Type -OutputAssembly $nativeFixture -OutputType ConsoleApplication -TypeDefinition @'
using System;
using System.Threading;
public static class NativeFixture {
    public static int Main(string[] args) {
        if (args[0] == "echo") { Console.Write(args[1]); return 0; }
        if (args[0] == "bytes") {
            byte[] data = { 0, 255, 13, 10, 65 };
            Console.OpenStandardOutput().Write(data, 0, data.Length);
            return 0;
        }
        if (args[0] == "fail") { Console.Error.Write("PRIVATE-CONTENT"); return 9; }
        Thread.Sleep(20000);
        return 0;
    }
}
'@

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}
function Test-Case([string] $Name, [scriptblock] $Body) {
    & $Body
    $script:Passed++
    Write-Output "PASS $Name"
}
function Assert-Throws([scriptblock] $Body, [string] $Pattern) {
    $caught = $null
    try { & $Body | Out-Null } catch { $caught = $_.Exception.Message }
    Assert-True ($caught -and $caught -match $Pattern) "Expected failure matching: $Pattern"
}

$script:FakeSourceId = 'a' * 64
$script:FakeTargetId = 'b' * 64
$script:FakeWriterId = 'c' * 64
$script:FakeImageId = 'sha256:' + ('d' * 64)
$script:Tables = @('broker_order', 'daily_price_bar', 'flyway_schema_history', 'harness_run_entity',
    'harness_step_entity', 'strategy_portfolio', 'trade_record_entity')
$script:Dump = @'
CREATE DATABASE `stock_agent_harness`;
INSERT INTO sample VALUES (1, NULL, 0x00FF, '{"reason":"buy"}');
'@

function New-FakeContainer([string] $Kind) {
    $target = $Kind -eq 'target'
    $writer = $Kind -eq 'writer'
    $volume = if ($target) { 'stock-restore-' + $script:TestId + '-data' } else { 'original-data' }
    return [pscustomobject]@{
        id = $(if ($target) { $script:FakeTargetId } elseif ($writer) { $script:FakeWriterId } else { $script:FakeSourceId })
        name = $(if ($target) { '/stock-restore-' + $script:TestId } else { '/' + $Kind })
        image = $script:FakeImageId
        state = [pscustomobject]@{ Running = $(if ($writer) { $script:WriterRunning } elseif ($target) { $script:TargetRunning } else { $true })
            Status = $(if ($writer -or ($target -and -not $script:TargetRunning)) { 'exited' } else { 'running' })
            Paused = $false; Restarting = $false; StartedAt = '2026-10-06T00:00:00Z'; FinishedAt = '0001-01-01T00:00:00Z' }
        labels = [pscustomobject]@{ 'com.docker.compose.project' = 'stock-agent-harness'
            'com.docker.compose.service' = $(if ($writer) { 'app' } else { 'mysql' }); 'com.stock.backup-verification' = $(if ($target) { $script:TestId } else { $null }) }
        mounts = @([pscustomobject]@{ Type = 'volume'; Name = $volume; Destination = '/var/lib/mysql' })
        networkMode = $(if ($target) { 'none' } else { 'default' })
        portBindings = [pscustomobject]@{}
    }
}

function Reset-FakeDocker {
    $script:Calls = [Collections.Generic.List[object]]::new()
    $script:TestId = 'mysql-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $script:WriterRunning = $false
    $script:TargetRunning = $false
    $script:ExistingResource = $false
    $script:ExistingVolume = $false
    $script:Mismatch = $false
    $script:SourceChange = $false
    $script:ImportFails = $false
    $script:DumpFails = $false
    $script:StopFails = $false
    $script:TamperBackup = $false
    $script:MissingWriter = $false
    $script:Engine = 'InnoDB'
    $script:HasPrimaryKey = 1
    $script:OtherClients = 0
    $script:FlywaySuccess = 1
    $script:EventScheduler = 'OFF'
    $script:ReplicaConfigured = $false
    $script:WriterLifecycleChanged = $false
    $script:RowCountMismatch = $false
    $script:TargetOwnerMismatch = $false
    $script:ReverseCounts = $false
    $script:SourceDumpCount = 0
}

# A deterministic Docker substitute: tests never contact a daemon or a real DB.
function Invoke-Docker {
    param([string[]] $Arguments, [string] $OutputPath, [int] $TimeoutSeconds = 60)
    $script:Calls.Add([pscustomobject]@{ arguments = $Arguments; outputPath = $OutputPath; timeout = $TimeoutSeconds })
    if ($Arguments[0] -eq 'container') {
        $kind = if ($Arguments[-1] -eq $script:FakeTargetId) { 'target' } elseif ($Arguments[-1] -eq 'app') { 'writer' } else { 'source' }
        $container = New-FakeContainer $kind
        if ($script:WriterLifecycleChanged -and $kind -eq 'writer' -and $script:SourceDumpCount -gt 0) { $container.state.StartedAt = '2026-10-06T00:00:01Z' }
        if ($script:TargetOwnerMismatch -and $kind -eq 'target') { $container.labels.'com.stock.backup-verification' = 'different-verification' }
        return ($container | ConvertTo-Json -Depth 8 -Compress)
    }
    if ($Arguments[0] -eq 'ps') {
        if ('--filter' -in $Arguments) { return $(if ($script:MissingWriter) { 'e' * 12 } else { $script:FakeWriterId.Substring(0, 12) }) }
        return $(if ($script:ExistingResource) { 'stock-restore-' + $script:TestId } else { 'source' })
    }
    if ($Arguments[0] -eq 'volume') {
        if ($Arguments[1] -eq 'ls') { return $(if ($script:ExistingVolume) { 'stock-restore-' + $script:TestId + '-data' } else { 'original-data' }) }
        if ($Arguments[1] -eq 'inspect') { return ('{"com.stock.backup-verification":"' + $script:TestId + '"}') }
        if ($Arguments[1] -eq 'create') { return $Arguments[-1] }
    }
    if ($Arguments[0] -eq 'create') { return $script:FakeTargetId }
    if ($Arguments[0] -eq 'start') { $script:TargetRunning = $true; return $script:FakeTargetId }
    if ($Arguments[0] -eq 'stop') {
        if ($script:StopFails) { throw 'Simulated stop failure.' }
        $script:TargetRunning = $false; return $script:FakeTargetId
    }
    if ($Arguments[0] -eq 'cp') {
        if ($script:TamperBackup) { [IO.File]::AppendAllText($Arguments[1], 'tampered') }
        return ''
    }
    if ($Arguments[0] -eq 'exec') {
        if ('--version' -in $Arguments) { return ($Arguments[2] + '  Ver 8.4.11 for Linux') }
        if ('mysqldump' -in $Arguments) {
            if ($script:DumpFails) { throw 'Simulated dump failure.' }
            $contents = $script:Dump
            if ($Arguments[1] -eq $script:FakeSourceId) { $script:SourceDumpCount++ }
            if (($script:Mismatch -and $Arguments[1] -eq $script:FakeTargetId) -or
                ($script:SourceChange -and $script:SourceDumpCount -gt 1)) { $contents = $contents.Replace('buy', 'sell') }
            $stream = [IO.File]::Open($OutputPath, [IO.FileMode]::CreateNew)
            try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($contents); $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
            return
        }
        if ($Arguments[-1] -match '< /tmp/stock-backup.sql') {
            if ($script:ImportFails) { throw 'Simulated import failure.' }
            return ''
        }
        $sql = $Arguments[-1]
        if ($sql -eq 'SELECT 1;') { return '1' }
        if ($sql -eq 'SHOW REPLICA STATUS;') { return $(if ($script:ReplicaConfigured) { 'configured' } else { '' }) }
        if ($sql -match "'lowerCaseTableNames'") { return (@{ version = '8.4.11'; lowerCaseTableNames = 0; eventScheduler = $script:EventScheduler; otherClients = $script:OtherClients } | ConvertTo-Json -Compress) }
        if ($sql -match 'information_schema.TABLES t') {
            return (($script:Tables | ForEach-Object { @{ name = $_; engine = $script:Engine; type = 'BASE TABLE'; hasPrimaryKey = $script:HasPrimaryKey } | ConvertTo-Json -Compress }) -join "`n")
        }
        if ($sql -match 'UNION ALL') {
            $count = if ($script:RowCountMismatch -and $Arguments[1] -eq $script:FakeTargetId) { 2 } else { 1 }
            $rows = @($script:Tables | ForEach-Object { @{ table = $_; rows = $count } | ConvertTo-Json -Compress })
            if ($script:ReverseCounts -and $Arguments[1] -eq $script:FakeTargetId) { [array]::Reverse($rows) }
            return ($rows -join "`n")
        }
        if ($sql -match 'installed_rank') { return (@{ rank = 1; version = '1'; description = 'schema'; script = 'V1.sql'; checksum = 123; success = $script:FlywaySuccess } | ConvertTo-Json -Compress) }
    }
    throw 'Unexpected fake Docker command.'
}

function Invoke-TestVerification([string] $Mode = 'Verify', [bool] $Confirmed = $true) {
    Invoke-BackupRestoreVerification -Mode $Mode -SourceContainer source -WriterContainers @('app') -Database stock_agent_harness `
        -ConfirmSourceQuiescent $Confirmed -VerificationId $script:TestId
}
function Assert-NoMutations {
    Assert-True (@($script:Calls | Where-Object { $_.arguments[0] -in @('create', 'start', 'stop', 'cp') -or $_.arguments[1] -eq 'create' -or $_.outputPath }).Count -eq 0) 'Read-only failure performed a mutation.'
}

try {
    Test-Case 'reject unsafe database and container names' {
        foreach ($name in @('', '--option', '../db', 'db;DROP', 'db space')) { Assert-Throws { Assert-SafeName $name 'database' } 'Invalid' }
        Assert-Throws { Assert-SafeName '--option' 'container' } 'Invalid'
        Assert-Throws { Assert-SafeName 'mysql' 'database' } 'System database'
    }
    Test-Case 'Windows argument quoting preserves spaces quotes and trailing slashes' {
        $value = 'path with space\end\"quoted"\'
        $actual = Invoke-NativeProcess $nativeFixture @('echo', $value)
        Assert-True ($actual -ceq $value) 'Native argument did not survive quoting.'
    }
    Test-Case 'raw binary stdout remains byte-identical and cannot overwrite' {
        $path = Join-Path $script:Workspace 'bytes.sql'
        Invoke-NativeProcess $nativeFixture @('bytes') $path
        Assert-True (([IO.File]::ReadAllBytes($path) -join ',') -eq '0,255,13,10,65') 'Backup bytes were transcoded.'
        Assert-Throws { Invoke-NativeProcess $nativeFixture @('echo', 'overwrite') $path } '.'
        Assert-True (([IO.File]::ReadAllBytes($path) -join ',') -eq '0,255,13,10,65') 'Existing backup was overwritten.'
    }
    Test-Case 'native failure does not disclose stderr' {
        Assert-Throws { Invoke-NativeProcess $nativeFixture @('fail') } '^External command failed \(exit=9\)'
    }
    Test-Case 'native command timeout is bounded' {
        Assert-Throws { Invoke-NativeProcess $nativeFixture @('sleep') -TimeoutSeconds 1 } 'timed out'
    }
    Test-Case 'preflight only queries and inspects' {
        Reset-FakeDocker
        $result = Invoke-TestVerification 'Preflight'
        Assert-True ($result.state.tables.Count -eq $script:Tables.Count) 'Missing table inventory.'
        Assert-NoMutations
    }
    Test-Case 'confirmation required before Docker access' {
        Reset-FakeDocker
        Assert-Throws { Invoke-TestVerification -Confirmed $false } 'ConfirmSourceQuiescent'
        Assert-True ($script:Calls.Count -eq 0) 'Contacted Docker before confirmation.'
    }
    foreach ($case in @(
        @{ name = 'running writer'; setup = { $script:WriterRunning = $true }; error = 'writer is running' },
        @{ name = 'missing Compose writer'; setup = { $script:MissingWriter = $true }; error = 'missing from' },
        @{ name = 'nontransactional table'; setup = { $script:Engine = 'MyISAM' }; error = 'Only InnoDB' },
        @{ name = 'missing primary key'; setup = { $script:HasPrimaryKey = 0 }; error = 'Only InnoDB' },
        @{ name = 'other DB client'; setup = { $script:OtherClients = 1 }; error = 'other DB clients' },
        @{ name = 'active event scheduler'; setup = { $script:EventScheduler = 'ON' }; error = 'event scheduler' },
        @{ name = 'replica configuration'; setup = { $script:ReplicaConfigured = $true }; error = 'Replication configuration' },
        @{ name = 'failed migration'; setup = { $script:FlywaySuccess = 0 }; error = 'unsuccessful Flyway' }
    )) {
        Test-Case ("refuse " + $case.name) {
            Reset-FakeDocker; & $case.setup
            Assert-Throws { Invoke-TestVerification } $case.error
            Assert-NoMutations
        }
    }
    Test-Case 'reject evidence directory reuse and unsafe verification IDs' {
        $path = New-EvidenceDirectory 'mysql-existing'
        Assert-Throws { New-EvidenceDirectory 'mysql-existing' } 'directory exists'
        Assert-Throws { New-EvidenceDirectory '../escape' } 'Invalid verification ID'
        Assert-True (Test-Path -LiteralPath $path) 'Existing evidence disappeared.'
    }
    Test-Case 'reject restore onto source volume or published port' {
        Reset-FakeDocker
        $target = New-FakeContainer target
        $target.mounts[0].Name = 'original-data'
        Assert-Throws { Assert-IsolatedTarget $target (New-FakeContainer source) $script:TestId 'original-data' } 'isolation'
        $target = New-FakeContainer target
        $target.portBindings = [pscustomobject]@{ '3306/tcp' = @(@{ HostPort = '3309' }) }
        Assert-Throws { Assert-IsolatedTarget $target (New-FakeContainer source) $script:TestId $target.mounts[0].Name } 'isolation'
    }
    foreach ($case in @(
        @{ name = 'source container'; setup = { $target.id = $script:FakeSourceId } },
        @{ name = 'different image'; setup = { $target.image = 'sha256:' + ('e' * 64) } },
        @{ name = 'incorrect owner label'; setup = { $target.labels.'com.stock.backup-verification' = 'wrong' } },
        @{ name = 'connected network'; setup = { $target.networkMode = 'bridge' } }
    )) {
        Test-Case ("reject target " + $case.name) {
            Reset-FakeDocker
            $target = New-FakeContainer target
            & $case.setup
            Assert-Throws { Assert-IsolatedTarget $target (New-FakeContainer source) $script:TestId $target.mounts[0].Name } 'isolation'
        }
    }
    Test-Case 'backup root file or junction cannot redirect evidence writes' {
        $saved = $script:Workspace
        try {
            $script:Workspace = Join-Path $saved 'path-file'
            [void] [IO.Directory]::CreateDirectory($script:Workspace)
            [IO.File]::WriteAllText((Join-Path $script:Workspace 'data'), 'not a directory')
            Assert-Throws { New-EvidenceDirectory 'mysql-path-file' } 'must not be a file'
            $script:Workspace = Join-Path $saved 'path-junction'
            $outside = Join-Path $saved 'junction-destination'
            [void] [IO.Directory]::CreateDirectory($script:Workspace)
            [void] [IO.Directory]::CreateDirectory($outside)
            [void] (New-Item -ItemType Junction -Path (Join-Path $script:Workspace 'data') -Target $outside)
            Assert-Throws { New-EvidenceDirectory 'mysql-path-junction' } 'symlink or junction'
            Assert-True (@(Get-ChildItem -LiteralPath $outside -Force).Count -eq 0) 'Wrote evidence outside backup root.'
        } finally { $script:Workspace = $saved }
    }
    Test-Case 'complete fake verification with matching dumps and stopped isolated target' {
        Reset-FakeDocker
        $result = Invoke-TestVerification
        Assert-True ($result.status -eq 'PASSED' -and $result.targetStopped) 'Successful verification missing.'
        Assert-True ($result.backup.sha256 -ceq $result.restoredDump.sha256) 'Hashes differ.'
        $create = @($script:Calls | Where-Object { $_.arguments[0] -eq 'create' })[0].arguments
        Assert-True ('none' -in $create -and '--pull=never' -in $create -and $script:FakeImageId -in $create) 'Image/network guard missing.'
        Assert-True (@($script:Calls | Where-Object { $_.arguments[0] -eq 'stop' -and $_.arguments[-1] -ne $script:FakeTargetId }).Count -eq 0) 'Stopped an original container.'
        Assert-True (@($script:Calls | Where-Object { $_.arguments[0] -eq 'cp' -and $_.arguments[-1] -notlike "$($script:FakeTargetId):*" }).Count -eq 0) 'Copied backup to original.'
        Assert-True (@($script:Calls | Where-Object { $_.arguments[0] -in @('rm', 'pull') -or $_.arguments[1] -in @('rm', 'prune') }).Count -eq 0) 'Deleted resources or pulled an image.'
        $exports = @($script:Calls | Where-Object { 'mysqldump' -in $_.arguments -and $_.outputPath })
        Assert-True ($exports.Count -eq 3 -and @($exports | Where-Object { '--single-transaction' -notin $_.arguments -or '--order-by-primary' -notin $_.arguments -or '--hex-blob' -notin $_.arguments }).Count -eq 0) 'Dump consistency options missing.'
        $import = @($script:Calls | Where-Object { $_.arguments[-1] -match '< /tmp/stock-backup.sql' })[0]
        Assert-True ($import.arguments[-1] -match '--binary-mode=1' -and $import.arguments[1] -eq $script:FakeTargetId) 'Binary-preserving isolated import missing.'
    }
    Test-Case 'row-count query order does not cause a false mismatch' {
        Reset-FakeDocker; $script:ReverseCounts = $true
        $result = Invoke-TestVerification
        Assert-True ($result.status -eq 'PASSED') 'Equivalent row counts were order-dependent.'
    }
    foreach ($case in @(
        @{ name = 'existing target resource'; setup = { $script:ExistingResource = $true }; stage = 'resource-check' },
        @{ name = 'existing target volume'; setup = { $script:ExistingVolume = $true }; stage = 'resource-check' },
        @{ name = 'dump failure'; setup = { $script:DumpFails = $true }; stage = 'backup' },
        @{ name = 'writer restarted after backup'; setup = { $script:WriterLifecycleChanged = $true }; stage = 'backup' },
        @{ name = 'import failure'; setup = { $script:ImportFails = $true }; stage = 'restore' },
        @{ name = 'row count mismatch'; setup = { $script:RowCountMismatch = $true }; stage = 'compare' },
        @{ name = 'same row count but changed content'; setup = { $script:Mismatch = $true }; stage = 'compare' },
        @{ name = 'source changed during verification'; setup = { $script:SourceChange = $true }; stage = 'compare' },
        @{ name = 'backup file changed during verification'; setup = { $script:TamperBackup = $true }; stage = 'compare' },
        @{ name = 'target shutdown failure'; setup = { $script:StopFails = $true }; stage = 'stop-target' }
    )) {
        Test-Case ("fail closed on " + $case.name) {
            Reset-FakeDocker; & $case.setup
            Assert-Throws { Invoke-TestVerification } ("failed at " + $case.stage)
            $reportPath = Join-Path $script:Workspace ("data\backups\mysql\$($script:TestId)\result.json")
            $result = Get-Content -Encoding UTF8 -Raw $reportPath | ConvertFrom-Json
            Assert-True ($result.status -eq 'FAILED') 'Failed verification claimed success.'
            if ($case.stage -in @('restore', 'compare')) { Assert-True $result.targetStopped 'Owned target not stopped on failure.' }
            if ($case.stage -eq 'resource-check') { Assert-NoMutations }
        }
    }
    Test-Case 'ownership failure prevents restore and cleanup against unowned target' {
        Reset-FakeDocker; $script:TargetOwnerMismatch = $true
        Assert-Throws { Invoke-TestVerification } 'failed at create-target'
        Assert-True (@($script:Calls | Where-Object { $_.arguments[0] -in @('start', 'stop', 'cp') }).Count -eq 0) 'Operated on an unowned target.'
    }
    Write-Output "Passed $($script:Passed) offline tests. No Docker daemon, Broker or OpenAI calls."
} finally { $script:Workspace = $productionWorkspace }
