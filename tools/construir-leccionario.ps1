param(
    # Por defecto escribe el JSON en los assets de la app; se puede desviar
    # a otra carpeta para probar sin tocar el proyecto.
    [string]$Salida = '',
    [string]$Proyecto = ''
)
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
#
# Lo que la fuente no trae (dias con varias misas y sin citas, domingos que un
# ano tapo una fiesta, ferias que no cayeron entre 2023 y 2027) se completa con
# la tabla $A_MANO, y el script FALLA si al final queda algun dia sin lecturas
# entre $CUBRIR_DESDE y $CUBRIR_HASTA: ningun hueco llega a la app sin avisar.
#
# Las fiestas de fecha fija llevan su nombre y su grado (fijasInfo): con ellos
# la app decide en Precedencia.kt si la fiesta le gana al dia. Cada lectura
# nueva o cambiada tiene otra clave de audio: despues de regenerar esta tabla
# hay que pasar el generador de voz IA para grabarlas.

. (Join-Path $PSScriptRoot 'parser.ps1')

$proyecto = if ($Proyecto) { $Proyecto } else { Split-Path $PSScriptRoot -Parent }
$srcReadings = 'E:\PG\.liturgia\readings_usccb.json'
$outDir = if ($Salida) { $Salida } else { Join-Path $proyecto 'app\src\main\assets\liturgia' }
$bibliaDir = Join-Path $proyecto 'app\src\main\assets\bible-cat'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$CUBRIR_DESDE = [datetime]::new(2026, 9, 29)
$CUBRIR_HASTA = [datetime]::new(2032, 12, 31)

# ----------------------------------------------------------------- calendario
# Tiene que dar las MISMAS claves que CalendarioLiturgico.kt: si se toca uno,
# hay que tocar el otro.

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

# Bautismo del Senor segun el calendario de EE.UU. (el del conjunto de datos,
# que en esto coincide con el de Mexico): Epifania se traslada al domingo entre
# el 2 y el 8 de enero, y el Bautismo es el domingo siguiente, o el lunes si
# Epifania cae el 7 u 8.
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

