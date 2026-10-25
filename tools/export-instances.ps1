# Zips each Pandora instance's .minecraft folder so it can be loaded as a game directory in Amethyst (iOS).
$src = "$env:APPDATA\PandoraLauncher\instances"
$out = Join-Path $PSScriptRoot "export"
New-Item -ItemType Directory -Force $out | Out-Null

foreach ($inst in Get-ChildItem -LiteralPath $src -Directory) {
    $mc = Join-Path $inst.FullName ".minecraft"
    if (-not (Test-Path -LiteralPath $mc)) { continue }
    $info = Get-Content -LiteralPath (Join-Path $inst.FullName "info_v1.json") -Raw | ConvertFrom-Json
    $name = $inst.Name -replace '[\[\]]', ''
    $zip = Join-Path $out "$name.zip"
    Remove-Item -LiteralPath $zip -ErrorAction SilentlyContinue
    # tar.exe ships with Windows and has no 2GB limit, unlike Compress-Archive
    # info_v1.json (and icon.png) at the zip root tell the iOS importer the version and loader
    $meta = @("info_v1.json") + @(if (Test-Path -LiteralPath (Join-Path $inst.FullName "icon.png")) { "icon.png" })
    tar.exe -a -c -f $zip --exclude=logs --exclude=crash-reports --exclude=downloads -C $mc . -C $inst.FullName @meta
    if ($LASTEXITCODE -ne 0) { Write-Warning "Failed: $($inst.Name)"; continue }
    "{0,-40} MC {1}  loader {2}" -f $name, $info.minecraft_version, $info.loader
}
