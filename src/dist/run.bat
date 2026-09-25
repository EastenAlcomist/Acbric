@echo off
rem 使用内置 Java 启动 Fabric；game、libs、loader-libs、jre 应保持相邻。
setlocal
pushd "%~dp0game" || exit /b 1
set "JAVA_EXE=%~dp0jre\bin\java.exe"
if not exist "%JAVA_EXE%" (
  echo Missing bundled Java runtime: "%JAVA_EXE%"
  popd
  exit /b 1
)
rem 默认关闭 Steam 集成，可在调用脚本前设置 AIRSHIPS_STEAM。
if not defined AIRSHIPS_STEAM set "AIRSHIPS_STEAM=false"
"%JAVA_EXE%" -Xmx4G --add-opens=java.base/java.util=ALL-UNNAMED -Ddev=true "-Dsteam=%AIRSHIPS_STEAM%" "-Dorg.lwjgl.librarypath=%~dp0game\lib\native" "-Dnet.java.games.input.librarypath=%~dp0game\lib\native" "-Djava.library.path=%~dp0game\lib\native" -cp "%~dp0loader-libs\*" net.fabricmc.loader.impl.launch.knot.KnotClient %*
set "ACBRIC_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %ACBRIC_EXIT_CODE%
