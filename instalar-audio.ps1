# Copia al telefono, por cable, el audio de la voz IA generado en la PC.
# Solo copia lo nuevo y borra del telefono lo que ya no se usa: se puede
# lanzar las veces que haga falta, incluso mientras el generador trabaja.
#
# Uso: powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\instalar-audio.ps1
# Requisitos: telefono conectado por USB con la depuracion USB activada,
# y la app Biblia en Voz 2.1 o posterior instalada.
$ErrorActionPreference = 'Continue'

$adb = 'E:\PG\.toolchain\sdk\platform-tools\adb.exe'
$origen = 'E:\PG\BibliaVoz-IA\audio'
$destino = '/sdcard/Android/data/com.bibliavoz.app/files/voz-ia'

if (-not (Test-Path "$origen\manifest.json")) { Write-Output "No hay audio generado en $origen"; exit 1 }

$dispositivos = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $dispositivos) { Write-Output 'No hay ningun telefono conectado; se copiara la proxima vez.'; exit 0 }

# La carpeta la tiene que crear la propia app (asi tiene permiso de leerla).
# Si aun no existe, basta con abrir la app una vez.
$existe = & $adb shell "[ -d $destino ] && echo si"
if ("$existe".Trim() -ne 'si') {
    & $adb shell am start -n com.bibliavoz.app/.MainActivity | Out-Null
    Start-Sleep -Seconds 4
}

# --sync copia solo lo que falta o cambio. La app no usa un capitulo hasta que
# estan todos sus archivos, asi que el orden de la copia no importa.
& $adb push --sync "$origen\." "$destino/" | Select-Object -Last 1

# Lo que ya no existe en la PC (audio de repartos anteriores, restos) sobra.
$locales = @{}
Get-ChildItem $origen -File | ForEach-Object { $locales[$_.Name] = $true }
$remotos = & $adb shell "ls $destino" | ForEach-Object { "$_".Trim() } | Where-Object { $_ }
$sobran = @($remotos | Where-Object { -not $locales.ContainsKey($_) })
for ($i = 0; $i -lt $sobran.Count; $i += 100) {
    $lote = ($sobran[$i..([Math]::Min($i + 99, $sobran.Count - 1))] | ForEach-Object { "$destino/$_" }) -join ' '
    & $adb shell "rm -f $lote"
}
if ($sobran.Count -gt 0) { Write-Output "Borrados del telefono $($sobran.Count) archivos que ya no se usan." }
Write-Output 'Listo. En la app, Ajustes > Voz IA muestra lo instalado.'
