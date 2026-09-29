# Tabla de libros y parser de citas. Se carga con dot-sourcing desde el script principal.

$BOOK_ALIASES = @{}
function Add-Book([int]$n, [string[]]$aliases) {
    foreach ($a in $aliases) { $BOOK_ALIASES[$a] = $n }
}

Add-Book 1  @('gn','gen','genesis')
Add-Book 2  @('ex','exo','exod','exodus')
Add-Book 3  @('lv','lev','leviticus')
Add-Book 4  @('nm','num','numb','numbers')
Add-Book 5  @('dt','deut','deuteronomy')
Add-Book 6  @('jos','josh','joshua')
Add-Book 7  @('jgs','jdg','judg','judges')
Add-Book 8  @('ru','rt','ruth')
Add-Book 9  @('1 sm','1 sam','1 samuel','1sm','1sam')
Add-Book 10 @('2 sm','2 sam','2 samuel','2sm','2sam')
Add-Book 11 @('1 kgs','1 kg','1 kings','1kgs','1 kin')
Add-Book 12 @('2 kgs','2 kg','2 kings','2kgs','2 kin')
Add-Book 13 @('1 chr','1 chron','1 chronicles','1chr')
Add-Book 14 @('2 chr','2 chron','2 chronicles','2chr')
Add-Book 15 @('ezr','ezra')
Add-Book 16 @('neh','nehemiah')
Add-Book 17 @('tb','tob','tobit')
Add-Book 18 @('jdt','judith')
Add-Book 19 @('est','esth','esther')
Add-Book 20 @('1 mc','1 mac','1 macc','1 maccabees','1mc')
Add-Book 21 @('2 mc','2 mac','2 macc','2 maccabees','2mc')
Add-Book 22 @('jb','job')
Add-Book 23 @('ps','pss','psalm','psalms')
Add-Book 24 @('prv','prov','pr','proverbs')
Add-Book 25 @('eccl','eccles','qoh','ecclesiastes')
Add-Book 26 @('sg','sgs','song','song of songs','song of solomon','cant','canticle of canticles','sos')
Add-Book 27 @('ws','wis','wisd','wisdom')
Add-Book 28 @('sir','sirach','ecclus','ecclesiasticus','sirarch')  # 'sirarch': errata de la fuente
Add-Book 29 @('is','isa','isaiah')
Add-Book 30 @('jer','jr','jeremiah')
Add-Book 31 @('lam','lm','lamentations')
Add-Book 32 @('bar','ba','baruch')
Add-Book 33 @('ez','ezek','eze','ezekiel')
Add-Book 34 @('dn','dan','daniel')
Add-Book 35 @('hos','ho','hosea')
Add-Book 36 @('jl','joel')
Add-Book 37 @('am','amos')
Add-Book 38 @('ob','obad','obadiah')
Add-Book 39 @('jon','jonah')
Add-Book 40 @('mi','mic','micah')
Add-Book 41 @('na','nah','nahum')
Add-Book 42 @('hb','hab','habakkuk')
Add-Book 43 @('zep','zeph','zephaniah')
Add-Book 44 @('hg','hag','haggai')
Add-Book 45 @('zec','zech','zechariah')
Add-Book 46 @('mal','malachi')
Add-Book 47 @('mt','matt','matthew')
Add-Book 48 @('mk','mark','mrk','m')           # 'm': errata de la fuente (6-1-2023)
Add-Book 49 @('lk','luke')
Add-Book 50 @('jn','john')
Add-Book 51 @('acts','ac','acts of the apostles')
Add-Book 52 @('rom','rm','romans')
Add-Book 53 @('1 cor','1 co','1 corinthians','1cor')
Add-Book 54 @('2 cor','2 co','2 corinthians','2cor')
Add-Book 55 @('gal','ga','galatians')
Add-Book 56 @('eph','ephesians')
Add-Book 57 @('phil','php','philippians','phiippians')  # 'phiippians': errata de la fuente
Add-Book 58 @('col','colossians')
Add-Book 59 @('1 thes','1 thess','1 th','1 thessalonians','1thes')
Add-Book 60 @('2 thes','2 thess','2 th','2 thessalonians','2thes')
Add-Book 61 @('1 tm','1 tim','1 timothy','1tm')
Add-Book 62 @('2 tm','2 tim','2 timothy','2tm')
Add-Book 63 @('ti','tit','titus')
Add-Book 64 @('phlm','phm','philemon')
Add-Book 65 @('heb','hebrews')
Add-Book 66 @('jas','jam','james')
Add-Book 67 @('1 pt','1 pet','1 peter','1pt')
Add-Book 68 @('2 pt','2 pet','2 peter','2pt')
Add-Book 69 @('1 jn','1 john','1jn')
Add-Book 70 @('2 jn','2 john','2jn')
Add-Book 71 @('3 jn','3 john','3jn')
Add-Book 72 @('jude','jd')
Add-Book 73 @('rv','rev','revelation','apoc','apocalypse')

