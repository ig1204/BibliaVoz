$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'parser.ps1')

$json = [System.IO.File]::ReadAllText('E:\PG\.liturgia\readings_usccb.json', [System.Text.Encoding]::UTF8) | ConvertFrom-Json

function Show-Day([string]$ds) {
    $e = $json.$ds
    if (-not $e) { Write-Output "$ds : SIN ENTRADA"; return }
    Write-Output "--- $ds  feast='$($e.feast)'  mass='$($e.mass)'  lect='$($e.lectionary_number)' ---"
    $r = $e.readings
    foreach ($f in @('first_reading','responsorial_psalm','second_reading','alleluia','gospel')) {
        $v = $r.$f
        if (-not $v) { continue }
        $arr = @($v)
        $cits = ($arr | ForEach-Object { $_.citation }) -join '  ||  '
        Write-Output ("   {0,-20} n={1}  {2}" -f $f, $arr.Count, $cits)
    }
}

Write-Output '===== Sabado de la semana 1 del Tiempo Ordinario, ciclo I ====='
foreach ($d in @('2023-01-14','2025-01-18','2027-01-16')) { Show-Day $d }

Write-Output ''
Write-Output '===== 30 de diciembre en cuatro anos ====='
foreach ($d in @('2023-12-30','2024-12-30','2025-12-30','2026-12-30')) { Show-Day $d }

Write-Output ''
Write-Output '===== Domingo 30 del Tiempo Ordinario, ciclo A ====='
foreach ($d in @('2023-10-29','2026-10-25')) { Show-Day $d }
