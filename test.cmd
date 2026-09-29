@echo off
rem ===========================================================================
rem  test.cmd -- Acbric fast test entry point (Windows)
rem
rem  Usage:
rem    test                     run every regression suite
rem    test all                 same as above
rem    test <suite>...          run selected suites (unique prefix works: ev -> event)
rem    test list                list available suites
rem    test help                show this text
rem
rem  This is a thin wrapper over build.cmd: the suite selection travels in the
rem  ACBRIC_SUITES environment variable, and build.cmd owns JDK discovery and the
rem  forward to gradlew. Nothing about the build is reimplemented here.
rem
rem  It uses an environment variable rather than -Pacbric.suites= so that test.cmd
rem  and test.sh read the same in both shells (cmd.exe splits an argument at '=').
rem  Gradle itself accepts both forms.
rem
rem  This does NOT change CI: check and gradlew build always run every suite.
rem
rem  ASCII-only and CRLF on purpose -- see build.cmd for why.
rem ===========================================================================
setlocal
rem Keep the entry location before SHIFT changes the batch parameters.
set "ACBRIC_TEST_BUILD=%~dp0build.cmd"
set "SEL="
set "RUN_ALL="

:parse
if "%~1"=="" goto :run
if /i "%~1"=="help"   goto :usage
if /i "%~1"=="-h"     goto :usage
if /i "%~1"=="--help" goto :usage
if /i "%~1"=="all"    goto :all
if not defined SEL goto :first
set "SEL=%SEL%,%~1"
goto :parse_next
:first
set "SEL=%~1"
:parse_next
shift
goto :parse

:all
set "RUN_ALL=1"
goto :parse_next

:run
if defined RUN_ALL goto :run_all
if not defined SEL goto :run_all
echo [test] suites: %SEL%
set "ACBRIC_SUITES=%SEL%"
call "%ACBRIC_TEST_BUILD%" regressionTest
exit /b %ERRORLEVEL%

:run_all
echo [test] running all regression suites
set "ACBRIC_SUITES="
call "%ACBRIC_TEST_BUILD%" regressionTest
exit /b %ERRORLEVEL%

:usage
echo   test                     run every suite
echo   test all                 same as above
echo   test ^<suite^>...         run selected suites (unique prefix works: ev -^> event)
echo   test list                list available suites
echo.
echo   Suites: data bundle classpath event rename mods
echo   CI and gradlew build always run every suite; this only affects local runs.
exit /b 0
