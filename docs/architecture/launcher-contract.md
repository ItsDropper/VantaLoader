# Vanta Launcher Integration Contract

This document describes the implemented handoff between Vanta Launcher and the VantaLoader Java agent. It is a narrow bootstrap contract, not a promise that every Minecraft version or mod is compatible.

## Responsibilities

**Vanta Launcher owns:**

- Instance creation and isolation.
- Downloading an immutable commit-specific nightly release.
- Verifying the agent asset against the release's SHA-256 checksum.
- Validating the agent manifest and required bundled runtime classes.
- Installing Mojang's official client mappings into the instance.
- Selecting the Minecraft version and client environment.
- Starting the JVM with the instance as its working directory.

**VantaLoader owns:**

- The Java-agent entrypoint and pre-main initialization.
- Native mod discovery under the selected instance's `mods/` directory.
- Metadata validation, dependency resolution, classloader isolation, and mod lifecycle.
- Its transformation coordinator and the capabilities it actually implements.

The launcher must not duplicate mod discovery, dependency resolution, or runtime initialization. The loader must not depend on launcher implementation classes or private launcher APIs.

## JVM properties

Vanta Launcher passes these properties before `-javaagent:`:

| Property | Meaning |
| --- | --- |
| `vanta.instance.directory` | Absolute normalized path to the selected instance. |
| `vanta.minecraft.version` | Exact Minecraft version selected for the instance. |
| `vanta.environment` | Launch environment; currently `client`. |
| `vanta.minecraft.mappings` | Instance-local path to the verified official client mappings. |

The instance directory is the authoritative location for native mod discovery and per-instance files. VantaLoader resolves it from `vanta.instance.directory`. For standalone users and older launcher integrations, it falls back to the JVM `user.dir` value.

The launcher also sets the process working directory to the same instance directory and supplies Minecraft's normal `--gameDir` argument. These are deliberate compatibility measures: Minecraft, older mods, and libraries frequently resolve relative paths from the working directory. The explicit property prevents the agent's bootstrap contract from depending solely on that process-wide behavior.

## Compatibility and failure behavior

- The launcher pins the agent to an immutable commit-specific nightly identifier; it must not silently replace an existing instance's loader version during ordinary launch.
- Repair is the explicit update path and must preserve instance data such as worlds, configuration, and native mod JARs.
- The agent is loaded with `-javaagent:`, not placed on Minecraft's application classpath.
- Missing or invalid agent artifacts and missing required mappings are launch-blocking errors. The launcher should direct users to repair rather than continue with a partially configured loader.
- The agent must fail startup with a useful diagnostic if native loader initialization fails; it must not silently start Minecraft with mods skipped.
- New launcher properties are additive. Standalone operation retains the `user.dir` fallback.

## Current verification boundary

CI launches Minecraft 1.21.11 through the agent, initializes native mods, and verifies the title screen and in-game Mods screen. The launcher and agent handoff is exercised by that path. The visual smoke test does not enter a world, so Auto Sprint's player-tick behavior is not yet verified in a live game. Mixin interoperability and Fabric/Forge mod compatibility are not implemented.

Any future property or artifact-format change should be updated here and tested at both ends: launcher command construction and agent-side resolution.
