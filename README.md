# Biblia en Voz

App de Android que **lee la Biblia en voz alta**, sin conexión a internet: la
Biblia completa y las **lecturas de la misa** de cada día (calendario de México,
CEM), con una voz humana grabada con IA («Hilary narrador», de Fish Audio) que lee
como un audiolibro.

- Kotlin + Jetpack Compose, Android 7.0 o superior, sin permiso de internet.
- Texto bíblico: Santa Biblia Libre (eBible.org, «spabll»), de dominio público.
  Adaptación para México: el nombre divino «Yahvé» se lee «el Señor».
- Icono «Santa Biblia» generado con Meta AI.
- La documentación completa, en español, está en **[LEEME.md](LEEME.md)**: cómo se usa,
  cómo se genera la voz IA, cómo se compila y cómo está hecho.
- Para descargar los audios se crea un enlace de Google Drive para que lo puedan descargar
  sin tener que generarlos por su cuenta

## Versiones

Cada versión está en [Releases](../../releases), con su APK cuando cabe en GitHub.

| Versión | Fecha | Qué trajo |
|---|---|---|
| 1.0 – 1.2 | 12–20 sep 2026 | Lectura de la Reina-Valera 1909 con la voz del teléfono |
| 1.3 | 20 sep 2026 | Lecturas de la misa y la Santa Biblia Libre |
| 2.0 | 28 sep 2026 | Voz IA en vivo por internet (obsoleta) |
| 2.1 | 28 sep 2026 | Voz IA grabada, sin internet, copiada por cable |
| 2.2 | 29 sep 2026 | La voz IA va dentro del APK; lecturas de la misa corregidas y muchos arreglos |
| 3.0 | 5 oct 2026 | Edición de México: Santa Biblia Libre con «el Señor», leccionario de la CEM, salmo responsorial con sus versículos, solo la voz IA (regrabada entera), arreglo del corte de las últimas palabras e icono nuevo |

Los APK de la 2.2 (unos 2,8 GB) y de la 3.0 (unos 3,2 GB) llevan dentro toda la voz IA,
y GitHub no admite archivos de más de 2 GB: esos APK se reparten aparte. El código de
las versiones anteriores a la 2.1 no se conservó; de ellas solo quedan los APK.

La voz IA (los MP3) no está en este repositorio: se genera con `generar-audio.bat`
(necesita una clave propia de Fish Audio) y la compilación la mete en el APK.
