# VantaLoader Proof Mod

A small end-to-end smoke-test mod for verifying that VantaLoader discovers a
native mod JAR and invokes its entrypoint from a real Minecraft launch.

On initialization, it:

- Logs a distinctive `VANTALOADER PROOF MOD INITIALIZED` banner.
- Writes the detected loader, Minecraft, and environment versions to
  `config/vantaloader/vanta_proof/loaded.txt` inside the instance directory.
- Logs the absolute path to that proof file.

This example intentionally uses only the native VantaLoader entrypoint API.
It does **not** add an in-game HUD or gameplay feature: Minecraft lifecycle
hooks and a verified in-game hook are not implemented yet.

## Requirements

- JDK 21
- Gradle 8.14 or compatible Gradle 8.x
- A VantaLoader agent/API build from this repository

## Build

Run these commands from the VantaLoader repository root:

```powershell
gradle clean build
```

This builds the loader and generates:

- `build/agent/VantaLoader-agent.jar`
- `build/agent/VantaLoader-api.jar`

Then build the example mod:

```powershell
cd examples/proof-mod
gradle clean build
```

The distributable mod JAR is:

```text
build/libs/vantaloader-proof-mod-1.0.0.jar
```

## Install into a Vanta instance

1. Open the Vanta Launcher instance that has VantaLoader enabled.
2. Open that instance's game directory.
3. Copy `build/libs/vantaloader-proof-mod-1.0.0.jar` into its `mods` directory.
4. Launch the instance normally through Vanta Launcher.

Do not put the API JAR or agent JAR in the mods directory. Vanta Launcher should
continue to provide the agent through its existing VantaLoader integration.

## Verify

A successful load should produce the log banner:

```text
VANTALOADER PROOF MOD INITIALIZED
```

It should also create this file in the instance directory:

```text
config/vantaloader/vanta_proof/loaded.txt
```

Open `loaded.txt`. It should contain the mod ID, mod version, VantaLoader
version, Minecraft version, environment, and initialization timestamp.

If the banner and proof file both appear, the mod was discovered, its entrypoint
class was loaded, and `onInitialize` completed during the Minecraft launch.

If Minecraft starts but neither appears, check the VantaLoader agent log for
discovery or initialization errors. Reaching the title screen alone does not
prove this mod loaded.
