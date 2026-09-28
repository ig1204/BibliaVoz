# Compila la app con el toolchain portatil de E:\PG\.toolchain
# Uso:  powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\compilar.ps1
# (se guarda dentro del proyecto a proposito: la carpeta temporal se borra sola)
$ErrorActionPreference = 'Continue'

$env:JAVA_HOME = 'E:\PG\.toolchain\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'E:\PG\.toolchain\sdk'
$env:ANDROID_SDK_ROOT = 'E:\PG\.toolchain\sdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = 'E:\PG\.toolchain\gradle-home'

$gradle = 'E:\PG\.toolchain\gradle\gradle-8.11.1\bin\gradle.bat'
$proj = 'E:\PG\BibliaVoz'

$task = if ($args.Count -gt 0) { $args[0] } else { 'assembleRelease' }

Write-Output "=== gradle $task ==="
& $gradle -p $proj $task --no-daemon --stacktrace --warning-mode=none
$code = $LASTEXITCODE
Write-Output "=== gradle exit code: $code ==="

$apk = Join-Path $proj 'app\build\outputs\apk\release\app-release.apk'
if ($code -eq 0 -and (Test-Path $apk)) {
    Write-Output "APK OK: $apk  ($([math]::Round((Get-Item $apk).Length/1MB,2)) MB)"
} else {
    Write-Output "SIN APK (exit=$code)"
}
