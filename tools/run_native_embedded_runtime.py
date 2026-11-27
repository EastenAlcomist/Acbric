"""Run the default API's native embedded-ACMOD probe in an isolated Knot process."""

from pathlib import Path
import argparse
import json
import os
import re
import shutil
import subprocess
import zipfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--game-data", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--output-root", type=Path)
    args = parser.parse_args()

    if os.name != "nt":
        parser.error("This probe currently supports Windows only")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*", args.tag):
        parser.error("Invalid tag")
    if args.java_home is None:
        parser.error("Set JAVA_HOME or --java-home to JDK 21")

    project = Path(__file__).resolve().parents[1]
    output_root = (args.output_root or project / "build" / "native-embedded-runtime").resolve()
    run = output_root / args.tag
    if run.exists():
        parser.error(f"Output exists; use a fresh tag: {run}")

    data = args.game_data.resolve()
    jdk = Path(args.java_home).resolve() / "bin"
    loader_libs = project / "build" / "external-dist" / "Acbric" / "loader-libs"
    api = project / "build" / "external-dist" / "Acbric" / "core" / "acbric-api.jar"
    classes = project / "build" / "classes" / "java" / "regressionTest"
    probe = classes / "net/fabricacs/api/impl/NativeEmbeddedAcmodsRuntimeProbe.class"
    required = [jdk / "java.exe", api, loader_libs, probe, data / "fontmetrics", data / "lang"]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        parser.error("Missing inputs:\n" + "\n".join(missing))

    run.mkdir(parents=True)
    for name in ("libs", "loader-libs", "game/mods", "game/data", "userdata", "home", "appdata"):
        (run / name).mkdir(parents=True, exist_ok=True)

    for path in (project / "libs").iterdir():
        if path.is_file() and not re.search(
            r"acbric|fabric-loader|sponge-mixin|asm-", path.name, re.IGNORECASE
        ):
            shutil.copy2(path, run / "libs" / path.name)
    for path in loader_libs.iterdir():
        if path.is_file():
            shutil.copy2(path, run / "loader-libs" / path.name)
    shutil.copy2(api, run / "game" / "mods" / "acbric-api.jar")
    for name in ("fontmetrics", "lang"):
        shutil.copytree(data / name, run / "game" / "data" / name)

    with zipfile.ZipFile(run / "game" / "mods" / "native-embedded-probe.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        jar.write(probe, "net/fabricacs/api/impl/NativeEmbeddedAcmodsRuntimeProbe.class")
        jar.writestr("fabric.mod.json", json.dumps({
            "schemaVersion": 1,
            "id": "acbric_native_embedded_probe",
            "version": "1",
            "name": "Native embedded ACMOD probe",
        }))

    (run / "game" / "Airships.json").write_text(json.dumps({
        "mainClass": "net.fabricacs.api.impl.NativeEmbeddedAcmodsRuntimeProbe",
        "classPath": ["asplit-A.zip", "asplit-B.zip"],
    }), encoding="utf-8")
    (run / "game" / "launch_settings.json").write_text(json.dumps({
        "customDataDirectoryLocation": str(run / "userdata")
    }), encoding="utf-8")

    command = [
        str(jdk / "java.exe"),
        "-Dsteam=false",
        "-Ddev=true",
        "-Djava.awt.headless=true",
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        f"-Duser.home={run / 'home'}",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "-cp",
        f"{run / 'libs'}/*;{run / 'loader-libs'}/*",
        "net.fabricmc.loader.impl.launch.knot.KnotClient",
    ]
    environment = dict(os.environ, APPDATA=str(run / "appdata"))
    log_path = run / "runtime.log"
    with log_path.open("w", encoding="utf-8") as log:
        result = subprocess.run(
            command,
            cwd=run / "game",
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
            timeout=60,
        )
    output = log_path.read_text(encoding="utf-8")
    if result.returncode != 0 or "NATIVE EMBEDDED ACMOD RUNTIME PASS" not in output:
        raise RuntimeError(f"Probe failed: {log_path}\n{output[-8000:]}")
    print(f"NATIVE EMBEDDED ACMOD RUNTIME PASS: {log_path}")


if __name__ == "__main__":
    main()
