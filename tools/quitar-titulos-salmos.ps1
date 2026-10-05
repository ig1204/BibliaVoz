param(
  [string]$assets = "E:\PG\BibliaVoz\app\src\main\assets\bible-cat",
  [string]$csv    = "E:\PG\BibliaVoz\tools\titulos-salmos.csv",
  [switch]$Aplicar   # sin -Aplicar solo informa (dry-run)
)
$ErrorActionPreference = "Stop"
$enc = New-Object System.Text.UTF8Encoding($false)

# Quita el TÍTULO (inscripción) del versículo 1 de los Salmos: «Para el director
# musical. Con la melodía de… Un salmo de Asaf.». Decisión del usuario: fuera en
# TODO (misa y Biblia por capítulos). La tabla (tools/titulos-salmos.csv) la revisó
# a mano la sesión de revisión, salmo por salmo, sobre la SBL ya convertida
# («siervo del Señor» en el Sal 18); 116 de 150 llevan título. Los «¡Alabado sea el
# Señor!» (aleluya) y «¡Canten…» del Sal 96 son texto y NO están en la tabla.
# El título va ANTES del contenido, así que no choca con los vocativos corregidos.
# PowerShell 5.1 lee .ps1 sin BOM como ANSI: guardar CON BOM.

function Esc([string]$s){
  $sb = New-Object System.Text.StringBuilder
  foreach ($ch in $s.ToCharArray()){
    $code=[int]$ch
    switch ($ch){ '"'{[void]$sb.Append('\"')} '\'{[void]$sb.Append('\\')} "`n"{[void]$sb.Append('\n')} "`r"{[void]$sb.Append('\r')} "`t"{[void]$sb.Append('\t')} default{ if($code -lt 32){[void]$sb.Append('\u{0:x4}' -f $code)}else{[void]$sb.Append($ch)} } }
  }
  return $sb.ToString()
}
function BookJson($o){
  $caps = foreach($cap in $o.chapters){ $vs = foreach($v in $cap){ '"'+(Esc ([string]$v))+'"' }; '['+($vs -join ',')+']' }
  return '{"n":'+$o.n+',"name":"'+(Esc $o.name)+'","abbr":"'+(Esc $o.abbr)+'","chapters":['+($caps -join ',')+']}'
}

$tabla = Import-Csv $csv -Encoding UTF8
$f = Join-Path $assets "23.json"   # Salmos
$o = Get-Content $f -Raw -Encoding UTF8 | ConvertFrom-Json
$quitados = 0; $yaSin = 0
$abortos = New-Object System.Collections.ArrayList
foreach ($r in $tabla) {
  if ($r.titulo -eq '') { continue }
  $sal = [int]$r.salmo
  $v1 = [string]$o.chapters[$sal-1][0]
  if ($v1.StartsWith($r.titulo, [System.StringComparison]::Ordinal)) {
    $nuevo = $v1.Substring($r.titulo.Length).TrimStart()
    if ($nuevo.Length -eq 0) { [void]$abortos.Add("Sal ${sal}: el versículo 1 quedaría vacío"); continue }
    $o.chapters[$sal-1][0] = $nuevo
    $quitados++
  } elseif ($v1.StartsWith($r.empieza.Substring(0,[Math]::Min(20,$r.empieza.Length)), [System.StringComparison]::Ordinal)) {
    $yaSin++   # ya se le quitó el título (idempotente)
  } else {
    [void]$abortos.Add("Sal ${sal}: el v1 no empieza con el título esperado")
  }
}
Write-Host "Títulos quitados: $quitados (ya estaban sin título: $yaSin; esperado 116)."
Write-Host "ABORTOS (debe 0): $($abortos.Count)"
$abortos | ForEach-Object { Write-Host "  X $_" }
if ($abortos.Count -eq 0 -and $Aplicar) {
  [System.IO.File]::WriteAllText($f, (BookJson $o), $enc)
  Write-Host "Guardado."
} elseif (-not $Aplicar) {
  Write-Host "`n(DRY-RUN: no se escribió nada. Reejecuta con -Aplicar para guardar.)"
}
# muestra de control
Write-Host "--- muestras ---"
foreach ($s in 3,18,23,79,88,98,150) { Write-Host ("  Sal ${s}:1 -> " + ([string]$o.chapters[$s-1][0]).Substring(0,[Math]::Min(55,([string]$o.chapters[$s-1][0]).Length))) }
