@rem Gradle startup script
@if "%DEBUG%"=="" @echo off
@rem Use the gradle wrapper jar if available
if exist "%USERPROFILE%\.gradle\wrapper\dists\*" (
    "%JAVA_HOME%/bin/java" -jar "gradle/wrapper/gradle-wrapper.jar" %*
) else (
    echo Please run: gradle wrapper --gradle-version 8.5 first
)
