@echo off
rem Doble clic: copia a cada telefono conectado por cable el audio de la voz IA
rem generado, y al final comprueba que haya llegado todo.
powershell -ExecutionPolicy Bypass -File "%~dp0instalar-audio.ps1"
pause
