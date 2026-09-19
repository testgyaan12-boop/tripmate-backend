@echo off
set LOG=%~dp0server.log
echo [%DATE% %TIME%] Starting TripMate backend >> "%LOG%"
"C:\Program Files\Java\jdk-18.0.2.1\bin\javaw.exe" -jar "%~dp0target\tripmate-backend-1.0.0.jar" --logging.file.name="%~dp0server.log"
