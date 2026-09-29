# Copia a los teléfonos conectados por cable el audio de la voz IA generado en la PC.
# Solo copia lo nuevo y borra del teléfono lo que ya no se usa: se puede
# lanzar las veces que haga falta, incluso mientras el generador trabaja.
# Con varios teléfonos conectados copia a cada uno, uno detrás de otro, y al
# final comprueba en cada uno que haya llegado todo.
#
# Uso: powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\instalar-audio.ps1
# Requisitos: teléfono conectado por USB con la depuración USB activada (y el
# aviso «¿Permitir depuración USB?» aceptado), y la app Biblia en Voz 2.1 o
# posterior instalada. Nunca pregunta nada: el generador lo lanza solo cada hora.
$ErrorActionPreference = 'Continue'

# El generador guarda la última línea de este script en generador.log y la lee
# como UTF-8; en la consola normal se deja la codificación como está.
if ([Console]::IsOutputRedirected) {
    try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
}

$adb = 'E:\PG\.toolchain\sdk\platform-tools\adb.exe'
$origen = 'E:\PG\BibliaVoz-IA\audio'
$paquete = 'com.bibliavoz.app'
$destino = "/sdcard/Android/data/$paquete/files/voz-ia"
$lote = 200          # archivos por cada adb push (la línea de órdenes tiene límite)
$margen = 100MB      # lo que se deja libre en el teléfono además del audio

if (-not (Test-Path $adb)) { Write-Output "No encuentro adb en $adb"; exit 1 }

# --quitar: borra de los teléfonos conectados la copia por cable. Desde la 2.2 la
# voz IA va dentro de la app, así que esa copia (unos 2,8 GB) ya solo ocupa sitio.
# No toca la posición ni los ajustes, ni el .nomedia de la carpeta.
if ($args -contains '--quitar') {
    $hubo = $false
    $mal = $false
    foreach ($fila in (& $adb devices)) {
        if ("$fila" -notmatch '^(\S+)\tdevice$') { continue }
        $serie = $Matches[1]
        $hubo = $true
        $modelo = "$(& $adb -s $serie shell getprop ro.product.model)".Trim()
        if (-not $modelo) { $modelo = $serie }
        # El manifiesto primero: la app nunca ve uno que nombre archivos ya borrados.
        & $adb -s $serie shell "rm -f $destino/manifest.json $destino/generador.log; find $destino -maxdepth 1 -name '*.mp3' -delete" | Out-Null
        $quedan = "$(& $adb -s $serie shell "ls $destino 2>/dev/null | grep -c '\.mp3$'")".Trim()
        if ($quedan -eq '0') {
            Write-Output "${modelo}: copia por cable borrada. La app sigue con la voz IA que trae dentro."
        } else {
            Write-Output "${modelo}: quedaron $quedan archivos; vuelve a lanzarlo."
            $mal = $true
        }
    }
    if (-not $hubo) { Write-Output 'No hay ningún teléfono conectado con la depuración USB activada.'; exit 1 }
    if ($mal) { exit 1 }
    exit 0
}

if (-not (Test-Path "$origen\manifest.json")) { Write-Output "No hay audio generado en $origen"; exit 1 }

function Tamano($bytes) {
    if ($bytes -ge 1GB) { return ('{0:N1} GB' -f ($bytes / 1GB)) }
    return ('{0:N0} MB' -f ($bytes / 1MB))
}

# Foto fija de lo que hay en la PC: primero el manifiesto y después los MP3. El
# generador escribe cada MP3 antes de nombrarlo en el manifiesto, así que todo lo
# que nombra esta copia del manifiesto está en la lista, aunque siga trabajando.
# No viajan generador.log ni los .part/.tmp a medio escribir.
# (Un archivo por proceso: el generador y una copia a mano pueden coincidir.)
$manifiesto = Join-Path $env:TEMP "bibliavoz-manifest-$PID.json"
function Salir($codigo) {
    Remove-Item $manifiesto -Force -ErrorAction SilentlyContinue
    exit $codigo
}
$copiado = $false
for ($i = 0; $i -lt 5 -and -not $copiado; $i++) {
    try { Copy-Item "$origen\manifest.json" $manifiesto -Force -ErrorAction Stop; $copiado = $true }
    catch { Start-Sleep -Seconds 1 }
}
if (-not $copiado) { Write-Output 'No pude leer manifest.json (el generador lo estaba escribiendo). Vuelve a lanzarlo.'; exit 1 }
$tamManifiesto = (Get-Item $manifiesto).Length

