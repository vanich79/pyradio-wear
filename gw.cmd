@echo off
rem Gradle wrapper with an ASCII temp directory.
rem
rem This user's profile path contains non-ASCII characters, which breaks the JVM twice:
rem
rem   1. The default TEMP lives inside the profile. The Windows AF_UNIX implementation
rem      cannot connect over such a path, so the JVM fails to open an NIO selector and
rem      Gradle dies with "Unable to establish loopback connection" before it reads any
rem      build settings.
rem   2. The default GRADLE_USER_HOME lives there too. Gradle launches its test workers
rem      with a classpath pointing into it, and the mangled path makes the worker fail
rem      with "Could not find or load main class GradleWorkerMain" - so every test task
rem      dies before running a single test.
rem
rem Both variables must be set before the JVM starts, which gradle.properties cannot do.
rem
rem Comments here are ASCII on purpose: cmd.exe reads this file in the OEM code page
rem and mangles UTF-8 text, which breaks even REM lines. The explanation in Russian
rem lives in docs/BUILDING.md.
rem
rem On a machine with ASCII paths plain gradlew is enough.
setlocal
if not exist D:\Tools\tmp mkdir D:\Tools\tmp
set "TMP=D:\Tools\tmp"
set "TEMP=D:\Tools\tmp"
set "GRADLE_USER_HOME=D:\Tools\gradle-home"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
if not defined ANDROID_HOME set "ANDROID_HOME=D:\Android\Sdk"
call "%~dp0gradlew.bat" %*
endlocal
