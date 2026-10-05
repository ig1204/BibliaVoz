param(
  [string]$assets = "E:\PG\BibliaVoz\app\src\main\assets\bible-cat",
  [switch]$Aplicar   # sin -Aplicar solo informa (dry-run)
)
$ErrorActionPreference = "Stop"
$enc = New-Object System.Text.UTF8Encoding($false)

# Segunda pasada tras convertir-senor.ps1 (Yahvé→«el Señor»): corrige 19 VOCATIVOS
# que quedaron como «El Señor,» con artículo. La regla de convertir-senor miraba el
# inicio del versículo, pero en los Salmos el vocativo viene tras el título («Para el
# director musical… Salmo de David. Yahvé, …»), es decir a inicio de FRASE, no de
# versículo, y salió «El Señor,». Aquí se cambia a «Señor,» solo en estos versos
# comprobados a mano (contra las aposiciones correctas tipo «el Señor, Dios de…», que
# NO se tocan). PowerShell 5.1 lee .ps1 sin BOM como ANSI: guardar CON BOM.

# libro (nº en bible-cat) -> lista de @(capítulo, versículo), numeración SBL/KJV
$VOCATIVOS = @{
  23 = @(@(5,12),@(6,1),@(7,1),@(8,1),@(15,1),@(30,10),@(36,6),@(38,1),@(70,5),@(85,1),
         @(104,1),@(131,1),@(132,1),@(139,1),@(141,1),@(143,1))   # Salmos
  9  = @(,@(23,11))   # 1 Samuel 23:11  (@(,...) evita que PowerShell aplane el array de uno)
  14 = @(,@(14,11))   # 2 Crónicas 14:11
  42 = @(,@(1,12))    # Habacuc 1:12
}

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

$cambios = 0; $yaHechos = 0
foreach ($bn in $VOCATIVOS.Keys) {
  $f = Join-Path $assets "$bn.json"
  $o = Get-Content $f -Raw -Encoding UTF8 | ConvertFrom-Json
  $tocado = $false
  foreach ($cv in $VOCATIVOS[$bn]) {
    $t = [string]$o.chapters[$cv[0]-1][$cv[1]-1]
    $idx = $t.IndexOf('El Señor,')
    if ($idx -lt 0) { $yaHechos++; continue }   # ya está «Señor,» (idempotente)
    $o.chapters[$cv[0]-1][$cv[1]-1] = $t.Substring(0,$idx) + 'Señor,' + $t.Substring($idx+9)
    $tocado = $true; $cambios++
    Write-Host ("  {0} {1}:{2}" -f $o.name, $cv[0], $cv[1])
  }
  if ($tocado -and $Aplicar) { [System.IO.File]::WriteAllText($f, (BookJson $o), $enc) }
}
Write-Host "Vocativos corregidos: $cambios (ya estaban: $yaHechos; total esperado 19)."

# Revisión: lista los «El Señor,» a inicio de frase que NO son aposición, por si
# quedara algún vocativo sin corregir (deberían ser solo las aposiciones conocidas).
$apos = '^(tú |tu |su |nuestro |vuestro |el Dios|Dios de|Dios,|Dios\.|Rey de|Rey,|que |a quien|el juez)'
$rev = New-Object System.Collections.ArrayList
for ($n=1; $n -le 73; $n++) {
  $o = Get-Content (Join-Path $assets "$n.json") -Raw -Encoding UTF8 | ConvertFrom-Json
  for ($c=0; $c -lt $o.chapters.Count; $c++) { for ($v=0; $v -lt $o.chapters[$c].Count; $v++) {
    $t = [string]$o.chapters[$c][$v]
    foreach ($m in [regex]::Matches($t, '(?:^|[.!?»”]\s+)El Señor, (.{0,22})')) {
      if ($m.Groups[1].Value -notmatch $apos) { [void]$rev.Add("$($o.name) $($c+1):$($v+1) > El Señor, $($m.Groups[1].Value)") }
    }
  } }
}
Write-Host "`nREVISAR «El Señor,» a inicio de frase no-aposición (deberia 0): $($rev.Count)"
$rev | ForEach-Object { Write-Host "  ? $_" }
if (-not $Aplicar) { Write-Host "`n(DRY-RUN: no se escribió nada. Reejecuta con -Aplicar para guardar.)" }
