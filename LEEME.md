# Biblia en Voz

App de Android que **lee la Biblia en voz alta**, sin conexión a internet.

- **Dos textos**, ambos de dominio público y empaquetados dentro de la app:
  - **Reina-Valera 1909** — 66 libros, 1 189 capítulos, 31 084 versículos.
  - **Santa Biblia Libre** — 73 libros (canon católico, con los deuterocanónicos),
    1 328 capítulos, 35 423 versículos.
- **Lecturas de la misa** — las lecturas de cada día en el orden de la Iglesia:
  primera lectura, salmo responsorial, segunda lectura y Evangelio.
- **Voz IA (desde la 2.1):** la voz humana «Hilary narrador» de Fish Audio, que lee como
  un audiolibro, con emociones donde el texto las pide. Está **grabada en el teléfono**:
  se genera una sola vez en la PC y se copia por cable. La app no tiene permiso de
  internet.
- **Voz del teléfono:** el motor de texto a voz de Android, para lo que no tenga voz IA.

---

## Instalar el APK en el teléfono

El archivo es **`BibliaEnVoz-2.1.apk`** (14 MB). Se instala encima de la 1.3 sin perder
la posición ni los ajustes. (La 2.0 pedía la voz por internet: quedó obsoleta.)

1. Pasa el archivo al teléfono: por cable USB, Google Drive, WhatsApp a ti mismo,
   o el método que prefieras.
2. Ábrelo desde el teléfono (app **Archivos** → Descargas → tocar el APK).
3. Android avisará de que la app viene de un «origen desconocido». Toca
   **Ajustes** → activa **Permitir desde esta fuente** → **Atrás** → **Instalar**.
   Esto es normal: solo significa que la app no viene de Google Play.
4. Si aparece Play Protect diciendo que no reconoce la app, toca
   **Instalar de todos modos**.

Requisitos: Android 7.0 o superior.

### Si no se oye nada

La app usa la voz del sistema. Si tu teléfono no tiene voz en español instalada,
la propia app te lo dirá y te ofrecerá un botón **«Arreglar»** que abre la pantalla
correcta de Android. También puedes ir a mano a:

> Ajustes de Android → Accesibilidad → Texto a voz
> (en algunos teléfonos: Ajustes → Administración general → Texto a voz)

Ahí elige el motor **Google Text-to-Speech**, idioma **Español**, y descarga la voz.
Si no tienes el motor de Google, instálalo desde Play Store (*Speech Recognition
and Synthesis from Google*).

---

## Cómo se usa

| Pantalla | Qué hace |
|---|---|
| **Inicio** | Tarjeta «Continuar escuchando» con el último versículo donde te quedaste, buscador de libros, y las pestañas Antiguo / Nuevo Testamento. |
| **Capítulos** | Cuadrícula con todos los capítulos del libro. El capítulo que está sonando aparece resaltado. |
| **Lector** | El texto del capítulo. El versículo que se está leyendo se resalta y la pantalla lo sigue sola. Toca cualquier versículo para que empiece a leer desde ahí. |

**Barra de reproducción** (abajo, siempre visible):

- ⏮ / ⏭ capítulo anterior y siguiente
- ⏪ / ⏩ versículo anterior y siguiente
- ▶ / ⏸ escuchar y pausar
- **Velocidad**: de 0,75× a 2×
- **Temporizador**: apaga la voz sola a los 10, 15, 30, 45, 60 o 90 minutos
  (pensado para escuchar antes de dormir)

**Sigue leyendo con la pantalla apagada** y con la app cerrada. Los controles
aparecen en la barra de notificaciones y en la pantalla de bloqueo, y los
botones de los audífonos también funcionan. Si entra una llamada, se pausa sola
y sigue al colgar.

**Ajustes** (icono de engrane): velocidad, tono de voz, anunciar el capítulo,
leer el número de versículo, continuar solo al capítulo siguiente, tamaño de
letra, mantener pantalla encendida y tema claro / oscuro / automático.

La app recuerda siempre dónde te quedaste.

### Voz IA (grabada, sin internet)

**Ajustes → Voz IA.** Un interruptor para usarla y lo que hay instalado en el teléfono.
Donde hay audio de voz IA, suena esa voz; lo que aún no lo tiene lo lee la voz del
teléfono, sin cortes.

