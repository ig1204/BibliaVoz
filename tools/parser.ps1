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
Add-Book 48 @('mk','mark','mrk')
Add-Book 49 @('lk','luke')
Add-Book 50 @('jn','john')
Add-Book 51 @('acts','ac','acts of the apostles')
Add-Book 52 @('rom','rm','romans')
Add-Book 53 @('1 cor','1 co','1 corinthians','1cor')
Add-Book 54 @('2 cor','2 co','2 corinthians','2cor')
Add-Book 55 @('gal','ga','galatians')
Add-Book 56 @('eph','ephesians')
Add-Book 57 @('phil','php','philippians')
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

function Parse-Citation([string]$raw, [int]$defaultBook = 0) {
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }

    $s = $raw.Trim()
    # normalizar guiones largos y espacios raros
    $s = $s -replace '[‐-―−]', '-'
    $s = $s -replace ' ', ' '
    $s = $s -replace '\s+', ' '
    # quitar "Cf.", "See", "cf"
    $s = $s -replace '^(?i)(cf\.?|see)\s+', ''
    # si ofrece alternativas, la primera
    $s = ($s -split '(?i)\s+or\s+')[0].Trim()
    # quitar parentesis sueltos
    $s = $s -replace '[\(\)\[\]]', ''
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

    $ranges = New-Object System.Collections.ArrayList
    $currentChapter = 0

    foreach ($tokenRaw in ($rest -split ',')) {
        $token = $tokenRaw.Trim()
        if ($token -eq '') { continue }
        # Primero se quitan los espacios y luego los sufijos de versiculo
        # parcial (7a, 3CD, "12 cd"): al reves, "12 cd-20" no se limpiaria.
        $token = $token -replace '\s', ''
        $token = $token -replace '(?<=\d)[A-Za-z]+', ''
        if ($token -eq '' -or $token -notmatch '\d') { continue }

        if ($token -match '^(\d+):(\d+)-(\d+):(\d+)$') {
            [void]$ranges.Add(@([int]$Matches[1], [int]$Matches[2], [int]$Matches[3], [int]$Matches[4]))
            $currentChapter = [int]$Matches[3]
        } elseif ($token -match '^(\d+):(\d+)-(\d+)$') {
            $currentChapter = [int]$Matches[1]
            [void]$ranges.Add(@($currentChapter, [int]$Matches[2], $currentChapter, [int]$Matches[3]))
        } elseif ($token -match '^(\d+):(\d+)$') {
            $currentChapter = [int]$Matches[1]
            [void]$ranges.Add(@($currentChapter, [int]$Matches[2], $currentChapter, [int]$Matches[2]))
        } elseif ($token -match '^(\d+)-(\d+)$') {
            if ($currentChapter -gt 0) {
                [void]$ranges.Add(@($currentChapter, [int]$Matches[1], $currentChapter, [int]$Matches[2]))
            } else {
                # capitulos enteros, p. ej. "Jon 3-4"
                [void]$ranges.Add(@([int]$Matches[1], 1, [int]$Matches[2], 999))
                $currentChapter = [int]$Matches[2]
            }
        } elseif ($token -match '^(\d+)$') {
            if ($currentChapter -gt 0) {
                [void]$ranges.Add(@($currentChapter, [int]$Matches[1], $currentChapter, [int]$Matches[1]))
            } else {
                $currentChapter = [int]$Matches[1]
                [void]$ranges.Add(@($currentChapter, 1, $currentChapter, 999))
            }
        }
    }

    if ($ranges.Count -eq 0) { return $null }

    $first = $ranges[0]; $last = $ranges[$ranges.Count - 1]
    $name = $SPANISH[$book]
    $label = if ($first[0] -eq $last[2]) {
        if ($first[1] -eq $last[3]) { "$name $($first[0]), $($first[1])" }
        else { "$name $($first[0]), $($first[1])-$($last[3])" }
    } else {
        "$name $($first[0]), $($first[1]) - $($last[2]), $($last[3])"
    }

    return @{ book = $book; ranges = $ranges; label = $label }
}
