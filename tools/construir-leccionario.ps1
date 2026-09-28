$ErrorActionPreference = 'Stop'

# Construye la tabla del leccionario que se empaqueta en la app.
#
# El calendario se CALCULA (Pascua -> temporadas -> semana -> ciclo) y no caduca.
# Las citas se IMPORTAN de un conjunto de datos indexado por fecha que solo
# llega a 2027; para que no caduque se invierte: a cada fecha se le calcula su
# CLAVE LITURGICA y se guarda la tabla clave -> lecturas.
#
# Efecto secundario util: eso VALIDA el calendario. Las ferias del ciclo I
# (2023, 2025, 2027) deben dar las mismas lecturas bajo la misma clave; si el
# calculo de temporadas estuviera mal, apareceran conflictos.

. (Join-Path $PSScriptRoot 'parser.ps1')

$srcReadings = 'E:\PG\.liturgia\readings_usccb.json'
$outDir = 'E:\PG\BibliaVoz\app\src\main\assets\liturgia'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# ----------------------------------------------------------------- calendario

function Get-Easter([int]$year) {
    $a = $year % 19
    $b = [math]::Floor($year / 100); $c = $year % 100
    $d = [math]::Floor($b / 4); $e = $b % 4
    $f = [math]::Floor(($b + 8) / 25)
    $g = [math]::Floor(($b - $f + 1) / 3)
    $h = (19*$a + $b - $d - $g + 15) % 30
    $i = [math]::Floor($c / 4); $k = $c % 4
    $l = (32 + 2*$e + 2*$i - $h - $k) % 7
    $m = [math]::Floor(($a + 11*$h + 22*$l) / 451)
    $month = [math]::Floor(($h + $l - 7*$m + 114) / 31)
    $day = (($h + $l - 7*$m + 114) % 31) + 1
    return [datetime]::new($year, $month, $day)
}

function Get-Advent1([int]$year) {
    $christmas = [datetime]::new($year, 12, 25)
    $dow = [int]$christmas.DayOfWeek
    $sunday = if ($dow -eq 0) { $christmas.AddDays(-7) } else { $christmas.AddDays(-$dow) }
    return $sunday.AddDays(-21)
}

# Bautismo del Senor segun el calendario de EE.UU. (el del conjunto de datos):
# Epifania se traslada al domingo entre el 2 y el 8 de enero, y el Bautismo es
# el domingo siguiente, o el lunes si Epifania cae el 7 u 8.
function Get-Epiphany([int]$year) {
    $jan2 = [datetime]::new($year, 1, 2)
    $dow = [int]$jan2.DayOfWeek
    if ($dow -eq 0) { return $jan2 }
    return $jan2.AddDays(7 - $dow)
}

function Get-Baptism([int]$year) {
    $epiphany = Get-Epiphany $year
    if ($epiphany.Day -ge 7) { return $epiphany.AddDays(1) }
    return $epiphany.AddDays(7)
}

function Get-LiturgicalYear([datetime]$d) {
    $advent = Get-Advent1 $d.Year
    if ($d -ge $advent) { return $d.Year + 1 }
    return $d.Year
}

function Get-SundayCycle([int]$litYear) {
    switch ($litYear % 3) { 1 { 'A' } 2 { 'B' } default { 'C' } }
}

function Get-WeekdayCycle([int]$litYear) {
    if ($litYear % 2 -eq 1) { 'I' } else { 'II' }
}