La voz es **Hilary narrador** (de la biblioteca de fish.audio), elegida escuchando cuatro
narradores masculinos en español latino leyendo Juan 11. El audio se hace **una sola vez,
en la PC**, y luego se copia al teléfono por cable:

1. **Generar** (PC, necesita internet y la clave de Fish Audio en
   `E:\PG\BibliaVoz-IA\clave-fish.txt`): doble clic en **`generar-audio.bat`**. Genera
   las lecturas de la misa de las próximas dos semanas, luego la Reina-Valera entera y
   luego el resto de lecturas: **6 159 tramos, unas 105 horas de audio, ~3 GB en MP3 de
   64 kbps**, en unas 16 horas. Se puede cerrar y volver a lanzar: sigue donde se quedó.
   Mientras trabaja pide a Windows que no suspenda la PC, y cada hora copia al teléfono
   lo ya hecho si está conectado. Usa el modelo gratuito *S2.1 Pro Free* (Fish Audio
   anunció que lo ofrece así hasta el 30 de noviembre de 2026).
   Desde la consola: `generar-audio.ps1 --solo "Juan 11"` genera solo eso, `--contar`
   dice cuánto falta, `--limpiar` borra audio que ya no corresponde a nada.
2. **Copiar al teléfono** (conectado por USB con la depuración USB activada): doble clic
   en **`instalar-audio.bat`**. Copia solo lo nuevo a
   `Android/data/com.bibliavoz.app/files/voz-ia` y borra del teléfono lo que ya no se usa.
   La app usa cada capítulo en cuanto tiene todos sus archivos. Si se desinstala la app,
   Android borra esa carpeta y hay que volver a copiarla.

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
| oró, se postró, clamó a Jehová | reverente | `[reverent tone]` |
| una voz de los cielos que decía | con eco | `[echo]` |
| dijo Dios, así dice Jehová | solemne | `[solemn tone]` |
| (después del anuncio del capítulo) | pausa | `[pause]` |

Las etiquetas van en inglés porque así las aprendió el modelo S2: son las de su
biblioteca de emociones. El libro de Lamentaciones se lee siempre con tristeza.

**Cómo está grabado.** Cada capítulo se parte en tramos de alrededor de un minuto,
cortados en final de frase y sin colas sueltas. Cada tramo es una sola toma de la voz: con
tramos largos hay pocas costuras y la lectura fluye como un audiolibro. Un
`manifest.json` dice en qué punto de cada tramo empieza cada versículo (estimado por
letras, con un margen de un segundo): así la pantalla resalta el que suena y se puede
empezar desde cualquiera. La velocidad (0,75× a 2×) funciona igual que con la otra voz.

**Si falla.** Si un archivo está dañado o a medio copiar, la lectura **sigue con la voz del
teléfono desde el mismo versículo** y un aviso lo explica. Vuelve a intentarse al pulsar ▶.

**Privacidad.** El teléfono no se conecta a nada. Solo la PC, al generar, envía a
api.fish.audio el texto bíblico de cada tramo con tu clave; después puedes borrar el
archivo de la clave y revocarla en fish.audio.

**Licencia.** Built with Fish Audio. El audio se generó con el servicio de Fish Audio. El
modelo S2 Pro descargado en `E:\PG\mf` está bajo la *Fish Audio Research License* (uso
personal y no comercial) y no se usa: esta PC no tiene tarjeta gráfica para moverlo.

### Elegir la voz del teléfono

**Ajustes → Voz del teléfono.** Lista todas las voces en español que tiene el teléfono,
ordenadas por calidad, con un botón ▶ en cada una para oírla antes de decidir.
También deja cambiar de motor de voz y activar las voces «de red», que suenan
mejor pero necesitan conexión.

Android **no expone el sexo de una voz** por ninguna API, y los nombres técnicos
(`es-es-x-eed-local`) no lo dicen de forma fiable. Por eso la app no etiqueta
ninguna voz como masculina: se eligen escuchándolas.

La app **lee tramos de varios versículos de corrido**, no versículo a versículo.
Esa era la causa principal de que sonara entrecortado: los versículos parten las
frases por la mitad, y parar en cada uno rompe la entonación del motor.

