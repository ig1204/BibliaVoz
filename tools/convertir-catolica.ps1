$ErrorActionPreference = 'Stop'

# Convierte la "Santa Biblia Libre" (eBible spabll, dominio publico) al formato
# compacto que ya usa la app, quedandose con el canon catolico de 73 libros.
# Ester y Daniel se toman en su forma griega (ESG, DNG), que es la catolica.

$src = 'E:\PG\.liturgia\spabll\spabll_vpl.txt'
$outDir = 'E:\PG\BibliaVoz\app\src\main\assets\bible-cat'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# codigo eBible ; nombre ; abreviatura ; testamento
$CANON = @(
 'GEN;Génesis;Gn;AT','EXO;Éxodo;Ex;AT','LEV;Levítico;Lv;AT','NUM;Números;Nm;AT','DEU;Deuteronomio;Dt;AT',
 'JOS;Josué;Jos;AT','JDG;Jueces;Jue;AT','RUT;Rut;Rt;AT','1SA;1 Samuel;1S;AT','2SA;2 Samuel;2S;AT',
 '1KI;1 Reyes;1R;AT','2KI;2 Reyes;2R;AT','1CH;1 Crónicas;1Cr;AT','2CH;2 Crónicas;2Cr;AT',
 'EZR;Esdras;Esd;AT','NEH;Nehemías;Neh;AT','TOB;Tobías;Tb;AT','JDT;Judit;Jdt;AT','ESG;Ester;Est;AT',
 '1MA;1 Macabeos;1M;AT','2MA;2 Macabeos;2M;AT','JOB;Job;Job;AT','PSA;Salmos;Sal;AT','PRO;Proverbios;Pr;AT',
 'ECC;Eclesiastés;Ec;AT','SOL;Cantar de los Cantares;Ct;AT','WIS;Sabiduría;Sab;AT','SIR;Eclesiástico;Eclo;AT',
 'ISA;Isaías;Is;AT','JER;Jeremías;Jer;AT','LAM;Lamentaciones;Lm;AT','BAR;Baruc;Ba;AT','EZE;Ezequiel;Ez;AT',
 'DNG;Daniel;Dn;AT','HOS;Oseas;Os;AT','JOE;Joel;Jl;AT','AMO;Amós;Am;AT','OBA;Abdías;Abd;AT','JON;Jonás;Jon;AT',
 'MIC;Miqueas;Mi;AT','NAH;Nahúm;Na;AT','HAB;Habacuc;Hab;AT','ZEP;Sofonías;Sof;AT','HAG;Ageo;Ag;AT',
 'ZEC;Zacarías;Zac;AT','MAL;Malaquías;Mal;AT',
 'MAT;Mateo;Mt;NT','MAR;Marcos;Mc;NT','LUK;Lucas;Lc;NT','JOH;Juan;Jn;NT','ACT;Hechos;Hch;NT',
 'ROM;Romanos;Rom;NT','1CO;1 Corintios;1Co;NT','2CO;2 Corintios;2Co;NT','GAL;Gálatas;Gal;NT',
 'EPH;Efesios;Ef;NT','PHI;Filipenses;Flp;NT','COL;Colosenses;Col;NT','1TH;1 Tesalonicenses;1Ts;NT',
 '2TH;2 Tesalonicenses;2Ts;NT','1TI;1 Timoteo;1Tm;NT','2TI;2 Timoteo;2Tm;NT','TIT;Tito;Tit;NT',
 'PHM;Filemón;Flm;NT','HEB;Hebreos;Heb;NT','JAM;Santiago;Stg;NT','1PE;1 Pedro;1P;NT','2PE;2 Pedro;2P;NT',
 '1JO;1 Juan;1Jn;NT','2JO;2 Juan;2Jn;NT','3JO;3 Juan;3Jn;NT','JUD;Judas;Jds;NT','REV;Apocalipsis;Ap;NT'
)

function Esc([string]$s) {
    $t = $s -replace '\\', '\\\\'
    $t = $t -replace '"', '\"'
    $t = $t -replace '[\u0000-\u001f]', ' '
    $t = $t -replace '\s+', ' '
    return $t.Trim()
}

