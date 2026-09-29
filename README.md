# Biblia en Voz

App de Android que **lee la Biblia en voz alta**, sin conexión a internet: la
Reina-Valera 1909 completa y las **lecturas de la misa** de cada día, con una voz
humana grabada con IA («Hilary narrador», de Fish Audio) que lee como un audiolibro.
Lo que no tiene voz IA lo lee la voz del teléfono.

- Kotlin + Jetpack Compose, Android 7.0 o superior, sin permiso de internet.
- Textos de dominio público: Reina-Valera 1909 y Santa Biblia Libre (canon católico,
  para las lecturas de la misa).
- La documentación completa, en español, está en **[LEEME.md](LEEME.md)**: cómo se usa,
  cómo se genera la voz IA, cómo se compila y cómo está hecho.

## Versiones

Cada versión está en [Releases](../../releases), con su APK cuando cabe en GitHub.

| Versión | Fecha | Qué trajo |
|---|---|---|
| 1.0 – 1.2 | 12–20 sep 2026 | Lectura de la Reina-Valera 1909 con la voz del teléfono |
| 1.3 | 20 sep 2026 | Lecturas de la misa y la Santa Biblia Libre |
| 2.0 | 28 sep 2026 | Voz IA en vivo por internet (obsoleta) |
| 2.1 | 28 sep 2026 | Voz IA grabada, sin internet, copiada por cable |
| 2.2 | 29 sep 2026 | La voz IA va dentro del APK; lecturas de la misa corregidas y muchos arreglos |

El APK de la 2.2 pesa unos 2,8 GB porque lleva dentro toda la voz IA, y GitHub no
admite archivos de más de 2 GB: ese APK se reparte aparte. El código de las versiones
anteriores a la 2.1 no se conservó; de ellas solo quedan los APK.

La voz IA (los MP3) no está en este repositorio: se genera con `generar-audio.bat`
(necesita una clave propia de Fish Audio) y la compilación la mete en el APK.
