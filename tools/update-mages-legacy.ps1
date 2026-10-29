# Ставит/обновляет мод Mage's Legacy (NeoForge 1.21.1) и Photon с его библиотеками в папку mods TLauncher.
# Запуск: двойной клик по update-mages-legacy.bat (лежит рядом).
#   -Stable           взять последний стабильный релиз (v*), а не свежую dev-сборку
#   -VersionName "…"  имя версии NeoForge в TLauncher (по умолчанию ищется сама)
#   -GameDir "…"      папка игры (по умолчанию %APPDATA%\.minecraft)
param(
    [switch]$Stable,
    [string]$VersionName = '',
    [string]$GameDir = (Join-Path $env:APPDATA '.minecraft')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo = 'stardely62-oss/mages-legacy'
$Headers = @{ 'User-Agent' = 'mages-legacy-updater' }
# NeoForge не старше этой версии нужен KilaGraph (библиотеке Photon)
$MinNeoForge = [version]'21.1.217'

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }

# Версия NeoForge из json версии TLauncher, или $null, если это не NeoForge 1.21.1
function NeoForgeOf($dir) {
    foreach ($json in Get-ChildItem $dir -Filter '*.json' -ErrorAction SilentlyContinue) {
        $text = Get-Content $json.FullName -Raw
        foreach ($pattern in @('net\.neoforged:neoforge:(21\.1\.[0-9]+)', 'fml\.neoForgeVersion"\s*,\s*"(21\.1\.[0-9]+)', 'neoforge-(21\.1\.[0-9]+)')) {
            $m = [regex]::Match($text, $pattern)
            if ($m.Success) { return [version]$m.Groups[1].Value }
        }
    }
    return $null
}

# Свежий основной файл мода с Modrinth для NeoForge 1.21.1; старые копии (по префиксу имени) удаляются
function InstallFromModrinth($project, $title, $prefix) {
    $query = 'game_versions=' + [uri]::EscapeDataString('["1.21.1"]') + '&loaders=' + [uri]::EscapeDataString('["neoforge"]')
    $versions = @(Invoke-RestMethod "https://api.modrinth.com/v2/project/$project/version?$query" -Headers $Headers)
    if ($versions.Count -eq 0) { throw "$title для NeoForge 1.21.1 не найден на Modrinth." }
    $v = $versions[0]
    $file = $v.files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $file) { $file = $v.files[0] }
    if (Test-Path (Join-Path $mods $file.filename)) {
        Say "$title уже последней версии."
        return
    }
    $tmp = Join-Path $env:TEMP $file.filename
    Invoke-WebRequest $file.url -OutFile $tmp -Headers $Headers -UseBasicParsing
    Get-ChildItem $mods -Filter "$prefix*.jar" | Remove-Item -Force
    Move-Item $tmp (Join-Path $mods $file.filename) -Force
    Say "$title установлен: $($file.filename)" Green
}

try {
    Say "== Mage's Legacy: обновление ==" Cyan

    # 1. Версия NeoForge 1.21.1 в TLauncher: названная или найденная сама
    $versionsDir = Join-Path $GameDir 'versions'
    if ($VersionName) {
        $versionDir = Join-Path $versionsDir $VersionName
        if (-not (Test-Path $versionDir)) {
            throw "Не нашёл версию '$VersionName' в $versionsDir. Установи её в TLauncher и запусти один раз."
        }
        $neo = NeoForgeOf $versionDir
        if (-not $neo) { throw "Версия '$VersionName' не похожа на NeoForge 1.21.1." }
    } else {
        $found = @(Get-ChildItem $versionsDir -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $n = NeoForgeOf $_.FullName; if ($n) { [pscustomobject]@{ Dir = $_; Neo = $n } } } |
            Sort-Object Neo -Descending)
        if ($found.Count -eq 0) {
            throw "Не нашёл NeoForge 1.21.1 в $versionsDir. Установи в TLauncher версию NeoForge 1.21.1 и запусти её один раз."
        }
        $versionDir = $found[0].Dir.FullName
        $VersionName = $found[0].Dir.Name
        $neo = $found[0].Neo
    }
    Say "Версия: $VersionName (NeoForge $neo)"
    if ($neo -lt $MinNeoForge) {
        throw "NeoForge $neo слишком старый, нужен $MinNeoForge или новее. Поставь в TLauncher NeoForge 1.21.1 поновее."
    }

    # 2. Папка mods: отдельная у версии, если TLauncher так настроен, иначе общая
    $mods = Join-Path $versionDir 'mods'
    if (-not (Test-Path $mods)) { $mods = Join-Path $GameDir 'mods' }
    New-Item -ItemType Directory -Force -Path $mods | Out-Null
    Say "Папка модов: $mods"

    if (Get-Process -Name javaw, java -ErrorAction SilentlyContinue) {
        Say "Похоже, Minecraft запущен. Закрой игру, иначе старые файлы не удалятся." Yellow
    }

    # 3. Photon и его библиотеки с Modrinth
    InstallFromModrinth 'ldlib' 'LDLib2' 'ldlib2-neoforge-'
    InstallFromModrinth 'kilagraph' 'KilaGraph' 'kilagraph-neoforge-'
    InstallFromModrinth 'photon-editor' 'Photon' 'photon-neoforge-'

    # 4. Сам мод из GitHub Releases
    $api = if ($Stable) { "https://api.github.com/repos/$Repo/releases/latest" } else { "https://api.github.com/repos/$Repo/releases/tags/neo-dev" }
    $release = Invoke-RestMethod $api -Headers $Headers
    $asset = $release.assets | Where-Object { $_.name -like 'mages-legacy-*.jar' -and $_.name -notlike '*-sources*' } | Select-Object -First 1
    if (-not $asset) { throw "В релизе '$($release.name)' нет jar-файла мода." }

    if (Test-Path (Join-Path $mods $asset.name)) {
        Say "Мод уже последней версии: $($asset.name)" Green
    } else {
        $tmp = Join-Path $env:TEMP $asset.name
        Invoke-WebRequest $asset.browser_download_url -OutFile $tmp -Headers $Headers -UseBasicParsing
        Get-ChildItem $mods -Filter 'mages-legacy-*.jar' | Remove-Item -Force
        Move-Item $tmp (Join-Path $mods $asset.name) -Force
        Say "Установлен $($asset.name) ($($release.name))" Green
    }

    # Ender Obelisks больше не входит в сборку
    Get-ChildItem $mods -Filter 'ender-obelisks-*.jar' | Remove-Item -Force

    Say ""
    Say "Готово! Запускай в TLauncher версию '$VersionName'." Cyan
}
catch {
    Say ""
    Say "Ошибка: $($_.Exception.Message)" Red
    exit 1
}
