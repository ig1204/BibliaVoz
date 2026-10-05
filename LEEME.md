# Biblia en Voz

App de Android que **lee la Biblia en voz alta**, sin conexión a internet.

- **El texto**, de dominio público y empaquetado dentro de la app:
  - **Santa Biblia Libre** (eBible.org, «spabll») — 73 libros (canon católico, con los
    deuterocanónicos), una sola Biblia para leer por capítulos y para las lecturas de la
    misa. Para México se adapta: el nombre divino «Yahvé» se lee «el Señor».
- **Lecturas de la misa** — las lecturas de cada día según el calendario de México (CEM):
  primera lectura, salmo responsorial, segunda lectura y Evangelio.
- **Voz IA:** la voz humana «Hilary narrador» de Fish Audio, que lee como un audiolibro,
  con emociones donde el texto las pide. **Va dentro de la app**: se genera una sola vez en
  la PC y viaja en el APK, así que cualquier teléfono la tiene al instalarla. La app no
  tiene permiso de internet. Es la única voz; si un pasaje no tuviera audio, se avisa.

---

## Novedades de la 3.0 (México)

- **Una sola Biblia, la Santa Biblia Libre**, para los capítulos y para la misa. El nombre
  divino «Yahvé» se lee «el Señor», como en el leccionario, y se quitó la marca musical
  «Selah» de los Salmos para que la voz no la pronuncie.
- **Lecturas de la misa según el calendario de México (CEM):** el Corpus Christi el jueves,
  Nuestra Señora de Guadalupe el 12 de diciembre con sus lecturas propias, la Santa Cruz y
  Felipe y Santiago en mayo, Jesucristo Sumo y Eterno Sacerdote, San Felipe de Jesús y Santa
  Rosa de Lima, y los salmos con la numeración del misal mexicano (Vulgata).
- **Solo la voz IA.** Se quitó la voz del teléfono: toda la Biblia y todas las lecturas van
  grabadas en el APK. Si un pasaje no tuviera audio, se avisa y se pausa, en vez de leerlo
  con otra voz.
- **El salmo responsorial se anuncia y se lee como en misa:** con su número del misal
  mexicano y sus versículos («Salmo setenta y nueve, versículos nueve, del doce al
  dieciséis…»), y solo esos versículos, no el salmo entero.
- **Salmos sin títulos:** ya no se leen inscripciones como «Para el director musical…», y
  donde se habla a Dios se dice «Señor, …» (vocativo) y no «El Señor, …».
- **Texto completado:** 1 Samuel 11,15, que la Santa Biblia Libre trae en blanco, se
  completa a partir de la Reina-Valera 1909.
- **Guadalupe** se celebra el 12 de diciembre aunque caiga en domingo de Adviento (con la
  2ª lectura de ese domingo, como dispone la CEM), y entre semana las fiestas del Señor
  llevan 1ª lectura, salmo y Evangelio.
- **Toda la voz IA se regrabó** con el texto nuevo (7 234 tramos).
- **Final de cada lectura más limpio:** se corrigió un corte de las últimas palabras que
  aparecía en los tramos largos (lo causaba la reproducción de Android, no la grabación).
- **Icono nuevo «Santa Biblia»**, con Jesús, la paloma y una corona de laurel.

---

## Novedades de la 2.2

Se instala encima de la 2.1 sin perder la posición ni los ajustes.

- **La voz IA viene dentro del APK.** Ya no hace falta copiar el audio por cable ni
  activar la depuración USB: basta con instalar la app. A cambio, el APK pesa unos 2,8 GB.
- **La lectura ya no se queda muda ni se mezcla.** Pausar y seguir desde la notificación,
  la pantalla de bloqueo, los audífonos o después de una llamada continúa donde iba.
  Después de oír las lecturas de la misa, elegir un capítulo lee ese capítulo, y oír la
  misa ya no mueve el versículo guardado de la Biblia ni «Continuar escuchando».
- **Audífonos:** al desconectarlos (cable o Bluetooth) la lectura se pausa en vez de
  seguir por el altavoz.
- **Pantallas:** el botón Atrás ya no hace retroceder la posición guardada, la barra de
  reproducción no se descuadra con letra grande, y el resaltado sigue al versículo que
  suena también al saltar con ⏪ ⏩.