$SPANISH = @('','Génesis','Éxodo','Levítico','Números','Deuteronomio','Josué','Jueces','Rut',
 '1 Samuel','2 Samuel','1 Reyes','2 Reyes','1 Crónicas','2 Crónicas','Esdras','Nehemías','Tobías',
 'Judit','Ester','1 Macabeos','2 Macabeos','Job','Salmo','Proverbios','Eclesiastés',
 'Cantar de los Cantares','Sabiduría','Eclesiástico','Isaías','Jeremías','Lamentaciones','Baruc',
 'Ezequiel','Daniel','Oseas','Joel','Amós','Abdías','Jonás','Miqueas','Nahúm','Habacuc','Sofonías',
 'Ageo','Zacarías','Malaquías','Mateo','Marcos','Lucas','Juan','Hechos','Romanos','1 Corintios',
 '2 Corintios','Gálatas','Efesios','Filipenses','Colosenses','1 Tesalonicenses','2 Tesalonicenses',
 '1 Timoteo','2 Timoteo','Tito','Filemón','Hebreos','Santiago','1 Pedro','2 Pedro','1 Juan','2 Juan',
 '3 Juan','Judas','Apocalipsis')

# Libros de un solo capitulo: sus citas no traen capitulo ("Phlm 7-20", "Jude 17, 20b-25").
$UN_CAPITULO = @(38, 64, 70, 71, 72)

# Un tramo es @(capitulo, versiculo, capitulo, versiculo); 999 = "hasta el final
# del capitulo", solo para capitulos enteros citados como tales.
$HASTA_EL_FINAL = 999

