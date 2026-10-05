param([string]$Proyecto = '')
$ErrorActionPreference = 'Stop'

# Correcciones puntuales al texto de la Santa Biblia Libre empaquetada
# (assets/bible-cat), que solo se usa para las lecturas de la misa. Se puede
# ejecutar las veces que haga falta: lo ya corregido se deja como esta. Hay que
# volver a pasarlo si se regenera la Biblia con convertir-catolica.ps1.
#
# - Lucas 11, 2-4; 20, 17 y 20, 42: la fuente trae sueltas las marcas de verso
#   de la poesia ("digan: 1 'Padre nuestro... 2 santificado..."), y la voz las
#   leia en voz alta ("uno", "dos") en medio del Padrenuestro.
# - Salmo 119: el nombre de cada letra hebrea (ALEF, BET...) va pegado al final
#   del versiculo anterior, y un salmo de la misa que terminaba ahi acababa
#   diciendo "Bet" o "Tav". Se quitan: en una lectura con saltos de versiculos,
#   aunque se movieran al principio del siguiente, volverian a sonar sueltas.

$proyecto = if ($Proyecto) { $Proyecto } else { Split-Path $PSScriptRoot -Parent }
$dir = Join-Path $proyecto 'app\src\main\assets\bible-cat'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Como Esc() de convertir-catolica.ps1: asi va escrito cada versiculo en el JSON.
function Esc([string]$s) { ($s -replace '\\', '\\\\') -replace '"', '\"' }

# libro -> lista de @(capitulo, versiculo, { texto -> texto corregido })
$CORRECCIONES = @{
    49 = @(
        @(11, 2, { param($t) $t -replace ' [12](?= |$)', '' }),
        @(11, 3, { param($t) $t -replace ' [12](?= |$)', '' }),
        @(11, 4, { param($t) $t -replace ' [12](?= |$)', '' }),
        @(20, 17, { param($t) $t -replace ' [12](?= |$)', '' }),
        @(20, 42, { param($t) $t -replace ' [12](?= |$)', '' })
    )
    23 = @(
        , @(119, 1, { param($t) $t -creplace '^\p{Lu}{2,}\s+', '' })
    ) + @(8..168 | Where-Object { $_ % 8 -eq 0 } | ForEach-Object { , @(119, $_, { param($t) $t -creplace '\s+\p{Lu}{2,}(?:\s+Y\s+\p{Lu}{2,})?$', '' }) })
}

# Versiculos que la SBL (que es un borrador) deja VACIOS y hemos decidido
# completar con la Reina-Valera 1909 (dominio publico), adaptada a "el Señor" y
# a ortografia actual. A diferencia de las correcciones de arriba, esto cambia
# cuantos versiculos tiene el capitulo, por eso va en su propio paso.
#  - 1 Samuel 11:15 (libro 9): la SBL lo deja en blanco.
# libro -> lista de @(capitulo, versiculo, texto)
$RELLENOS = @{
    9 = @(
        , @(11, 15, 'Y todo el pueblo fue a Gilgal, y allí proclamaron rey a Saúl delante del Señor. También ofrecieron allí sacrificios de paz delante del Señor, y Saúl y todos los israelitas se alegraron mucho.')
    )
}

$cambios = 0
foreach ($libro in $CORRECCIONES.Keys) {
    $archivo = Join-Path $dir "$libro.json"
    $raw = [System.IO.File]::ReadAllText($archivo, $utf8)
    $datos = $raw | ConvertFrom-Json
    $antes = @($datos.chapters | ForEach-Object { @($_).Count })
    foreach ($c in $CORRECCIONES[$libro]) {
        $viejo = [string]@($datos.chapters[$c[0] - 1])[$c[1] - 1]
        $nuevo = (& $c[2] $viejo).Trim()
        if ($nuevo -eq $viejo) { continue }
        $de = '"' + (Esc $viejo) + '"'; $a = '"' + (Esc $nuevo) + '"'
        $veces = ([regex]::Matches($raw, [regex]::Escape($de))).Count
        if ($veces -ne 1) { throw "$libro $($c[0]):$($c[1]) aparece $veces veces en el archivo: no se toca" }
        $raw = $raw.Replace($de, $a)
        Write-Output "  $($datos.abbr) $($c[0]):$($c[1])  ->  $nuevo"
        $cambios++
    }
    # El archivo tiene que seguir siendo el mismo libro, con los mismos versiculos.
    $despues = @(($raw | ConvertFrom-Json).chapters | ForEach-Object { @($_).Count })
    if (($antes -join ',') -ne ($despues -join ',')) { throw "$libro.json cambiaria de estructura: no se escribe" }
    [System.IO.File]::WriteAllText($archivo, $raw, $utf8)
}
Write-Output "Versiculos corregidos: $cambios"

# Rellenar los versiculos vacios que hemos decidido completar. Puede cambiar el
# numero de versiculos del capitulo, asi que no comprueba la estructura como las
# correcciones de arriba. Es idempotente: si ya esta puesto, no toca nada.
$rellenados = 0
foreach ($libro in $RELLENOS.Keys) {
    $archivo = Join-Path $dir "$libro.json"
    $raw = [System.IO.File]::ReadAllText($archivo, $utf8)
    $datos = $raw | ConvertFrom-Json
    $modificado = $false
    foreach ($r in $RELLENOS[$libro]) {
        $cap = $r[0]; $ver = $r[1]; $texto = $r[2]
        $capArr = @($datos.chapters[$cap - 1])
        $viejoN = $capArr.Count
        $actual = if ($ver -le $viejoN) { [string]$capArr[$ver - 1] } else { '' }
        if ($actual -eq $texto) { continue }                              # ya puesto
        if ($actual -ne '') { throw "$libro $cap`:$ver ya trae otro texto, no se rellena" }
        $nuevoN = [Math]::Max($viejoN, $ver)
        $viejoCap = '[' + (((1..$viejoN) | ForEach-Object { '"' + (Esc ([string]$capArr[$_ - 1])) + '"' }) -join ',') + ']'
        $nuevoCap = '[' + (((1..$nuevoN) | ForEach-Object {
            $val = if ($_ -eq $ver) { $texto } elseif ($_ -le $viejoN) { [string]$capArr[$_ - 1] } else { '' }
            '"' + (Esc $val) + '"'
        }) -join ',') + ']'
        $veces = ([regex]::Matches($raw, [regex]::Escape($viejoCap))).Count
        if ($veces -ne 1) { throw "El capitulo $libro $cap aparece $veces veces en el archivo: no se rellena" }
        $raw = $raw.Replace($viejoCap, $nuevoCap)
        $modificado = $true
        Write-Output "  RELLENO $($datos.abbr) $cap`:$ver  ->  $texto"
        $rellenados++
    }
    if ($modificado) { [System.IO.File]::WriteAllText($archivo, $raw, $utf8) }
}
Write-Output "Versiculos rellenados: $rellenados"
