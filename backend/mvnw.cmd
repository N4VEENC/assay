@echo off
setlocal
set "MAVEN_VERSION=3.9.9"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%-bin\apache-maven-%MAVEN_VERSION%"
set "MAVEN_ARCHIVE=%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Downloading Apache Maven %MAVEN_VERSION%...
  if not exist "%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%-bin" mkdir "%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%-bin"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%MAVEN_ARCHIVE%'; Expand-Archive -LiteralPath '%MAVEN_ARCHIVE%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%-bin' -Force; Remove-Item -LiteralPath '%MAVEN_ARCHIVE%' -Force"
  if errorlevel 1 exit /b %ERRORLEVEL%
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
