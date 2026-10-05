@echo off
rem Doble clic: sigue generando el audio de la voz IA donde se quedo.
rem Desde la 2.2 el audio va dentro del APK: al terminar, vuelve a compilar con
rem compilar.ps1 (o copialo al telefono por cable con instalar-audio.bat).
rem Sin --limpiar: borraria el audio que aun no se haya regrabado. Limpia a mano,
rem con todo ya generado: generar-audio.ps1 --limpiar
powershell -ExecutionPolicy Bypass -File "%~dp0generar-audio.ps1" %*
pause
