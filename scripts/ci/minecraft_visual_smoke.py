#!/usr/bin/env python3
"""Launch vanilla Minecraft with VantaLoader and capture real title/mod screens."""
from __future__ import annotations
import argparse, concurrent.futures, hashlib, json, os, re, shutil, subprocess, sys, time, urllib.request, zipfile
from pathlib import Path

VERSION = "1.21.11"
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
UUID = "00000000-0000-0000-0000-000000000000"

def sha1(path):
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def fetch(url, destination, expected_sha1=None):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file() and (not expected_sha1 or sha1(destination) == expected_sha1):
        return destination
    destination.unlink(missing_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    error = None
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "VantaLoader-CI/1.0"})
            with urllib.request.urlopen(request, timeout=60) as response, temporary.open("wb") as output:
                shutil.copyfileobj(response, output)
            if expected_sha1 and sha1(temporary) != expected_sha1:
                raise RuntimeError("SHA-1 mismatch for " + url)
            temporary.replace(destination)
            return destination
        except Exception as failure:
            error = failure
            temporary.unlink(missing_ok=True)
            if attempt < 2:
                time.sleep(2 ** attempt)
    raise RuntimeError("Download failed for " + url + ": " + str(error))

def rule_matches(rule):
    os_rule = rule.get("os", {})
    if os_rule.get("name") not in (None, "linux"):
        return False
    if os_rule.get("arch") and not re.fullmatch(os_rule["arch"], "x86_64"):
        return False
    if os_rule.get("version") and not re.search(os_rule["version"], "6."):
        return False
    return True

def allowed(item, features):
    rules = item.get("rules")
    if not rules:
        return True
    result = False
    for rule in rules:
        if any(features.get(key, False) != value for key, value in rule.get("features", {}).items()):
            continue
        if rule_matches(rule):
            result = rule["action"] == "allow"
    return result

def resolve_arguments(items, features, substitutions):
    result = []
    for item in items:
        if isinstance(item, str):
            values = [item]
        else:
            if not allowed(item, features):
                continue
            value = item.get("value", [])
            values = [value] if isinstance(value, str) else value
        for value in values:
            for key, replacement in substitutions.items():
                value = value.replace(chr(36) + "{" + key + "}", replacement)
            if chr(36) + "{" in value:
                raise RuntimeError("Unresolved Minecraft launch argument: " + value)
            result.append(value)
    return result

def install_client(cache):
    manifest_path = fetch(MANIFEST_URL, cache / "version_manifest_v2.json")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    version_entry = next((entry for entry in manifest["versions"] if entry["id"] == VERSION), None)
    if version_entry is None:
        raise RuntimeError("Mojang manifest does not contain " + VERSION)
    version_path = cache / "versions" / VERSION / (VERSION + ".json")
    version_path = fetch(version_entry["url"], version_path, version_entry.get("sha1"))
    metadata = json.loads(version_path.read_text(encoding="utf-8"))
    version_dir = cache / "versions" / VERSION
    client = fetch(metadata["downloads"]["client"]["url"], version_dir / (VERSION + ".jar"),
                   metadata["downloads"]["client"].get("sha1"))
    libraries_dir = cache / "libraries"
    natives_dir = version_dir / "natives"
    natives_dir.mkdir(parents=True, exist_ok=True)
    classpath = [client]

    for library in metadata.get("libraries", []):
        if not allowed(library, {}):
            continue
        downloads = library.get("downloads", {})
        artifact = downloads.get("artifact")
        if artifact:
            classpath.append(fetch(artifact["url"], libraries_dir / artifact["path"], artifact.get("sha1")))
        classifier_name = library.get("natives", {}).get("linux")
        classifier = downloads.get("classifiers", {}).get(classifier_name)
        if classifier:
            native_jar = fetch(classifier["url"], libraries_dir / classifier["path"], classifier.get("sha1"))
            excludes = set(library.get("extract", {}).get("exclude", []))
            with zipfile.ZipFile(native_jar) as archive:
                for member in archive.infolist():
                    name = member.filename.replace("\\", "/")
                    if name.startswith("META-INF/") or any(
                        name == prefix or name.startswith(prefix.rstrip("/") + "/") for prefix in excludes
                    ):
                        continue
                    target = (natives_dir / name).resolve()
                    if natives_dir.resolve() not in target.parents:
                        raise RuntimeError("Unsafe native archive entry: " + member.filename)
                    if member.is_dir():
                        target.mkdir(parents=True, exist_ok=True)
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with archive.open(member) as source, target.open("wb") as output:
                            shutil.copyfileobj(source, output)

    assets_dir = cache / "assets"
    asset_index = metadata["assetIndex"]
    index_path = fetch(asset_index["url"], assets_dir / "indexes" / (asset_index["id"] + ".json"),
                       asset_index.get("sha1"))
    objects = json.loads(index_path.read_text(encoding="utf-8")).get("objects", {})
    assets = []
    for asset in objects.values():
        digest = asset["hash"]
        assets.append((f"https://resources.download.minecraft.net/{digest[:2]}/{digest}",
                       assets_dir / "objects" / digest[:2] / digest, digest))
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
        list(executor.map(lambda item: fetch(*item), assets))

    mapping_info = metadata.get("downloads", {}).get("client_mappings")
    if not mapping_info:
        raise RuntimeError(f"Mojang did not publish official client mappings for {VERSION}")
    mappings_path = fetch(mapping_info["url"], version_dir / "client-mappings.txt",
                          mapping_info.get("sha1"))

    logging_path = ""
    logging = metadata.get("logging", {}).get("client")
    if logging:
        log_file = logging["file"]
        logging_path = str(fetch(log_file["url"], assets_dir / "log_configs" / log_file["id"],
                                 log_file.get("sha1")))
    return metadata, assets_dir, natives_dir, classpath, logging_path, mappings_path