- **Anuncio de cada lectura, como en misa:** antes de leerla, la voz dice el libro, el
  capítulo y los versículos: «Primera lectura del libro del profeta Daniel, capítulo
  siete, versículos del nueve al catorce», «Lectura del santo Evangelio según san
  Marcos, capítulo…». El salmo se nombra («Salmo responsorial. Salmo ochenta y cinco»)
  porque se lee completo. Lo dicen igual la voz IA y la voz del teléfono. (Desde la 3.0
  se leen y se anuncian solo los versículos citados, y ya no hay voz del teléfono.)
- **Cada lectura con su botón:** en la pantalla de la misa, el botón de la lectura que
  suena muestra ⏸ (pausar) y, en pausa, ▶ para seguir donde iba, igual que el de abajo.
- **Lecturas de la misa corregidas:** fiestas que se colaban en domingos, Adviento o
  Jueves Santo; días que salían sin lecturas (como Navidad); citas mal entendidas que
  hacían leer un capítulo entero, o el salmo dos veces; algún salmo equivocado, y las
  lecturas de los libros de un solo capítulo (Filemón, Judas, Abdías…).
- **Audio de la voz IA:** las lecturas corregidas se volvieron a grabar con la voz IA, y
  también un tramo de Job 41 que terminaba cortado. **Ajustes → Voz IA** cuenta solo lo
  que tiene todos sus archivos y avisa si falta algo.
- **Copiar la voz IA por cable** (`instalar-audio.bat`, ahora opcional): funciona con uno
  o varios teléfonos, explica en palabras claras qué falta (aceptar el aviso de
  depuración, instalar la app, liberar espacio) y al final comprueba que llegó todo. Solo
  dice «Listo» si es así.
- **Copia de seguridad de Google:** vuelve a guardar la posición y los ajustes; el audio
  de la voz IA queda fuera (no cabría).
- En Android 7 a 10, los archivos de la voz IA ya no aparecen como canciones en las apps
  de música.

---

## Instalar el APK en el teléfono

El archivo es **`E:\PG\BibliaEnVoz-3.0.apk`** (unos 3,2 GB, porque lleva dentro toda la
voz IA). Es el mismo para cualquier teléfono. Se instala encima de cualquier versión
anterior sin perder la posición ni los ajustes. (La 2.0 pedía la voz por internet: quedó
obsoleta.)

**Espacio:** mientras se instala, el teléfono necesita unos **7 GB libres** (el APK que
copias más la app instalada); después puedes borrar el APK de Descargas y la app ocupa
unos 3,2 GB.

1. Pasa el archivo al teléfono: por **cable USB** (conecta el teléfono, elige
   «Transferencia de archivos» y copia el APK a la carpeta *Download*), por Google Drive
   o con una memoria USB. **WhatsApp no sirve**: no deja mandar archivos de más de 2 GB.
2. Ábrelo desde el teléfono (app **Archivos** → Descargas → tocar el APK).
3. Android avisará de que la app viene de un «origen desconocido». Toca
   **Ajustes** → activa **Permitir desde esta fuente** → **Atrás** → **Instalar**.
   Esto es normal: solo significa que la app no viene de Google Play.
4. Si aparece Play Protect diciendo que no reconoce la app, toca
   **Instalar de todos modos**.

Requisitos: Android 7.0 o superior. Un APK de este tamaño se probó en un Pixel 9 Pro
con Android 17; si en un teléfono muy antiguo la instalación fallara, dímelo.

> **La voz IA va dentro del APK.** No hay que copiar nada más: al instalarlo, Ajustes →
> Voz IA dice «Incluida en la app».

### Si se corta con la pantalla apagada (Xiaomi, Samsung, Huawei, Oppo, Realme)

Algunas marcas cierran las apps en segundo plano para ahorrar batería, o al tocar
«Limpiar todo» en Recientes. Si la lectura se corta sola con la pantalla apagada, quita
el ahorro de batería para Biblia en Voz:

- **Xiaomi / Redmi / POCO:** Ajustes → Aplicaciones → Biblia en Voz → Ahorro de batería →
  **Sin restricciones**. Y en Recientes, deja la app fijada con el candado.