$mp3 = @(Get-ChildItem $origen -File | Where-Object { $_.Extension -eq '.mp3' } | Sort-Object Name)
if ($mp3.Count -eq 0) { Write-Output "No hay audio generado en $origen"; Salir 1 }
$bytesPc = [long]($mp3 | Measure-Object -Property Length -Sum).Sum
$validos = @{ 'manifest.json' = $true }
foreach ($f in $mp3) { $validos[$f.Name] = $true }

# Nombre y tamaño de cada archivo de la carpeta de audio del teléfono
# (tamaño -1 si el teléfono no deja leerlo; entonces solo se mira el nombre).
function Leer-Telefono($serie) {
    $r = @{}
    $lineas = & $adb -s $serie shell "cd $destino && stat -c '%s %n' *.mp3 manifest.json 2>/dev/null"
    foreach ($l in $lineas) {
        if ("$l".Trim() -match '^(\d+) (\S+)$') { $r[$Matches[2]] = [long]$Matches[1] }
    }
    if ($r.Count -eq 0) {
        $nombres = & $adb -s $serie shell "ls $destino 2>/dev/null"
        foreach ($l in $nombres) {
            $n = "$l".Trim()
            if ($n -like '*.mp3' -or $n -eq 'manifest.json') { $r[$n] = [long]-1 }
        }
    }
    return $r
}

