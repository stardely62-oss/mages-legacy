# Ставит/обновляет мод Mage's Legacy (NeoForge 1.21.1) и всё, что нужно для тестов: движок эффектов (Veil, GeckoLib)
# и рендер с шейдерами (Sodium, Iris, Iris Veil Compat) — тех же версий, что проверяет сборка, — в папку mods TLauncher.
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
# Самому моду нужен NeoForge 21.1.217+, Iris Veil Compat — 21.1.228+
$MinNeoForge = [version]'21.1.217'
$IrisVeilNeoForge = [version]'21.1.228'

# Моды для тестов: ровно те версии, с которыми мод проверяется в сборке (Iris 1.8.14-beta.1 работает только с Sodium 0.8.13).
# Имя, префиксы старых копий для удаления, файл, ссылка Modrinth, SHA-512
$Pinned = @(
    @{ Title = 'Veil'; Prefixes = @('veil-neoforge-'); File = 'veil-neoforge-1.21.1-4.5.2.jar'
       Url = 'https://cdn.modrinth.com/data/3KUWeVhG/versions/IpGnlNgK/veil-neoforge-1.21.1-4.5.2.jar'
       Sha = '046de1b45b94431c4faba4dd16b559491a433644a1ea1aa7e1edf2a5c37bde85d192bbf362297d4a4a04b8ab24ba9b6f9ec72a84f8de3affdb87bc72d52aa8c1' },
    @{ Title = 'GeckoLib'; Prefixes = @('geckolib-neoforge-'); File = 'geckolib-neoforge-1.21.1-4.9.3.jar'
       Url = 'https://cdn.modrinth.com/data/8BmcQJ2H/versions/Grwn5rUB/geckolib-neoforge-1.21.1-4.9.3.jar'
       Sha = '8dfa5f8c57224a5e34e5098367ea9819ca6dc9c11357cd3062c16da3455c8bd1a483098af7383debc4b9ce8e4d7ab3fdc799b415644eb829da83eb72a7335dfd' },
    @{ Title = 'Sodium'; Prefixes = @('sodium-neoforge-'); File = 'sodium-neoforge-0.8.13+mc1.21.1.jar'
       Url = 'https://cdn.modrinth.com/data/AANobbMI/versions/uMOpc5uV/sodium-neoforge-0.8.13%2Bmc1.21.1.jar'
       Sha = '4f537696af95411e9daf15795fb5fcbc49913d488a96fa912fa8f1d7db408710995e8a1461190f3cd0f8217bf9e35e9539176592428ed587ca2d7aa84450a390' },
    @{ Title = 'Iris'; Prefixes = @('iris-neoforge-'); File = 'iris-neoforge-1.8.14-beta.1+mc1.21.1.jar'
       Url = 'https://cdn.modrinth.com/data/YL57xq9U/versions/KduFYu4t/iris-neoforge-1.8.14-beta.1%2Bmc1.21.1.jar'
       Sha = '33fddb430e16222902eac75b8b3d57c381b0f3cc729f3b56c4628df12ca108f23a0b11e2feb167923d6d14d1fc7e8ee1a977ec421b19d1951bdca74c4092b9c5' },
    @{ Title = 'Iris Veil Compat'; Prefixes = @('irisveil-', 'iris-veil-'); File = 'irisveil-0.4.0.jar'; NeedsNeo = $IrisVeilNeoForge
       Url = 'https://cdn.modrinth.com/data/owQyCHo4/versions/hWItkPzn/irisveil-0.4.0.jar'
       Sha = '3ed3ed0234ea42a4c591c5bd651aca4feb7e2641ed0b48b5c2bd2536383c99a18f77917d425d32e007bfda3eb1b2e84c26a354ae83713708e71392c6085410bb' }
)
# Несовместимы с Veil/Sodium: удаляются
$Conflicting = @('embeddium-*.jar', 'rubidium-*.jar', 'oculus-*.jar', 'celeritas-*.jar', 'photon-neoforge-*.jar', 'ldlib2-neoforge-*.jar',
    'kilagraph-neoforge-*.jar', 'journeymap-*.jar', 'ender-obelisks-*.jar')

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }

# Версия NeoForge из json версии TLauncher, или $null, если это не NeoForge 1.21.1
function NeoForgeOf($dir) {
    foreach ($json in Get-ChildItem -LiteralPath $dir -Filter '*.json' -ErrorAction SilentlyContinue) {
        $text = Get-Content -LiteralPath $json.FullName -Raw
        foreach ($pattern in @('net\.neoforged:neoforge:(21\.1\.[0-9]+)', 'fml\.neoForgeVersion"\s*,\s*"(21\.1\.[0-9]+)', 'neoforge-(21\.1\.[0-9]+)')) {
            $m = [regex]::Match($text, $pattern)
            if ($m.Success) { return [version]$m.Groups[1].Value }
        }
    }
    return $null
}

function RemoveOld($prefixes, $keep) {
    foreach ($prefix in $prefixes) {
        Get-ChildItem -LiteralPath $mods -Filter "$prefix*.jar" | Where-Object { $_.Name -ne $keep } |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force; Say "  удалён старый $($_.Name)" }
    }
}

