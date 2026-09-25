@echo off
rem ===========================================================================
rem  build.cmd -- Acbric fast compile entry point (Windows)
rem
rem  Usage:
rem    build                     compile only (assemble), no tests
rem    build full                compile + all regression tests (gradle build)
rem    build clean               clean, then compile
rem    build help                show this text
rem    build <gradle task...>    anything else is forwarded to gradlew
rem
rem  This script does exactly two things: locate a JDK 21, and hand the command
rem  to gradlew. All build logic lives in build.gradle, so the IDE, CI and a
rem  hand-typed gradlew behave identically.
rem
rem  POSIX users: use build.sh in the same directory.
rem
rem  Deliberately ASCII-only and CRLF. cmd.exe reads .bat/.cmd using the console
rem  OEM code page, so non-ASCII comments get mis-decoded on machines with a
rem  different code page and can be executed as commands. Keep it ASCII;
rem  .gitattributes enforces *.cmd eol=crlf.
rem
rem  Batch hazards avoided on purpose (each one cost a debugging round):
rem    * for /f ('""quoted exe" args"') needs an EVEN number of quotes, or the
rem      parser swallows the rest of the file. We redirect to a temp file instead.
rem    * a path containing ")" breaks any ( ... ) block that expands it, so the
rem      accept routine uses goto-style flow with no parenthesised blocks.
rem ===========================================================================
setlocal enabledelayedexpansion
set "ROOT=%~dp0"
pushd "%ROOT%" || exit /b 1

if not exist "gradlew.bat" (
  echo [build] gradlew.bat not found. Run this from the Acbric repository.
  popd & exit /b 1
)

set "ACBRIC_TMPFILE=%TEMP%\acbric-java-version-%RANDOM%.txt"
call :find_jdk
call :cleanup

if not defined ACBRIC_JAVA_HOME (
  echo [build] No JDK 21 found.
  echo.
  echo   This project requires JDK 21 ^(build.gradle sets sourceCompatibility = 21^).
  echo   Either:
  echo     1. install a JDK 21 ^(Eclipse Adoptium, Microsoft OpenJDK, Corretto, ...^), or
  echo     2. set JAVA_HOME to an existing JDK 21 and run build again:
  echo          set JAVA_HOME=C:\path\to\jdk-21
  echo.
  echo   Current JAVA_HOME = "%JAVA_HOME%"
  echo.
  echo   See BUILDING.md / BUILDING.zh-CN.md.
  popd & exit /b 1
)
set "JAVA_HOME=%ACBRIC_JAVA_HOME%"

rem 原始命令行，用于检测 cmd.exe 在 '=' 处拆参数（见 :run 的提示）
set "RAW=%*"
set "TASKS=assemble"
set "EXTRA="

:parse
if "%~1"=="" goto :run
if /i "%~1"=="help"   goto :usage
if /i "%~1"=="-h"     goto :usage
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="full"   goto :full
if /i "%~1"=="clean"  goto :clean
set "EXTRA=%EXTRA% %1"
shift
goto :parse

:full
set "TASKS=build"
shift
goto :parse

:clean
set "TASKS=clean assemble"
shift
goto :parse

:run
rem cmd.exe 会把 "-Pkey=value" 在 '=' 处拆成两个参数，%1..%9 拿到的已经是拆开的，
rem 只有 %* 保留原样。检测到这种情况时给一句提示，否则使用者只会看到
rem Gradle 莫名其妙的 "Task 'value' not found"。
set "RAW_HAS_EQ="
if not defined RAW goto :run_now
echo %RAW%| findstr "=" >nul
if not errorlevel 1 set "RAW_HAS_EQ=1"
set "EXTRA_HAS_EQ="
if not defined EXTRA goto :run_pick
echo %EXTRA%| findstr "=" >nul
if not errorlevel 1 set "EXTRA_HAS_EQ=1"
:run_pick
if not defined RAW_HAS_EQ goto :run_now
if defined EXTRA_HAS_EQ goto :run_now
echo [build] note: cmd.exe splits an argument at '='; quote it, e.g.
echo [build]       build %TASKS% "-Pacbric.suites=event"
echo [build]       (or set ACBRIC_SUITES=event and just run: build %TASKS%)
:run_now
echo [build] JDK 21  = %JAVA_HOME%
echo [build] gradlew %TASKS%%EXTRA%
call gradlew.bat %TASKS% %EXTRA%
set "CODE=%ERRORLEVEL%"
popd
exit /b %CODE%

:usage
echo   build                     compile only / assemble
echo   build full                compile + all tests / build
echo   build clean               clean then compile
echo   build ^<gradle task...^>   forward to gradlew
echo.
echo   Tests: use test.cmd in the same directory -- test all / test ^<suite^> / test list
popd
exit /b 0

rem ---------------------------------------------------------------------------
rem  Locate a JDK 21: JAVA_HOME -> java on PATH -> common install locations.
rem  Only a major version >= 21 is accepted, so a PATH pointing at Java 8 gives
rem  a clear message instead of a confusing compile failure.
rem ---------------------------------------------------------------------------
:find_jdk
set "ACBRIC_JAVA_HOME="
if not defined JAVA_HOME goto :find_jdk_path
call :accept "%JAVA_HOME%"
if defined ACBRIC_JAVA_HOME goto :eof

:find_jdk_path
for %%j in (java.exe) do call :accept_bin "%%~$PATH:j"
if defined ACBRIC_JAVA_HOME goto :eof

for /d %%d in (
  "%ProgramFiles%\Eclipse Adoptium\jdk-2*"
  "%ProgramFiles%\Java\jdk-2*"
  "%ProgramFiles%\Microsoft\jdk-2*"
  "%ProgramFiles%\Amazon Corretto\jdk2*"
  "%ProgramFiles%\Zulu\zulu-2*"
  "%ProgramFiles%\BellSoft\LibericaJDK-2*"
  "%ProgramFiles%\OpenJDK*"
  "%ProgramFiles(x86)%\Java\jdk-2*"
  "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-2*"
  "%USERPROFILE%\.jdks\*"
  "%USERPROFILE%\.gradle\jdks\*"
) do call :accept "%%~fd"
goto :eof

:accept_bin
rem %1 = full path to java.exe; the JDK root is the parent of its bin directory.
rem "%~dp1.." is normalised to that parent by %%~fp.
if "%~1"=="" goto :eof
for %%p in ("%~dp1..") do call :accept "%%~fp"
goto :eof

:accept
rem %1 = candidate JDK root. No parenthesised blocks here: a path containing
rem ")" would close the block early and abort the script.
if "%~1"=="" goto :eof
if not exist "%~1\bin\java.exe" goto :eof
"%~1\bin\java.exe" -version > "%ACBRIC_TMPFILE%" 2>&1
set "JLINE="
set /p JLINE=<"%ACBRIC_TMPFILE%"
if not defined JLINE goto :eof
rem First line is always "<name> version \"x.y.z\" <date>"; token 3 is the version.
set "JVER="
for /f "tokens=3" %%a in ("!JLINE!") do set "JVER=%%~a"
if not defined JVER goto :eof
set "JMAJ="
for /f "tokens=1,2 delims=._" %%a in ("!JVER!") do if "%%a"=="1" (set "JMAJ=%%b") else (set "JMAJ=%%a")
if not defined JMAJ goto :eof
if !JMAJ! GEQ 21 set "ACBRIC_JAVA_HOME=%~1"
goto :eof

:cleanup
if defined ACBRIC_TMPFILE del "%ACBRIC_TMPFILE%" >nul 2>&1
goto :eof