function Get-TemporalKey([datetime]$d, [switch]$Fuente) {
    $y = $d.Year
    $dow = [int]$d.DayOfWeek
    $litYear = Get-LiturgicalYear $d
    $sundayCycle = Get-SundayCycle $litYear
    $suffix = if ($dow -eq 0) { '-' + $sundayCycle } else { '-' + (Get-WeekdayCycle $litYear) }

    $easter = Get-Easter $y
    $ashWed = $easter.AddDays(-46)
    $lent1 = $easter.AddDays(-42)
    $palm = $easter.AddDays(-7)
    $holyThu = $easter.AddDays(-3)
    $pentecost = $easter.AddDays(49)
    $advent1 = Get-Advent1 $y
    $baptism = Get-Baptism $y
    $christmas = [datetime]::new($y, 12, 25)

    # Del 17 al 24 de DICIEMBRE las ferias de Adviento tienen lecturas propias
    # POR FECHA, no por dia de la semana (nº oficial 193 = 17 dic, 194 = 18 dic...).
    # Un dia de Adviento de finales de noviembre es una feria normal.
    if ($d -ge $advent1 -and $d -lt $christmas) {
        if ($dow -ne 0 -and $d.Month -eq 12 -and $d.Day -ge 17) { return ('ADVDIC-{0:00}' -f $d.Day) }
        $w = [math]::Floor(($d - $advent1).TotalDays / 7) + 1
        return "ADV-$w-$dow$suffix"
    }

    # El 1 de enero es solemnidad fija (Santa Maria, Madre de Dios).
    if ($d.Month -eq 1 -and $d.Day -eq 1) { return 'ENE01' }

    # Tiempo de Navidad: el 25 tiene su misa aunque caiga en domingo; la Sagrada
    # Familia es el domingo de la octava o, si no lo hay (Navidad en domingo), el
    # 30 de diciembre, y lleva siempre el ciclo dominical. Las ferias del 26 al
    # 31 van por fecha.
    if ($d -ge $christmas) {
        if ($d -eq $christmas) { return 'NAV-12-25' }
        if ($dow -eq 0 -or ([int]$christmas.DayOfWeek -eq 0 -and $d.Day -eq 30)) { return "SAGFAM-$sundayCycle" }
        return ('NAV-{0:00}-{1:00}' -f $d.Month, $d.Day)
    }

    if ($d -lt $baptism) {
        $epiphany = Get-Epiphany $y
        if ($d -eq $epiphany) { return 'EPIFANIA' }
        if ($dow -eq 0) { return "SAGFAM-$sundayCycle" }
        # Antes de Epifania las lecturas van por fecha; despues, por dia de la
        # semana (lunes = 212, martes = 213, ... sabado = 217).
        if ($d -lt $epiphany) { return ('ANTEPIF-{0:00}' -f $d.Day) }
        return "TRASEPIF-$dow"
    }
    # El Bautismo tiene evangelio de cada ciclo A/B/C aunque caiga en lunes.
    if ($d -eq $baptism) { return "BAUTISMO-$sundayCycle" }

    if ($d -ge $holyThu -and $d -lt $easter) {
        $names = @('JUE', 'VIE', 'SAB')
        $k = "TRI-$($names[[int]($d - $holyThu).TotalDays])"
        # La Vigilia pascual tambien tiene evangelio propio de cada ciclo.
        if ($k -eq 'TRI-SAB') { $k += "-$sundayCycle" }
        return $k
    }
    if ($d -ge $palm -and $d -lt $holyThu) { return "SANTA-$dow$suffix" }

    if ($d -ge $ashWed -and $d -lt $lent1) { return "CENIZA-$dow" }
    if ($d -ge $lent1 -and $d -lt $palm) {
        $w = [math]::Floor(($d - $lent1).TotalDays / 7) + 1
        return "CUA-$w-$dow$suffix"
    }

    # PAS-7-0 es la Ascension, que en Mexico (y en casi todo EE. UU.) se celebra
    # el VII domingo de Pascua; PAS-8-0 es Pentecostes.
    if ($d -ge $easter -and $d -le $pentecost) {
        $w = [math]::Floor(($d - $easter).TotalDays / 7) + 1
        return "PAS-$w-$dow$suffix"
    }

    # Solemnidades moviles atadas a la Pascua, que caen ya en Tiempo Ordinario
    # y desplazan a la feria que tocaria ese dia.
    $trinity = $easter.AddDays(56)          # domingo siguiente a Pentecostes
    # En Mexico el Corpus es el JUEVES despues de la Trinidad (pascua+60), y el
    # domingo siguiente vuelve a ser domingo ordinario. La fuente de EE. UU. lo
    # trae el domingo (pascua+63): al CLASIFICAR la fuente se usa su fecha
    # (-Fuente) para que sus lecturas caigan en CORPUS-A/B/C; para el calendario
    # de la app (sin -Fuente) se usa el jueves mexicano.
    $corpus = if ($Fuente) { $easter.AddDays(63) } else { $easter.AddDays(60) }
    $sacredHeart = $easter.AddDays(68)      # viernes siguiente
    if ($d -eq $trinity) { return "TRINIDAD$suffix" }
    if ($d -eq $corpus) { return "CORPUS-$sundayCycle" }
    # El Sagrado Corazon tiene ciclo propio A/B/C aunque caiga en viernes
    # (nº oficial 170 = A, 171 = B, 172 = C), no el ciclo ferial I/II.
    if ($d -eq $sacredHeart) { return 'SAGCORAZON-' + $sundayCycle }
    # El Corazon Inmaculado es memoria opcional: se deja la feria que toque.

    if ($d -gt $baptism -and $d -lt $ashWed) {
        $sunday = $d.AddDays(-$dow)
        # Redondeo, no division entera: si el Bautismo cae en lunes, el domingo
        # siguiente (6 dias despues) ya es el 2º del Tiempo Ordinario.
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

# Lugar del dia en la tabla de precedencia (1 = Triduo ... 13 = feria comun),
# segun su clave. Copia de Precedencia.rangoDelTiempo (liturgia/Precedencia.kt).
function Get-TemporalRank([string]$k, [datetime]$d) {
    $domingo = ([int]$d.DayOfWeek -eq 0)
    if ($k -like 'TRI-*') { return 1 }
    if ($k -eq 'NAV-12-25' -or $k -eq 'EPIFANIA' -or $k -eq 'CENIZA-3') { return 2 }
    if ($k -like 'SANTA-*' -or $k -like 'PAS-1-*') { return 2 }
    if ($domingo -and ($k -like 'ADV-*' -or $k -like 'CUA-*' -or $k -like 'PAS-*')) { return 2 }
    if ($k -eq 'ENE01' -or $k -like 'TRINIDAD*' -or $k -like 'CORPUS*' -or $k -like 'SAGCORAZON*' -or $k -like 'ORD-34-0-*') { return 3 }
    if ($k -like 'BAUTISMO*' -or $k -like 'SAGFAM*') { return 5 }
    if ($domingo) { return 6 }
    if ($k -like 'ADVDIC-*' -or $k -like 'NAV-*' -or $k -like 'CUA-*' -or $k -like 'CENIZA-*') { return 9 }
    return 13
}
$RANGO_GRADO = @{ S = 3; FS = 5; F = 7; M = 10 }

# ------------------------------------------------------ fiestas de fecha fija
# Solo las que lo son de verdad (calendario romano general y el de Mexico): la
# fuente trae ademas traslados de un ano concreto (San Jose el 20 de marzo, la
# Anunciacion en abril, la Inmaculada el 9 de diciembre), memorias que dependen
# de Pentecostes (Maria Madre de la Iglesia, el Corazon Inmaculado) y las ferias
# de la semana 34 con numero de leccionario 507-508. Guardadas por fecha, se
# repetirian cada ano el mismo dia, caiga en domingo o en Adviento.
#   n     nombre para la cabecera de la pantalla
#   g     grado: S solemnidad, FS fiesta del Senor, F fiesta, M memoria
#   lect  numeros oficiales con que la fuente trae esa misa (a veces con errata)
#   firma evangelio propio con que se reconoce la memoria cuando la fuente la
#         trae con el numero de la feria (y en 2026, sin nombre)
#   propias  (memorias) lecturas propias; el resto se toma de la feria del dia
#   porCiclo las lecturas cambian con el ciclo dominical A/B/C
$FIJAS = [ordered]@{
    '01-25' = @{ n = 'La Conversión de San Pablo, apóstol'; g = 'F'; lect = @(519) }
    '01-26' = @{ n = 'Santos Timoteo y Tito, obispos'; g = 'M'; lect = @(520); propias = @('Primera lectura', 'Salmo responsorial') }
    '02-02' = @{ n = 'La Presentación del Señor'; g = 'FS'; lect = @(524) }
    # Fiesta propia de México; lecturas a mano (la fuente de EE. UU. no la trae).
    '02-05' = @{ n = 'San Felipe de Jesús, protomártir de México'; g = 'F'; lect = @() }
    '02-22' = @{ n = 'La Cátedra de San Pedro, apóstol'; g = 'F'; lect = @(535) }
    '03-19' = @{ n = 'San José, esposo de la Virgen María'; g = 'S'; lect = @(543) }
    '03-25' = @{ n = 'La Anunciación del Señor'; g = 'S'; lect = @(545) }
    '04-25' = @{ n = 'San Marcos, evangelista'; g = 'F'; lect = @(555, 573) }
    # México: el 3 de mayo es la Santa Cruz y el 4, Felipe y Santiago (en EE. UU.
    # los dos apóstoles van el 3). Sus lecturas se ponen a mano ($A_MANO): con
    # lect = @() se descartan los registros nº 561/638 de la fuente (son ≥ 509).
    '05-03' = @{ n = 'La Santa Cruz'; g = 'F'; lect = @() }
    '05-04' = @{ n = 'Santos Felipe y Santiago, apóstoles'; g = 'F'; lect = @() }
    '05-14' = @{ n = 'San Matías, apóstol'; g = 'F'; lect = @(564) }
    '05-31' = @{ n = 'La Visitación de la Virgen María'; g = 'F'; lect = @(572) }
    '06-11' = @{ n = 'San Bernabé, apóstol'; g = 'M'; lect = @(580); propias = @('Primera lectura', 'Salmo responsorial') }
    '06-24' = @{ n = 'La Natividad de San Juan Bautista'; g = 'S'; lect = @(586, 587) }
    '06-29' = @{ n = 'Santos Pedro y Pablo, apóstoles'; g = 'S'; lect = @(590, 591) }
    '07-03' = @{ n = 'Santo Tomás, apóstol'; g = 'F'; lect = @(593) }
    '07-22' = @{ n = 'Santa María Magdalena'; g = 'F'; lect = @(603) }
    '07-25' = @{ n = 'Santiago, apóstol'; g = 'F'; lect = @(605) }
    # Memorias con evangelio propio: la fuente las trae con el numero de la
    # feria, y guardadas como feria meterian ese evangelio en otros anos.
    '07-29' = @{ n = 'Santos Marta, María y Lázaro'; g = 'M'; lect = @(); firma = 'Juan 11, 19-27'; propias = @('Evangelio') }
    '08-06' = @{ n = 'La Transfiguración del Señor'; g = 'FS'; lect = @(614, 410); porCiclo = $true }
    '08-10' = @{ n = 'San Lorenzo, diácono y mártir'; g = 'F'; lect = @(618) }
    '08-15' = @{ n = 'La Asunción de la Virgen María'; g = 'S'; lect = @(621, 622) }
    '08-24' = @{ n = 'San Bartolomé, apóstol'; g = 'F'; lect = @(629) }
    '08-29' = @{ n = 'El Martirio de San Juan Bautista'; g = 'M'; lect = @(); firma = 'Marcos 6, 17-29'; propias = @('Evangelio') }
    # Fiesta propia de México (patrona de América); lecturas a mano.
    '08-30' = @{ n = 'Santa Rosa de Lima, virgen'; g = 'F'; lect = @() }
    '09-08' = @{ n = 'La Natividad de la Virgen María'; g = 'F'; lect = @(636) }
    # La Exaltación de la Santa Cruz (14 sep): fiesta del Señor UNIVERSAL, distinta
    # de 'La Santa Cruz' propia de México del 3 de mayo ('05-03'). México la lee con
    # estructura ORDINARIA (solo 1ª, salmo y Evangelio, sin 2ª lectura, por decision
    # del usuario), tambien cuando cae en domingo. Lecturas a mano; lect=@() descarta
    # el registro nº 638 de la fuente.
    '09-14' = @{ n = 'La Exaltación de la Santa Cruz'; g = 'FS'; lect = @() }
    '09-15' = @{ n = 'Nuestra Señora de los Dolores'; g = 'M'; lect = @(); firma = 'Juan 19, 25-27'; propias = @('Evangelio') }
    '09-21' = @{ n = 'San Mateo, apóstol y evangelista'; g = 'F'; lect = @(643) }
    '09-29' = @{ n = 'Santos Miguel, Gabriel y Rafael, arcángeles'; g = 'F'; lect = @(647) }
    '10-02' = @{ n = 'Los Santos Ángeles Custodios'; g = 'M'; lect = @(); firma = 'Mateo 18, 1-5. 10'; propias = @('Evangelio') }
    '10-18' = @{ n = 'San Lucas, evangelista'; g = 'F'; lect = @(661) }
    '10-28' = @{ n = 'Santos Simón y Judas, apóstoles'; g = 'F'; lect = @(666) }
    '11-01' = @{ n = 'Todos los Santos'; g = 'S'; lect = @(667) }
    # La Conmemoracion de los difuntos tiene el rango de las solemnidades
    # (gana al domingo), pero no se llama asi: la cabecera no le anade el grado.
    '11-02' = @{ n = 'Conmemoración de todos los fieles difuntos'; g = 'S'; lect = @(668) }
    '11-09' = @{ n = 'La Dedicación de la Basílica de Letrán'; g = 'FS'; lect = @(671) }
    '11-30' = @{ n = 'San Andrés, apóstol'; g = 'F'; lect = @(684) }
    '12-08' = @{ n = 'La Inmaculada Concepción de la Virgen María'; g = 'S'; lect = @(689) }
    # En Mexico es solemnidad (en EE. UU., fiesta). Mexico cambia las CUATRO partes
    # respecto al leccionario de EE. UU. (Is 7; Sal 66/67; Gal 4; Lc 1,39-48), asi
    # que van a mano ($A_MANO); con lect = @() se descarta el registro nº 690.
    '12-12' = @{ n = 'Nuestra Señora de Guadalupe'; g = 'S'; lect = @() }
    '12-26' = @{ n = 'San Esteban, protomártir'; g = 'F'; lect = @(696) }
    '12-27' = @{ n = 'San Juan, apóstol y evangelista'; g = 'F'; lect = @(697) }
    '12-28' = @{ n = 'Los Santos Inocentes, mártires'; g = 'F'; lect = @(698) }
}

# Ferias que cambian una lectura segun el ciclo dominical: en el ciclo indicado,
# el domingo anterior ya proclamo el pasaje de la feria y esta lee otro. Esos
# dias se guardan aparte con la clave "CLAVE/CICLO" (p. ej. PAS-4-1-I/A), que la
# app busca antes que la clave sola (Precedencia.claveLecturasDelTiempo).
$FERIAS_POR_CICLO = @{
    'ADV-1-1'  = 'A'   # ano A: Isaias 4, 2-6 (el domingo se leyo Isaias 2, 1-5)
    'CUA-5-1'  = 'C'   # ano C: Juan 8, 12-20 (el domingo se leyo Juan 8, 1-11)
    'PAS-4-1'  = 'A'   # ano A: Juan 10, 11-18 (el domingo se leyo Juan 10, 1-10)
    'ORD-18-1' = 'A'   # ano A: Mateo 14, 22-36 (el domingo se leyo Mateo 14, 13-21)
}

# Memorias moviles (dependen de Pentecostes) que la fuente trae con numero de
# feria: sus lecturas no valen para la feria de otros anos.
$FIESTAS_DESCARTADAS = 'Immaculate Heart|Mother of the Church'

# ------------------------------------------------------ lecturas puestas a mano
# Lo que la fuente no trae. Las citas van en ingles, con la notacion de la
# fuente, y pasan por el mismo interprete, la misma conversion de versiculos y
# las mismas comprobaciones que las demas. 'lect' es el numero oficial del
# Leccionario, para poder cotejarlas. En una clave que ya existe, cada lectura
# sustituye a la de su mismo titulo (o se anade si faltaba).
#   Un elemento @{ b; c; r } da los tramos ya en la numeracion de la Biblia
#   empaquetada, para lo que el interprete no puede leer (Ester griego).
$ASCENSION = @(@('Primera lectura', 'Acts 1:1-11'), @('Salmo responsorial', 'Ps 47:2-3, 6-7, 8-9'), @('Segunda lectura', 'Eph 1:17-23'))
$PENTECOSTES = @(@('Primera lectura', 'Acts 2:1-11'), @('Salmo responsorial', 'Ps 104:1, 24, 29-30, 31, 34'), @('Segunda lectura', '1 Cor 12:3b-7, 12-13'), @('Evangelio', 'Jn 20:19-23'))
$JUEVES_PASCUA6 = @(@('Primera lectura', 'Acts 18:1-8'), @('Salmo responsorial', 'Ps 98:1, 2-3ab, 3cd-4'), @('Evangelio', 'Jn 16:16-20'))
$A_MANO = [ordered]@{
    # Navidad, misa del dia (la fuente trae las cuatro misas sin citas).
    'NAV-12-25'   = @{ lect = 16; partes = @(@('Primera lectura', 'Is 52:7-10'), @('Salmo responsorial', 'Ps 98:1, 2-3, 3-4, 5-6'), @('Segunda lectura', 'Heb 1:1-6'), @('Evangelio', 'Jn 1:1-18')) }
    # Asuncion, misa del dia (tampoco trae citas ningun ano).
    '08-15'       = @{ lect = 622; partes = @(@('Primera lectura', 'Rv 11:19a; 12:1-6a, 10ab'), @('Salmo responsorial', 'Ps 45:10, 11, 12, 16'), @('Segunda lectura', '1 Cor 15:20-27'), @('Evangelio', 'Lk 1:39-56')) }
    # Ascension (VII domingo de Pascua en Mexico): la fuente la trae sin citas
    # o como misa alternativa; el evangelio es de cada ciclo.
    'PAS-7-0-A'   = @{ lect = 58; partes = $ASCENSION + @(, @('Evangelio', 'Mt 28:16-20')) }
    'PAS-7-0-B'   = @{ lect = 58; partes = $ASCENSION + @(, @('Evangelio', 'Mk 16:15-20')) }
    'PAS-7-0-C'   = @{ lect = 58; partes = $ASCENSION + @(, @('Evangelio', 'Lk 24:46-53')) }
    # Jueves de la VI semana de Pascua: donde la Ascension pasa al domingo,
    # feria propia (nº 294); la fuente solo la trae vacia.
    'PAS-6-4-I'   = @{ lect = 294; partes = $JUEVES_PASCUA6 }
    'PAS-6-4-II'  = @{ lect = 294; partes = $JUEVES_PASCUA6 }
    # Pentecostes, misa del dia, igual los tres ciclos (asi la trae la fuente).
    'PAS-8-0-A'   = @{ lect = 63; partes = $PENTECOSTES }
    'PAS-8-0-B'   = @{ lect = 63; partes = $PENTECOSTES }
    'PAS-8-0-C'   = @{ lect = 63; partes = $PENTECOSTES }
    # Domingos del ciclo C que en 2025 tapo una fiesta o vinieron sin citas.
    'ORD-4-0-C'   = @{ lect = 72; partes = @(@('Primera lectura', 'Jer 1:4-5, 17-19'), @('Salmo responsorial', 'Ps 71:1-2, 3-4, 5-6, 15, 17'), @('Segunda lectura', '1 Cor 12:31—13:13'), @('Evangelio', 'Lk 4:21-30')) }
    'CUA-3-0-C'   = @{ lect = 30; partes = @(@('Primera lectura', 'Ex 3:1-8a, 13-15'), @('Salmo responsorial', 'Ps 103:1-2, 3-4, 6-7, 8, 11'), @('Segunda lectura', '1 Cor 10:1-6, 10-12'), @('Evangelio', 'Lk 13:1-9')) }
    'CUA-4-0-C'   = @{ lect = 33; partes = @(@('Primera lectura', 'Jos 5:9a, 10-12'), @('Salmo responsorial', 'Ps 34:2-3, 4-5, 6-7'), @('Segunda lectura', '2 Cor 5:17-21'), @('Evangelio', 'Lk 15:1-3, 11-32')) }
    'CUA-5-0-C'   = @{ lect = 36; partes = @(@('Primera lectura', 'Is 43:16-21'), @('Salmo responsorial', 'Ps 126:1-2, 2-3, 4-5, 6'), @('Segunda lectura', 'Phil 3:8-14'), @('Evangelio', 'Jn 8:1-11')) }
    'ORD-12-0-C'  = @{ lect = 96; partes = @(@('Primera lectura', 'Zec 12:10-11; 13:1'), @('Salmo responsorial', 'Ps 63:2, 3-4, 5-6, 8-9'), @('Segunda lectura', 'Gal 3:26-29'), @('Evangelio', 'Lk 9:18-24')) }
    'ORD-13-0-C'  = @{ lect = 99; partes = @(@('Primera lectura', '1 Kgs 19:16b, 19-21'), @('Salmo responsorial', 'Ps 16:1-2, 5, 7-8, 9-10, 11'), @('Segunda lectura', 'Gal 5:1, 13-18'), @('Evangelio', 'Lk 9:51-62')) }
    'ORD-24-0-C'  = @{ lect = 132; partes = @(@('Primera lectura', 'Ex 32:7-11, 13-14'), @('Salmo responsorial', 'Ps 51:3-4, 12-13, 17, 19'), @('Segunda lectura', '1 Tm 1:12-17'), @('Evangelio', 'Lk 15:1-32')) }
    'ORD-31-0-C'  = @{ lect = 153; partes = @(@('Primera lectura', 'Wis 11:22—12:2'), @('Salmo responsorial', 'Ps 145:1-2, 8-9, 10-11, 13, 14'), @('Segunda lectura', '2 Thes 1:11—2:2'), @('Evangelio', 'Lk 19:1-10')) }
    'ORD-32-0-C'  = @{ lect = 156; partes = @(@('Primera lectura', '2 Mc 7:1-2, 9-14'), @('Salmo responsorial', 'Ps 17:1, 5-6, 8, 15'), @('Segunda lectura', '2 Thes 2:16—3:5'), @('Evangelio', 'Lk 20:27-38')) }
    # Domingos de los ciclos A y B que la fuente nunca trajo (los taparon
    # Corpus Christi o la Trinidad, o no cayeron entre 2023 y 2027).
    'ORD-10-0-A'  = @{ lect = 88; partes = @(@('Primera lectura', 'Hos 6:3-6'), @('Salmo responsorial', 'Ps 50:1, 8, 12-13, 14-15'), @('Segunda lectura', 'Rom 4:18-25'), @('Evangelio', 'Mt 9:9-13')) }
    'ORD-7-0-B'   = @{ lect = 80; partes = @(@('Primera lectura', 'Is 43:18-19, 21-22, 24b-25'), @('Salmo responsorial', 'Ps 41:2-3, 4-5, 13-14'), @('Segunda lectura', '2 Cor 1:18-22'), @('Evangelio', 'Mk 2:1-12')) }
    'ORD-8-0-B'   = @{ lect = 83; partes = @(@('Primera lectura', 'Hos 2:16b, 17b, 21-22'), @('Salmo responsorial', 'Ps 103:1-2, 3-4, 8, 10, 12-13'), @('Segunda lectura', '2 Cor 3:1b-6'), @('Evangelio', 'Mk 2:18-22')) }
    # Domingos ordinarios que libera el paso del Corpus al jueves (MX-01): con el
    # Corpus en pascua+60, el domingo siguiente vuelve a ser ordinario. La fuente
    # de EE. UU. nunca los trajo (alli el Corpus seguia tapandolos). Verificados
    # con el Leccionario romano (catholic-resources.org / USCCB). Salmos en
    # numeracion hebrea (Get-SalmoLiturgico los reetiqueta a la Vulgata).
    'ORD-9-0-A'   = @{ lect = 85; partes = @(@('Primera lectura', 'Dt 11:18, 26-28, 32'), @('Salmo responsorial', 'Ps 31:2-3, 3-4, 17, 25'), @('Segunda lectura', 'Rom 3:21-25, 28'), @('Evangelio', 'Mt 7:21-27')) }
    'ORD-9-0-B'   = @{ lect = 86; partes = @(@('Primera lectura', 'Dt 5:12-15'), @('Salmo responsorial', 'Ps 81:3-4, 5-6, 7-8, 10-11'), @('Segunda lectura', '2 Cor 4:6-11'), @('Evangelio', 'Mk 2:23-3:6')) }
    'ORD-11-0-C'  = @{ lect = 93; partes = @(@('Primera lectura', '2 Sm 12:7-10, 13'), @('Salmo responsorial', 'Ps 32:1-2, 5, 7, 11'), @('Segunda lectura', 'Gal 2:16, 19-21'), @('Evangelio', 'Lk 7:36-8:3')) }
    # Segunda lectura del domingo 23 C, que la fuente omite (Filemon).
    'ORD-23-0-C'  = @{ lect = 129; partes = @(, @('Segunda lectura', 'Phlm 9-10, 12-17')) }
    # Primeras lecturas que la fuente omite: Filemon, 2 y 3 Juan, Judas y Ester.
    'ORD-32-4-II' = @{ lect = 494; partes = @(, @('Primera lectura', 'Phlm 7-20')) }
    'ORD-32-5-II' = @{ lect = 495; partes = @(, @('Primera lectura', '2 Jn 4-9')) }
    'ORD-32-6-II' = @{ lect = 496; partes = @(, @('Primera lectura', '3 Jn 5-8')) }
    'ORD-8-6-II'  = @{ lect = 352; partes = @(, @('Primera lectura', 'Jude 17, 20b-25')) }
    # Jueves I de Cuaresma: Ester C, 12. 14-16. 23-25 (el texto griego, que la
    # Biblia empaquetada pone dentro del capitulo 4: C,1 = 4,18).
    'CUA-1-4-I'   = @{ lect = 227; partes = @(, @('Primera lectura', @{ b = 19; c = 'Ester C, 12. 14-16. 23-25'; r = @(@(4, 29, 4, 29), @(4, 31, 4, 33), @(4, 40, 4, 42)) })) }
    'CUA-1-4-II'  = @{ lect = 227; partes = @(, @('Primera lectura', @{ b = 19; c = 'Ester C, 12. 14-16. 23-25'; r = @(@(4, 29, 4, 29), @(4, 31, 4, 33), @(4, 40, 4, 42)) })) }
    # Semana 34, jueves (nº 506): en EE. UU. es Accion de Gracias y la fuente
    # nunca trae sus citas.
    'ORD-34-4-I'  = @{ lect = 506; partes = @(@('Primera lectura', 'Dn 6:12-28'), @('Salmo responsorial', 'Dn 3:68, 69, 70, 71, 72, 73, 74'), @('Evangelio', 'Lk 21:20-28')) }
    'ORD-34-4-II' = @{ lect = 506; partes = @(@('Primera lectura', 'Rv 18:1-2, 21-23; 19:1-3, 9a'), @('Salmo responsorial', 'Ps 100:1b-2, 3, 4, 5'), @('Evangelio', 'Lk 21:20-28')) }
    # Ferias del ano II que no cayeron entre 2023 y 2027 (semanas 6 y 7).
    'ORD-6-3-II'  = @{ lect = 337; partes = @(@('Primera lectura', 'Jas 1:19-27'), @('Salmo responsorial', 'Ps 15:2-3a, 3bc-4ab, 5'), @('Evangelio', 'Mk 8:22-26')) }
    'ORD-6-4-II'  = @{ lect = 338; partes = @(@('Primera lectura', 'Jas 2:1-9'), @('Salmo responsorial', 'Ps 34:2-3, 4-5, 6-7'), @('Evangelio', 'Mk 8:27-33')) }
    'ORD-6-5-II'  = @{ lect = 339; partes = @(@('Primera lectura', 'Jas 2:14-24, 26'), @('Salmo responsorial', 'Ps 112:1-2, 3-4, 5-6'), @('Evangelio', 'Mk 8:34—9:1')) }
    'ORD-6-6-II'  = @{ lect = 340; partes = @(@('Primera lectura', 'Jas 3:1-10'), @('Salmo responsorial', 'Ps 12:2-3, 4-5, 7-8'), @('Evangelio', 'Mk 9:2-13')) }
    'ORD-7-1-II'  = @{ lect = 341; partes = @(@('Primera lectura', 'Jas 3:13-18'), @('Salmo responsorial', 'Ps 19:8, 9, 10, 15'), @('Evangelio', 'Mk 9:14-29')) }
    # Jueves III de Adviento (nº 190): entre 2023 y 2027 siempre cayo ya del 17
    # de diciembre en adelante, que tiene lecturas por fecha.
    'ADV-3-4-I'   = @{ lect = 190; partes = @(@('Primera lectura', 'Is 54:1-10'), @('Salmo responsorial', 'Ps 30:2 and 4, 5-6, 11-12a and 13b'), @('Evangelio', 'Lk 7:24-30')) }
    'ADV-3-4-II'  = @{ lect = 190; partes = @(@('Primera lectura', 'Is 54:1-10'), @('Salmo responsorial', 'Ps 30:2 and 4, 5-6, 11-12a and 13b'), @('Evangelio', 'Lk 7:24-30')) }
    # Lunes XVIII del ano A: en 2023 el domingo fue la Transfiguracion y la
    # fuente dejo el evangelio de los anos B y C.
    'ORD-18-1-I/A' = @{ lect = 407; partes = @(, @('Evangelio', 'Mt 14:22-36')) }
    # Lunes XXVI del ano I (nº 455): en 2023 fue la memoria de los Angeles
    # Custodios y 2027 viene vacio.
    'ORD-26-1-I'  = @{ lect = 455; partes = @(@('Primera lectura', 'Zec 8:1-8'), @('Salmo responsorial', 'Ps 102:16-18, 19-21, 29 and 22-23'), @('Evangelio', 'Lk 9:46-50')) }
    # Martes XVI del ano I (nº 396): la fuente lo trae vacio en 2027.
    'ORD-16-2-I'  = @{ lect = 396; partes = @(@('Primera lectura', 'Ex 14:21—15:1'), @('Salmo responsorial', 'Ex 15:8-9, 10 and 12, 17'), @('Evangelio', 'Mt 12:46-50')) }
    # El Eclesiastico y Tobias de la Biblia empaquetada siguen otra numeracion (la
    # griega; Tobias en su recension corta) y en estos pasajes no hay una regla
    # por capitulo: los tramos van ya en su numeracion, cotejados con el texto
    # (verificacion independiente del 2026-09-29).
    # Miercoles VIII del ano I: Eclo 36, 1. 4-5a. 10-17 = 36, 1-2. 5-6. 11-17.
    'ORD-8-3-I'   = @{ lect = 349; partes = @(, @('Primera lectura', @{ b = 28; c = 'Eclesiástico 36, 1. 4-5. 10-17'; r = @(@(36, 1, 36, 2), @(36, 5, 36, 6), @(36, 11, 36, 17)) })) }
    # Jueves IX del ano I: Tob 6, 10-11; 7, 1. 9-17; 8, 4-9a = 6, 9-10; 7, 1; 7, 8-18; 8, 4-8.
    'ORD-9-4-I'   = @{ lect = 356; partes = @(, @('Primera lectura', @{ b = 17; c = 'Tobías 6, 10-11; 7, 1. 9-17; 8, 4-9'; r = @(@(6, 9, 6, 10), @(7, 1, 7, 1), @(7, 8, 7, 18), @(8, 4, 8, 8)) })) }
    # Domingo XXX del ciclo C: Eclo 35, 12-14. 16-18 = 35, 12-14. 16-17 (el 18
    # de esta Biblia sigue con versos que el leccionario no lee).
    'ORD-30-0-C'  = @{ lect = 150; partes = @(, @('Primera lectura', @{ b = 28; c = 'Eclesiástico 35, 12-14. 16-18'; r = @(@(35, 12, 35, 14), @(35, 16, 35, 17)) })) }
    # Fiestas propias de México (calendario de la CEM). Citas en numeracion
    # hebrea de los salmos (Get-SalmoLiturgico las reetiqueta a la Vulgata, p. ej.
    # Sal 78 -> 77, Sal 67 -> 66, Sal 124 -> 123). Los salmos cortos se leen
    # completos, asi que la seleccion exacta de versiculos solo afecta la etiqueta.
    # La Santa Cruz (3 may): misal.mx/2024-05-03 (Flp 2; Sal 77; Jn 3,13-17).
    '05-03'       = @{ lect = 638; partes = @(@('Primera lectura', 'Phil 2:6-11'), @('Salmo responsorial', 'Ps 78:1bc-2, 34-35, 36-37, 38'), @('Evangelio', 'Jn 3:13-17')) }
    # Felipe y Santiago (4 may): misalmx.com/2026-05-04 (1 Cor 15; Sal 18; Jn 14,6-14).
    '05-04'       = @{ lect = 561; partes = @(@('Primera lectura', '1 Cor 15:1-8'), @('Salmo responsorial', 'Ps 19:2-3, 4-5'), @('Evangelio', 'Jn 14:6-14')) }
    # San Felipe de Jesus (5 feb): misal.mx/2025-02-05 (Sab 3; Sal 123=124 heb; Lc 9,23-26).
    '02-05'       = @{ lect = 970; partes = @(@('Primera lectura', 'Wis 3:1-9'), @('Salmo responsorial', 'Ps 124'), @('Evangelio', 'Lk 9:23-26')) }
    # Santa Rosa de Lima (30 ago): misal.mx/2025-08-30 (2 Cor 10,17-11,2; Sal 148; Mt 13,44-46).
    '08-30'       = @{ lect = 971; partes = @(@('Primera lectura', '2 Cor 10:17-11:2'), @('Salmo responsorial', 'Ps 148'), @('Evangelio', 'Mt 13:44-46')) }
    # Exaltación de la Santa Cruz (14 sep), nº 638: SOLO 3 partes (estructura
    # ordinaria, sin 2ª lectura). El salmo y el Evangelio son los mismos que la Santa
    # Cruz del 3 de mayo (misma ClaveLectura); solo Nm 21 como 1ª lectura es nueva.
    # Verificado (dominicos.org / ACI Prensa / USCCB nº 638).
    '09-14'       = @{ lect = 638; partes = @(@('Primera lectura', 'Nm 21:4b-9'), @('Salmo responsorial', 'Ps 78:1bc-2, 34-35, 36-37, 38'), @('Evangelio', 'Jn 3:13-17')) }
    # Guadalupe (12 dic): misal.mx cambia las cuatro partes respecto a EE. UU.
    # El salmo es 67 en numeracion hebrea (66 en la Vulgata del misal).
    '12-12'       = @{ lect = 690; partes = @(@('Primera lectura', 'Is 7:10-14'), @('Salmo responsorial', 'Ps 67:2-3, 5, 7-8'), @('Segunda lectura', 'Gal 4:4-7'), @('Evangelio', 'Lk 1:39-48')) }
}

# Celebraciones moviles que dependen de la Pascua: la app calcula su dia
# (CalendarioLiturgico.memoriaMovil) y aqui van su nombre, su grado y sus lecturas,
# con la misma notacion que $A_MANO. La fuente las trae con numero de feria o del
# santoral ($FIESTAS_DESCARTADAS), o no las trae (la fiesta propia de México), asi
# que no se toman de ella. La clave no es una fecha: van a la tabla fija y a
# fijasInfo. Sin 'propias' se exigen las tres lecturas (1ª, salmo, Evangelio); con
# 'propias' solo se usan esas y el resto es la feria de ese ano.
#   Santa Maria, Madre de la Iglesia: lunes despues de Pentecostes (nº 572).
#   Jesucristo, Sumo y Eterno Sacerdote: jueves despues de Pentecostes; Fiesta
#   propia de México (misal.mx/2025-06-12: Is 52,13-53,12; Sal 39=40 heb; Lc 22,14-20).
#   Inmaculado Corazon: sabado despues del Sagrado Corazon (nº 573); solo el
#   evangelio es propio, el resto es la feria de ese ano.
$MOVILES = [ordered]@{
    'MADRE-IGLESIA'  = @{ n = 'Santa María, Madre de la Iglesia'; g = 'M'; lect = 572; propias = @('Primera lectura', 'Salmo responsorial', 'Evangelio')
                          partes = @(@('Primera lectura', 'Gn 3:9-15, 20'), @('Salmo responsorial', 'Ps 87:1-2, 3 and 5, 6-7'), @('Evangelio', 'Jn 19:25-34')) }
    # Fiesta propia de México: Is 52,13-53,12 reusa la ClaveLectura de TRI-VIE.
    'SUMO-SACERDOTE' = @{ n = 'Jesucristo, Sumo y Eterno Sacerdote'; g = 'F'; lect = 972
                          partes = @(@('Primera lectura', 'Is 52:13-53:12'), @('Salmo responsorial', 'Ps 40'), @('Evangelio', 'Lk 22:14-20')) }
    'CORAZON-MARIA'  = @{ n = 'Inmaculado Corazón de la Virgen María'; g = 'M'; lect = 573; propias = @('Evangelio')
                          partes = @(, @('Evangelio', 'Lk 2:41-51')) }
}

# ----------------------------------------------------------------- proceso

Write-Output 'Leyendo leccionario...'
$json = [System.IO.File]::ReadAllText($srcReadings, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$dates = $json.PSObject.Properties | ForEach-Object { $_.Name } | Sort-Object
Write-Output "  $($dates.Count) fechas, de $($dates[0]) a $($dates[-1])"

# Campo de la fuente -> titulo de la lectura. Los de la Vigilia pascual van en
# el orden en que se proclaman.
$TITULOS = [ordered]@{
    first_reading      = 'Primera lectura'
    responsorial_psalm_1 = 'Salmo responsorial'
    second_reading     = 'Segunda lectura'
    responsorial_psalm_2 = 'Salmo responsorial'
    third_reading      = 'Tercera lectura'
    responsorial_psalm_3 = 'Salmo responsorial'
    fourth_reading     = 'Cuarta lectura'
    responsorial_psalm_4 = 'Salmo responsorial'
    fifth_reading      = 'Quinta lectura'
    responsorial_psalm_5 = 'Salmo responsorial'
    sixth_reading      = 'Sexta lectura'
    responsorial_psalm_6 = 'Salmo responsorial'
    seventh_reading    = 'Séptima lectura'
    responsorial_psalm_7 = 'Salmo responsorial'
    epistle            = 'Epístola'
    responsorial_psalm = 'Salmo responsorial'
    gospel             = 'Evangelio'
}
# Orden de una misa normal: el salmo va detras de la primera lectura.
$CAMPOS_MISA = @('first_reading', 'responsorial_psalm', 'second_reading', 'gospel')
$CAMPOS_VIGILIA = @($TITULOS.Keys)
$ORDEN_TITULOS = @('Evangelio de la procesión', 'Primera lectura', 'Salmo responsorial', 'Segunda lectura', 'Evangelio')

# Misas que no son la del dia: vigilias, las de la noche y la aurora de
# Navidad, la crismal, la vigilia extendida de Pentecostes, Accion de Gracias.
$MISAS_DESCARTADAS = @('vigil', 'night', 'dawn', 'chrism', 'extended', 'extendedvigil', 'thanksgiving')

function Select-Mass($masses, [datetime]$d) {
    $cands = @($masses | Where-Object { $_.readings -and @($_.readings.PSObject.Properties).Count -gt 0 })
    $cands = @($cands | Where-Object { $MISAS_DESCARTADAS -notcontains ([string]$_.mass).ToLower() })
    if ($cands.Count -le 1) { return $cands }
    # Domingos de Cuaresma: la fuente da las lecturas del ano A y las del ciclo del ano.
    $ciclo = Get-SundayCycle (Get-LiturgicalYear $d)
    $porCiclo = @($cands | Where-Object { [string]$_.mass -eq "Year$ciclo" })
    if ($porCiclo.Count -gt 0) { return , $porCiclo[0] }
    $delDia = @($cands | Where-Object { @('day', 'mass', 'default', 'supper', 'eveningmass', 'sunday') -contains ([string]$_.mass).ToLower() })
    if ($delDia.Count -gt 0) { return , $delDia[0] }
    return , $cands[0]
}

$records = @{}
$unparsed = New-Object System.Collections.ArrayList
$totalCitas = 0

foreach ($ds in $dates) {
    $d = [datetime]::ParseExact($ds, 'yyyy-MM-dd', $null)
    $mass = @(Select-Mass @($json.$ds) $d)
    if ($mass.Count -eq 0) { continue }
    $mass = $mass[0]
    $r = $mass.readings
    $lect = 0
    [void][int]::TryParse((([string]$mass.lectionary_number).Trim() -replace '[^\d].*$', ''), [ref]$lect)

    $vigilia = ($lect -eq 41)
    $ramos = ($lect -eq 37 -or $lect -eq 38 -or [string]$mass.feast -match '(?i)palm sunday')
    $campos = if ($vigilia) { $CAMPOS_VIGILIA } else { $CAMPOS_MISA }

    $parts = New-Object System.Collections.ArrayList
    $failures = 0
    foreach ($field in $campos) {
        $v = @($r.$field)
        if ($v.Count -eq 0 -or -not $v[0]) { continue }
        $defecto = if ($field -like 'responsorial_psalm*') { 23 } else { 0 }
        $citas = New-Object System.Collections.ArrayList
        if ($field -eq 'gospel' -and $ramos -and $v.Count -ge 2) {
            # Domingo de Ramos: la fuente da primero el evangelio de la procesion
            # y despues la Pasion (larga y breve). El de la misa es la Pasion.
            [void]$citas.Add(@('Evangelio de la procesión', $v[0].citation))
            [void]$citas.Add(@('Evangelio', $v[1].citation))
        } else {
            # Si hay alternativas ("Mi 5,1-4a o Rom 8,28-30"), la primera.
            [void]$citas.Add(@($TITULOS[$field], $v[0].citation))
        }
        foreach ($c in $citas) {
            if (-not $c[1]) { continue }
            $totalCitas++
            $p = Parse-Citation $c[1] $defecto
            if ($null -eq $p) { [void]$unparsed.Add("$ds $field :: $($c[1])"); $failures++; continue }
            if ($p.unknown.Count -gt 0) { [void]$unparsed.Add("$ds $field :: $($c[1])  (sin leer: $($p.unknown -join ' | '))"); $failures++ }
            [void]$parts.Add(@{ tipo = $c[0]; parsed = $p; cita = $c[1] })
        }
    }
    if ($parts.Count -eq 0) { continue }

    $records[$ds] = @{
        date = $d
        # La fuente es el calendario de EE. UU.: el Corpus va en pascua+63.
        temporalKey = Get-TemporalKey $d -Fuente
        fixedKey = '{0:00}-{1:00}' -f $d.Month, $d.Day
        parts = $parts
        fingerprint = (($parts | ForEach-Object { "$($_.tipo)=$($_.parsed.label)" }) -join '|')
        # Numero oficial del leccionario: identifica la misa con independencia
        # de como se hayan abreviado las citas ese ano. Sirve de comprobacion
        # independiente de mi propio calculo del calendario.
        lect = $lect
        feast = [string]$mass.feast
        failures = $failures
    }
}

Write-Output "  citas totales        : $totalCitas"
Write-Output "  citas no interpretadas: $($unparsed.Count)"
$unparsed | Select-Object -First 12 | ForEach-Object { "     ? $_" }
Write-Output "  dias utilizables     : $($records.Count)"

# --- clasificacion ---
# Una fecha va a la tabla fija solo si esta en $FIJAS y la fuente trae ese dia
# la misa de la fiesta (su numero oficial, o el evangelio propio de la memoria).
# Lo demas con numero del Propio del Tiempo (hasta el 508, que incluye la
# semana 34) va a su clave temporal; lo que tiene numero del santoral y no es
# una fiesta fija (traslados, memorias moviles) no vale para ninguna de las dos.
$SANTORAL_DESDE = 509
$byFixed = @{}; $byTemporal = @{}; $descartadosSantoral = New-Object System.Collections.ArrayList
# En orden de fecha: asi los empates de Select-Best se resuelven siempre igual.
foreach ($k in ($records.Keys | Sort-Object)) {
    $rec = $records[$k]
    $fk = $rec.fixedKey
    $ciclo = Get-SundayCycle (Get-LiturgicalYear $rec.date)
    $esFija = $false
    if ($FIJAS.Contains($fk)) {
        $esFija = $FIJAS[$fk].lect -contains $rec.lect
        if (-not $esFija -and $FIJAS[$fk].firma) {
            $esFija = [bool]($rec.parts | Where-Object { $_.tipo -eq 'Evangelio' -and $_.parsed.label -eq $FIJAS[$fk].firma })
        }
    }
    if ($esFija) {
        $grupo = $fk
        if ($FIJAS[$fk].porCiclo) { $grupo = "$fk/$ciclo" }
        if (-not $byFixed.ContainsKey($grupo)) { $byFixed[$grupo] = New-Object System.Collections.ArrayList }
        [void]$byFixed[$grupo].Add($rec)
    } elseif ($rec.lect -gt 0 -and $rec.lect -lt $SANTORAL_DESDE -and $rec.feast -notmatch $FIESTAS_DESCARTADAS) {
        $tk = $rec.temporalKey
        $base = $tk -replace '-(I|II)$', ''
        if ($FERIAS_POR_CICLO.ContainsKey($base) -and $FERIAS_POR_CICLO[$base] -eq $ciclo) { $tk = "$tk/$ciclo" }
        if (-not $byTemporal.ContainsKey($tk)) { $byTemporal[$tk] = New-Object System.Collections.ArrayList }
        [void]$byTemporal[$tk].Add($rec)
    } else {
        [void]$descartadosSantoral.Add("$k [$($rec.lect)] $($rec.feast)")
    }
}

# De cada grupo se elige el dia cuyas citas se interpretaron mejor.
function Select-Best($group) {
    $byLect = @($group | Group-Object { $_.lect } | Sort-Object Count -Descending)
    $winners = @($group | Where-Object { $_.lect -eq $byLect[0].Group[0].lect })
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
Write-Output "Dias con misa del santoral que no es fiesta fija (no se usan): $($descartadosSantoral.Count)"
$descartadosSantoral | Sort-Object | ForEach-Object { "   - $_" }

# ------------------------------------------------- versificacion y validacion
#
# Las citas del leccionario usan la numeracion hebrea (la de las Biblias
# catolicas), y la Santa Biblia Libre empaquetada usa la de la Reina-Valera.
# Hay dos riesgos distintos:
#   - cita FUERA DE RANGO: se ve al instante (no hay texto)
#   - cita DENTRO de rango pero desplazada: silenciosa, y por eso peor
# Aqui se convierte todo lo que difiere y se descarta, CON AVISO, lo que no
# encaje. Un tramo imposible nunca se convierte en "desde el versiculo 1".

$estructura = @{}   # libro -> array con el numero de versiculos de cada capitulo
$textos = @{}       # libro -> capitulos (listas de versiculos)
for ($b = 1; $b -le 73; $b++) {
    $libro = [System.IO.File]::ReadAllText((Join-Path $bibliaDir "$b.json"), [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    $textos[$b] = @($libro.chapters)
    $estructura[$b] = @($libro.chapters | ForEach-Object { @($_).Count })
}

# Capitulos donde las dos numeraciones no coinciden, comprobados contra el texto
# de bible-cat: libro, capitulo del leccionario, primer y ultimo versiculo a los
# que se aplica, capitulo en la Biblia empaquetada, cantidad a sumar.
# (Mal 3,19-24 = 4,1-6; Zac 2,14 = 2,10; Miq 5,1 = 5,2; Os 14,2 = 14,1; Ex 22,20 =
# 22,21; Is 9,1 = 9,2; Gn 32,23 = 32,22; 1 S 24,3 = 24,2; 2 S 19,1 = 18,33;
# Nah 2,1 = 1,15; Dn 6,12 = 6,11; Jl 3,1 = 2,28; Jon 2,1 = 1,17; y los salmos
# largos cuyo titulo cuenta como versiculo 1 en el leccionario.)
$VERSIFICACION = @(
    @(1, 32, 1, 1, 31, 54), @(1, 32, 2, 33, 32, -1)                 # Genesis
    @(2, 7, 26, 29, 8, -25), @(2, 8, 1, 28, 8, 4)                   # Exodo 8
    @(2, 21, 37, 37, 22, -36), @(2, 22, 1, 30, 22, 1)               # Exodo 22
    @(3, 5, 20, 26, 6, -19), @(3, 6, 1, 23, 6, 7)                   # Levitico
    @(4, 17, 1, 15, 16, 35), @(4, 17, 16, 28, 17, -15)              # Numeros 17
    @(4, 30, 1, 1, 29, 39), @(4, 30, 2, 17, 30, -1)                 # Numeros 30
    @(5, 13, 1, 1, 12, 31), @(5, 13, 2, 19, 13, -1)                 # Deuteronomio 13
    @(5, 23, 1, 1, 22, 29), @(5, 23, 2, 26, 23, -1)                 # Deuteronomio 23
    @(5, 28, 69, 69, 29, -68), @(5, 29, 1, 28, 29, 1)               # Deuteronomio 29
    @(9, 21, 1, 1, 20, 41), @(9, 21, 2, 16, 21, -1)                 # 1 Samuel 21
    @(9, 24, 1, 1, 23, 28), @(9, 24, 2, 23, 24, -1)                 # 1 Samuel 24
    @(10, 19, 1, 1, 18, 32), @(10, 19, 2, 44, 19, -1)               # 2 Samuel 19
    @(11, 5, 1, 14, 4, 20), @(11, 5, 15, 32, 5, -14)                # 1 Reyes 5
    @(12, 12, 1, 1, 11, 20), @(12, 12, 2, 22, 12, -1)               # 2 Reyes 12
    @(13, 5, 27, 41, 6, -26), @(13, 6, 1, 66, 6, 15)                # 1 Cronicas 6
    @(14, 1, 18, 18, 2, -17), @(14, 2, 1, 17, 2, 1)                 # 2 Cronicas 2
    @(14, 13, 23, 23, 14, -22), @(14, 14, 1, 14, 14, 1)             # 2 Cronicas 14
    @(16, 3, 33, 38, 4, -32), @(16, 4, 1, 17, 4, 6)                 # Nehemias 4
    @(16, 10, 1, 1, 9, 37), @(16, 10, 2, 40, 10, -1)                # Nehemias 10
    @(22, 40, 25, 32, 41, -24), @(22, 41, 1, 26, 41, 8)             # Job 41
    # Los salmos con titulo van aparte (se generan abajo, tras $reglasVersif).
    @(25, 4, 17, 17, 5, -16), @(25, 5, 1, 19, 5, 1)                 # Eclesiastes 5
    @(26, 7, 1, 1, 6, 12), @(26, 7, 2, 14, 7, -1)                   # Cantar 7
    @(29, 8, 23, 23, 9, -22), @(29, 9, 1, 20, 9, 1)                 # Isaias 9
    , @(29, 64, 1, 11, 64, 1)                                       # Isaias 64 (63,19b va aparte)
    @(30, 8, 23, 23, 9, -22), @(30, 9, 1, 25, 9, 1)                 # Jeremias 9
    @(33, 21, 1, 5, 20, 44), @(33, 21, 6, 37, 21, -5)               # Ezequiel 21
    @(34, 3, 98, 100, 4, -97), @(34, 4, 1, 34, 4, 3)                # Daniel 3-4
    @(34, 6, 1, 1, 5, 30), @(34, 6, 2, 29, 6, -1)                   # Daniel 6
    @(35, 2, 1, 2, 1, 9), @(35, 2, 3, 25, 2, -2)                    # Oseas 2
    @(35, 12, 1, 1, 11, 11), @(35, 12, 2, 15, 12, -1)               # Oseas 12
    @(35, 14, 1, 1, 13, 15), @(35, 14, 2, 10, 14, -1)               # Oseas 14
    @(36, 3, 1, 5, 2, 27), @(36, 4, 1, 21, 3, 0)                    # Joel
    @(39, 2, 1, 1, 1, 16), @(39, 2, 2, 11, 2, -1)                   # Jonas
    @(40, 4, 14, 14, 5, -13), @(40, 5, 1, 14, 5, 1)                 # Miqueas
    @(41, 2, 1, 1, 1, 14), @(41, 2, 2, 14, 2, -1)                   # Nahum
    @(45, 2, 1, 4, 1, 17), @(45, 2, 5, 17, 2, -4)                   # Zacarias
    , @(46, 3, 19, 24, 4, -18)                                      # Malaquias
)
$reglasVersif = @{}
foreach ($rg in $VERSIFICACION) {
    # Una regla sola en su linea necesita la coma delante: sin ella PowerShell
    # la deshace en seis numeros sueltos y la conversion no se aplica.
    if (@($rg).Count -ne 6) { throw "Regla de versificacion mal escrita: $($rg -join ',')" }
    $clave = "$($rg[0])-$($rg[1])"
    if (-not $reglasVersif.ContainsKey($clave)) { $reglasVersif[$clave] = New-Object System.Collections.ArrayList }
    [void]$reglasVersif[$clave].Add($rg)
}

# Salmos con titulo: en la numeracion HEBREA del leccionario el titulo cuenta como
# versiculo(s); en la SBL (que sigue la KJV) el titulo va DENTRO del v.1. Hay que
# restar 1 (titulo de una linea) o 2 (titulo de dos lineas) a cada verso citado.
# El v.1 (y el v.2 en los de -2) caen en el v.1 de la SBL. Lista confirmada contra
# bible-cat. El NUMERO que se muestra sigue siendo el de la Vulgata (Get-SalmoLiturgico);
# esto solo convierte los VERSICULOS de los rangos.
$SALMOS_MENOS_1 = @(3, 4, 5, 6, 7, 8, 9, 12, 18, 19, 20, 21, 22, 30, 31, 34, 36, 38, 39, 40, 41, 42,
    44, 45, 46, 47, 48, 49, 53, 55, 56, 57, 58, 59, 61, 62, 63, 64, 65, 67, 68, 69, 70, 75, 76, 77,
    80, 81, 83, 84, 85, 88, 89, 92, 102, 108, 140, 142)
$SALMOS_MENOS_2 = @(51, 52, 54, 60)
function Add-ReglaVersif($rg) {
    $clave = "$($rg[0])-$($rg[1])"
    if (-not $reglasVersif.ContainsKey($clave)) { $reglasVersif[$clave] = New-Object System.Collections.ArrayList }
    [void]$reglasVersif[$clave].Add($rg)
}
foreach ($p in $SALMOS_MENOS_1) { Add-ReglaVersif @(23, $p, 2, 200, $p, -1) }   # v>=2 -> v-1; v1 -> 1
foreach ($p in $SALMOS_MENOS_2) {
    Add-ReglaVersif @(23, $p, 2, 2, $p, -1)                                      # v2 -> 1
    Add-ReglaVersif @(23, $p, 3, 200, $p, -2)                                    # v>=3 -> v-2; v1 -> 1
}
# Salmo 13: v2-5 hebreos -> v-1; el v6 hebreo son el 5 y el 6 de la SBL (ver Convert-Segment).

function Convert-Verse([int]$book, [int]$c, [int]$v, [bool]$esFin, [string]$sfx) {
    if ($v -eq $HASTA_EL_FINAL) { return @($c, $v) }
    # Isaias 63,19b ("Ojala rasgaras los cielos") es 64,1 en la otra numeracion.
    if ($book -eq 29 -and $c -eq 63 -and $v -eq 19 -and $sfx -like 'b*') { return @(64, 1) }
    # 2 Corintios 13: el 12 del leccionario son el 12 y el 13 de la RV.
    if ($book -eq 54 -and $c -eq 13) {
        if ($v -ge 13) { return @(13, ($v + 1)) }
        if ($v -eq 12 -and $esFin) { return @(13, 13) }
        return @($c, $v)
    }
    $reglas = $reglasVersif["$book-$c"]
    if ($reglas) {
        foreach ($rg in $reglas) {
            if ($v -ge $rg[2] -and $v -le $rg[3]) {
                $nv = $v + $rg[5]
                if ($nv -lt 1) { $nv = 1 }
                return @($rg[4], $nv)
            }
        }
    }
    return @($c, $v)
}

# Un tramo de la cita (numeracion del leccionario) -> tramos de la Biblia empaquetada.
function Convert-Segment([int]$book, $seg) {
    # Romanos 16,25-27: la Santa Biblia Libre pone la doxologia en 14,24-26 y
    # deja el 16,25 vacio.
    if ($book -eq 52 -and $seg[0] -eq 16 -and $seg[2] -eq 16 -and $seg[3] -ge 25) {
        $out = New-Object System.Collections.ArrayList
        if ($seg[1] -lt 25) { [void]$out.Add(@(16, $seg[1], 16, 24)) }
        $ini = [Math]::Max($seg[1], 25)
        [void]$out.Add(@(14, ($ini - 1), 14, ($seg[3] - 1)))
        return , $out.ToArray()
    }
    # Salmo 13: titulo de una linea (v2-5 -> v-1), pero el v6 hebreo se repartio en
    # los versiculos 5 y 6 de la SBL. Un tramo que empieza en el v6 arranca en el 5;
    # uno que termina en el v6 llega al 6.
    if ($book -eq 23 -and $seg[0] -eq 13 -and $seg[2] -eq 13) {
        $ini = if ($seg[1] -ge 6) { 5 } elseif ($seg[1] -ge 2) { $seg[1] - 1 } else { 1 }
        $fin = if ($seg[3] -ge 6) { 6 } elseif ($seg[3] -ge 2) { $seg[3] - 1 } else { 1 }
        return , @(, @(13, $ini, 13, $fin))
    }
    $a = Convert-Verse $book $seg[0] $seg[1] $false ([string]$seg[4])
    # Un versiculo suelto partido ("19b") termina donde empieza.
    $e = if ($seg[0] -eq $seg[2] -and $seg[1] -eq $seg[3]) { $a } else { Convert-Verse $book $seg[2] $seg[3] $true '' }
    return , @(, @($a[0], $a[1], $e[0], $e[1]))
}

$avisos = New-Object System.Collections.ArrayList
$ajustes = @{}

function Get-VersesCount([int]$book, $t) {
    $n = 0
    for ($c = $t[0]; $c -le $t[2]; $c++) {
        $vs = $textos[$book][$c - 1]
        $ini = if ($c -eq $t[0]) { $t[1] } else { 1 }
        $fin = if ($c -eq $t[2]) { [Math]::Min($t[3], @($vs).Count) } else { @($vs).Count }
        for ($v = $ini; $v -le $fin; $v++) { if (-not [string]::IsNullOrWhiteSpace([string]$vs[$v - 1])) { $n++ } }
    }
    return $n
}

# Ajusta tramos ya convertidos al texto real: recorta el final si se pasa del
# capitulo (una cita a un versiculo que esta Biblia no tiene) y DESCARTA con
# aviso lo imposible (inicio fuera del capitulo, inicio despues del final) y lo
# que no tiene texto.
function Adjust-Ranges([int]$book, $tramos, [string]$donde) {
    $caps = $estructura[$book]
    $out = New-Object System.Collections.ArrayList
    foreach ($t in $tramos) {
        $c1 = $t[0]; $v1 = $t[1]; $c2 = $t[2]; $v2 = $t[3]
        if ($c1 -lt 1 -or $c1 -gt $caps.Count -or $c2 -lt $c1) {
            [void]$avisos.Add("DESCARTADO $donde [$c1,$v1,$c2,$v2]: capitulo fuera del libro"); continue
        }
        if ($c2 -gt $caps.Count) { [void]$avisos.Add("RECORTE $donde [$c1,$v1,$c2,$v2]: capitulo final $c2 > $($caps.Count)"); $c2 = $caps.Count; $v2 = $caps[$c2 - 1] }
        # Un inicio justo despues del final del capitulo (lo deja Remove-Overlaps) es el 1 del siguiente.
        if ($v1 -gt $caps[$c1 - 1] -and $c1 -lt $c2) { $c1++; $v1 = 1 }
        if ($v1 -lt 1 -or $v1 -gt $caps[$c1 - 1]) {
            [void]$avisos.Add("DESCARTADO $donde [$c1,$v1,$c2,$v2]: el versiculo $v1 no existe (capitulo $c1 tiene $($caps[$c1 - 1]))"); continue
        }
        if ($v2 -eq $HASTA_EL_FINAL) { $v2 = $caps[$c2 - 1] }
        elseif ($v2 -gt $caps[$c2 - 1]) { [void]$avisos.Add("RECORTE $donde [$c1,$v1,$c2,$v2]: el capitulo $c2 tiene $($caps[$c2 - 1])"); $v2 = $caps[$c2 - 1] }
        if ($c1 -eq $c2 -and $v1 -gt $v2) {
            [void]$avisos.Add("DESCARTADO $donde [$c1,$v1,$c2,$v2]: el inicio va despues del final"); continue
        }
        if ((Get-VersesCount $book @($c1, $v1, $c2, $v2)) -eq 0) {
            [void]$avisos.Add("DESCARTADO $donde [$c1,$v1,$c2,$v2]: sin texto en la Biblia empaquetada"); continue
        }
        [void]$out.Add(@($c1, $v1, $c2, $v2))
    }
    # La coma inicial es imprescindible: sin ella PowerShell desenrolla la lista
    # al devolverla y un tramo de cuatro numeros acaba siendo cuatro tramos.
    return ,$out.ToArray()
}

# Numeracion de los salmos: la app (y la Biblia empaquetada) usa la HEBREA; el
# Leccionario mexicano usa la griega/Vulgata (Sal 79 donde la app dice 80). Esta
# funcion pasa el numero hebreo $n al de la Vulgata, usando el primer versiculo
# citado $v para los dos salmos que la Vulgata parte en dos (116 y 147). Solo
# cambia la ETIQUETA que se muestra y se locuta; los versiculos de los rangos
# siguen con el numero hebreo. (MX-08.)
function Get-SalmoLiturgico([int]$n, [int]$v) {
    if ($n -ge 9 -and $n -le 10) { return 9 }
    if ($n -ge 11 -and $n -le 113) { return $n - 1 }
    if ($n -ge 114 -and $n -le 115) { return 113 }
    if ($n -eq 116) { if ($v -le 9) { return 114 } else { return 115 } }
    if ($n -ge 117 -and $n -le 146) { return $n - 1 }
    if ($n -eq 147) { if ($v -le 11) { return 146 } else { return 147 } }
    return $n
}

# Una lectura interpretada -> @{ t; c; b; r } con los tramos de la Biblia empaquetada.
function Resolve-Part([string]$tipo, $parsed, [string]$donde) {
    $book = $parsed.book
    $tramos = New-Object System.Collections.ArrayList
    # Todas las lecturas, incluido el salmo responsorial, leen SUS versiculos
    # citados (convertidos de la numeracion del leccionario a la de la Biblia
    # empaquetada); ya no se fuerza leer el salmo entero.
    foreach ($s in $parsed.segs) {
        $conv = Convert-Segment $book $s
        foreach ($t in $conv) {
            [void]$tramos.Add($t)
            if ($t[0] -ne $s[0] -or $t[1] -ne $s[1] -or $t[2] -ne $s[2] -or $t[3] -ne $s[3]) { $ajustes["$($SPANISH[$book])"] = 1 }
        }
    }
    $r = Adjust-Ranges $book (Remove-Overlaps $tramos.ToArray()) $donde
    # El salmo responsorial se etiqueta con la numeracion de la Vulgata del misal
    # mexicano, aunque los tramos sigan leyendo del salmo hebreo (MX-08).
    $label = $parsed.label
    if ($book -eq 23 -and $tipo -eq 'Salmo responsorial') {
        $hebreo = [int]$parsed.segs[0][0]
        $vulgata = Get-SalmoLiturgico $hebreo ([int]$parsed.segs[0][1])
        if ($vulgata -ne $hebreo) { $label = $label -replace "^Salmo\s+$hebreo\b", "Salmo $vulgata" }
    }
    return @{ t = $tipo; c = $label; b = $book; r = $r }
}

function Sort-Parts($lista) {
    $ordenadas = @($lista | Sort-Object @{ e = { $i = [array]::IndexOf($ORDEN_TITULOS, $_.t); if ($i -lt 0) { 99 } else { $i } } })
    return , ([System.Collections.ArrayList]$ordenadas)
}

function Resolve-Record($rec, [string]$donde) {
    $out = New-Object System.Collections.ArrayList
    foreach ($p in $rec.parts) {
        $x = Resolve-Part $p.tipo $p.parsed "$donde $($p.tipo)"
        if ($x.r.Count -eq 0) { [void]$avisos.Add("LECTURA SIN TEXTO $donde $($p.tipo): $($p.cita)"); continue }
        [void]$out.Add($x)
    }
    # Orden de la misa (el evangelio de la procesion de Ramos va delante). La
    # Vigilia pascual, con un salmo tras cada lectura, se deja como viene.
    $titulos = @($out | ForEach-Object { $_.t })
    if (@($titulos | Select-Object -Unique).Count -eq $titulos.Count) { return Sort-Parts $out }
    return , $out
}

$tablaFijas = @{}
foreach ($k in $fixedTable.Keys) { $tablaFijas[$k] = Resolve-Record $fixedTable[$k] "fijas/$k" }
$tablaTemporales = @{}
foreach ($k in $temporalTable.Keys) { $tablaTemporales[$k] = Resolve-Record $temporalTable[$k] "temporales/$k" }

# Memorias: solo quedan sus lecturas propias (las demas son las de la feria de
# ese ano y la app las toma de la feria que toque).
foreach ($fk in $FIJAS.Keys) {
    $propias = $FIJAS[$fk].propias
    if (-not $propias -or -not $tablaFijas.ContainsKey($fk)) { continue }
    $tablaFijas[$fk] = [System.Collections.ArrayList]@($tablaFijas[$fk] | Where-Object { $propias -contains $_.t })
}

# --- lecturas a mano ---
$aMano = 0
foreach ($k in $A_MANO.Keys) {
    $entrada = $A_MANO[$k]
    $nuevas = New-Object System.Collections.ArrayList
    foreach ($par in $entrada.partes) {
        $tipo = $par[0]; $cita = $par[1]
        if ($cita -is [hashtable]) {
            $r = Adjust-Ranges $cita.b $cita.r "a mano/$k $tipo"
            [void]$nuevas.Add(@{ t = $tipo; c = $cita.c; b = $cita.b; r = $r })
            continue
        }
        $defecto = if ($tipo -eq 'Salmo responsorial') { 23 } else { 0 }
        $p = Parse-Citation $cita $defecto
        if ($null -eq $p -or $p.unknown.Count -gt 0) { throw "Cita a mano sin interpretar ($k, $tipo): $cita" }
        [void]$nuevas.Add((Resolve-Part $tipo $p "a mano/$k $tipo"))
    }
    $tabla = if ($k -match '^\d\d-\d\d') { $tablaFijas } else { $tablaTemporales }
    if ($tabla.ContainsKey($k)) {
        $lista = New-Object System.Collections.ArrayList
        $titulos = @($nuevas | ForEach-Object { $_.t })
        foreach ($l in $tabla[$k]) { if ($titulos -notcontains $l.t) { [void]$lista.Add($l) } }
        foreach ($l in $nuevas) { [void]$lista.Add($l) }
        $tabla[$k] = Sort-Parts $lista
    } else {
        $tabla[$k] = Sort-Parts $nuevas
    }
    $aMano++
}
Write-Output ''
Write-Output "Claves completadas a mano: $aMano"

# --- memorias moviles ---
foreach ($k in $MOVILES.Keys) {
    $lista = New-Object System.Collections.ArrayList
    foreach ($par in $MOVILES[$k].partes) {
        $defecto = if ($par[0] -eq 'Salmo responsorial') { 23 } else { 0 }
        $p = Parse-Citation $par[1] $defecto
        if ($null -eq $p -or $p.unknown.Count -gt 0) { throw "Cita de memoria movil sin interpretar ($k, $($par[0])): $($par[1])" }
        [void]$lista.Add((Resolve-Part $par[0] $p "moviles/$k $($par[0])"))
    }
    $tablaFijas[$k] = Sort-Parts $lista
}
Write-Output "Memorias moviles: $($MOVILES.Count)"

# Las ferias de Adviento, Navidad, Cuaresma y Pascua son iguales los anos I y
# II (solo las del Tiempo Ordinario cambian): si la fuente trajo solo uno de
# los dos, vale para el otro.
$gemelas = 0
foreach ($k in @($tablaTemporales.Keys)) {
    if ($k -match '^((?:ADV|CUA|PAS)-\d+|SANTA)-([1-6])-(I|II)(/[ABC])?$') {
        $otroAno = if ($Matches[3] -eq 'I') { 'II' } else { 'I' }
        $otra = "$($Matches[1])-$($Matches[2])-$otroAno$($Matches[4])"
        if (-not $tablaTemporales.ContainsKey($otra)) { $tablaTemporales[$otra] = $tablaTemporales[$k]; $gemelas++ }
    }
}
Write-Output "Ferias copiadas del otro ano (I/II): $gemelas"

Write-Output ''
Write-Output "Conversiones de versificacion en: $((($ajustes.Keys | Sort-Object) -join ', '))"
Write-Output "Avisos: $($avisos.Count)"
$avisos | ForEach-Object { "   - $_" }

# ------------------------------------------------------------ comprobaciones
# Nada de huecos ni lecturas rotas: si algo falla aqui, el script se para.
$errores = New-Object System.Collections.ArrayList

function Test-Parts($lista, [string]$donde, [string[]]$requeridas) {
    $titulos = @($lista | ForEach-Object { $_.t })
    foreach ($t in $requeridas) {
        if ($titulos -notcontains $t) { [void]$errores.Add("$donde : falta la lectura '$t'") }
    }
    foreach ($l in $lista) {
        $n = 0
        foreach ($t in $l.r) { $n += Get-VersesCount $l.b $t }
        if ($n -eq 0) { [void]$errores.Add("$donde : '$($l.t)' ($($l.c)) no tiene ningun versiculo") }
    }
}

foreach ($k in $tablaTemporales.Keys) {
    $req = if ($k -match '^(TRI-SAB|TRI-VIE)') { @('Primera lectura', 'Salmo responsorial', 'Evangelio') }
           elseif ($k -match '-[ABC]$' -or $k -eq 'NAV-12-25' -or $k -eq 'EPIFANIA' -or $k -eq 'ENE01' -or $k -eq 'TRI-JUE') { @('Primera lectura', 'Salmo responsorial', 'Segunda lectura', 'Evangelio') }
           else { @('Primera lectura', 'Salmo responsorial', 'Evangelio') }
    Test-Parts $tablaTemporales[$k] "temporales/$k" $req
}
foreach ($k in $tablaFijas.Keys) {
    $info = if ($MOVILES.Contains($k)) { $MOVILES[$k] } else { $FIJAS[$k.Substring(0, 5)] }
    $req = if ($info.propias) { @() } elseif ($info.g -eq 'S') { @('Primera lectura', 'Salmo responsorial', 'Segunda lectura', 'Evangelio') } else { @('Primera lectura', 'Salmo responsorial', 'Evangelio') }
    Test-Parts $tablaFijas[$k] "fijas/$k" $req
}
foreach ($fk in $FIJAS.Keys) {
    if ($FIJAS[$fk].porCiclo) {
        foreach ($ci in 'A', 'B', 'C') { if (-not $tablaFijas.ContainsKey("$fk/$ci")) { [void]$errores.Add("fijas/$fk/$ci : sin lecturas") } }
    } elseif (-not $tablaFijas.ContainsKey($fk)) { [void]$errores.Add("fijas/$fk ($($FIJAS[$fk].n)) : sin lecturas") }
}

# Todos los dias del periodo tienen que encontrar sus lecturas. La feria solo
# puede faltar si ese dia gana seguro su fiesta fija (y no es una memoria, que
# toma de la feria parte de las lecturas).
$faltan = New-Object System.Collections.ArrayList
for ($d = $CUBRIR_DESDE; $d -le $CUBRIR_HASTA; $d = $d.AddDays(1)) {
    $tk = Get-TemporalKey $d
    $base = $tk -replace '-(I|II)$', ''
    $ciclo = Get-SundayCycle (Get-LiturgicalYear $d)
    if ($FERIAS_POR_CICLO.ContainsKey($base) -and $FERIAS_POR_CICLO[$base] -eq $ciclo -and -not $tablaTemporales.ContainsKey("$tk/$ciclo")) {
        [void]$faltan.Add("$($d.ToString('yyyy-MM-dd ddd')) $tk/$ciclo")
    }
    if ($tablaTemporales.ContainsKey($tk)) { continue }
    $fk = '{0:00}-{1:00}' -f $d.Month, $d.Day
    $gana = $false
    if ($FIJAS.Contains($fk) -and $FIJAS[$fk].g -ne 'M') { $gana = $RANGO_GRADO[$FIJAS[$fk].g] -lt (Get-TemporalRank $tk $d) }
    if (-not $gana) { [void]$faltan.Add("$($d.ToString('yyyy-MM-dd ddd')) $tk") }
}
foreach ($f in $faltan) { [void]$errores.Add("sin lecturas: $f") }

if ($errores.Count -gt 0) {
    Write-Output ''
    Write-Output "=== ERRORES ($($errores.Count)) ==="
    $errores | ForEach-Object { "   X $_" }
    throw "El leccionario tiene $($errores.Count) problemas: no se escribe."
}
Write-Output ''
Write-Output "Cobertura $($CUBRIR_DESDE.ToString('yyyy-MM-dd'))..$($CUBRIR_HASTA.ToString('yyyy-MM-dd')): completa"

# ----------------------------------------------------------------- salida

function Emit-List($lista) {
    $items = foreach ($p in $lista) {
        $rangesJson = ($p.r | ForEach-Object { '[' + ($_ -join ',') + ']' }) -join ','
        '{"t":"' + $p.t + '","c":"' + ($p.c -replace '"', '\"') + '","b":' + $p.b + ',"r":[' + $rangesJson + ']}'
    }
    return '[' + ($items -join ',') + ']'
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.Append('{"fuente":"Leccionario de México (CEM)","fijas":{')
$i = 0
foreach ($k in ($tablaFijas.Keys | Sort-Object)) {
    if ($i++ -gt 0) { [void]$sb.Append(',') }
    [void]$sb.Append('"').Append($k).Append('":').Append((Emit-List $tablaFijas[$k]))
}
# Nombre y grado de cada fiesta fija: la app los usa para decidir si la fiesta
# gana al dia (Precedencia.kt) y para la cabecera.
[void]$sb.Append('},"fijasInfo":{')
$i = 0
foreach ($info in @($FIJAS, $MOVILES)) {
    foreach ($k in $info.Keys) {
        if ($i++ -gt 0) { [void]$sb.Append(',') }
        [void]$sb.Append('"').Append($k).Append('":{"n":"').Append($info[$k].n).Append('","g":"').Append($info[$k].g).Append('"}')
    }
}
[void]$sb.Append('},"temporales":{')
$i = 0
foreach ($k in ($tablaTemporales.Keys | Sort-Object)) {
    if ($i++ -gt 0) { [void]$sb.Append(',') }
    [void]$sb.Append('"').Append($k).Append('":').Append((Emit-List $tablaTemporales[$k]))
}
[void]$sb.Append('}}')

$out = Join-Path $outDir 'leccionario.json'
[System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output ''
Write-Output "Escrito $out ($([math]::Round((Get-Item $out).Length/1KB,1)) KB)"
Write-Output 'LECCIONARIO LISTO'
