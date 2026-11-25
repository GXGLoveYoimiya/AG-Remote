# AG Remote PC service uninstaller: stops the service, removes shortcuts, the firewall rule (one UAC prompt)
# and the program files. data\ (pairing, drafts, logs) is kept unless -Purge is given.
#   -InstallDir <dir>  where it was installed (default: the folder this script is in)
#   -Purge             also delete data\
#   -WhatIf            only list what would be done
param([string]$InstallDir = $PSScriptRoot, [switch]$Purge, [switch]$WhatIf)
$RuleName = 'AG Remote (Tailscale)'
$AppName = 'AG Remote'
$dir = [IO.Path]::GetFullPath($InstallDir).TrimEnd('\')

function Do-It($desc, [scriptblock]$action) {
    if ($WhatIf) { Write-Host "[WhatIf] $desc" } else { & $action; Write-Host $desc }
}

# 1. processes started from this folder: watchdog cmd first (otherwise node is restarted 3 s later), then node, wincap
foreach ($name in 'cmd.exe', 'node.exe', 'wincap.exe') {
    Get-CimInstance Win32_Process -Filter "Name='$name'" -ErrorAction SilentlyContinue |
        Where-Object { ($_.CommandLine -and $_.CommandLine -like "*$dir*") -or ($_.ExecutablePath -and $_.ExecutablePath -like "$dir\*") } |
        ForEach-Object { $id = $_.ProcessId; Do-It "Stopped $name (PID $id)" { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue } }
}

# 2. shortcuts that point into this folder
$sh = New-Object -ComObject WScript.Shell
$links = @(
    (Join-Path ([Environment]::GetFolderPath('Startup')) "$AppName.lnk"),
    (Join-Path ([Environment]::GetFolderPath('Startup')) 'AG遥控服务.lnk'),
    (Join-Path ([Environment]::GetFolderPath('Programs')) "$AppName.lnk"),
    (Join-Path ([Environment]::GetFolderPath('Desktop')) "$AppName.lnk")
)
foreach ($l in $links) {
    if ((Test-Path $l) -and ($sh.CreateShortcut($l).Arguments -like "*$dir*")) { Do-It "Removed $l" { Remove-Item $l -Force } }
}

# 3. firewall rule
if (Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue) {
    Do-It "Removed firewall rule '$RuleName'" {
        try { Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList @('-NoProfile', '-Command', "Remove-NetFirewallRule -DisplayName '$RuleName'") } catch { }
    }
}

# 4. program files (only the ones the installer puts there)
Start-Sleep -Milliseconds 600
$files = 'server.mjs', 'pair-ui.html', 'run-forever.cmd', 'start-service.vbs', 'open-pair.vbs', 'wincap.cs', 'build-wincap.cmd',
         'wincap.exe', 'app.ico', 'app-icon.svg', 'AGRemote.apk', 'README.txt', 'node-LICENSE.txt', 'node.exe'
$isInstall = Test-Path (Join-Path $dir 'node.exe')  # a source checkout has no node.exe here: never delete files from it
if ($isInstall) {
    foreach ($f in $files) { $p = Join-Path $dir $f; if (Test-Path $p) { Do-It "Deleted $p" { Remove-Item $p -Force } } }
    if ($Purge -and (Test-Path (Join-Path $dir 'data'))) { Do-It "Deleted $dir\data" { Remove-Item (Join-Path $dir 'data') -Recurse -Force } }
    # the uninstaller itself and the folder go last (a running script can be deleted on Windows once loaded)
    if (-not $WhatIf) {
        Remove-Item (Join-Path $dir 'uninstall.cmd') -Force -ErrorAction SilentlyContinue
        Remove-Item (Join-Path $dir 'uninstall.ps1') -Force -ErrorAction SilentlyContinue
        if (-not (Get-ChildItem $dir -Force -ErrorAction SilentlyContinue)) { Remove-Item $dir -Force -ErrorAction SilentlyContinue }
    }
}
if ($Purge -or -not (Test-Path (Join-Path $dir 'data'))) { Write-Host 'Done.' }
else { Write-Host "Done. Pairing data kept in $dir\data (run with -Purge to delete it)." }
