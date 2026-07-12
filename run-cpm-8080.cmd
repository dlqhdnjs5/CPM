@echo off
cd /d C:\cpm
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat bootRun --args="--server.port=8080" > build\cpm-app-8080.log 2>&1
