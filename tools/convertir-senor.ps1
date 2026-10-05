param(
  [string]$assets = "E:\PG\BibliaVoz\app\src\main\assets\bible-cat",
  [switch]$Aplicar   # sin -Aplicar solo informa (dry-run)
)
$ErrorActionPreference = "Stop"
$enc = New-Object System.Text.UTF8Encoding($false)

# Cambia el nombre divino «Yahvé» de la Santa Biblia Libre por «el Señor», para la
# versión mexicana (el leccionario de la CEM usa «el Señor»). Reglas acordadas con la
# sesión de revisión. PowerShell 5.1 lee los .ps1 sin BOM como ANSI y corrompe los
# acentos de los literales: este archivo debe guardarse CON BOM.

$VOC = [char]0x0001   # marcador temporal: vocativo (sin artículo)
$ART = [char]0x0002   # marcador temporal: con artículo (el/del/al Señor)
# aperturas tras las que «el Señor» va en mayúscula: « " “ ¡ ¿
$AP = '\u00AB\u0022\u201C\u00A1\u00BF'

function Convertir([string]$t) {
  if ($t -notmatch 'Yah') { return $t }
  $t = $t -replace 'Señor Yahvé', 'Señor Dios'                         # a (Adonai YHWH adyacente)
  $t = $t -replace 'Señor, Yahvé de los Ejércitos', 'Señor, Dios de los Ejércitos'  # a2 (ha-Adon YHWH Tzevaot: evita «el Señor, el Señor»)
  $t = $t -replace 'Yahvé el Señor', 'Yahvé Dios'                      # a3 (ha-Adon YHWH: colapsa el doblete a «Yahvé Dios»)
  $t = $t -replace '([\u00A1]?[Oo]h,? )Yahvé', "`$1$VOC"               # d (oh Señor) — antes que b, para «oh Yahvé Dios» -> «oh Señor Dios»
  $t = $t -replace '([Tt]ú, )Yahvé', "`$1$VOC"                         # d2 (vocativo «tú, Señor [Dios]»)
  $t = $t -replace '([Yy]o, )Yahvé', "`$1$ART"                         # d-excepción (yo, el Señor)
  $t = $t -replace 'Yahvé Dios', "$ART Dios"                           # b (YHWH Elohim -> el Señor Dios)
  $t = $t -replace "(^|[$AP]\s?|, )Yahvé(?=\s?[,;:!?.])", "`$1$VOC"     # d (vocativo por puntuación)
  $t = $t -replace 'Yahvé', $ART                                        # e (resto)
  $t = $t -replace '(?<!\p{L})Yah(?!\p{L})', $ART                       # c (Yah suelto)
  # resolver marcadores (creplace: distingue mayúsculas; lookbehind: que «de/a» sea palabra suelta, no cola de «para/desde»)
  $t = $t -creplace ('(?<!\p{L})de ' + $ART), 'del Señor'
  $t = $t -creplace ('(?<!\p{L})De ' + $ART), 'Del Señor'
  $t = $t -creplace ('(?<!\p{L})a ' + $ART), 'al Señor'
  $t = $t -creplace ('(?<!\p{L})A ' + $ART), 'Al Señor'
  $t = $t -replace "(^|[$AP]\s?|[.:;!?]\s)$ART", '$1El Señor'
  $t = $t -replace $ART, 'el Señor'
  $t = $t -replace $VOC, 'Señor'
  return $t
}

# Quita «Selah» (marca musical de los Salmos y de Habacuc 3): la voz la lee «Cela».
# Decisión del usuario (regla h). La puntuación de la frase ya está antes de «Selah»,
# así que basta con borrar la palabra con su propio punto y recomponer los espacios.
function QuitarSelah([string]$t) {
  if ($t -notmatch 'Selah') { return $t }
  $t = $t -replace '\s*—\s*Selah\s*—\s*', ' '   # «— Selah —» en medio del verso
  $t = $t -replace '\s*(?<!\p{L})Selah\.(?=\s|$)', ''     # «… Selah.» (con su punto)
  $t = $t -replace '\s*(?<!\p{L})Selah(?!\p{L})', ''      # «… Selah» (sin punto) o cualquier resto
  $t = $t -replace '  +', ' '
  return $t.Trim()
}