### Lecturas de la misa

Desde la tarjeta de la pantalla de inicio. Muestra el día litúrgico, las lecturas
con su cita y su texto, y las lee en orden anunciando cada una. Se puede ver
cualquier otro día con las flechas.

**Cómo funciona:** el calendario litúrgico se **calcula** (cómputo de la Pascua,
temporadas, ciclos A/B/C y I/II), así que no caduca nunca. Las citas están
empaquetadas indexadas por *clave litúrgica*, no por fecha, por la misma razón.
Comprobado: **97.7% de los días de 2028 a 2032 encuentran sus lecturas**, aunque
los datos de origen terminen en 2027.

**Limitaciones, dichas claramente:**

- Está basado en el **leccionario romano general**. Los domingos y las ferias
  coinciden con México; algunas memorias del **santoral propio mexicano**
  (Guadalupe, Juan Diego) pueden variar.
- El texto es la Santa Biblia Libre, **no la traducción que se proclama en misa**,
  que tiene derechos reservados y no se puede empaquetar.
- El **salmo responsorial se lee completo** en vez del tramo citado. La numeración
  de versículos de los salmos no coincide entre el leccionario y esta traducción
  (el desfase depende del título de cada salmo: 0, 1 o 2 versículos), así que leer
  un tramo citado sonaría desplazado sin que se notara. Leerlo entero nunca miente.
- Joel y Jonás llevan una corrección de versificación explícita, comprobada contra
  el texto real.

---

## Volver a compilar

Todo lo necesario está en este equipo, en `E:\PG\.toolchain\`
(JDK 17, Android SDK 35 y Gradle 8.11.1; nada de esto se instaló en Windows,
vive solo en esa carpeta).

```powershell
powershell -ExecutionPolicy Bypass -File E:\PG\BibliaVoz\compilar.ps1 assembleRelease
```

El APK queda en `app\build\outputs\apk\release\app-release.apk`. Las pruebas
(`compilar.ps1 testReleaseUnitTest`) incluyen pasar el director de la voz IA por
las dos Biblias completas: garantizan que al modelo nunca le llega una etiqueta
inventada, una cifra, una palabra en mayúsculas ni la ortografía de 1909.

También puedes abrir la carpeta `E:\PG\BibliaVoz` directamente en Android Studio
y darle a **Run**.

### Firma

`bibliavoz-release.jks` + `keystore.properties` son la llave con la que se firma
la app. **Consérvalos.** Si se pierden, Android considerará cualquier versión
futura como una app distinta y habría que desinstalar la anterior antes de
actualizar. Son de uso personal: no sirven para publicar en Google Play con
otra identidad.

---

## Cómo está hecho

Kotlin + Jetpack Compose (Material 3), sin librerías de terceros más allá de AndroidX.

```
app/src/main/
├── assets/bible/          index.json + 1.json … 66.json (el texto)
├── java/com/bibliavoz/app/
│   ├── data/              BibleRepository (lee los assets), Prefs, modelos
│   ├── player/            PlaybackService  ← el motor de lectura
│   │                      AudioLocal / HablanteIa ← la voz IA grabada
│   │                      PlayerState / PlayerBus (estado compartido)
│   ├── voz/               Director (limpieza + emociones), Segmentador (tramos),
│   │                      Anuncios, Tramos, ClaveLectura, VocesIa — código puro que
│   │                      también compila el generador de la PC
│   ├── ui/                pantallas Compose + barra de reproducción
│   └── MainActivity.kt
└── res/                   iconos, colores, textos, temas

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

Basta con sustituir los archivos de `assets/bible/` manteniendo el formato:

```json
// index.json
{"translation":"…","abbreviation":"…","language":"es",
 "books":[{"n":1,"name":"Génesis","abbr":"Gn","t":"AT","chapters":50}, …]}

// 1.json  (un archivo por libro, numerados del 1 al 66)
{"n":1,"name":"Génesis","abbr":"Gn","chapters":[["versículo 1","versículo 2", …], …]}
```

Ten en cuenta que las traducciones modernas (RV1960, NVI, NTV…) tienen derechos
reservados y no se pueden distribuir dentro de una app sin licencia del editor.
La RV1909 se usó justamente por ser de dominio público.
