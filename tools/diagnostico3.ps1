$ErrorActionPreference = 'Stop'
$json = [System.IO.File]::ReadAllText('E:\PG\.liturgia\readings_usccb.json', [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$dates = $json.PSObject.Properties | ForEach-Object { $_.Name } | Sort-Object

Write-Output '=== todas las citas de Jonas y de Joel en la fuente ==='
foreach ($ds in $dates) {
    $r = $json.$ds.readings
    if (-not $r) { continue }
    foreach ($f in @('first_reading','responsorial_psalm','second_reading','gospel')) {
        $v = $r.$f
        if (-not $v) { continue }
        $c = @($v)[0].citation
        if (-not $c) { continue }
        if ($c -match '^(?i)(jon|jonah|jl|joel)\b') {
            Write-Output ("{0}  {1,-18} {2}" -f $ds, $f, $c)
        }
    }
}
