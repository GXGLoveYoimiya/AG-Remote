# Render bridge\app-icon.svg (same artwork as the Android launcher icon) into bridge\app.ico
# with 256/128/64/48/32/24/16 px images. Uses Microsoft Edge (headless) to rasterize the SVG.
$ErrorActionPreference = 'Stop'
$Bridge = Join-Path (Split-Path $PSScriptRoot -Parent) 'bridge'
$Tmp = Join-Path $env:TEMP 'agremote-icon'
New-Item -ItemType Directory -Force $Tmp | Out-Null

$edge = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $edge) { throw 'Microsoft Edge not found' }

# 1. rasterize at 1024 px on a transparent background
$svg = [IO.File]::ReadAllText((Join-Path $Bridge 'app-icon.svg')) -replace 'width="256" height="256"', 'width="1024" height="1024"'
[IO.File]::WriteAllText("$Tmp\icon.html", "<!doctype html><html><body style='margin:0;background:transparent'>$svg</body></html>")
$big = "$Tmp\icon1024.png"
if (Test-Path $big) { Remove-Item $big }
$p = Start-Process $edge -PassThru -WindowStyle Hidden -ArgumentList @('--headless=new', '--disable-gpu', '--hide-scrollbars',
    '--default-background-color=00000000', "--user-data-dir=`"$Tmp\profile`"", '--window-size=1024,1024',
    '--force-device-scale-factor=1', "--screenshot=`"$big`"", "file:///$($Tmp -replace '\\', '/')/icon.html")
$p.WaitForExit(30000) | Out-Null
if (-not (Test-Path $big)) { throw 'Edge did not produce the image' }

# 2. downscale to each icon size and pack them as PNG entries into one .ico
Add-Type -AssemblyName System.Drawing
$src = [Drawing.Image]::FromFile($big)
$sizes = 256, 128, 64, 48, 32, 24, 16
$pngs = foreach ($n in $sizes) {
    $bmp = New-Object Drawing.Bitmap $n, $n
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = 'HighQualityBicubic'; $g.PixelOffsetMode = 'HighQuality'; $g.SmoothingMode = 'AntiAlias'
    $g.DrawImage($src, 0, 0, $n, $n); $g.Dispose()
    $ms = New-Object IO.MemoryStream; $bmp.Save($ms, [Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    , $ms.ToArray()
}
$src.Dispose()
$ico = New-Object IO.MemoryStream; $w = New-Object IO.BinaryWriter $ico
$w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]$sizes.Count)
$off = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $n = $sizes[$i]; $d = $pngs[$i]
    $w.Write([Byte]($n % 256)); $w.Write([Byte]($n % 256)); $w.Write([Byte]0); $w.Write([Byte]0)   # 256 is stored as 0
    $w.Write([UInt16]1); $w.Write([UInt16]32); $w.Write([UInt32]$d.Length); $w.Write([UInt32]$off)
    $off += $d.Length
}
foreach ($d in $pngs) { $w.Write($d) }
$w.Flush()
[IO.File]::WriteAllBytes((Join-Path $Bridge 'app.ico'), $ico.ToArray())
Write-Host "OK: $(Join-Path $Bridge 'app.ico')"
