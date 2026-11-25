# AG Remote PC service installer: copies the program to %LOCALAPPDATA%\AGRemote, adds the firewall rule
# (one UAC prompt), Start menu / desktop / startup shortcuts, starts the service and opens the pairing page.
# Running it again upgrades in place (same folder as before) and keeps data\ (pairing, drafts). Double-click install.cmd to run.
#   -InstallDir <dir>  install somewhere else
#   -NoStart           don't start the service or open the pairing page
#   -NoShortcuts       don't create shortcuts (and no autostart)
#   -NoFirewall        don't add the firewall rule
param(
    [string]$InstallDir = (Join-Path $env:LOCALAPPDATA 'AGRemote'),
    [switch]$NoStart, [switch]$NoShortcuts, [switch]$NoFirewall
)
$ErrorActionPreference = 'Stop'
# Upgrading: when -InstallDir is not given, reuse the folder the existing startup shortcut points to
if (-not $PSBoundParameters.ContainsKey('InstallDir')) {
    $old = Join-Path ([Environment]::GetFolderPath('Startup')) 'AG Remote.lnk'
    if (Test-Path $old) {
        $arg = (New-Object -ComObject WScript.Shell).CreateShortcut($old).Arguments.Trim('"')
        if ($arg -and (Test-Path $arg)) { $InstallDir = Split-Path $arg }
    }
}
$Src = $PSScriptRoot
$Port = if ($env:AGR_PORT) { [int]$env:AGR_PORT } else { 5100 }
$RuleName = 'AG Remote (Tailscale)'
$AppName = 'AG Remote'