# Bytes libres donde va el audio, o -1 si no se pudo saber.
function Libre-Telefono($serie) {
    $lineas = @(& $adb -s $serie shell "df -k $destino" | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
    if ($lineas.Count -lt 2) { return [long]-1 }
    $campos = $lineas[-1] -split '\s+'
    [long]$kb = 0
    if ($campos.Count -ge 4 -and [long]::TryParse($campos[3], [ref]$kb)) { return $kb * 1KB }
    return [long]-1
}

# Copia a un teléfono. Escribe lo que va pasando y deja en $script:estado una
# frase corta para el resumen del final ('listo' si todo llegó).
function Instalar($serie) {
    $modelo = "$(& $adb -s $serie shell getprop ro.product.model)".Trim()
    if (-not $modelo) { $modelo = $serie }
    $script:nombre = $modelo
    Write-Output ''
    Write-Output "=== $modelo ==="

    $ruta = "$(& $adb -s $serie shell pm path $paquete)"
    if ($ruta -notmatch 'package:') {
        Write-Output '   La app Biblia en Voz no está instalada en este teléfono.'
        Write-Output '   Instala primero el APK (BibliaEnVoz-2.2.apk), ábrela una vez y vuelve a lanzar esto.'
        $script:estado = 'falta instalar la app'
        return
    }

    # La carpeta la tiene que crear la propia app (así tiene permiso de leerla):
    # si aún no existe, se abre la app y se espera a que aparezca.
    $existe = & $adb -s $serie shell "[ -d $destino ] && echo si"
    if ("$existe".Trim() -ne 'si') {
        Write-Output '   Abriendo la app en el teléfono para que prepare su carpeta...'
        & $adb -s $serie shell am start -n "$paquete/.MainActivity" | Out-Null
        for ($i = 0; $i -lt 15 -and "$existe".Trim() -ne 'si'; $i++) {
            Start-Sleep -Seconds 1
            $existe = & $adb -s $serie shell "[ -d $destino ] && echo si"
        }
    }
    if ("$existe".Trim() -ne 'si') {
        Write-Output '   La app no ha preparado su carpeta. Desbloquea el teléfono, abre Biblia en Voz'
        Write-Output '   una vez y vuelve a lanzar esto.'
        $script:estado = 'abre la app una vez en el teléfono'
        return
    }

    # Lo que falta: archivos que no están o que no miden lo mismo.
    $antes = Leer-Telefono $serie
    $faltan = 0
    [long]$bytesFaltan = 0
    foreach ($f in $mp3) {
        $t = $antes[$f.Name]
        if ($null -eq $t -or ($t -ge 0 -and $t -ne $f.Length)) { $faltan++; $bytesFaltan += $f.Length }
    }
    $libre = Libre-Telefono $serie
    if ($libre -ge 0 -and $bytesFaltan + $margen -gt $libre) {
        Write-Output "   No cabe: hay que copiar $(Tamano $bytesFaltan) y el teléfono solo tiene $(Tamano $libre) libres."
        Write-Output "   Libera al menos $(Tamano ($bytesFaltan + $margen - $libre)) (fotos, videos, apps que no uses) y vuelve a lanzar esto."
        $script:estado = "no hay espacio (libera $(Tamano ($bytesFaltan + $margen - $libre)))"
        return
    }

    if ($faltan -gt 0) {
        Write-Output "   Copiando $faltan archivos ($(Tamano $bytesFaltan)). No desconectes el cable..."
    } else {
        Write-Output '   Ya tenía todo el audio; se comprueba igualmente.'
    }

    # --sync copia solo lo que falta o cambió. Primero los MP3 y al final el
    # manifiesto: así el teléfono no ve un manifiesto nuevo antes que su audio.
    # La app no usa un capítulo hasta que están todos sus archivos.
    $fallo = $null
    for ($i = 0; $i -lt $mp3.Count; $i += $lote) {
        $parte = @($mp3[$i..([Math]::Min($i + $lote - 1, $mp3.Count - 1))] | ForEach-Object { $_.FullName })
        $salida = @(& $adb -s $serie push --sync $parte "$destino/" 2>&1 | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
        if ($LASTEXITCODE -ne 0) {
            $fallo = $salida | Where-Object { $_ -match 'error|fail|space|denied|no devices|not found' } | Select-Object -Last 1
            if (-not $fallo) { $fallo = $salida | Select-Object -Last 1 }
            break
        }
        $hechos = [Math]::Min($i + $lote, $mp3.Count)
        if ($faltan -gt 0 -and ($hechos % 1000 -eq 0 -or $hechos -eq $mp3.Count)) {
            Write-Output "   ... revisados $hechos de $($mp3.Count)"
        }
    }
    # El manifiesto va aunque la copia se haya cortado: con él la app ya usa los
    # capítulos que sí llegaron enteros.
    $salida = @(& $adb -s $serie push --sync $manifiesto "$destino/manifest.json" 2>&1 | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
    $manifiestoCopiado = ($LASTEXITCODE -eq 0)
    if (-not $manifiestoCopiado -and -not $fallo) { $fallo = $salida | Select-Object -Last 1 }

    # Lo que ya no existe en la PC (audio de repartos anteriores, generador.log de
    # versiones viejas de este script) sobra. Solo con el manifiesto nuevo ya en
    # su sitio, y nunca los archivos que empiezan por punto (el .nomedia de la app).
    if ($manifiestoCopiado) {
        $remotos = & $adb -s $serie shell "ls $destino" | ForEach-Object { "$_".Trim() } |
            Where-Object { $_ -match '^[A-Za-z0-9][A-Za-z0-9._-]*$' }
        $sobran = @($remotos | Where-Object { -not $validos.ContainsKey($_) })
        for ($i = 0; $i -lt $sobran.Count; $i += 100) {
            $borrar = ($sobran[$i..([Math]::Min($i + 99, $sobran.Count - 1))] | ForEach-Object { "$destino/$_" }) -join ' '
            & $adb -s $serie shell "rm -f $borrar" | Out-Null
        }
        if ($sobran.Count -gt 0) { Write-Output "   Borrados del teléfono $($sobran.Count) archivos que ya no se usan." }
    }

    # Comprobación: cada MP3 de la PC tiene que estar en el teléfono y medir lo mismo.
    $despues = Leer-Telefono $serie
    $faltanN = 0
    [long]$faltanB = 0
    $sinTamano = $false
    foreach ($f in $mp3) {
        $t = $despues[$f.Name]
        if ($null -eq $t -or ($t -ge 0 -and $t -ne $f.Length)) { $faltanN++; $faltanB += $f.Length }
        elseif ($t -lt 0) { $sinTamano = $true }
    }
    $mp3Tel = @($despues.Keys | Where-Object { $_ -like '*.mp3' })
    [long]$bytesTel = 0
    foreach ($k in $mp3Tel) { if ($despues[$k] -gt 0) { $bytesTel += $despues[$k] } }
    $tm = $despues['manifest.json']
    $manifiestoBien = ($null -ne $tm -and ($tm -lt 0 -or $tm -eq $tamManifiesto))

    if ($sinTamano) {
        Write-Output "   En el teléfono: $($mp3Tel.Count) archivos de audio; en la PC: $($mp3.Count) (este teléfono no deja comparar tamaños)."
    } else {
        Write-Output "   En el teléfono: $($mp3Tel.Count) archivos de audio, $(Tamano $bytesTel). En la PC: $($mp3.Count), $(Tamano $bytesPc)."
    }
    if ($faltanN -eq 0 -and $manifiestoBien) {
        Write-Output '   Todo el audio llegó bien.'
        $script:estado = 'listo'
        return
    }
    if ($fallo) { Write-Output "   La copia falló: $fallo" }
    if ($faltanN -gt 0) {
        if ("$fallo" -match 'space') {
            Write-Output "   Faltan $faltanN archivos ($(Tamano $faltanB)): el teléfono se quedó sin espacio. Libera espacio"
            Write-Output '   (fotos, videos, apps que no uses) y vuelve a lanzar instalar-audio.bat: solo copia lo que falta.'
            $script:estado = "faltan $faltanN archivos (sin espacio)"
        } else {
            Write-Output "   Faltan $faltanN archivos ($(Tamano $faltanB)). Revisa el cable y vuelve a lanzar instalar-audio.bat:"
            Write-Output '   solo copia lo que falta.'
            $script:estado = "faltan $faltanN archivos"
        }
    } else {
        Write-Output '   Falta el índice del audio (manifest.json). Vuelve a lanzar instalar-audio.bat.'
        $script:estado = 'falta manifest.json'
    }
}

# Teléfonos conectados. adb devices da una fila «serie<TAB>estado» por teléfono.
$listos = @()
$resumen = @()
foreach ($fila in (& $adb devices)) {
    if ("$fila" -notmatch '^(\S+)\t(.+)$') { continue }
    $serie = $Matches[1]
    $conexion = $Matches[2].Trim()
    if ($conexion -eq 'device') { $listos += $serie; continue }
    Write-Output ''
    Write-Output "=== Teléfono $serie ==="
    if ($conexion -eq 'unauthorized') {
        Write-Output '   Está conectado pero todavía no ha dado permiso. Desbloquéalo: aparece el aviso'
        Write-Output '   «¿Permitir depuración USB?». Marca «Permitir siempre desde esta computadora»,'
        Write-Output '   toca «Permitir» y vuelve a lanzar instalar-audio.bat. Si no sale el aviso,'
        Write-Output '   desconecta y vuelve a conectar el cable.'
        $resumen += "teléfono $serie`: falta aceptar el aviso de depuración USB"
    } elseif ($conexion -eq 'offline') {
        Write-Output '   No responde. Desconecta y vuelve a conectar el cable; si sigue igual, reinicia'
        Write-Output '   el teléfono y vuelve a lanzar instalar-audio.bat.'
        $resumen += "teléfono $serie`: no responde (desconecta y reconecta el cable)"
    } else {
        Write-Output "   No se puede usar ahora (estado: $conexion). Reinícialo normalmente y vuelve a lanzar esto."
        $resumen += "teléfono $serie`: estado $conexion"
    }
}

if ($listos.Count -eq 0 -and $resumen.Count -eq 0) {
    Write-Output 'No hay ningún teléfono conectado (o no tiene activada la depuración USB); se copiará la próxima vez.'
    Salir 0
}

if ($listos.Count -gt 1) { Write-Output "Hay $($listos.Count) teléfonos conectados: se copia a cada uno." }
$completos = @()
foreach ($serie in $listos) {
    $script:estado = 'sin terminar'
    $script:nombre = $serie
    Instalar $serie
    if ($script:estado -eq 'listo') { $completos += $script:nombre }
    $resumen += "$($script:nombre): $($script:estado)"
}

# La última línea resume todo (es la que queda en generador.log).
Write-Output ''
if ($completos.Count -eq $resumen.Count) {
    Write-Output ('Listo: la voz IA está completa en ' + ($completos -join ', ') + '. En la app, Ajustes > Voz IA muestra lo instalado.')
    Salir 0
}
Write-Output ('NO TERMINÓ. ' + ($resumen -join ' · ') + '. Haz lo que se indica arriba y vuelve a lanzar instalar-audio.bat.')
Salir 1
