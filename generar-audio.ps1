# Genera en la PC el audio de la voz IA (Fish Audio, voz Hilary narrador).
# Se hace UNA sola vez; despues la app lo reproduce sin internet.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\generar-audio.ps1
#       (todo: lecturas de las proximas 2 semanas, la Reina-Valera y el resto de lecturas)
#   ... generar-audio.ps1 --solo "Juan 11"        (un capitulo)
#   ... generar-audio.ps1 --solo "Juan; Salmos"   (libros enteros)
#   ... generar-audio.ps1 --solo "misa:30"        (lecturas de los proximos 30 dias)
#   ... generar-audio.ps1 --contar                (solo cuenta lo que falta)
#
# La clave de Fish Audio se lee de E:\PG\BibliaVoz-IA\clave-fish.txt
# El audio queda en E:\PG\BibliaVoz-IA\audio  (se puede cortar y volver a lanzar).
$ErrorActionPreference = 'Continue'

$env:JAVA_HOME = 'E:\PG\.toolchain\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'E:\PG\.toolchain\sdk'
$env:ANDROID_SDK_ROOT = 'E:\PG\.toolchain\sdk'
$env:GRADLE_USER_HOME = 'E:\PG\.toolchain\gradle-home'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$proj = 'E:\PG\BibliaVoz'
$gradle = 'E:\PG\.toolchain\gradle\gradle-8.11.1\bin\gradle.bat'

& $gradle -p $proj :generador:installDist --no-daemon --quiet --warning-mode=none
if ($LASTEXITCODE -ne 0) { Write-Output 'No se pudo compilar el generador.'; exit 1 }

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Que la PC no se duerma mientras genera (la pantalla si puede apagarse).
# No cambia ninguna configuracion: Windows lo olvida en cuanto este script termina.
Add-Type -Namespace Win32 -Name Energia -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint esFlags);'
[Win32.Energia]::SetThreadExecutionState([uint32]2147483649) | Out-Null

& "$proj\generador\build\install\generador\bin\generador.bat" --proyecto $proj @args
exit $LASTEXITCODE
