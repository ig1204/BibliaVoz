# Compila la app con el toolchain portatil de E:\PG\.toolchain
# Uso:  powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\compilar.ps1
# (se guarda dentro del proyecto a proposito: la carpeta temporal se borra sola)
# Al compilar la version release deja una copia en E:\PG\BibliaEnVoz-<versionName>.apk
$ErrorActionPreference = 'Continue'

$env:JAVA_HOME = 'E:\PG\.toolchain\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'E:\PG\.toolchain\sdk'
$env:ANDROID_SDK_ROOT = 'E:\PG\.toolchain\sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = 'E:\PG\.toolchain\gradle-home'

$gradle = 'E:\PG\.toolchain\gradle\gradle-8.11.1\bin\gradle.bat'
$proj = 'E:\PG\BibliaVoz'

$task = if ($args.Count -gt 0) { $args[0] } else { 'assembleRelease' }
# Lo demas pasa tal cual a gradle (p. ej. -PsinVozIa para compilar sin el audio).
$extra = @(if ($args.Count -gt 1) { $args[1..($args.Count - 1)] })

Write-Output "=== gradle $task $extra ==="
& $gradle -p $proj $task @extra --no-daemon --stacktrace --warning-mode=none
$code = $LASTEXITCODE
Write-Output "=== gradle exit code: $code ==="

$apk = Join-Path $proj 'app\build\outputs\apk\release\app-release.apk'
if ($code -eq 0 -and (Test-Path $apk)) {
    Write-Output "APK OK: $apk  ($([math]::Round((Get-Item $apk).Length/1MB,2)) MB)"
} else {
    Write-Output "SIN APK (exit=$code)"
}

# Copia con el nombre de la version junto a las anteriores, en E:\PG. Solo si esta
# vez se pidio el APK release: con otras tareas (p. ej. testReleaseUnitTest) el
# que hay en build\ puede ser de antes.
# Sin la voz IA (-PsinVozIa) es un APK de prueba: no se reparte con ese nombre.
# Se mira dentro del APK, no en los argumentos, para que no se cuele nunca.
$traeVozIa = $false
if ($code -eq 0 -and (Test-Path $apk)) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try { $traeVozIa = [bool]($zip.GetEntry('assets/voz-ia/manifest.json')) } finally { $zip.Dispose() }
    if (-not $traeVozIa) { Write-Output 'Este APK no trae la voz IA (prueba rapida): no se copia a E:\PG.' }
}
if ($code -eq 0 -and $traeVozIa -and $task -match 'assemble(Release)?$|^build$') {
    $gradleApp = Get-Content (Join-Path $proj 'app\build.gradle.kts') -Raw -Encoding UTF8
    $version = if ($gradleApp -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { $null }
    $codigo = if ($gradleApp -match 'versionCode\s*=\s*(\d+)') { $Matches[1] } else { $null }
    if (-not $version -or -not $codigo) {
        Write-Output 'No encontre versionName/versionCode en app\build.gradle.kts: no se copia el APK.'
    } else {
        $final = "E:\PG\BibliaEnVoz-$version.apk"
        $copiar = $true
        # Si ya hay uno con ese nombre solo se reemplaza si es la misma version
        # (mismo versionCode): asi nunca se pisa un APK ya repartido de otra version.
        if (Test-Path $final) {
            $aapt = Get-ChildItem "$env:ANDROID_HOME\build-tools\*\aapt.exe" -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1
            $previo = ''
            if ($aapt) { $previo = "$(& $aapt.FullName dump badging $final 2>$null | Select-Object -First 1)" }
            if ($previo -notmatch "versionCode='$codigo'") {
                $copiar = $false
                Write-Output "Ya existe $final y es de otra compilacion (no es versionCode $codigo): no se toca."
                Write-Output 'Sube versionCode y versionName en app\build.gradle.kts y vuelve a compilar.'
            }
        }
        if ($copiar) {
            # Primero a un temporal: un disco lleno o un archivo abierto (subiendolo
            # a Drive, por ejemplo) nunca deja un APK a medias con el nombre final.
            $tmp = "$final.tmp"
            try {
                Copy-Item $apk $tmp -Force -ErrorAction Stop
                if ((Get-Item $tmp).Length -ne (Get-Item $apk).Length) { throw 'la copia quedo incompleta' }
                Move-Item $tmp $final -Force -ErrorAction Stop
                Write-Output "APK para instalar: $final  (version $version, versionCode $codigo, $([math]::Round((Get-Item $final).Length/1GB,2)) GB)"
            } catch {
                Remove-Item $tmp -Force -ErrorAction SilentlyContinue
                Write-Output "NO se pudo copiar el APK a ${final}: $($_.Exception.Message)"
                $code = 1
            }
        }
    }
}
exit $code