Write-Output "Leyendo $src ..."
$lines = [System.IO.File]::ReadAllLines($src, [System.Text.Encoding]::UTF8)
Write-Output "  $($lines.Count) lineas"

# code -> chapter -> verseNumber -> text
$data = @{}
foreach ($l in $lines) {
    if ($l -match '^([A-Z0-9]{3})\s+(\d+):(\d+)\s+(.*)$') {
        $code = $Matches[1]; $ch = [int]$Matches[2]; $v = [int]$Matches[3]; $txt = $Matches[4]
        if (-not $data.ContainsKey($code)) { $data[$code] = @{} }
        if (-not $data[$code].ContainsKey($ch)) { $data[$code][$ch] = @{} }
        $data[$code][$ch][$v] = $txt
    }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$indexParts = New-Object System.Collections.ArrayList
$totalVerses = 0; $totalChapters = 0
$gaps = New-Object System.Collections.ArrayList

$n = 0
foreach ($entry in $CANON) {
    $n++
    $parts = $entry -split ';'
    $code = $parts[0]; $name = $parts[1]; $abbr = $parts[2]; $test = $parts[3]

    if (-not $data.ContainsKey($code)) { throw "FALTA el libro $code ($name) en la fuente" }

    $chapters = $data[$code]
    $chapterNums = $chapters.Keys | Sort-Object

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('{"n":').Append($n)
    [void]$sb.Append(',"name":"').Append((Esc $name)).Append('"')
    [void]$sb.Append(',"abbr":"').Append((Esc $abbr)).Append('"')
    [void]$sb.Append(',"chapters":[')

    $bookVerses = 0; $ci = 0
    foreach ($ch in $chapterNums) {
        if ($ci -gt 0) { [void]$sb.Append(',') }
        [void]$sb.Append('[')
        $verses = $chapters[$ch]
        $maxV = ($verses.Keys | Measure-Object -Maximum).Maximum
        for ($v = 1; $v -le $maxV; $v++) {
            if ($v -gt 1) { [void]$sb.Append(',') }
            if ($verses.ContainsKey($v)) {
                [void]$sb.Append('"').Append((Esc $verses[$v])).Append('"')
            } else {
                # Hueco de numeracion: se deja vacio para que el indice del array
                # siga coincidiendo con el numero de versiculo de las citas.
                [void]$gaps.Add("$name $ch`:$v")
                [void]$sb.Append('""')
            }
        }
        [void]$sb.Append(']')
        $bookVerses += $maxV
        $ci++
    }
    [void]$sb.Append(']}')

    [System.IO.File]::WriteAllText((Join-Path $outDir "$n.json"), $sb.ToString(), $utf8NoBom)

    $totalVerses += $bookVerses; $totalChapters += $ci
    [void]$indexParts.Add('{"n":' + $n + ',"name":"' + (Esc $name) + '","abbr":"' + (Esc $abbr) + '","t":"' + $test + '","chapters":' + $ci + '}')
    Write-Output ("[{0,2}/73] {1,-26} {2,-4} caps={3,-4} vers={4}" -f $n, $name, $code, $ci, $bookVerses)
}

$idx = '{"translation":"Santa Biblia Libre","abbreviation":"SBL","language":"es","books":[' + ($indexParts -join ',') + ']}'
[System.IO.File]::WriteAllText((Join-Path $outDir 'index.json'), $idx, $utf8NoBom)

Write-Output ''
Write-Output "LIBROS    : 73"
Write-Output "CAPITULOS : $totalChapters"
Write-Output "VERSICULOS: $totalVerses"
Write-Output "HUECOS DE NUMERACION: $($gaps.Count)"
$gaps | Select-Object -First 15 | ForEach-Object { "   hueco: $_" }
$size = (Get-ChildItem $outDir | Measure-Object -Property Length -Sum).Sum
Write-Output "TAMANO    : $([math]::Round($size/1MB,2)) MB"
Write-Output 'BIBLIA CATOLICA LISTA'