# Ставит мод ровно этой версии (проверяя SHA-512); другие его версии удаляются
function InstallPinned($m) {
    $target = Join-Path $mods $m.File
    if (Test-Path -LiteralPath $target) {
        $have = (Get-FileHash -LiteralPath $target -Algorithm SHA512).Hash.ToLower()
        if ($have -eq $m.Sha) {
            RemoveOld $m.Prefixes $m.File
            Say "$($m.Title) уже стоит: $($m.File)"
            return
        }
    }
    $tmp = Join-Path $env:TEMP $m.File
    Invoke-WebRequest $m.Url -OutFile $tmp -Headers $Headers -UseBasicParsing
    $got = (Get-FileHash -LiteralPath $tmp -Algorithm SHA512).Hash.ToLower()
    if ($got -ne $m.Sha) {
        Remove-Item -LiteralPath $tmp -Force
        throw "$($m.Title): скачанный файл не совпал по SHA-512 (повреждён загрузкой?). Запусти ещё раз."
    }
    RemoveOld $m.Prefixes $m.File
    [IO.File]::Copy($tmp, $target, $true)
    Remove-Item -LiteralPath $tmp -Force
    Say "$($m.Title) установлен: $($m.File)" Green
}

try {
    Say "== Mage's Legacy: обновление ==" Cyan

    # 1. Версия NeoForge 1.21.1 в TLauncher: названная или найденная сама
    $versionsDir = Join-Path $GameDir 'versions'
    if ($VersionName) {
        $versionDir = Join-Path $versionsDir $VersionName
        if (-not (Test-Path -LiteralPath $versionDir)) {
            throw "Не нашёл версию '$VersionName' в $versionsDir. Установи её в TLauncher и запусти один раз."
        }
        $neo = NeoForgeOf $versionDir
        if (-not $neo) { throw "Версия '$VersionName' не похожа на NeoForge 1.21.1." }
    } else {
        $found = @(Get-ChildItem -LiteralPath $versionsDir -Directory -ErrorAction SilentlyContinue |
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
    if (-not (Test-Path -LiteralPath $mods)) { $mods = Join-Path $GameDir 'mods' }
    [IO.Directory]::CreateDirectory($mods) | Out-Null
    Say "Папка модов: $mods"

    if (Get-Process -Name javaw, java -ErrorAction SilentlyContinue) {
        Say "Похоже, Minecraft запущен. Закрой игру, иначе старые файлы не удалятся." Yellow
    }

    # 3. Всё для тестов с Modrinth: Veil, GeckoLib, Sodium, Iris, Iris Veil Compat; несовместимое убирается
    foreach ($old in $Conflicting) {
        Get-ChildItem -LiteralPath $mods -Filter $old | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force; Say "  удалён $($_.Name)" }
    }
    $skipped = @()
    foreach ($m in $Pinned) {
        if ($m.NeedsNeo -and $neo -lt $m.NeedsNeo) {
            RemoveOld $m.Prefixes ''
            $skipped += "$($m.Title) (нужен NeoForge $($m.NeedsNeo)+)"
            continue
        }
        InstallPinned $m
    }

    # 4. Сам мод из GitHub Releases
    $api = if ($Stable) { "https://api.github.com/repos/$Repo/releases/latest" } else { "https://api.github.com/repos/$Repo/releases/tags/neo-dev" }
    $release = Invoke-RestMethod $api -Headers $Headers
    $asset = $release.assets | Where-Object { $_.name -like 'mages-legacy-*.jar' -and $_.name -notlike '*-sources*' } | Select-Object -First 1
    if (-not $asset) { throw "В релизе '$($release.name)' нет jar-файла мода." }

    if (Test-Path -LiteralPath (Join-Path $mods $asset.name)) {
        Say "Мод уже последней версии: $($asset.name)" Green
    } else {
        $tmp = Join-Path $env:TEMP $asset.name
        Invoke-WebRequest $asset.browser_download_url -OutFile $tmp -Headers $Headers -UseBasicParsing
        Get-ChildItem -LiteralPath $mods -Filter 'mages-legacy-*.jar' | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
        [IO.File]::Copy($tmp, (Join-Path $mods $asset.name), $true)
        Remove-Item -LiteralPath $tmp -Force
        Say "Установлен $($asset.name) ($($release.name))" Green
    }

    Say ""
    if ($skipped.Count -gt 0) {
        Say "Не поставлено: $($skipped -join ', ')." Yellow
        Say "Поставь в TLauncher NeoForge 1.21.1 версии $IrisVeilNeoForge или новее, запусти её один раз и запусти этот скрипт снова." Yellow
        Say "Без Iris Veil Compat шейдеры Iris и эффекты мода (Veil) могут конфликтовать: играй без шейдер-пака." Yellow
    }
    Say "Шейдеры: в игре Настройки -> Настройки графики -> Шейдеры (Iris). Папка shaderpacks — рядом с mods." DarkGray

    Say "Готово! Запускай в TLauncher версию '$VersionName'." Cyan
}
catch {
    Say ""
    Say "Ошибка: $($_.Exception.Message)" Red
    exit 1
}
