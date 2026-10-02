@echo off
setlocal
python "%~dp0verify-cert-pins.py" %*
exit /b %ERRORLEVEL%
