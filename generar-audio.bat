@echo off
rem Doble clic: sigue generando el audio de la voz IA donde se quedo.
rem Cada hora copia al telefono lo generado, si esta conectado por cable.
powershell -ExecutionPolicy Bypass -File "%~dp0generar-audio.ps1" --limpiar --instalar %*
pause