def wait_for_log_marker(process, log_path, marker, timeout):
    def failure(reason):
        log_text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
        tail = "\\n".join(log_text.splitlines()[-120:])
        return RuntimeError(f"{reason}; log tail ({log_path}):\\n{tail or '<log is empty>'}")

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise failure(f"Minecraft exited ({process.returncode})")
        if marker in log_path.read_text(encoding="utf-8", errors="replace"):
            return
        time.sleep(1)
    raise failure(f"Timed out waiting for Minecraft log marker {marker!r}")


def activate_minecraft_window():
    for _ in range(30):
        result = subprocess.run(
            ["xdotool", "search", "--onlyvisible", "--name", "Minecraft"],
            capture_output=True, text=True, check=False
        )
        window_ids = result.stdout.split()
        if window_ids:
            window_id = window_ids[-1]
            subprocess.run(["xdotool", "windowraise", window_id], check=True, timeout=10)
            subprocess.run(["xdotool", "windowmove", window_id, "0", "0"], check=True, timeout=10)
            subprocess.run(["xdotool", "windowsize", window_id, "1280", "720"], check=True, timeout=10)
            subprocess.run(["xdotool", "windowfocus", window_id], check=True, timeout=10)
            time.sleep(2)
            return
        time.sleep(1)
    raise RuntimeError("Minecraft window was not found by xdotool")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--agent", type=Path, required=True)
    parser.add_argument("--mod", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()
    agent = args.agent.resolve()
    mods = [path.resolve() for path in args.mod]
    missing_artifacts = [path for path in [agent, *mods] if not path.is_file()]
    if missing_artifacts:
        raise FileNotFoundError("Missing launch artifacts: " + ", ".join(map(str, missing_artifacts)))

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    cache = Path(os.environ.get("VANTA_MINECRAFT_CACHE",
                  Path.home() / ".cache" / "vantaloader" / "minecraft" / VERSION))
    metadata, assets_dir, natives_dir, classpath, logging_path, mappings_path = install_client(cache)

    game_dir = output / "instance"
    mods_dir = game_dir / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    for mod in mods:
        shutil.copy2(mod, mods_dir / mod.name)
    (game_dir / "options.txt").write_text(
        "fullscreen:false\nguiScale:2\nmaxFps:60\nrenderDistance:2\nsimulationDistance:5\nnarrator:0\n",
        encoding="utf-8"
    )
    features = {"is_demo_user": False, "has_custom_resolution": True,
                "is_quick_play_singleplayer": False, "is_quick_play_multiplayer": False,
                "is_quick_play_realms": False}
    substitutions = {
        "auth_player_name": "VantaCI", "version_name": VERSION, "game_directory": str(game_dir),
        "assets_root": str(assets_dir), "assets_index_name": metadata["assetIndex"]["id"],
        "auth_uuid": UUID, "auth_access_token": "0", "auth_session": "0", "user_type": "legacy",
        "version_type": "release", "user_properties": "{}", "clientid": "", "auth_xuid": "",
        "natives_directory": str(natives_dir), "launcher_name": "VantaLoader-CI",
        "launcher_version": "1.0", "classpath": os.pathsep.join(map(str, classpath)),
        "classpath_separator": os.pathsep, "library_directory": str(cache / "libraries"),
        "resolution_width": "1280", "resolution_height": "720", "user_home": str(Path.home()),
        "version_id": VERSION, "game_assets": str(assets_dir), "path": logging_path,
    }
    jvm = resolve_arguments(metadata.get("arguments", {}).get("jvm", []), features, substitutions)
    game = resolve_arguments(metadata.get("arguments", {}).get("game", []), features, substitutions)
    jvm.extend(["-Xms512M", "-Xmx2G", f"-Djava.library.path={natives_dir}",
                f"-Dvanta.minecraft.version={VERSION}", f"-Dvanta.minecraft.mappings={mappings_path}",
                f"-javaagent:{agent}", "-Djava.awt.headless=false"])
    if "--width" not in game:
        game.extend(["--width", "1280", "--height", "720"])
    java = str(Path(os.environ.get("JAVA_HOME", "/usr")).joinpath("bin", "java"))
    if not Path(java).is_file():
        java = shutil.which("java") or "java"
    command = [java, *jvm, metadata["mainClass"], *game]
    (output / "launch-command.txt").write_text(" ".join(command), encoding="utf-8")
    log_path = game_dir / "logs" / "visual-smoke.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"LIBGL_ALWAYS_SOFTWARE": "1", "MESA_GL_VERSION_OVERRIDE": "4.5",
                        "ALSOFT_DRIVERS": "null"})
    process = None
    try:
        with log_path.open("w", encoding="utf-8") as log:
            process = subprocess.Popen(command, cwd=game_dir, env=environment,
                                       stdout=log, stderr=subprocess.STDOUT)
            wait_for_log_marker(process, log_path, "Sound engine started", min(args.timeout, 180))
            time.sleep(8)
            activate_minecraft_window()
            # Assert the real widget lifecycle through runtime instrumentation, not OCR
            # of a small anti-aliased button label.
            wait_for_log_marker(process, log_path, "VantaLoader Mods button registered at ", 30)
            wait_for_log_marker(process, log_path, "VantaLoader integration probe: client tick START observed", 30)
            wait_for_log_marker(process, log_path, "VantaLoader integration probe: client tick END observed", 30)
            logs = log_path.read_text(encoding="utf-8", errors="replace")
            match = re.search(r"VantaLoader Mods button registered at (\d+),(\d+),(\d+),(\d+)", logs)
            if not match:
                raise RuntimeError("Mods button registration was not reported by the title-screen hook")
            x, y, width, height = map(int, match.groups())
            subprocess.run(["java", "-cp", str(Path(__file__).resolve().parent), "ScreenCapture",
                            str(output / "main-menu-probe.png")], check=True, timeout=15)
            # Preserve the title-screen screenshot without testing footer text.
            shutil.copy2(output / "main-menu-probe.png", output / "main-menu.png")
            print(f"Mods button bounds (logical): {x},{y},{width},{height}")

            # Click the center of the actual widget, using bounds reported by the game.
            # guiScale=2 on the 1280x720 CI display.
            center_x = (x + width // 2) * 2
            center_y = (y + height // 2) * 2
            subprocess.run(["xdotool", "mousemove", "--sync", str(center_x), str(center_y)],
                           check=True, timeout=10)
            time.sleep(1)
            subprocess.run(["xdotool", "click", "--clearmodifiers", "--delay", "100", "1"],
                           check=True, timeout=10)
            wait_for_log_marker(process, log_path, "VantaLoader menu button activated", 15)
            wait_for_log_marker(process, log_path, "VantaLoader mod menu rendered; entries=4", 30)
            subprocess.run(["xdotool", "mousemove", "--sync", "20", "20"],
                           check=True, timeout=10)
            time.sleep(1)
            subprocess.run(["java", "-cp", str(Path(__file__).resolve().parent), "ScreenCapture",
                            str(output / "mod-menu-probe.png")], check=True, timeout=15)
            shutil.copy2(output / "mod-menu-probe.png", output / "mod-menu.png")
            print(f"Mod menu confirmed by runtime render hook; {len(mods)} mod entries loaded.")

            # Return to the title screen and open the singleplayer world picker.
            subprocess.run(["xdotool", "key", "Escape"], check=True, timeout=10)
            time.sleep(2)
            subprocess.run(["xdotool", "mousemove", "--sync", "640", "296"], check=True, timeout=10)
            subprocess.run(["xdotool", "click", "--clearmodifiers", "1"], check=True, timeout=10)
            time.sleep(3)
            subprocess.run(["java", "-cp", str(Path(__file__).resolve().parent), "ScreenCapture",
                            str(output / "create-world-probe.png")], check=True, timeout=15)
            print("Opened Minecraft world creation screen.")

            # Create a fresh singleplayer world and require a LocalPlayer.aiStep callback.
            subprocess.run(["xdotool", "mousemove", "--sync", "482", "688"], check=True, timeout=10)
            subprocess.run(["xdotool", "click", "--clearmodifiers", "1"], check=True, timeout=10)
            wait_for_log_marker(process, log_path,
                                "VantaLoader gameplay probe: local player tick observed", 180)
            time.sleep(3)
            subprocess.run(["java", "-cp", str(Path(__file__).resolve().parent), "ScreenCapture",
                            str(output / "in-world.png")], check=True, timeout=15)
            print("Singleplayer world loaded; LocalPlayer.aiStep runtime probe observed.")

        logs = log_path.read_text(encoding="utf-8", errors="replace")
        manifest_path = Path(__file__).with_name("game-tests.json")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if manifest.get("schema_version") != 1 or manifest.get("minecraft_version") != VERSION:
            raise RuntimeError("Unsupported or mismatched in-game test manifest")
        required = tuple(dict.fromkeys(
            marker for test in manifest.get("tests", [])
            for marker in test.get("required_log_markers", [])
        ))
        missing = [marker for marker in required if marker not in logs]
        if missing:
            raise RuntimeError("Missing loader log markers: " + ", ".join(missing))
        logs = log_path.read_text(encoding="utf-8", errors="replace")
        start_index = logs.index("VantaLoader integration probe: client tick START observed")
        end_index = logs.index("VantaLoader integration probe: client tick END observed")
        if start_index >= end_index:
            raise RuntimeError("Client tick END was observed before START")
        print(f"Captured Minecraft {VERSION} title, mod-menu, and in-world screenshots; client tick phases and LocalPlayer.aiStep verified with {len(mods)} mods loaded.")
        return 0
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)

def write_evidence_report(output: Path, exit_code: int, error: str | None) -> None:
    """Persist machine-readable proof even when an in-game assertion fails."""
    output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((Path(__file__).with_name("game-tests.json")).read_text(encoding="utf-8"))
    log_path = output / "instance" / "logs" / "visual-smoke.log"
    log_text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.is_file() else ""
    tests = []
    for test in manifest.get("tests", []):
        missing = [marker for marker in test.get("required_log_markers", []) if marker not in log_text]
        screenshots = []
        for filename in test.get("screenshots", []):
            path = output / filename
            item = {"file": filename, "exists": path.is_file()}
            if path.is_file():
                item["size_bytes"] = path.stat().st_size
                item["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
            screenshots.append(item)
        missing_screenshots = [item["file"] for item in screenshots if not item["exists"]]
        tests.append({
            "id": test["id"], "description": test.get("description", ""),
            "passed": not missing and not missing_screenshots,
            "missing_log_markers": missing, "screenshots": screenshots
        })
    report = {
        "schema_version": 1, "suite": "VantaLoader in-game integration",
        "minecraft_version": manifest.get("minecraft_version"),
        "status": "passed" if exit_code == 0 and all(test["passed"] for test in tests) else "failed",
        "exit_code": exit_code, "error": error,
        "commit": os.environ.get("GITHUB_SHA"), "run_id": os.environ.get("GITHUB_RUN_ID"),
        "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "generated_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "log": {"file": str(log_path.relative_to(output)) if log_path.is_file() else None,
                "exists": log_path.is_file(),
                "sha256": hashlib.sha256(log_path.read_bytes()).hexdigest() if log_path.is_file() else None,
                "tail": log_text.splitlines()[-200:]},
        "tests": tests
    }
    report_path = output / "game-test-evidence.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"In-game evidence report written to {report_path}")

if __name__ == "__main__":
    output_parser = argparse.ArgumentParser(add_help=False)
    output_parser.add_argument("--output", type=Path, required=True)
    parsed, _ = output_parser.parse_known_args()
    exit_code = 0
    failure = None
    try:
        exit_code = main()
    except Exception as error:
        exit_code = 1
        failure = f"{type(error).__name__}: {error}"
        print("VantaLoader in-game tests failed: " + failure, file=sys.stderr)
        raise
    finally:
        write_evidence_report(parsed.output.resolve(), exit_code, failure)
