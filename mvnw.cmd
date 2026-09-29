@REM Maven Wrapper bootstrap for Windows
@echo off
setlocal
set "PROJECT_DIR=%~dp0"
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROJECT_DIR%.mvn\wrapper\maven-wrapper.properties"') do set "DIST_URL=%%B"
for %%F in ("%DIST_URL%") do set "DIST_FILE=%%~nxF"
set "MAVEN_VERSION=%DIST_FILE:apache-maven-=%"
set "MAVEN_VERSION=%MAVEN_VERSION:-bin.zip=%"
if defined MAVEN_USER_HOME (set "M2_HOME=%MAVEN_USER_HOME%") else (set "M2_HOME=%USERPROFILE%\.m2")
set "MAVEN_HOME=%M2_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%MAVEN_HOME%\maven.zip'; Expand-Archive -Force '%MAVEN_HOME%\maven.zip' '%MAVEN_HOME%\tmp'; Move-Item '%MAVEN_HOME%\tmp\apache-maven-*\*' '%MAVEN_HOME%'; Remove-Item -Recurse -Force '%MAVEN_HOME%\tmp','%MAVEN_HOME%\maven.zip'"
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*

