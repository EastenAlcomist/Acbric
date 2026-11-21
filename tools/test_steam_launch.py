"""Launch the cleaned Acbric distribution through Steam App 342560 and verify the real game."""

from pathlib import Path
import argparse
import json
import os
import re
import shutil
import subprocess
import time
import zipfile


APP_ID = "342560"


def run(command: list[str], timeout: int = 60, **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(command, timeout=timeout, **kwargs)


def snapshot(root: Path) -> dict[str, tuple[int, int]]:
    """Cheap installation guard; the external suite separately verifies full hashes."""
    result = {}
    for path in sorted(root.rglob("*")):
        if path.is_file():
            stat = path.stat()
            result[path.relative_to(root).as_posix()] = (stat.st_size, stat.st_mtime_ns)
    return result


def airships_pids() -> set[int]:
    output = run(
        ["tasklist", "/FI", "IMAGENAME eq Airships.exe", "/FO", "CSV", "/NH"],
        capture_output=True,
        text=True,
    ).stdout
    values = set()
    for line in output.splitlines():
        columns = re.findall(r'"([^"]*)"', line)
        if len(columns) >= 2:
            try:
                values.add(int(columns[1]))
            except ValueError:
                pass
    return values


def steam_running() -> bool:
    output = run(["tasklist", "/FI", "IMAGENAME eq steam.exe", "/FO", "CSV", "/NH"],
                 capture_output=True, text=True).stdout
    return "steam.exe" in output.lower()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--game-dir", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--resume", action="store_true", help="Resume an interrupted fresh test directory")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*", args.tag):
        parser.error("Invalid tag")
    if airships_pids():
        parser.error("Close every Airships.exe before this test")
    if not steam_running():
        parser.error("Sign in and start Steam before this test")

    project = Path(__file__).resolve().parents[1]
    game = args.game_dir.resolve()
    java_home = args.java_home.resolve()
    version = json.loads((project / "src/apiMod/resources/fabric.mod.json").read_text(encoding="utf-8"))["version"]
    package = project / f"build/external-dist/Acbric-external-{version}.zip"
    probe_class = (project / "build/classes/java/regressionTest/net/fabricacs/acbric/"
                   "SteamLaunchIntegrationHarness.class")
    fixture_class = (project / "build/classes/java/regressionTest/net/fabricacs/regression/"
                     "fixtures/PublicLaunchFixture.class")
    required = [package, java_home / "bin/java.exe", probe_class, fixture_class,
                game / "Airships.exe", game / "Airships.json"]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        parser.error("Missing inputs:\n" + "\n".join(missing))

    run_root = project / "build/steam-release-tests" / args.tag
    if run_root.exists() and not args.resume:
        parser.error(f"Output exists: {run_root}")
    if args.resume and not run_root.is_dir():
        parser.error(f"Nothing to resume: {run_root}")
    run_root.mkdir(parents=True, exist_ok=True)
    unpack = run_root / "acbric package"
    bundle = unpack / "Acbric"
    instance = run_root / "isolated instance"

    if not args.resume:
        with zipfile.ZipFile(package) as archive:
            archive.extractall(unpack)
        setup_log = run_root / "setup.log"
        with setup_log.open("w", encoding="utf-8") as output:
            result = run([
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", str(bundle / "setup.ps1"),
                "-GameDir", str(game),
                "-InstanceDir", str(instance),
                "-Language", "en",
            ], timeout=60, stdout=output, stderr=subprocess.STDOUT)
        if result.returncode != 0:
            raise RuntimeError(f"Setup failed: {setup_log}")

        mods = bundle / "mods"
        mods.mkdir(parents=True, exist_ok=True)
        native = mods / "native_test"
        native.mkdir()
        (native / "info.json").write_text(
            json.dumps({"id": "native_test", "name": "Steam native test"}),
            encoding="utf-8",
        )
        environment = instance / "acbric-launcher/environment.json"
        environment.parent.mkdir(parents=True, exist_ok=True)
        environment.write_text(json.dumps({
            "schema": "1",
            "mode": "isolated",
            "vanilla": "",
        }), encoding="utf-8")
        userdata = instance / "userdata"
        userdata.mkdir(parents=True, exist_ok=True)
        (userdata / "prefs.json").write_text(json.dumps({
            "language": "en",
            "phoneHomeWithErrors": False,
            **{"mod_known_" + name: True for name in [
                "native_test", "public_launch_test", "installed_native",
            ]},
        }), encoding="utf-8")
        (instance / "steam-context-test").write_text("verify Steam context", encoding="utf-8")
        (instance / "allow-test-exit").write_text("exit after checkpoint", encoding="utf-8")

    # Always refresh the observer from the current regression build, including resume runs.
    mods = bundle / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    (mods / "steam-test.jar").unlink(missing_ok=True)
    mixin_class = "net/fabricacs/regression/fixtures/PublicLaunchFixture"
    with zipfile.ZipFile(mods / "public-test.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        jar.write(fixture_class, mixin_class + ".class")
        jar.writestr("fabric.mod.json", json.dumps({
            "schemaVersion": 1,
            "id": "public_launch_test",
            "version": "1",
            "mixins": ["public-test.mixins.json"],
        }))
        jar.writestr("acbric_vanilla/info.json", json.dumps({
            "id": "public_launch_test",
            "name": "Steam launch test",
        }))
        jar.writestr("public-test.mixins.json", json.dumps({
            "required": True,
            "package": "net.fabricacs.regression.fixtures",
            "compatibilityLevel": "JAVA_21",
            "mixins": ["PublicLaunchFixture"],
            "injectors": {"defaultRequire": 1},
        }))

    before_game = snapshot(game)
    checkpoint = instance / "public-checkpoint.txt"
    checkpoint.unlink(missing_ok=True)
    test_env = dict(os.environ,
                    APPDATA=str(run_root / "appdata"),
                    LOCALAPPDATA=str(run_root / "localappdata"),
                    USERPROFILE=str(run_root / "home"),
                    TEMP=str(run_root / "tmp"),
                    TMP=str(run_root / "tmp"),
                    JAVA_HOME=str(java_home))
    for name in ("appdata", "localappdata", "home", "tmp"):
        (run_root / name).mkdir(exist_ok=True)
    for key in ("JAVA_TOOL_OPTIONS", "JDK_TOOL_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"):
        test_env.pop(key, None)

    classpath = str(bundle / "loader-libs" / "*") + ";" + str(
        project / "build/classes/java/regressionTest"
    )
    harness_log = run_root / "steam-launch-harness.log"
    with harness_log.open("w", encoding="utf-8") as output:
        result = run([
            str(java_home / "bin/java.exe"),
            "-cp", classpath,
            "net.fabricacs.acbric.SteamLaunchIntegrationHarness",
            str(bundle), str(game), str(instance),
        ], timeout=45, cwd=run_root, env=test_env, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode != 0 or "STEAM_LAUNCH_REQUESTED" not in harness_log.read_text(encoding="utf-8"):
        raise RuntimeError(f"Steam launch request failed: {harness_log}")

    deadline = time.monotonic() + args.timeout
    while not checkpoint.exists():
        if time.monotonic() > deadline:
            for pid in airships_pids():
                run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
            workshop = instance / "userdata/steam/mods"
            workshop_items = list(workshop.glob("*")) if workshop.is_dir() else []
            workshop_bytes = sum(path.stat().st_size for path in workshop.rglob("*") if path.is_file())
            raise RuntimeError(
                "Steam-launched Acbric did not reach the public menu checkpoint; "
                f"workshop items={len(workshop_items)}, bytes={workshop_bytes}"
            )
        time.sleep(0.25)

    text = checkpoint.read_text(encoding="utf-8")
    if "Steam App 342560 launch" not in text or "Steamworks/UGC" not in text:
        raise RuntimeError("Steam-specific checkpoint missing: " + text)
    session_logs = sorted(instance.glob("logs/acbric/*/startup.json"))
    if not session_logs:
        raise RuntimeError("Acbric startup diagnostics were not written")
    startup = json.loads(session_logs[-1].read_text(encoding="utf-8"))
    if startup.get("phase") not in (None, "LOADED", "MAIN_RETURNED"):
        # The fixture exits from inside Main; exact phase naming differs by build.
        if "FAILED" in json.dumps(startup):
            raise RuntimeError("Startup diagnostics report failure: " + str(startup))

    exit_deadline = time.monotonic() + 30
    while airships_pids() and time.monotonic() < exit_deadline:
        time.sleep(0.25)
    if airships_pids():
        for pid in airships_pids():
            run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
        raise RuntimeError("Steam test left Airships.exe running")

    after_game = snapshot(game)
    if before_game != after_game:
        changed = {
            "added": sorted(after_game.keys() - before_game.keys()),
            "removed": sorted(before_game.keys() - after_game.keys()),
            "modified": sorted(k for k in before_game.keys() & after_game.keys()
                               if before_game[k] != after_game[k]),
        }
        raise RuntimeError("Steam launch changed the game installation: " + json.dumps(changed))

    report = {
        "status": "PASS",
        "package": str(package),
        "checkpoint": text.strip(),
        "steamLaunchLogs": [str(path) for path in instance.glob("logs/acbric/launcher/steam-launch-*.log")],
        "startup": str(session_logs[-1]),
        "gameInstallationUnchanged": True,
        "steamAppId": APP_ID,
    }
    (run_root / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"STEAM RELEASE TEST PASS: {run_root / 'report.json'}")


if __name__ == "__main__":
    main()
