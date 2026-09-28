$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'parser.ps1')

$json = [System.IO.File]::ReadAllText('E:\PG\.liturgia\readings_usccb.json', [System.Text.Encoding]::UTF8) | ConvertFrom-Json

$TIPOS = [ordered]@{
    first_reading      = @('Primera lectura', 0)
    responsorial_psalm = @('Salmo responsorial', 23)
    second_reading     = @('Segunda lectura', 0)
    gospel             = @('Evangelio', 0)
}

function Get-Fingerprint([string]$ds) {
    $r = $json.$ds.readings
    $parts = @()
    foreach ($f in $TIPOS.Keys) {
        $v = $r.$f
        if (-not $v) { continue }
        $cit = @($v)[0].citation
        if (-not $cit) { continue }
        $p = Parse-Citation $cit $TIPOS[$f][1]
        if ($null -eq $p) { $parts += "$f=<FALLO:$cit>"; continue }
        $parts += "$($TIPOS[$f][0])=$($p.label)"
    }
    return ($parts -join '|')
}

Write-Output '=== HUELLAS de sabado semana 1 TO ciclo I ==='
$fps = @{}
foreach ($d in @('2023-01-14','2025-01-18','2027-01-16')) {
    $fp = Get-Fingerprint $d
    $fps[$d] = $fp
    Write-Output "$d -> $fp"
}
$distinct = $fps.Values | Select-Object -Unique
Write-Output "huellas distintas: $($distinct.Count)  (deberia ser 1)"

Write-Output ''
Write-Output '=== Group-Object sobre hashtables: funciona? ==='
$a = @{ fingerprint = 'IGUAL'; n = 1 }
$b = @{ fingerprint = 'IGUAL'; n = 2 }
$c = @{ fingerprint = 'DISTINTA'; n = 3 }
$lista = New-Object System.Collections.ArrayList
[void]$lista.Add($a); [void]$lista.Add($b); [void]$lista.Add($c)

$g1 = $lista | Group-Object { $_.fingerprint }
Write-Output "con scriptblock  -> $($g1.Count) grupos (correcto seria 2); nombres: $(($g1 | ForEach-Object { '[' + $_.Name + ']' }) -join ' ')"

$g2 = $lista | Group-Object -Property fingerprint
Write-Output "con -Property    -> $($g2.Count) grupos; nombres: $(($g2 | ForEach-Object { '[' + $_.Name + ']' }) -join ' ')"

# alternativa manual
$manual = @{}
foreach ($item in $lista) {
    $k = $item.fingerprint
    if (-not $manual.ContainsKey($k)) { $manual[$k] = 0 }
    $manual[$k]++
}
Write-Output "manual           -> $($manual.Count) grupos; $(($manual.Keys | ForEach-Object { "$_=$($manual[$_])" }) -join ' ')"

Write-Output ''
Write-Output '=== lectionary_number como huella oficial ==='
foreach ($d in @('2023-01-14','2025-01-18','2027-01-16','2023-10-29','2026-10-25')) {
    Write-Output "$d -> lect=$($json.$d.lectionary_number)"
}