# ---------- messages (same languages as the app; falls back to English) ----------
$M = @{
  'en' = @{ title='AG Remote PC setup'; node='Node.js {0}'; noNode='Node.js was not found. Install Node.js 22.13 or newer (https://nodejs.org/) and run this again.'; oldNode='Node.js {0} is too old; 22.13 or newer is needed.'; copy='Installed to {0}'; wincap='Control helper ready'; wincapFail='Could not build the control helper (needs .NET Framework 4.8). Screen view and mouse control will not work.'; fw='Firewall rule added (Tailscale only)'; fwHave='Firewall rule already exists'; fwSkip='Firewall rule not added. If the phone cannot connect, run the installer again and accept the prompt.'; lnk='Shortcuts created (Start menu, desktop, startup)'; started='Service running on port {0}'; busy='Port {0} is already used by another program ({1}). The service was installed but not started.'; startFail='The service did not start. Log: {0}'; done='Done. The pairing page will open now.'; agNote='Antigravity must be installed and running on this PC.' }
  'zh-CN' = @{ title='AG Remote 电脑端安装'; node='Node.js {0}'; noNode='没找到 Node.js。请安装 Node.js 22.13 或更新版本（https://nodejs.org/）后再运行。'; oldNode='Node.js {0} 版本太旧，需要 22.13 或更新版本。'; copy='已安装到 {0}'; wincap='控制组件已就绪'; wincapFail='控制组件编译失败（需要 .NET Framework 4.8），电脑画面和鼠标控制将不可用。'; fw='已添加防火墙规则（只允许 Tailscale）'; fwHave='防火墙规则已存在'; fwSkip='没有添加防火墙规则。如果手机连不上，请重新运行安装程序并同意权限提示。'; lnk='已创建快捷方式（开始菜单、桌面、开机自启）'; started='服务已运行，端口 {0}'; busy='端口 {0} 已被其他程序占用（{1}），已安装但没有启动。'; startFail='服务没能启动，日志：{0}'; done='完成，马上打开配对页。'; agNote='这台电脑需要安装并打开 Antigravity。' }
  'zh-TW' = @{ title='AG Remote 電腦端安裝'; node='Node.js {0}'; noNode='找不到 Node.js。請安裝 Node.js 22.13 或更新版本（https://nodejs.org/）後再執行。'; oldNode='Node.js {0} 版本太舊，需要 22.13 或更新版本。'; copy='已安裝到 {0}'; wincap='控制元件已就緒'; wincapFail='控制元件編譯失敗（需要 .NET Framework 4.8），電腦畫面和滑鼠控制將無法使用。'; fw='已新增防火牆規則（僅允許 Tailscale）'; fwHave='防火牆規則已存在'; fwSkip='未新增防火牆規則。如果手機無法連線，請重新執行安裝程式並同意權限提示。'; lnk='已建立捷徑（開始功能表、桌面、開機啟動）'; started='服務已執行，連接埠 {0}'; busy='連接埠 {0} 已被其他程式佔用（{1}），已安裝但未啟動。'; startFail='服務未能啟動，記錄：{0}'; done='完成，即將開啟配對頁面。'; agNote='這台電腦需要安裝並開啟 Antigravity。' }
  'ja' = @{ title='AG Remote PC セットアップ'; node='Node.js {0}'; noNode='Node.js が見つかりません。Node.js 22.13 以降（https://nodejs.org/）をインストールしてから再実行してください。'; oldNode='Node.js {0} は古すぎます。22.13 以降が必要です。'; copy='{0} にインストールしました'; wincap='操作コンポーネントの準備完了'; wincapFail='操作コンポーネントをビルドできませんでした（.NET Framework 4.8 が必要）。画面表示とマウス操作は使えません。'; fw='ファイアウォール規則を追加しました（Tailscale のみ）'; fwHave='ファイアウォール規則は追加済みです'; fwSkip='ファイアウォール規則を追加しませんでした。スマホから接続できない場合は再実行して許可してください。'; lnk='ショートカットを作成しました（スタート メニュー、デスクトップ、スタートアップ）'; started='サービス実行中（ポート {0}）'; busy='ポート {0} は別のプログラム（{1}）が使用中です。インストールしましたが起動していません。'; startFail='サービスを起動できませんでした。ログ：{0}'; done='完了しました。ペアリング画面を開きます。'; agNote='この PC に Antigravity をインストールして起動しておく必要があります。' }
  'ko' = @{ title='AG Remote PC 설치'; node='Node.js {0}'; noNode='Node.js를 찾을 수 없습니다. Node.js 22.13 이상(https://nodejs.org/)을 설치한 뒤 다시 실행하세요.'; oldNode='Node.js {0} 버전이 너무 낮습니다. 22.13 이상이 필요합니다.'; copy='{0}에 설치했습니다'; wincap='제어 구성 요소 준비 완료'; wincapFail='제어 구성 요소를 빌드하지 못했습니다(.NET Framework 4.8 필요). 화면 보기와 마우스 제어를 쓸 수 없습니다.'; fw='방화벽 규칙 추가됨(Tailscale만 허용)'; fwHave='방화벽 규칙이 이미 있습니다'; fwSkip='방화벽 규칙을 추가하지 않았습니다. 휴대폰이 연결되지 않으면 다시 실행해 허용하세요.'; lnk='바로 가기 생성됨(시작 메뉴, 바탕 화면, 시작 프로그램)'; started='서비스 실행 중(포트 {0})'; busy='포트 {0}을(를) 다른 프로그램({1})이 사용 중입니다. 설치했지만 시작하지 않았습니다.'; startFail='서비스를 시작하지 못했습니다. 로그: {0}'; done='완료. 페어링 페이지를 엽니다.'; agNote='이 PC에 Antigravity가 설치되어 실행 중이어야 합니다.' }
  'es' = @{ title='Instalación de AG Remote para PC'; node='Node.js {0}'; noNode='No se encontró Node.js. Instala Node.js 22.13 o posterior (https://nodejs.org/) y vuelve a ejecutar esto.'; oldNode='Node.js {0} es demasiado antiguo; se necesita 22.13 o posterior.'; copy='Instalado en {0}'; wincap='Componente de control listo'; wincapFail='No se pudo compilar el componente de control (requiere .NET Framework 4.8). La vista de pantalla y el control del ratón no funcionarán.'; fw='Regla de firewall añadida (solo Tailscale)'; fwHave='La regla de firewall ya existe'; fwSkip='No se añadió la regla de firewall. Si el teléfono no conecta, vuelve a ejecutar el instalador y acepta el aviso.'; lnk='Accesos directos creados (menú Inicio, escritorio, inicio automático)'; started='Servicio en ejecución en el puerto {0}'; busy='El puerto {0} lo usa otro programa ({1}). Se instaló pero no se inició.'; startFail='El servicio no se inició. Registro: {0}'; done='Listo. Se abrirá la página de vinculación.'; agNote='Antigravity debe estar instalado y abierto en este PC.' }
  'fr' = @{ title='Installation d''AG Remote pour PC'; node='Node.js {0}'; noNode='Node.js est introuvable. Installez Node.js 22.13 ou plus récent (https://nodejs.org/) puis relancez.'; oldNode='Node.js {0} est trop ancien ; la version 22.13 ou plus récente est nécessaire.'; copy='Installé dans {0}'; wincap='Composant de contrôle prêt'; wincapFail='Impossible de compiler le composant de contrôle (.NET Framework 4.8 requis). L''affichage de l''écran et la souris ne fonctionneront pas.'; fw='Règle de pare-feu ajoutée (Tailscale uniquement)'; fwHave='La règle de pare-feu existe déjà'; fwSkip='Règle de pare-feu non ajoutée. Si le téléphone ne se connecte pas, relancez l''installation et acceptez la demande.'; lnk='Raccourcis créés (menu Démarrer, Bureau, démarrage)'; started='Service actif sur le port {0}'; busy='Le port {0} est utilisé par un autre programme ({1}). Installé mais non démarré.'; startFail='Le service n''a pas démarré. Journal : {0}'; done='Terminé. La page d''association va s''ouvrir.'; agNote='Antigravity doit être installé et ouvert sur ce PC.' }
  'de' = @{ title='AG Remote PC-Einrichtung'; node='Node.js {0}'; noNode='Node.js wurde nicht gefunden. Installiere Node.js 22.13 oder neuer (https://nodejs.org/) und starte dies erneut.'; oldNode='Node.js {0} ist zu alt; 22.13 oder neuer wird benötigt.'; copy='Installiert nach {0}'; wincap='Steuerkomponente bereit'; wincapFail='Die Steuerkomponente konnte nicht erstellt werden (.NET Framework 4.8 nötig). Bildschirmansicht und Maussteuerung funktionieren nicht.'; fw='Firewall-Regel hinzugefügt (nur Tailscale)'; fwHave='Firewall-Regel existiert bereits'; fwSkip='Keine Firewall-Regel hinzugefügt. Falls das Handy keine Verbindung bekommt, Installation erneut ausführen und die Abfrage bestätigen.'; lnk='Verknüpfungen erstellt (Startmenü, Desktop, Autostart)'; started='Dienst läuft auf Port {0}'; busy='Port {0} wird von einem anderen Programm ({1}) verwendet. Installiert, aber nicht gestartet.'; startFail='Der Dienst ist nicht gestartet. Protokoll: {0}'; done='Fertig. Die Kopplungsseite wird geöffnet.'; agNote='Antigravity muss auf diesem PC installiert und geöffnet sein.' }
  'ru' = @{ title='Установка AG Remote для ПК'; node='Node.js {0}'; noNode='Node.js не найден. Установите Node.js 22.13 или новее (https://nodejs.org/) и запустите снова.'; oldNode='Node.js {0} слишком старый; нужна версия 22.13 или новее.'; copy='Установлено в {0}'; wincap='Компонент управления готов'; wincapFail='Не удалось собрать компонент управления (нужен .NET Framework 4.8). Просмотр экрана и управление мышью работать не будут.'; fw='Правило брандмауэра добавлено (только Tailscale)'; fwHave='Правило брандмауэра уже есть'; fwSkip='Правило брандмауэра не добавлено. Если телефон не подключается, запустите установщик снова и подтвердите запрос.'; lnk='Ярлыки созданы (меню «Пуск», рабочий стол, автозапуск)'; started='Служба работает на порту {0}'; busy='Порт {0} занят другой программой ({1}). Установлено, но не запущено.'; startFail='Служба не запустилась. Журнал: {0}'; done='Готово. Сейчас откроется страница сопряжения.'; agNote='На этом ПК должен быть установлен и запущен Antigravity.' }
  'pt' = @{ title='Instalação do AG Remote para PC'; node='Node.js {0}'; noNode='Node.js não encontrado. Instale o Node.js 22.13 ou mais recente (https://nodejs.org/) e execute novamente.'; oldNode='Node.js {0} é antigo demais; é necessário 22.13 ou mais recente.'; copy='Instalado em {0}'; wincap='Componente de controlo pronto'; wincapFail='Não foi possível compilar o componente de controlo (requer .NET Framework 4.8). A visualização do ecrã e o rato não funcionarão.'; fw='Regra de firewall adicionada (só Tailscale)'; fwHave='A regra de firewall já existe'; fwSkip='Regra de firewall não adicionada. Se o telefone não ligar, execute o instalador novamente e aceite o pedido.'; lnk='Atalhos criados (menu Iniciar, ambiente de trabalho, arranque)'; started='Serviço em execução na porta {0}'; busy='A porta {0} está a ser usada por outro programa ({1}). Instalado mas não iniciado.'; startFail='O serviço não iniciou. Registo: {0}'; done='Concluído. A página de emparelhamento vai abrir.'; agNote='O Antigravity tem de estar instalado e aberto neste PC.' }
}
$ui = (Get-UICulture).Name.ToLower()
$L = if ($ui -match '^zh-(tw|hk|mo)|hant') { 'zh-TW' } elseif ($ui -like 'zh*') { 'zh-CN' } else { $ui.Split('-')[0] }
if (-not $M.ContainsKey($L)) { $L = 'en' }
function Msg($key, [object[]]$a) { $s = $M[$L][$key]; if (-not $s) { $s = $M['en'][$key] }; if ($a) { [string]::Format($s, $a) } else { $s } }
function Say($key, [object[]]$a, $color = 'Gray') { Write-Host (Msg $key $a) -ForegroundColor $color }
function Fail($key, [object[]]$a) { Say $key $a 'Red'; exit 1 }