- **Samsung:** Ajustes → Aplicaciones → Biblia en Voz → Batería → **Sin restricciones**
  (y que no esté en «Aplicaciones en suspensión»).
- **Huawei:** Ajustes → Batería → Inicio de aplicaciones → Biblia en Voz → gestionar a
  mano y activar **Ejecución en segundo plano**.
- **Oppo / Realme:** Ajustes → Batería → Biblia en Voz → **Permitir actividad en segundo
  plano**. Y en Recientes, fíjala con el candado.

Los nombres cambian un poco según el modelo. Si aun así se detiene, pulsa ▶: sigue desde
donde te quedaste.

---

## Cómo se usa

| Pantalla | Qué hace |
|---|---|
| **Inicio** | Tarjeta «Continuar escuchando» con el último versículo donde te quedaste, tarjeta de las lecturas de la misa, buscador de libros, y las pestañas Antiguo / Nuevo Testamento. |
| **Capítulos** | Cuadrícula con todos los capítulos del libro. El capítulo que está sonando aparece resaltado. |
| **Lector** | El texto del capítulo. El versículo que se está leyendo se resalta y la pantalla lo sigue sola. Toca cualquier versículo para que empiece a leer desde ahí. |

**Barra de reproducción** (abajo, siempre visible):

- ⏮ / ⏭ capítulo anterior y siguiente
- ⏪ / ⏩ versículo anterior y siguiente
- ▶ / ⏸ escuchar y pausar
- **Velocidad**: de 0,75× a 2× (en Ajustes, con más detalle, de 0,5× a 2,5×)
- **Temporizador**: apaga la voz sola a los 10, 15, 30, 45, 60 o 90 minutos
  (pensado para escuchar antes de dormir)