function Parse-Citation([string]$raw, [int]$defaultBook = 0) {
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }

    $s = $raw.Trim()
    # normalizar guiones largos (tambien el doble guion "32--4:4") y espacios raros
    $s = $s -replace '[‐-―−]', '-'
    $s = $s -replace '-{2,}', '-'
    $s = $s -replace ' ', ' '
    $s = $s -replace '\s+', ' '
    # quitar "Cf.", "See", "cf"
    $s = $s -replace '^(?i)(cf\.?|see)\s+', ''
    # si ofrece alternativas, la primera
    $s = ($s -split '(?i)\s+or\s+')[0].Trim()
    # quitar parentesis sueltos
    $s = $s -replace '[\(\)\[\]]', ''
    # "27 and 29", "3 & 9", "6-7 + 9": la union separa tramos igual que la coma.
    # Tiene que ir ANTES de quitar los espacios: si no, "27 and 29" acaba en "2729".
    $s = $s -replace '(?i)\s*(?:\band\b|&|\+)\s*', ', '
    # el punto y coma separa tramos igual que la coma: "Zep 2:3; 3:12-13"
    $s = $s -replace ';', ','

    $book = 0
    $rest = $s

    # "1 Jn 2:22-28" / "PS 98:1" / "Song of Songs 2:8-14" / "98:1, 2-3"
    if ($s -match '^((?:[1-4]\s*)?[A-Za-z][A-Za-z\s\.]*?)\s*(\d.*)$') {
        $bookRaw = $Matches[1].Trim()
        $rest = $Matches[2].Trim()
        $key = ($bookRaw -replace '\.', '' -replace '\s+', ' ').Trim().ToLower()
        if ($BOOK_ALIASES.ContainsKey($key)) {
            $book = $BOOK_ALIASES[$key]
        } else {
            $compact = $key -replace '\s', ''
            if ($BOOK_ALIASES.ContainsKey($compact)) { $book = $BOOK_ALIASES[$compact] }
        }
    }

    if ($book -eq 0) {
        # cita sin libro: solo vale si sabemos el libro por el contexto (salmo)
        if ($defaultBook -gt 0 -and $s -match '^\d') { $book = $defaultBook; $rest = $s }
        else { return $null }
    }

    $segs = New-Object System.Collections.ArrayList
    $unknown = New-Object System.Collections.ArrayList
    $currentChapter = if ($UN_CAPITULO -contains $book) { 1 } else { 0 }

    foreach ($tokenRaw in ($rest -split ',')) {
        # Primero se quitan los espacios y luego los sufijos de versiculo
        # parcial (7a, 3CD, "12 cd"): al reves, "12 cd-20" no se limpiaria.
        $token = $tokenRaw.Trim() -replace '\s', ''
        if ($token -eq '') { continue }
        # "3b4" (errata por "3b-4"): una letra ENTRE dos cifras separa, no se pega.
        $token = $token -replace '(?<=\d)[A-Za-z]+(?=\d)', '-'
        # El sufijo del primer versiculo se guarda: hace falta en los pocos
        # versiculos que la otra numeracion parte entre dos capitulos (Is 63,19b).
        $sfx = ''
        if ($token -match '^(?:\d+:)?\d+([A-Za-z]+)') { $sfx = $Matches[1].ToLower() }
        $token = $token -replace '(?<=\d)[A-Za-z]+', ''
        if ($token -eq '' -or $token -notmatch '\d') { continue }

        if ($token -match '^(\d+):(\d+)-(\d+):(\d+)(?:-(\d+))?$') {
            # "1:5-2:2"; "1:1-2:1-2" (Jonas: del 1,1 al 2,2)
            $fin = if ($Matches[5]) { [int]$Matches[5] } else { [int]$Matches[4] }
            [void]$segs.Add(@([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], $fin, $sfx))
            $currentChapter = [int]$Matches[3]
        } elseif ($token -match '^(\d+):(\d+)((?:-\d+)+)$') {
            # "85:9-10" y la errata "85:9-10-11-12": del primero al ultimo
            $currentChapter = [int]$Matches[1]
            $ultimo = [int](($Matches[3] -split '-')[-1])
            [void]$segs.Add(@($currentChapter, [int]$Matches[2], $currentChapter, $ultimo, $sfx))
        } elseif ($token -match '^(\d+):(\d+)$') {
            $currentChapter = [int]$Matches[1]
            [void]$segs.Add(@($currentChapter, [int]$Matches[2], $currentChapter, [int]$Matches[2], $sfx))
        } elseif ($token -match '^(\d+)-(\d+):(\d+)$' -and $currentChapter -gt 0) {
            # "11-21:2" tras una coma: del versiculo 11 del capitulo en curso al 21,2
            [void]$segs.Add(@($currentChapter, [int]$Matches[1], [int]$Matches[2], [int]$Matches[3], $sfx))
            $currentChapter = [int]$Matches[2]
        } elseif ($token -match '^(\d+)-(\d+)$') {
            if ($currentChapter -gt 0) {
                [void]$segs.Add(@($currentChapter, [int]$Matches[1], $currentChapter, [int]$Matches[2], $sfx))
            } else {
                # capitulos enteros, p. ej. "Jon 3-4"
                [void]$segs.Add(@([int]$Matches[1], 1, [int]$Matches[2], $HASTA_EL_FINAL, ''))
                $currentChapter = [int]$Matches[2]
            }
        } elseif ($token -match '^(\d+)$') {
            if ($currentChapter -gt 0) {
                [void]$segs.Add(@($currentChapter, [int]$Matches[1], $currentChapter, [int]$Matches[1], $sfx))
            } else {
                $currentChapter = [int]$Matches[1]
                [void]$segs.Add(@($currentChapter, 1, $currentChapter, $HASTA_EL_FINAL, ''))
            }
        } else {
            [void]$unknown.Add($tokenRaw.Trim())
        }
    }

    if ($segs.Count -eq 0) { return $null }

    return @{ book = $book; segs = $segs; unknown = $unknown; label = (Format-Label $book $segs) }
}