# JSON compacto (mismo formato que los assets)
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

$totalCambios=0; $archivos=0
$abortos = New-Object System.Collections.ArrayList
$revisar = New-Object System.Collections.ArrayList
$muestras = New-Object System.Collections.ArrayList
for ($n=1; $n -le 73; $n++) {
  $f = Join-Path $assets "$n.json"
  $o = Get-Content $f -Raw -Encoding UTF8 | ConvertFrom-Json
  $cambio=$false
  for ($c=0; $c -lt $o.chapters.Count; $c++) {
    for ($v=0; $v -lt $o.chapters[$c].Count; $v++) {
      $orig = [string]$o.chapters[$c][$v]
      if ($orig -notmatch 'Yah' -and $orig -notmatch 'Selah') { continue }
      $nuevo = QuitarSelah (Convertir $orig)
      if ($nuevo -ne $orig) { $cambio=$true; $totalCambios++ }
      $ref = "$($o.name) $($c+1):$($v+1)"
      # Selah: que no quede suelto, ni deje el verso vacío o sin la puntuación que tenía.
      $teniaSelah = $orig -match '(?<!\p{L})Selah'
      $selahMal = $teniaSelah -and ($nuevo.Trim().Length -eq 0 -or $nuevo -match '(?<!\p{L})Selah(?!\p{L})' -or (($orig.TrimEnd() -match '[.;:!?»”]$') -and ($nuevo.TrimEnd() -notmatch '[.;:!?»”]$')))
      # ABORTO: errores reales que NO deben quedar en el texto.
      if ($selahMal -or $nuevo -match 'Yahv' -or $nuevo -match '(?<!\p{L})Yah(?!\p{L})' -or $nuevo -match ' de el Señor' -or $nuevo -match ' a el Señor' -or $nuevo -match 'el el Señor' -or $nuevo -match 'Señor el Señor' -or $nuevo -match '(?<=\p{L})al Señor' -or $nuevo -match '(?<=\p{L})del Señor' -or $nuevo.Contains($VOC) -or $nuevo.Contains($ART)) {
        [void]$abortos.Add("$ref`n      ORIG: " + $orig.Substring(0,[Math]::Min(90,$orig.Length)) + "`n      NEW : " + $nuevo.Substring(0,[Math]::Min(90,$nuevo.Length)))
      }
      # REVISAR: «Señor, el Señor» legítimo (Yahvés consecutivos o aposición) — solo para ojear.
      elseif ($nuevo -match 'Señor, el Señor') {
        [void]$revisar.Add("$ref :: " + $nuevo.Substring(0,[Math]::Min(100,$nuevo.Length)))
      }
      if ($orig -match '(oh Yahvé|tú, Yahvé|^Yahvé,)' -and $muestras.Count -lt 40) { [void]$muestras.Add("${ref}: " + $nuevo.Substring(0,[Math]::Min(75,$nuevo.Length))) }
      if ($teniaSelah -and $muestras.Count -lt 40) { [void]$muestras.Add("Selah ${ref}: " + $nuevo.Substring([Math]::Max(0,$nuevo.Length-55))) }
      $o.chapters[$c][$v] = $nuevo
    }
  }
  if ($cambio) { $archivos++; if ($Aplicar) { [System.IO.File]::WriteAllText($f, (BookJson $o), $enc) } }
}
Write-Host "Versiculos cambiados: $totalCambios en $archivos libros."
Write-Host "ABORTOS (deberia 0): $($abortos.Count)"
$abortos | ForEach-Object { Write-Host "  X $_" }
Write-Host "`nREVISAR 'Señor, el Señor' (legitimo, $($revisar.Count)):"
$revisar | ForEach-Object { Write-Host "  ? $_" }
Write-Host "`n--- muestras de vocativos convertidos ---"
$muestras | ForEach-Object { Write-Host "  $_" }
if (-not $Aplicar) { Write-Host "`n(DRY-RUN: no se escribió nada. Reejecuta con -Aplicar para guardar.)" }
