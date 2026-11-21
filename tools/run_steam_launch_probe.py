"""Verify Packr custom launch and Steam App 342560 context without changing Steam settings."""

from pathlib import Path
import argparse
import json
import os
import re
import subprocess
import time
import zipfile


APP_ID = "342560"
RESULT = "steam-launch-result.properties"


def run_checked(command: list[str], timeout: int = 20) -> subprocess.CompletedProcess:
    return subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=timeout,
    )


def process_names() -> list[str]:
    result = run_checked(["tasklist", "/FO", "CSV", "/NH"])
    names = []
    for line in result.stdout.splitlines():
        match = re.match(r'"([^"]+)"', line)
        if match:
            names.append(match.group(1).lower())
    return names


def airships_pids() -> set[int]:
    result = run_checked(
        ["tasklist", "/FI", "IMAGENAME eq Airships.exe", "/FO", "CSV", "/NH"]
    )
    pids = set()
    for line in result.stdout.splitlines():
        columns = re.findall(r'"([^"]*)"', line)
        if len(columns) >= 2:
            try:
                pids.add(int(columns[1]))
            except ValueError:
                pass
    return pids


def read_properties(path: Path) -> dict[str, str]:
    values = {}
    for line in path.read_text(encoding="latin-1").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        separator = stripped.find("=")
        if separator < 0:
            separator = stripped.find(":")
        if separator < 0:
            continue
        key = java_unescape(stripped[:separator])
        value = java_unescape(stripped[separator + 1:].lstrip())
        values[key] = value
    return values


def java_unescape(value: str) -> str:
    out = []
    index = 0
    while index < len(value):
        char = value[index]
        if char != "\\" or index + 1 >= len(value):
            out.append(char)
            index += 1
            continue
        index += 1
        escaped = value[index]
        index += 1
        if escaped == "t":
            out.append("\t")
        elif escaped == "n":
            out.append("\n")
        elif escaped == "r":
            out.append("\r")
        elif escaped == "f":
            out.append("\f")
        elif escaped == "u" and index + 4 <= len(value):
            try:
                out.append(chr(int(value[index:index + 4], 16)))
                index += 4
            except ValueError:
                out.append("u")
        else:
            out.append(escaped)
    return "".join(out)


def wait_for_result(path: Path, timeout: int) -> dict[str, str]:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.is_file():
            try:
                return read_properties(path)
            except OSError:
                pass
        time.sleep(0.25)
    raise RuntimeError(f"Probe result not produced: {path}")


def packr_command(airships: Path, config: Path, hold: int) -> list[str]:
    return [
        str(airships),
        "--cli",
        f"--config={config}",
        "--",
        "acbric-steam-probe",
        f"hold={hold}",
    ]