# Quita de cada tramo los versiculos que ya salieron en uno anterior ("2-3a, 3b-4"
# repetiria el 3) y descarta lo que quede vacio. El ORDEN de la cita se respeta:
# los salmos a veces vuelven atras ("1-2, 24, 35, 27-28") y asi se proclaman.
# No se juntan tramos contiguos: cambiaria la clave del audio sin cambiar el texto.
function Remove-Overlaps($tramos) {
    $out = New-Object System.Collections.ArrayList
    foreach ($t in $tramos) {
        $piezas = New-Object System.Collections.ArrayList
        [void]$piezas.Add(@($t[0], $t[1], $t[2], $t[3]))
        foreach ($p in $out) {
            $siguientes = New-Object System.Collections.ArrayList
            foreach ($q in $piezas) {
                # Posicion = capitulo * 1000 + versiculo: compara tambien tramos que
                # cruzan de capitulo.
                $qa = $q[0] * 1000 + $q[1]; $qb = $q[2] * 1000 + $q[3]
                $pa = $p[0] * 1000 + $p[1]; $pb = $p[2] * 1000 + $p[3]
                if ($qb -lt $pa -or $qa -gt $pb) { [void]$siguientes.Add($q); continue }
                if ($qa -lt $pa) {
                    # lo de antes del tramo ya visto
                    if ($p[1] -gt 1) { [void]$siguientes.Add(@($q[0], $q[1], $p[0], ($p[1] - 1))) }
                    else { [void]$siguientes.Add(@($q[0], $q[1], ($p[0] - 1), $HASTA_EL_FINAL)) }
                }
                # lo de despues (si pasa del final del capitulo, Tramos.versos lo recorta)
                if ($qb -gt $pb) { [void]$siguientes.Add(@($p[2], ($p[3] + 1), $q[2], $q[3])) }
            }
            $piezas = $siguientes
        }
        foreach ($q in $piezas) { [void]$out.Add($q) }
    }
    return ,$out.ToArray()
}

# Etiqueta en espanol hecha con la cita ORIGINAL (numeracion del leccionario):
# "Isaías 63, 16-17. 19; 64, 2-7", "Salmo 89, 2-5. 27. 29", "Filemón 7-20".
function Format-Label([int]$book, $segs) {
    $tramos = Remove-Overlaps (@($segs | ForEach-Object { , @($_[0], $_[1], $_[2], $_[3]) }))
    # Para leer la cita se juntan los tramos contiguos: "2-3. 4-5" -> "2-5".
    $juntos = New-Object System.Collections.ArrayList
    foreach ($t in $tramos) {
        if ($juntos.Count -gt 0) {
            $u = $juntos[$juntos.Count - 1]
            if ($u[2] -eq $t[0] -and $t[0] -eq $t[2] -and $u[3] -ne $HASTA_EL_FINAL -and $t[1] -eq $u[3] + 1) {
                $juntos[$juntos.Count - 1] = @($u[0], $u[1], $u[2], $t[3]); continue
            }
        }
        [void]$juntos.Add(@($t[0], $t[1], $t[2], $t[3]))
    }

    $sinCapitulo = ($UN_CAPITULO -contains $book) -and -not ($juntos | Where-Object { $_[0] -ne 1 -or $_[2] -ne 1 })
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append($SPANISH[$book])
    $cur = -1
    foreach ($t in $juntos) {
        $c1 = $t[0]; $v1 = $t[1]; $c2 = $t[2]; $v2 = $t[3]
        if ($v2 -eq $HASTA_EL_FINAL) {
            $sep = if ($cur -lt 0) { ' ' } else { '; ' }
            $cap = if ($c1 -eq $c2) { "$c1" } else { "$c1-$c2" }
            [void]$sb.Append($sep).Append($cap)
            $cur = -1
            continue
        }
        if ($sinCapitulo) {
            $sep = if ($cur -lt 0) { ' ' } else { '. ' }
        } elseif ($cur -eq $c1) {
            $sep = '. '
        } else {
            $sep = if ($cur -lt 0) { ' ' } else { '; ' }
            $sep += "$c1, "
        }
        $versos = if ($c1 -ne $c2) { "$v1 - $c2, $v2" } elseif ($v1 -eq $v2) { "$v1" } else { "$v1-$v2" }
        [void]$sb.Append($sep).Append($versos)
        $cur = $c2
    }
    return $sb.ToString()
}
