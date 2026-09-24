# Acbric Mod Template

Standalone Gradle template for Acbric mods. It builds from its own directory and
uses only the bundled `libs/` dependencies. See `README.zh-CN.md` for the full
Chinese guide.

Quick start:

```powershell
.\gradlew.bat build
.\gradlew.bat installMod
```

`build` does not depend on the main Acbric project. `installMod` copies the jar
to `gameDir/mods`; set `gameDir` in `gradle.properties` if the template is moved.

When working inside the main Acbric workspace, run `.\gradlew.bat syncModTemplateLibs`
from the workspace root to repopulate this template's `libs/` directory.
