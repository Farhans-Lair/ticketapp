@echo off
:: Maven Wrapper for Windows — downloads Maven 3.9.6 automatically on first run.
:: Usage: mvnw.cmd package   (instead of: mvn package)

set MAVEN_VERSION=3.9.6
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%
set MAVEN_ZIP_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip
set MVNW_EXEC=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd

if not exist "%MVNW_EXEC%" (
    echo Downloading Maven %MAVEN_VERSION%...
    if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
    powershell -Command "Invoke-WebRequest -Uri '%MAVEN_ZIP_URL%' -OutFile '%TEMP%\maven.zip'"
    powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists\' -Force"
    del "%TEMP%\maven.zip"
    echo Maven %MAVEN_VERSION% installed.
)

"%MVNW_EXEC%" %*
