# Build the AG Remote PC package: a zip with everything needed on any Windows 10/11 PC
# (Node.js runtime, service, pairing page, control helper, phone APK, installer).
#   -Out <zip>   output file (default: AGRemote-PC.zip two levels above the repo, next to AGRemote.apk;
#                or dist\AGRemote-PC.zip inside the repo when that folder layout is not there)
# Needs: node.exe (env NODE, the repo's _toolchain, or PATH) and the APK built by android\build.sh.
param([string]$Out)
$ErrorActionPreference = 'Stop'
$Repo = Split-Path $PSScriptRoot -Parent
$Bridge = Join-Path $Repo 'bridge'
$Ws = [IO.Path]::GetFullPath((Join-Path $Repo '..\..'))
if (-not $Out) {
    $Out = if (Test-Path (Join-Path $Ws '_toolchain')) { Join-Path $Ws 'AGRemote-PC.zip' } else { Join-Path $Repo 'dist\AGRemote-PC.zip' }
}

$Stage = Join-Path $env:TEMP 'agremote-pkg\AGRemote-PC'
if (Test-Path (Split-Path $Stage)) { Remove-Item (Split-Path $Stage) -Recurse -Force }
New-Item -ItemType Directory -Force $Stage | Out-Null

# 1. service files
$files = 'server.mjs', 'pair-ui.html', 'run-forever.cmd', 'start-service.vbs', 'open-pair.vbs',
         'wincap.cs', 'build-wincap.cmd', 'install.cmd', 'install.ps1', 'uninstall.cmd', 'uninstall.ps1'
foreach ($f in $files) { Copy-Item (Join-Path $Bridge $f) (Join-Path $Stage $f) }

# 2. Node.js runtime (+ its license, required when redistributing)
$node = $null
foreach ($c in @($env:NODE, (Join-Path $Ws '_toolchain\node\node.exe'))) { if ($c -and (Test-Path $c)) { $node = (Resolve-Path $c).Path; break } }
if (-not $node) { $cmd = Get-Command node -ErrorAction SilentlyContinue; if ($cmd) { $node = $cmd.Source } }
if (-not $node) { throw 'node.exe not found (set NODE)' }
Copy-Item $node (Join-Path $Stage 'node.exe')
$lic = Join-Path (Split-Path $node) 'LICENSE'
if (Test-Path $lic) { Copy-Item $lic (Join-Path $Stage 'node-LICENSE.txt') }
Write-Host "Node.js $(& $node -v) from $node"

# 3. control helper, built fresh from wincap.cs
& (Join-Path $Bridge 'build-wincap.cmd') (Join-Path $Stage 'wincap.exe') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'wincap.exe build failed' }

# 4. phone APK (served to the phone at http://<pc>:5100/dl)
$apk = @((Join-Path $Repo 'android\app\build\outputs\apk\release\app-release.apk'), (Join-Path $Ws 'AGRemote.apk')) |
    Where-Object { Test-Path $_ } | Sort-Object { (Get-Item $_).LastWriteTime } -Descending | Select-Object -First 1
if ($apk) { Copy-Item $apk (Join-Path $Stage 'AGRemote.apk'); Write-Host "APK from $apk" }
else { Write-Warning 'No APK found: run android\build.sh first. The package will not offer the phone app download.' }

# 5. icon: same artwork as the Android launcher icon (bridgepp-icon.svg; app.ico is rendered from it)
foreach ($f in 'app.ico', 'app-icon.svg') { Copy-Item (Join-Path $Bridge $f) (Join-Path $Stage $f) }

# 6. README
$readme = @'
AG Remote - PC service
======================

Lets the AG Remote phone app control Antigravity on this PC over Tailscale.

Requirements: Windows 10/11, Antigravity installed and open, Tailscale on this PC and on the phone (same account).

Install:   double-click install.cmd. It installs to %LOCALAPPDATA%\AGRemote, asks once for permission to add a
           firewall rule (Tailscale addresses only), starts the service and opens the pairing page.
Pairing:   Start menu or desktop -> "AG Remote". Enter the address and the 6-digit code shown there in the phone app.
Uninstall: %LOCALAPPDATA%\AGRemote\uninstall.cmd  (add -Purge to also delete pairing data)

-----
AG Remote 电脑端：让手机上的 AG Remote App 通过 Tailscale 控制这台电脑上的 Antigravity。
需要：Windows 10/11；已安装并打开 Antigravity；电脑和手机都装好 Tailscale 并登录同一账号。
安装：双击 install.cmd（装到 %LOCALAPPDATA%\AGRemote，会请求一次权限来添加只允许 Tailscale 的防火墙规则），装完自动打开配对页。
配对：开始菜单或桌面上的“AG Remote”，把页面上的地址和 6 位配对码填进手机 App。
卸载：运行 %LOCALAPPDATA%\AGRemote\uninstall.cmd（加 -Purge 连配对数据一起删除）。

AG Remote 電腦端：讓手機上的 AG Remote App 透過 Tailscale 控制這台電腦上的 Antigravity。
安裝：按兩下 install.cmd；配對：開始功能表或桌面上的「AG Remote」；解除安裝：%LOCALAPPDATA%\AGRemote\uninstall.cmd。

AG Remote PC 版：スマホの AG Remote アプリから Tailscale 経由でこの PC の Antigravity を操作します。
インストール：install.cmd をダブルクリック。ペアリング：スタート メニューまたはデスクトップの「AG Remote」。
アンインストール：%LOCALAPPDATA%\AGRemote\uninstall.cmd
'@
[IO.File]::WriteAllText((Join-Path $Stage 'README.txt'), ($readme -replace "`r?`n", "`r`n"), (New-Object Text.UTF8Encoding $true))

# 7. zip
New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
if (Test-Path $Out) { Remove-Item $Out -Force }
Compress-Archive -Path $Stage -DestinationPath $Out -CompressionLevel Optimal
Write-Host ("OK: {0} ({1:N1} MB)" -f $Out, ((Get-Item $Out).Length / 1MB))
