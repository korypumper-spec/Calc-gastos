@rem
@rem Copyright 2015 the original author or authors.
@rem
@if "%DEBUG%"=="" @echo off
setlocal enabledelayedexpansion
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if exist "%CLASSPATH%" (
    if defined JAVA_HOME (
        set JAVA_EXE=%JAVA_HOME%\bin\java.exe
    ) else (
        set JAVA_EXE=java.exe
    )
    "%JAVA_EXE%" -Xmx64m -Xms64m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) else (
    where gradle >nul 2>nul
    if %ERRORLEVEL%==0 (
        gradle %*
    ) else (
        echo ERROR: gradle-wrapper.jar no encontrado. Abre el proyecto en Android Studio o ejecuta "gradle wrapper".
        exit /b 1
    )
)
endlocal