function Get-TemporalKey([datetime]$d) {
    $y = $d.Year
    $dow = [int]$d.DayOfWeek
    $litYear = Get-LiturgicalYear $d
    $suffix = if ($dow -eq 0) { '-' + (Get-SundayCycle $litYear) } else { '-' + (Get-WeekdayCycle $litYear) }

    $easter = Get-Easter $y
    $ashWed = $easter.AddDays(-46)
    $lent1 = $easter.AddDays(-42)
    $palm = $easter.AddDays(-7)
    $holyThu = $easter.AddDays(-3)
    $pentecost = $easter.AddDays(49)
    $advent1 = Get-Advent1 $y
    $baptism = Get-Baptism $y
    $christmas = [datetime]::new($y, 12, 25)

    # Del 17 al 24 de diciembre las ferias de Adviento tienen lecturas propias
    # POR FECHA, no por dia de la semana (nº oficial 193 = 17 dic, 194 = 18 dic...).
    if ($d -ge $advent1 -and $d -lt $christmas) {
        if ($dow -ne 0 -and $d.Day -ge 17) { return ('ADVDIC-{0:00}' -f $d.Day) }
        $w = [math]::Floor(($d - $advent1).TotalDays / 7) + 1
        return "ADV-$w-$dow$suffix"
    }

    # El 1 de enero es solemnidad fija (Santa Maria, Madre de Dios).
    if ($d.Month -eq 1 -and $d.Day -eq 1) { return 'ENE01' }

    # Tiempo de Navidad: los domingos son la Sagrada Familia; las ferias del 26
    # al 31 van por fecha.
    if ($d -ge $christmas) {
        if ($dow -eq 0) { return "SAGFAM$suffix" }
        return ('NAV-{0:00}-{1:00}' -f $d.Month, $d.Day)
    }

    if ($d -lt $baptism) {
        $epiphany = Get-Epiphany $y
        if ($d -eq $epiphany) { return 'EPIFANIA' }
        if ($dow -eq 0) { return "SAGFAM$suffix" }
        # Antes de Epifania las lecturas van por fecha; despues, por dia de la
        # semana (lunes = 212, martes = 213, ... sabado = 217).
        if ($d -lt $epiphany) { return ('ANTEPIF-{0:00}' -f $d.Day) }
        return "TRASEPIF-$dow"
    }
    if ($d -eq $baptism) { return "BAUTISMO$suffix" }

    if ($d -ge $holyThu -and $d -lt $easter) {
        $names = @('JUE', 'VIE', 'SAB')
        return "TRI-$($names[[int]($d - $holyThu).TotalDays])"
    }
    if ($d -ge $palm -and $d -lt $holyThu) { return "SANTA-$dow$suffix" }

    if ($d -ge $ashWed -and $d -lt $lent1) { return "CENIZA-$dow" }
    if ($d -ge $lent1 -and $d -lt $palm) {
        $w = [math]::Floor(($d - $lent1).TotalDays / 7) + 1
        return "CUA-$w-$dow$suffix"
    }

    if ($d -ge $easter -and $d -le $pentecost) {
        $w = [math]::Floor(($d - $easter).TotalDays / 7) + 1
        return "PAS-$w-$dow$suffix"
    }

    # Solemnidades moviles atadas a la Pascua, que caen ya en Tiempo Ordinario
    # y desplazan a la feria que tocaria ese dia.
    $trinity = $easter.AddDays(56)          # domingo siguiente a Pentecostes
    $corpus = $easter.AddDays(63)           # domingo siguiente a la Trinidad (EE.UU.)
    $sacredHeart = $easter.AddDays(68)      # viernes siguiente
    $immaculateHeart = $easter.AddDays(69)  # sabado siguiente
    if ($d -eq $trinity) { return "TRINIDAD$suffix" }
    if ($d -eq $corpus) { return "CORPUS$suffix" }
    # El Sagrado Corazon tiene ciclo propio A/B/C aunque caiga en viernes
    # (nº oficial 170 = A, 171 = B, 172 = C), no el ciclo ferial I/II.
    if ($d -eq $sacredHeart) { return 'SAGCORAZON-' + (Get-SundayCycle $litYear) }
    # El Corazon Inmaculado es memoria opcional: se deja la feria que toque.

    if ($d -gt $baptism -and $d -lt $ashWed) {
        $sunday = $d.AddDays(-$dow)
        $w = [math]::Round(($sunday - $baptism).TotalDays / 7) + 1
        return "ORD-$w-$dow$suffix"
    }

    if ($d -gt $pentecost -and $d -lt $advent1) {
        $lastSunday = $advent1.AddDays(-7)
        $sunday = $d.AddDays(-$dow)
        $n = [math]::Round(($lastSunday - $sunday).TotalDays / 7)
        return "ORD-$(34 - $n)-$dow$suffix"
    }

    return ('OTRO-{0:00}-{1:00}' -f $d.Month, $d.Day)
}