def inspect_values(values: dict[str, str], label: str) -> None:
    if values.get("probe.completed") != "true":
        raise RuntimeError(f"{label}: probe failed: {values.get('probe.error')}")
    process_command = values.get("process.command", "").lower()
    if not process_command.endswith("airships.exe"):
        raise RuntimeError(f"{label}: JVM did not run inside Airships.exe: {process_command}")
    if values.get("args") != "acbric-steam-probe|hold=" + values.get("hold.seconds", ""):
        raise RuntimeError(f"{label}: arguments not forwarded: {values.get('args')}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--steam-exe", type=Path, required=True)
    parser.add_argument("--game-dir", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--framework", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=90)
    args = parser.parse_args()

    if os.name != "nt":
        parser.error("Steam launch probe currently supports Windows only")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*", args.tag):
        parser.error("Invalid tag")

    project = Path(__file__).resolve().parents[1]
    steam = args.steam_exe.resolve()
    game = args.game_dir.resolve()
    java_home = args.java_home.resolve()
    framework = args.framework.resolve()
    airships = game / "Airships.exe"
    steamworks = game / "lib" / "steamworks4j-1.9.0.jar"
    loader_jars = sorted((framework / "loader-libs").glob("*.jar"))
    probe_class = (
        project
        / "build"
        / "classes"
        / "java"
        / "regressionTest"
        / "net"
        / "fabricacs"
        / "acbric"
        / "SteamLaunchProbe.class"
    )
    required = [
        steam,
        airships,
        steamworks,
        java_home / "bin" / "server" / "jvm.dll",
        probe_class,
        framework / "loader-libs",
        framework / "core" / "acbric-api.jar",
    ]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        parser.error("Missing inputs:\n" + "\n".join(missing))

    names = process_names()
    if "steam.exe" not in names:
        parser.error("Steam is not running")
    if "airships.exe" in names:
        parser.error("Airships.exe is already running; close it before probing")
    existing_airships = airships_pids()

    output_root = project / "build" / "steam-launch-probe"
    run = output_root / args.tag
    if run.exists():
        parser.error(f"Output exists; use a fresh tag: {run}")
    run.mkdir(parents=True)

    probe_jar = run / "steam-launch-probe.jar"
    with zipfile.ZipFile(probe_jar, "w", zipfile.ZIP_DEFLATED) as archive:
        source_dir = probe_class.parent
        for class_file in sorted(source_dir.glob("SteamLaunchProbe*.class")):
            archive.write(
                class_file,
                f"net/fabricacs/acbric/{class_file.name}",
            )

    config_path = run / "steam-launch.json"
    result_path = run / RESULT
    config = {
        "jrePath": java_home.as_posix(),
        "classPath": [
            probe_jar.as_posix(),
            steamworks.as_posix(),
            *(path.as_posix() for path in loader_jars),
        ],
        "mainClass": "net.fabricacs.acbric.SteamLaunchProbe",
        "useZgcIfSupportedOs": False,
        "vmArgs": [
            "-Xmx256M",
            "-Dfile.encoding=UTF-8",
            f"-Dacbric.probe.result={result_path.as_posix()}",
        ],
    }
    config_path.write_text(json.dumps(config, indent=2), encoding="utf-8")

    direct_result = result_path
    direct = run_checked(
        packr_command(airships, config_path, 1),
        timeout=30,
    )
    direct_values = wait_for_result(direct_result, 20)
    inspect_values(direct_values, "direct Packr launch")
    (run / "direct-result.txt").write_text(
        f"exit={direct.returncode}\n{direct.stdout}\n", encoding="utf-8"
    )
    direct_result.unlink()

    steam_command = [
        str(steam),
        "-applaunch",
        APP_ID,
        *packr_command(airships, config_path, 8)[1:],
    ]
    (run / "steam-command.txt").write_text(
        subprocess.list2cmdline(steam_command) + "\n", encoding="utf-8"
    )
    launched = None
    steam_values = None
    success = False
    try:
        launched = subprocess.Popen(
            steam_command,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        steam_values = wait_for_result(direct_result, args.timeout)
        inspect_values(steam_values, "Steam App 342560 launch")
        if steam_values.get("env.SteamAppId") != APP_ID:
            raise RuntimeError(
                "SteamAppId was not inherited: "
                + steam_values.get("env.SteamAppId", "<missing>")
            )
        if steam_values.get("steam.initialized") != "true":
            raise RuntimeError("SteamAPI.init() returned false")
        if steam_values.get("steam.appId") != APP_ID:
            raise RuntimeError(
                "Unexpected Steam App ID: " + steam_values.get("steam.appId", "<missing>")
            )
        if steam_values.get("steam.ugcAvailable") != "true":
            raise RuntimeError(
                "Workshop UGC interface unavailable: "
                + steam_values.get("steam.ugcError", "unknown error")
            )
        if steam_values.get("loader.knotClass") != "true":
            raise RuntimeError(
                "Fabric KnotClient unavailable: "
                + steam_values.get("loader.knotError", "unknown error")
            )
        if steam_values.get("loader.gameProviderClass") != "true":
            raise RuntimeError(
                "Acbric GameProvider unavailable: "
                + steam_values.get("loader.gameProviderError", "unknown error")
            )
        if steam_values.get("loader.gameProviderService") != "true":
            raise RuntimeError(
                "Acbric GameProvider service registration unavailable: "
                + steam_values.get("loader.gameProviderServiceError", "unknown error")
            )
        if not steam_values.get("jvm.javaVersion", "").startswith("21"):
            raise RuntimeError(
                "Probe did not run on Java 21: " + steam_values.get("jvm.javaVersion", "")
            )
        success = True
    finally:
        if launched is not None:
            try:
                launched.wait(timeout=20)
            except subprocess.TimeoutExpired:
                launched.terminate()

        if not success:
            # Arguments being dropped would launch the normal game. Close only
            # processes that appeared after this probe started.
            for pid in sorted(airships_pids() - existing_airships):
                run_checked(
                    ["taskkill", "/PID", str(pid), "/T", "/F"],
                    timeout=20,
                )

    report = {
        "tag": args.tag,
        "command": steam_command,
        "loaderJars": [path.name for path in loader_jars],
        "steam": steam_values,
    }
    (run / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"STEAM LAUNCH PROBE PASS: {run / 'report.json'}")


if __name__ == "__main__":
    main()