**Sigue leyendo con la pantalla apagada** y con la app cerrada (en algunas marcas hay que
quitarle el ahorro de batería: mira [arriba](#si-se-corta-con-la-pantalla-apagada-xiaomi-samsung-huawei-oppo-realme)).
Los controles aparecen en la barra de notificaciones y en la pantalla de bloqueo, y los
botones de los audífonos también funcionan. Si entra una llamada, se pausa sola y sigue
al colgar.

**Ajustes** (icono de engrane): Voz IA, velocidad, continuar solo al capítulo
siguiente, tamaño de letra, mantener la pantalla encendida y tema Sistema / Claro /
Oscuro.

La app recuerda siempre dónde te quedaste.

### Voz IA (grabada, sin internet)

**Ajustes → Voz IA.** Muestra lo que hay en el teléfono («Incluida en la app»). Donde
hay audio de voz IA, suena esa voz; si un pasaje no lo tuviera, se avisa y se pausa.

La voz es **Hilary narrador** (de la biblioteca de fish.audio), elegida escuchando cuatro
narradores masculinos en español latino leyendo Juan 11. El audio se hace **una sola vez,
en la PC**, y luego se mete en el APK al compilar:

1. **Generar** (PC, necesita internet y la clave de Fish Audio en
   `E:\PG\BibliaVoz-IA\clave-fish.txt`): doble clic en **`generar-audio.bat`**. Genera
   las lecturas de la misa de las próximas dos semanas, luego la Biblia entera y
   luego el resto de lecturas: **unos 7 234 tramos, unas 118 horas de audio, ~3,2 GB en
   MP3 de 64 kbps**; la regrabación completa tardó unas 18-20 horas. Se puede cerrar y volver a
   lanzar: sigue donde se quedó. Si el texto de algo cambia (como las lecturas corregidas
   en la 2.2), solo se vuelve a grabar eso, y lo que ya no se usa se borra.
   Mientras trabaja pide a Windows que no suspenda la PC. Usa el modelo gratuito
   *S2.1 Pro Free* (Fish Audio anunció que lo ofrece así hasta el 30 de noviembre de 2026).
   Desde la consola: `generar-audio.ps1 --solo "Juan 11"` genera solo eso, `--contar`
   dice cuánto falta, `--limpiar` borra audio que ya no corresponde a nada.
2. **Meterlo en la app:** `compilar.ps1 assembleRelease` toma el audio de
   `E:\PG\BibliaVoz-IA\audio-mx` (la carpeta de la 3.0) y lo pone dentro del APK (ver
   [Volver a compilar](#volver-a-compilar)). `generar-audio.bat` ya no borra nada: el
   audio viejo solo se limpia a mano, con todo regrabado (`generar-audio.ps1 --limpiar`).
3. **Opcional, copiar por cable** (teléfono conectado por USB con la depuración USB
   activada): doble clic en **`instalar-audio.bat`**. Sirve para estrenar audio nuevo en
   un teléfono sin reinstalar la app. Copia solo lo nuevo a
   `Android/data/com.bibliavoz.app/files/voz-ia`, borra del teléfono lo que ya no se usa
   y al final cuenta los archivos del teléfono y los compara con los de la PC. Solo dice
   **«Listo»** si llegó todo; si no, dice qué falta y basta con volver a lanzarlo (solo
   copia lo que falta). Con varios teléfonos conectados, copia a cada uno. Si el
   teléfono tiene las dos copias (la de la app y la de la carpeta), la app usa la más
   reciente de cada capítulo.

### Llevar la voz IA a otro teléfono

**Basta con instalar el APK:** la voz IA va dentro. Lo que sigue solo hace falta para
copiar audio nuevo por cable sin reinstalar (paso 3 de arriba). No se puede copiar a
mano: desde Android 11, ni el explorador del teléfono ni el de Windows dejan meter
archivos en la carpeta `Android/data` de otra app.

1. **Instala la app** en ese teléfono (ver [Instalar el APK](#instalar-el-apk-en-el-teléfono))
   y **ábrela una vez**.
2. **Comprueba el espacio:** hacen falta unos **3,5 GB libres** (Ajustes → Almacenamiento).
3. **Activa las Opciones de desarrollador:** Ajustes → Acerca del teléfono → toca **7 veces
   seguidas** «**Número de compilación**» hasta que diga que ya eres desarrollador (puede
   pedir el PIN del teléfono). En Samsung está en Acerca del teléfono → Información de
   software; en Xiaomi se toca «Versión de MIUI» o «Versión del SO».
4. **Activa la Depuración USB:** Ajustes → Sistema → Opciones de desarrollador →
   **Depuración USB** (en Samsung y Xiaomi las Opciones de desarrollador aparecen al final
   de Ajustes o en «Ajustes adicionales»).
5. **Conecta el teléfono a la PC** con el cable y desbloquéalo. Aparece el aviso
   **«¿Permitir depuración USB?»**: marca «Permitir siempre desde esta computadora» y toca
   **Permitir**. Si no aparece, desconecta y vuelve a conectar el cable, o elige
   «Transferencia de archivos» en el aviso de USB del teléfono.
6. En la PC, doble clic en **`instalar-audio.bat`**. La primera vez tarda unos minutos.
   Espera a que diga **«Listo»**.

Si dice que no hay ningún teléfono aunque esté conectado y con la depuración activada,
puede faltar el controlador USB del fabricante en Windows (Samsung, por ejemplo, lo da en
su web como *Samsung Android USB Driver*). Cuando termine, puedes volver a desactivar la
Depuración USB: el audio se queda en el teléfono.

La copia de seguridad de Google no lleva el audio copiado por cable (guarda la posición y
los ajustes); el de la app vuelve al reinstalarla.

### Cómo lee la voz IA

Por qué MP3 y no Opus: Fish Audio ignora el bitrate que se le pide en Opus y lo entrega a
~270 kbps (13 GB para todo); en MP3 sí respeta los 64 kbps.

**Leer como novela.** El generador usa un «director» que pone etiquetas de emoción donde
el propio texto las pide. Es conservador a propósito: unas 7 por cada 100 versículos.

| Si el texto dice… | La voz lo lee… | Etiqueta |
|---|---|---|
| lloró, llorando, lamentación, luto | triste | `[sad]` |
| clamó a gran voz, dio voces, gritó | gritando | `[shouting]` |
| se enojó, se airó, se encendió su furor | enojado | `[angry]` |
| tuvo miedo, temieron, espantados | con miedo | `[fearful]` |
| en secreto, en voz baja, al oído | en susurro | `[whisper]` |
| se burlaban, escarnecían | burlón | `[mocking tone]` |
| se rió (Sara) | con risita | `[chuckling]` |
| se maravillaron (en gran manera) | sorprendido (conmocionado) | `[surprised]` / `[shocked]` |
| gozo, alegría, «Alabad», «Aleluya» | con deleite | `[delight]` |
| le rogaba, suplicó | suplicante | `[pleading tone]` |
| fue movido a misericordia, se compadeció | con ternura | `[tender tone]` |
| gimió, suspiró, «¡Ay de mí!» | con un suspiro | `[sigh]` |
| lo reprendió | firme | `[firm tone]` |
| oró, se postró, clamó al Señor | reverente | `[reverent tone]` |
| una voz de los cielos que decía | con eco | `[echo]` |
| dijo Dios, así dice el Señor | solemne | `[solemn tone]` |
| (después del anuncio del capítulo) | pausa | `[pause]` |

Las etiquetas van en inglés porque así las aprendió el modelo S2: son las de su
biblioteca de emociones. El libro de Lamentaciones se lee siempre con tristeza.

**Cómo está grabado.** Cada capítulo se parte en tramos de alrededor de un minuto,
cortados en final de frase y sin colas sueltas. Cada tramo es una sola toma de la voz: con
tramos largos hay pocas costuras y la lectura fluye como un audiolibro. Un
`manifest.json` dice en qué punto de cada tramo empieza cada versículo (estimado por
letras, con un margen de un segundo): así la pantalla resalta el que suena y se puede
empezar desde cualquiera.

**Si falla.** Si un archivo está dañado o a medio copiar, se reintenta una vez; si vuelve
a fallar, se salta ese tramo con un aviso, y tras tres fallos seguidos la lectura se pausa.
Vuelve a intentarse al pulsar ▶.

**Privacidad.** El teléfono no se conecta a nada. Solo la PC, al generar, envía a
api.fish.audio el texto bíblico de cada tramo con tu clave; después puedes borrar el
archivo de la clave y revocarla en fish.audio.

**Licencia.** Built with Fish Audio. El audio se generó con el servicio de Fish Audio. El
modelo S2 Pro descargado en `E:\PG\mf` está bajo la *Fish Audio Research License* (uso
personal y no comercial) y no se usa: esta PC no tiene tarjeta gráfica para moverlo.
Texto bíblico: Santa Biblia Libre (eBible.org, «spabll»), de dominio público, adaptada
para México («el Señor», sin «Selah» ni títulos de salmos); 1 Samuel 11,15 es una
adaptación libre de la Reina-Valera 1909, también de dominio público. Icono «Santa
Biblia» generado con Meta AI.

### Lecturas de la misa

Desde la tarjeta de la pantalla de inicio. Muestra el día litúrgico, las lecturas
con su cita y su texto, y las lee en orden anunciando cada una. Se puede ver
cualquier otro día con las flechas.

**Cómo funciona:** el calendario litúrgico se **calcula** (cómputo de la Pascua,
temporadas, ciclos A/B/C y I/II), así que no caduca nunca. Las citas están
empaquetadas indexadas por *clave litúrgica*, no por fecha, por la misma razón: aunque
los datos de origen terminen en 2027, **todos los días hasta 2032 tienen sus lecturas**
(comprobado día por día en las pruebas). Las fiestas de fecha fija siguen el orden de
precedencia de la Iglesia: un santo no desplaza a un domingo, a Adviento ni a la Semana
Santa. Una solemnidad que caiga en uno de esos días pasa al siguiente día libre, con dos
excepciones: San José en Semana Santa se adelanta al sábado anterior al Domingo de Ramos,
y la Anunciación en Semana Santa o en la octava de Pascua pasa al lunes siguiente al
II domingo de Pascua. La cabecera nombra la fiesta cuando se lee la de un santo. La
Ascensión se celebra en domingo, como en México, y Guadalupe (12 de diciembre) es
solemnidad. Si algún día no tuviera lecturas guardadas, la pantalla lo dice.

**Limitaciones, dichas claramente:**

- Está basado en el **leccionario de México (CEM)**: los domingos, las ferias y las
  fiestas propias de México (Guadalupe, San Felipe de Jesús, Santa Rosa de Lima,
  Jesucristo Sumo y Eterno Sacerdote, la Santa Cruz el 3 de mayo…) siguen el calendario
  mexicano.
- El texto es la Santa Biblia Libre, **no la traducción que se proclama en misa**,
  que tiene derechos reservados y no se puede empaquetar. Su numeración de versículos
  no siempre coincide con la del leccionario, así que algún tramo puede empezar o
  terminar un versículo antes o después.
- **1 Samuel 11:15**, que la Santa Biblia Libre deja en blanco, se completa con un
  versículo **adaptado de la Reina-Valera 1909** (dominio público), en redacción actual y
  con «el Señor». Lo repone `tools/corregir-biblia-cat.ps1`, así que no se pierde si se
  vuelve a generar la Biblia.
- El **salmo responsorial** se lee por los **versículos citados** en el leccionario, no
  el salmo entero. El leccionario nombra los salmos con la numeración litúrgica, que en
  casi todo el salterio va un número por detrás de la de esta Biblia; además, en el
  leccionario el título de muchos salmos cuenta como versículo 1 (o 1-2), y esta Biblia ya
  no trae los títulos, así que la cuenta se corre 0, 1 o 2. La app convierte cada cita a la
  numeración de esta Biblia para leer exactamente los versículos citados, y lo anuncia con
  el número litúrgico, como se oye en misa.
- Los libros cuya numeración difiere del leccionario llevan una corrección explícita,
  comprobada contra el texto real: Malaquías, Zacarías, Miqueas, Oseas, Éxodo, Isaías,
  Génesis, Samuel, Nahúm, Daniel, Joel, Jonás, 2 Corintios, Romanos 16 y otros. El
  Eclesiástico puede diferir en algún versículo.
- Si Guadalupe cae en domingo de Adviento (2027, 2032), se celebra ese mismo domingo,
  con la 2ª lectura del domingo en lugar de la de Gálatas, como dispone la CEM.
- Si se vuelve a generar la Santa Biblia Libre, hay que repetir **toda** la adaptación y
  en este orden: `tools/convertir-catolica.ps1` (que al final aplica
  `tools/corregir-biblia-cat.ps1`: quita números y letras hebreas sueltos y repone
  1 S 11,15), luego `tools/convertir-senor.ps1 -Aplicar` («Yahvé»→«el Señor», sin
  «Selah»), `tools/corregir-vocativos.ps1 -Aplicar` y `tools/quitar-titulos-salmos.ps1
  -Aplicar` (con `tools/titulos-salmos.csv`). Después, subir `TextosIa.ID` y regrabar lo
  que cambie.

---

## Volver a compilar

Todo lo necesario está en este equipo, en `E:\PG\.toolchain\`
(JDK 17, Android SDK con la plataforma 35 y Gradle 8.11.1; la app se compila con el
plugin de Android 8.7.3 y Kotlin 2.0.21, `compileSdk` y `targetSdk` 35, `minSdk` 24.
Nada de esto se instaló en Windows: vive solo en esa carpeta).

```powershell
powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\compilar.ps1 assembleRelease
```

El APK queda en `app\build\outputs\apk\release\app-release.apk`, y `compilar.ps1` lo copia
además a **`E:\PG\BibliaEnVoz-<versión>.apk`** (hoy `BibliaEnVoz-3.0.apk`), con la versión
que dice `versionName` en `app\build.gradle.kts`. Nunca pisa el APK de otra versión: si
ya existe uno con ese nombre y es de otra compilación, avisa y no lo toca. Al sacar una
versión nueva hay que subir `versionCode` y `versionName`.

**La voz IA dentro del APK.** La versión *release* mete en `assets/voz-ia` los MP3 que
cita el `manifest.json` de `E:\PG\BibliaVoz-IA\audio-mx` (con enlaces duros, sin ocupar
otra copia en el disco), **sin comprimir**, para que la app los reproduzca directamente
desde el APK. Por eso compilar tarda unos 15-20 minutos y el APK pesa unos 3,2 GB. Si
falta el audio, o si el manifiesto no lleva el sello del texto actual (`TextosIa.ID`, hoy
`mx-sbl-4`), la compilación se detiene y lo dice. Para una prueba rápida sin audio:
`compilar.ps1 assembleRelease -PsinVozIa` (ese APK no se copia a `E:\PG`). Otra carpeta
de audio: `-PvozIaDir=<ruta>`.

Las pruebas (`compilar.ps1 testReleaseUnitTest`) incluyen pasar el director de la voz IA
por las dos Biblias completas (la de la app y la RV1909 que se conserva solo para las
pruebas): garantizan que al modelo nunca le llega una etiqueta
inventada, una cifra ni una palabra en mayúsculas.

**Android Studio:** si abres la carpeta `E:\PG\BibliaVoz` y le das a **Run**, instala la
variante *debug*, que es **otra app** (`com.bibliavoz.app.debug`) con el mismo nombre e
icono, firmada con otra llave y **sin voz IA** (`instalar-audio.bat` solo copia a la app
normal). Para probar la de verdad, usa `compilar.ps1` e instala el APK, o elige la
variante *release* en **Build Variants** antes de darle a Run.

### Firma

`bibliavoz-release.jks` + `keystore.properties` son la llave con la que se firma
la app. **Consérvalos.** Si se pierden, Android considerará cualquier versión
futura como una app distinta y habría que desinstalar la anterior antes de
actualizar (y con ella se borraría el audio copiado por cable). Son de uso personal: no sirven
para publicar en Google Play con otra identidad.

---

## Cómo está hecho

Kotlin + Jetpack Compose (Material 3), sin librerías de terceros más allá de AndroidX.

```
app/src/main/
├── assets/bible-cat/      index.json + 1.json … 73.json — la Santa Biblia Libre
│                          (73 libros), la única Biblia que usa la app
├── assets/bible/          la Reina-Valera 1909: solo la usan las pruebas
│   (assets/voz-ia/)       la voz IA: no está aquí, la añade la compilación release
│                          desde E:\PG\BibliaVoz-IA\audio-mx (app/build.gradle.kts)
├── java/com/bibliavoz/app/
│   ├── data/              BibleRepository (lee los assets), Prefs, modelos
│   ├── liturgia/          CalendarioLiturgico, Precedencia, Leccionario, ColaLecturas (la misa)
│   ├── player/            PlaybackService  ← el motor de lectura
│   │                      AudioLocal / HablanteIa ← la voz IA grabada
│   │                      PlayerState / PlayerBus (estado compartido)
│   ├── voz/               Director (limpieza + emociones), Segmentador (tramos),
│   │                      Anuncios, Tramos, ClaveLectura, NumerosEnLetras, VocesIa,
│   │                      TextosIa (el sello del texto que lleva el audio) —
│   │                      código puro que también compila el generador de la PC
│   ├── ui/                pantallas Compose + barra de reproducción
│   └── MainActivity.kt
└── res/                   iconos, colores, textos, temas; xml/ con las reglas de la
                           copia de seguridad (todo menos la voz IA)

generador/                 programa de PC (Kotlin/JVM) que genera la voz IA con
                           Fish Audio: comparte con la app el código de voz/ y
                           el calendario litúrgico, para que el audio case con
                           los versículos exactamente
```

Dos decisiones que vale la pena conocer si algún día se toca el código:

- **La lectura vive en un servicio en primer plano**, no en la pantalla. Es la única
  forma de que la voz siga sonando con el teléfono bloqueado.
- **El texto se carga libro por libro y bajo demanda**, con una caché de 4 libros.
  Cargar la Biblia entera en memoria no aporta nada para leer un capítulo.

### Cambiar de traducción

Basta con sustituir los archivos de `assets/bible-cat/` manteniendo el formato:

```json
// index.json
{"translation":"…","abbreviation":"…","language":"es",
 "books":[{"n":1,"name":"Génesis","abbr":"Gn","t":"AT","chapters":50}, …]}

// 1.json  (un archivo por libro, numerados del 1 al 73)
{"n":1,"name":"Génesis","abbr":"Gn","chapters":[["versículo 1","versículo 2", …], …]}
```

Ten en cuenta que las traducciones modernas (RV1960, NVI, NTV…) tienen derechos
reservados y no se pueden distribuir dentro de una app sin licencia del editor.
La Santa Biblia Libre (spabll) se usó justamente por ser de dominio público; el cambio
para México («Yahvé» se lee «el Señor» y se quitó «Selah» de los Salmos) es una
adaptación de este proyecto, no de la traducción original. Si cambia el texto, la voz IA
de lo cambiado se vuelve a grabar la próxima vez que se lanza `generar-audio.bat`.