# ----------------------------------------------------------------- proceso

Write-Output 'Leyendo leccionario...'
$json = [System.IO.File]::ReadAllText($srcReadings, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$dates = $json.PSObject.Properties | ForEach-Object { $_.Name } | Sort-Object
Write-Output "  $($dates.Count) fechas, de $($dates[0]) a $($dates[-1])"

$TIPOS = [ordered]@{
    first_reading      = @('Primera lectura', 0)
    responsorial_psalm = @('Salmo responsorial', 23)
    second_reading     = @('Segunda lectura', 0)
    gospel             = @('Evangelio', 0)
}

$records = @{}
$unparsed = New-Object System.Collections.ArrayList
$totalCitas = 0

foreach ($ds in $dates) {
    $d = [datetime]::ParseExact($ds, 'yyyy-MM-dd', $null)
    $r = $json.$ds.readings
    if (-not $r) { continue }

    $parts = New-Object System.Collections.ArrayList
    $failures = 0
    foreach ($field in $TIPOS.Keys) {
        $v = $r.$field
        if (-not $v) { continue }
        $cit = @($v)[0].citation
        if (-not $cit) { continue }
        $totalCitas++
        $p = Parse-Citation $cit $TIPOS[$field][1]
        if ($null -eq $p) { [void]$unparsed.Add("$ds $field :: $cit"); $failures++; continue }
        [void]$parts.Add(@{ tipo = $TIPOS[$field][0]; parsed = $p })
    }
    if ($parts.Count -eq 0) { continue }

    $records[$ds] = @{
        date = $d
        temporalKey = Get-TemporalKey $d
        fixedKey = '{0:00}-{1:00}' -f $d.Month, $d.Day
        parts = $parts
        fingerprint = (($parts | ForEach-Object { "$($_.tipo)=$($_.parsed.label)" }) -join '|')
        # Numero oficial del leccionario: identifica la misa con independencia
        # de como se hayan abreviado las citas ese ano. Sirve de comprobacion
        # independiente de mi propio calculo del calendario.
        lect = ([string]$json.$ds.lectionary_number).Trim()
        failures = $failures
    }
}

Write-Output "  citas totales        : $totalCitas"
Write-Output "  citas no interpretadas: $($unparsed.Count)"
$unparsed | Select-Object -First 12 | ForEach-Object { "     ? $_" }
Write-Output "  dias utilizables     : $($records.Count)"

# --- clasificacion por el numero oficial del leccionario ---
# En la numeracion del Ordo Lectionum Missae, del 507 en adelante empieza el
# Propio de los Santos y los Comunes: son celebraciones de FECHA FIJA. Por
# debajo es el Tiempo (Propio del Tiempo), que depende de la Pascua.
# Usar este numero en vez de adivinar por repeticion es mucho mas fiable.
$SANTORAL_DESDE = 507

$byFixed = @{}; $byTemporal = @{}
foreach ($k in $records.Keys) {
    $rec = $records[$k]
    $lectNum = 0
    [void][int]::TryParse(($rec.lect -replace '[^\d].*$', ''), [ref]$lectNum)

    if ($lectNum -ge $SANTORAL_DESDE) {
        $fk = $rec.fixedKey
        if (-not $byFixed.ContainsKey($fk)) { $byFixed[$fk] = New-Object System.Collections.ArrayList }
        [void]$byFixed[$fk].Add($rec)
    } else {
        $tk = $rec.temporalKey
        if (-not $byTemporal.ContainsKey($tk)) { $byTemporal[$tk] = New-Object System.Collections.ArrayList }
        [void]$byTemporal[$tk].Add($rec)
    }
}

# De cada grupo se elige el dia cuyas citas se interpretaron mejor.
function Select-Best($group) {
    $byLect = @($group | Group-Object { $_.lect } | Sort-Object Count -Descending)
    $winners = @($group | Where-Object { $_.lect -eq $byLect[0].Name })
    # Desempate determinista: primero el que se interpreto sin fallos, y a
    # igualdad el ano mas reciente (notacion mas cuidada en la fuente).
    return ($winners | Sort-Object @{e={$_.failures}}, @{e={$_.date}; Descending=$true} | Select-Object -First 1)
}

$fixedTable = @{}
foreach ($fk in $byFixed.Keys) { $fixedTable[$fk] = Select-Best $byFixed[$fk] }

$temporalTable = @{}; $conflicts = New-Object System.Collections.ArrayList
$multiYear = 0; $agree = 0; $agreeLect = 0; $multiLect = 0
foreach ($tk in $byTemporal.Keys) {
    $group = $byTemporal[$tk]
    # @() imprescindible: con un solo grupo, Sort-Object devuelve el objeto
    # suelto y $groups.Count pasaria a ser el tamano del grupo, no 1.
    $groups = @($group | Group-Object { $_.fingerprint } | Sort-Object Count -Descending)
    if ($group.Count -ge 2) {
        $multiYear++
        if ($groups.Count -eq 1) { $agree++ }

        # El numero oficial manda: si coincide en todos los anos, la misa es la
        # misma y cualquier diferencia de huella es solo ruido de notacion.
        $lects = @($group | ForEach-Object { $_.lect } | Where-Object { $_ } | Select-Object -Unique)
        if ($lects.Count -ge 1) {
            $multiLect++
            if ($lects.Count -eq 1) { $agreeLect++ }
            else {
                [void]$conflicts.Add("$tk -> " +
                    (($group | ForEach-Object { $_.date.ToString('yyyy-MM-dd') + '(' + $_.lect + ')' }) -join ' '))
            }
        }
    }
    $temporalTable[$tk] = Select-Best $group
}

Write-Output ''
Write-Output '=== RESULTADO ==='
Write-Output "Claves de fecha fija : $($fixedTable.Count)"
Write-Output "Claves temporales    : $($temporalTable.Count)"
Write-Output "Claves con 2+ anos   : $multiYear"
Write-Output "  coincidentes (citas)     : $agree  ($([math]::Round(100*$agree/[math]::Max($multiYear,1),1))%)"
Write-Output "  coincidentes (nº oficial): $agreeLect de $multiLect  ($([math]::Round(100*$agreeLect/[math]::Max($multiLect,1),1))%)"
Write-Output "Conflictos REALES (nº oficial distinto): $($conflicts.Count)"
$conflicts | Select-Object -First 15 | ForEach-Object { "   ! $_" }

# ------------------------------------------------- versificacion y validacion
#
# Las citas del leccionario usan la numeracion hebrea/Vulgata, y la Biblia
# empaquetada no siempre coincide. Hay dos riesgos distintos:
#   - cita FUERA DE RANGO: se ve al instante (no hay texto)
#   - cita DENTRO de rango pero desplazada: silenciosa, y por eso peor
# Aqui se corrigen los casos comprobados y se descarta lo que no encaje.

$bibliaDir = 'E:\PG\BibliaVoz\app\src\main\assets\bible-cat'
$estructura = @{}   # libro -> array con el numero de versiculos de cada capitulo
for ($b = 1; $b -le 73; $b++) {
    $libro = [System.IO.File]::ReadAllText((Join-Path $bibliaDir "$b.json"), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    $estructura[$b] = @($libro.chapters | ForEach-Object { $_.Count })
}

$ajustes = New-Object System.Collections.ArrayList
$descartadas = New-Object System.Collections.ArrayList

function Adjust-Ranges($book, $ranges, $etiqueta) {
    $out = New-Object System.Collections.ArrayList
    foreach ($t in $ranges) {
        $c1 = $t[0]; $v1 = $t[1]; $c2 = $t[2]; $v2 = $t[3]

        # Joel: en numeracion hebrea tiene 4 capitulos; el empaquetado tiene 3,
        # donde nuestro cap. 3 ES el cap. 4 hebreo (comprobado en el texto).
        if ($book -eq 36) {
            if ($c1 -eq 4) { $c1 = 3 }; if ($c2 -eq 4) { $c2 = 3 }
            elseif ($c1 -eq 3 -and $c2 -eq 3) { $c1 = 2; $c2 = 2; $v1 += 27; $v2 += 27 }
            [void]$ajustes.Add("Joel $etiqueta")
        }

        # Jonas: la cita cuenta 2:1 donde el empaquetado pone 1:17.
        if ($book -eq 39 -and $c1 -eq 2) {
            if ($v1 -eq 1) { $c1 = 1; $v1 = 17 } else { $v1 -= 1 }
            [void]$ajustes.Add("Jonás $etiqueta")
        }
        if ($book -eq 39 -and $c2 -eq 2 -and $v2 -gt 1) { $v2 -= 1 }

        $caps = $estructura[$book]
        if ($null -eq $caps -or $c1 -lt 1 -or $c1 -gt $caps.Count) {
            [void]$descartadas.Add("$etiqueta (libro $book cap $c1)")
            continue
        }
        if ($c2 -gt $caps.Count) { $c2 = $caps.Count }
        $maxV = $caps[$c2 - 1]
        if ($v2 -gt $maxV) { $v2 = $maxV }
        if ($v1 -lt 1) { $v1 = 1 }
        if ($c1 -eq $c2 -and $v1 -gt $v2) { $v1 = 1 }

        [void]$out.Add(@($c1, $v1, $c2, $v2))
    }
    # La coma inicial es imprescindible: sin ella PowerShell desenrolla la lista
    # al devolverla y un tramo de cuatro numeros acaba siendo cuatro tramos.
    return ,$out.ToArray()
}

# El salmo responsorial se lee COMPLETO cuando es corto: el desfase de los
# titulos (0, 1 o 2 versiculos segun el salmo) haria que un tramo citado
# sonara desplazado, y eso no se nota al oirlo. Leerlo entero nunca miente.
$LIMITE_SALMO_COMPLETO = 30

function Fix-Record($rec) {
    foreach ($p in $rec.parts) {
        $book = $p.parsed.book
        if ($book -eq 23) {
            $cap = $p.parsed.ranges[0][0]
            $caps = $estructura[23]
            if ($cap -ge 1 -and $cap -le $caps.Count -and $caps[$cap - 1] -le $LIMITE_SALMO_COMPLETO) {
                $p.parsed.ranges = @(, @($cap, 1, $cap, $caps[$cap - 1]))
                continue
            }
        }
        $p.parsed.ranges = Adjust-Ranges $book $p.parsed.ranges $p.parsed.label
    }
}

foreach ($k in $fixedTable.Keys) { Fix-Record $fixedTable[$k] }
foreach ($k in $temporalTable.Keys) { Fix-Record $temporalTable[$k] }

Write-Output ''
Write-Output "Ajustes de versificacion : $($ajustes.Count)  ($((($ajustes | Select-Object -Unique) -join ', ')))"
Write-Output "Tramos descartados       : $($descartadas.Count)"
$descartadas | Select-Object -First 8 | ForEach-Object { "   - $_" }

# ----------------------------------------------------------------- salida

function Emit-Record($rec) {
    $items = foreach ($p in $rec.parts) {
        $rangesJson = ($p.parsed.ranges | ForEach-Object { '[' + ($_ -join ',') + ']' }) -join ','
        '{"t":"' + $p.tipo + '","c":"' + ($p.parsed.label -replace '"', '\"') + '","b":' + $p.parsed.book + ',"r":[' + $rangesJson + ']}'
    }
    return '[' + ($items -join ',') + ']'
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.Append('{"fuente":"Leccionario romano general","fijas":{')
$i = 0
foreach ($k in ($fixedTable.Keys | Sort-Object)) {
    if ($i++ -gt 0) { [void]$sb.Append(',') }
    [void]$sb.Append('"').Append($k).Append('":').Append((Emit-Record $fixedTable[$k]))
}
[void]$sb.Append('},"temporales":{')
$i = 0
foreach ($k in ($temporalTable.Keys | Sort-Object)) {
    if ($i++ -gt 0) { [void]$sb.Append(',') }
    [void]$sb.Append('"').Append($k).Append('":').Append((Emit-Record $temporalTable[$k]))
}
[void]$sb.Append('}}')

$out = Join-Path $outDir 'leccionario.json'
[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ''
Write-Output "Escrito $out ($([math]::Round((Get-Item $out).Length/1KB,1)) KB)"
Write-Output 'LECCIONARIO LISTO'