Write-Host ('== ' + (Msg 'title') + ' ==') -ForegroundColor Cyan
Say 'agNote'

# ---------- 1. Node.js: bundled node.exe → NODE → repo _toolchain → PATH ----------
$node = $null
foreach ($c in @((Join-Path $Src 'node.exe'), $env:NODE, (Join-Path $Src '..\..\..\_toolchain\node\node.exe'))) {
    if ($c -and (Test-Path $c)) { $node = (Resolve-Path $c).Path; break }
}
if (-not $node) { $cmd = Get-Command node -ErrorAction SilentlyContinue; if ($cmd) { $node = $cmd.Source } }
if (-not $node) { Fail 'noNode' }
$ver = (& $node -p 'process.versions.node').Trim()
$a, $b = $ver.Split('.')[0..1] | ForEach-Object { [int]$_ }
# node:sqlite works without flags from 22.13 / 23.4
if (-not (($a -gt 23) -or ($a -eq 23 -and $b -ge 4) -or ($a -eq 22 -and $b -ge 13))) { Fail 'oldNode' @($ver) }
Say 'node' @($ver)

# ---------- 2. copy program files (data\ is never touched) ----------
function Stop-Installed($dir) {
    $d = [IO.Path]::GetFullPath($dir).TrimEnd('\')
    Get-CimInstance Win32_Process -Filter "Name='cmd.exe' OR Name='node.exe' OR Name='wincap.exe'" -ErrorAction SilentlyContinue |
        Where-Object { ($_.CommandLine -and $_.CommandLine -like "*$d*") -or ($_.ExecutablePath -and $_.ExecutablePath -like "$d\*") } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
$Files = 'server.mjs', 'pair-ui.html', 'run-forever.cmd', 'start-service.vbs', 'open-pair.vbs', 'wincap.cs', 'build-wincap.cmd',
         'wincap.exe', 'app.ico', 'app-icon.svg', 'AGRemote.apk', 'uninstall.cmd', 'uninstall.ps1', 'README.txt', 'node-LICENSE.txt'
$same = [IO.Path]::GetFullPath($Src).TrimEnd('\') -ieq [IO.Path]::GetFullPath($InstallDir).TrimEnd('\')
if (-not $same) {
    Stop-Installed $InstallDir
    Start-Sleep -Milliseconds 600
    New-Item -ItemType Directory -Force $InstallDir | Out-Null
    foreach ($f in $Files) { $p = Join-Path $Src $f; if (Test-Path $p) { Copy-Item $p (Join-Path $InstallDir $f) -Force } }
    if ($node -ne (Join-Path $InstallDir 'node.exe')) { Copy-Item $node (Join-Path $InstallDir 'node.exe') -Force }
}
$nodeInst = Join-Path $InstallDir 'node.exe'
Say 'copy' @($InstallDir)

# ---------- 3. control helper (screen capture + mouse/keyboard) ----------
if (-not (Test-Path (Join-Path $InstallDir 'wincap.exe'))) {
    & (Join-Path $InstallDir 'build-wincap.cmd') | Out-Null
    if ($LASTEXITCODE -ne 0) { Say 'wincapFail' $null 'Yellow' } else { Say 'wincap' }
} else { Say 'wincap' }

# ---------- 4. firewall: allow inbound TCP port only from Tailscale (100.64.0.0/10) ----------
if (-not $NoFirewall) {
    $have = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
    if ($have) { Say 'fwHave' }
    else {
        $cmd = "New-NetFirewallRule -DisplayName '$RuleName' -Direction Inbound -Protocol TCP -LocalPort $Port -RemoteAddress 100.64.0.0/10 -Action Allow -Profile Any | Out-Null"
        try {
            Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList @('-NoProfile', '-Command', $cmd)
        } catch { }
        if (Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue) { Say 'fw' } else { Say 'fwSkip' $null 'Yellow' }
    }
}

# ---------- 5. shortcuts ----------
if (-not $NoShortcuts) {
    $sh = New-Object -ComObject WScript.Shell
    $wscript = Join-Path $env:WINDIR 'System32\wscript.exe'
    $icon = Join-Path $InstallDir 'app.ico'
    function New-Link($path, $script, $desc) {
        $l = $sh.CreateShortcut($path)
        $l.TargetPath = $wscript
        $l.Arguments = '"' + (Join-Path $InstallDir $script) + '"'
        $l.WorkingDirectory = $InstallDir
        $l.Description = $desc
        if (Test-Path $icon) { $l.IconLocation = "$icon,0" }
        $l.Save()
    }
    $programs = [Environment]::GetFolderPath('Programs')
    New-Link (Join-Path ([Environment]::GetFolderPath('Startup')) "$AppName.lnk") 'start-service.vbs' "$AppName service"
    New-Link (Join-Path $programs "$AppName.lnk") 'open-pair.vbs' "$AppName pairing"
    New-Link (Join-Path ([Environment]::GetFolderPath('Desktop')) "$AppName.lnk") 'open-pair.vbs' "$AppName pairing"
    # older versions used a Chinese-named startup shortcut; remove it so the service is not started twice
    $legacy = Join-Path ([Environment]::GetFolderPath('Startup')) 'AG遥控服务.lnk'
    if ((Test-Path $legacy) -and -not $same) {
        $t = $sh.CreateShortcut($legacy)
        if ($t.Arguments -like "*$InstallDir*") { Remove-Item $legacy -Force }
    }
    Say 'lnk'
}

# ---------- 6. start + open pairing page ----------
if (-not $NoStart) {
    $listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listen) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listen.OwningProcess)" -ErrorAction SilentlyContinue
        $path = if ($owner) { $owner.CommandLine } else { "PID $($listen.OwningProcess)" }
        if ($path -notlike "*$InstallDir*") { Say 'busy' @($Port, $path) 'Yellow'; exit 0 }
    } else {
        Start-Process (Join-Path $env:WINDIR 'System32\wscript.exe') -ArgumentList ('"' + (Join-Path $InstallDir 'start-service.vbs') + '"') -WorkingDirectory $InstallDir
        for ($i = 0; $i -lt 30 -and -not $listen; $i++) {
            Start-Sleep -Milliseconds 500
            $listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        }
        if (-not $listen) { Fail 'startFail' @((Join-Path $InstallDir 'data\server.log')) }
    }
    Say 'started' @($Port) 'Green'
    Say 'done' $null 'Green'
    Start-Process (Join-Path $env:WINDIR 'System32\wscript.exe') -ArgumentList ('"' + (Join-Path $InstallDir 'open-pair.vbs') + '"')
}
