# VantaLoader

**An independent Minecraft: Java Edition mod loader for the Vanta ecosystem.**

VantaLoader has moved from repository scaffolding into its first executable native-mod runtime. It discovers mod JARs, validates metadata, resolves dependencies, loads entrypoints, and manages initialization and shutdown.

> [!WARNING]
> **Pre-alpha runtime.** CI now launches Minecraft 1.21.11 with the Java agent, initializes two native mods, and verifies the title screen and in-game Mods menu. The first gameplay example, Auto Sprint, is built and its bytecode transformation is unit-tested; the visual smoke test does not enter a world, so actual player movement behavior still needs in-game validation. Mixin interoperability and broad gameplay-mod compatibility are not available.

## Implemented

- Native vanta.mod.json descriptor parsing with descriptions, JAR icons, license/project links, and required/optional dependency declarations.
- An in-game Mods screen with runtime-derived loader/version metadata, per-mod descriptions and project links, scrolling, and opt-in settings-screen buttons through the public Mod Menu API.
- Mod JAR discovery from a configured directory.
- Semantic version values and basic version constraints.
- Required dependency validation and deterministic topological ordering.
- Dependency-aware per-mod class loaders with child-first mod classes and parent-first Java/VantaLoader API classes.
- Explicit visibility of a mod's declared dependencies; unrelated mods cannot see its implementation classes.
- Native VantaMod entrypoints and a small ModContext API.
- A typed synchronous cross-mod event bus with deterministic priority ordering, listener failure aggregation, removable subscriptions, and automatic per-mod subscription cleanup.
- Dependency-order initialization and reverse-order shutdown.
- Initialization rollback, failure-tolerant reverse shutdown, and startup diagnostics.
- A buildable [Hello World example mod](examples/hello-mod/README.md).
- A standalone [Auto Sprint gameplay mod](examples/auto-sprint/README.md) using official 1.21.11 mappings and the native transformation API.
- A self-contained Minecraft Java-agent artifact with Gson and ASM 9.11.1 bundled and manifest/content verification.
- A standalone `VantaLoader-api.jar` for compiling native mods without depending on implementation classes.
- An ASM integration test that transforms, verifies, defines, and invokes a compiled fixture class.
- A Java Instrumentation transformation backend with mod-scoped registration, deterministic priority ordering, defensive byte-array handling, and failure diagnostics.
- An API-level ASM `HierarchyClassWriter` that resolves common supertypes from class-file resources without defining game classes, including interface ancestry and multidimensional array merges.
- Official Mojang client class-mapping parsing and named-class-to-obfuscated-runtime target dispatch, with launcher-managed SHA-1-verified mapping downloads.
- Unit tests for metadata, discovery, dependency resolution, and lifecycle ordering.
- End-to-end integration tests that compile independent Java mod JARs, verify JAR-origin loading and lifecycle callbacks, and prove declared-dependency visibility, unrelated-mod isolation, and same-name class separation.

## Status

| Area | Status |
|---|---|
| Java runtime and build | Implemented |
| Mod JAR discovery | Implemented |
| Metadata validation | Implemented; display metadata and dependency relations parsed |
| Mod Menu API | In-game mod list verified in CI; opt-in settings-screen factory implemented |
| Dependency resolution | Implemented |
| Native entrypoint lifecycle | Implemented |
| Cross-mod event bus | Implemented; not Minecraft game events |
| Minecraft Java-agent bootstrap | Experimental; Minecraft 1.21.11 title/menu launch smoke-tested in CI |
| Minecraft class/member mappings | Official class, method, and field mapping lookup by exact named JVM descriptor; version adapters remain incomplete |
| Native class transformation API | ASM-backed pipeline and Auto Sprint injection tested on fixtures; player-world execution unverified |
| VantaTransform planning, validation and reports | In progress |
| SpongePowered Mixin adapter | Planned |
| Resource reload | Not implemented |
| Vanta Client integration | Planned |

See the [roadmap](docs/roadmap.md) for milestone acceptance criteria.

## Requirements

- JDK 21
- Gradle 8.14 or compatible Gradle 8.x

Build and test:

~~~bash
gradle clean build
gradle test
~~~

Run the native runtime:

~~~bash
gradle run --args="--game-dir ./game --mods-dir ./mods"
~~~

Build the example mod (after building the loader JAR):

~~~bash
gradle build
gradle -p examples/hello-mod build
gradle -p examples/auto-sprint build
~~~

See [Hello World](examples/hello-mod/README.md) for the native lifecycle example and [Auto Sprint](examples/auto-sprint/README.md) for the first gameplay-oriented mod. Auto Sprint targets Minecraft 1.21.11 and requires verified class-transformation and official-mapping capabilities. It writes its default setting to `config/vantaloader/auto_sprint/enabled.properties`; set `enabled=false` to disable it.

Build the self-contained Minecraft agent artifact:

~~~bash
gradle agentJar
~~~

The artifacts are written to `build/agent/VantaLoader-agent.jar` and `build/agent/VantaLoader-api.jar`. The agent expects the Minecraft instance directory to be the process working directory and discovers native mod JARs in its `mods/` folder. The standalone API JAR is the compile-time dependency for native mods.

Create an installable application distribution:

~~~bash
gradle installDist
~~~

The generated distribution includes runtime dependencies and launch scripts under build/install/vantaloader/.

## Native mod format

Each native VantaLoader mod is a JAR containing vanta.mod.json at its root and a main entrypoint implementing VantaMod.

~~~json
{
  "schemaVersion": 1,
  "id": "example",
  "name": "Example Mod",
  "version": "1.0.0",
  "description": "A short description shown in Vanta's mod list.",
  "icon": "assets/example/icon.png",
  "license": "MIT",
  "website": "https://example.com",
  "sources": "https://github.com/example/example-mod",
  "entrypoints": {
    "main": "com.example.ExampleMod"
  },
  "depends": {
    "corelib": ">=1.0.0 <2.0.0"
  },
  "recommends": {
    "maplib": ">=2.0.0"
  },
  "suggests": {
    "optional_ui": "*"
  },
  "conflicts": {
    "legacy_api": "<2.0.0"
  },
  "breaks": {
    "broken_addon": "*"
  }
}
~~~

Supported dependency constraints are exact versions, >=, >, <=, <, and *. A constraint containing multiple tokens is an AND expression.

Example entrypoint:

~~~java
package com.example;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

public final class ExampleMod implements VantaMod {
    @Override
    public void onInitialize(ModContext context) {
        context.logger().info("Example mod initialized");
    }

    @Override
    public void onShutdown() {
        // Release resources owned by this mod.
    }
}
~~~

The entrypoint needs a public no-argument constructor. Mods initialize after their declared dependencies and shut down in reverse order. `ModContext` exposes the game directory, per-mod config directory, loader version, Minecraft version, client environment, logger, and transformation registry. The launcher supplies the Minecraft version and client environment before agent startup. `ModContext.minecraftCapabilities()` exposes an immutable snapshot of verified facilities; mods must query it rather than guessing support from a version string. Lifecycle events, resource reload, and Mixin interoperability remain unavailable until implemented and tested. See the [native mod quickstart](docs/modding/getting-started.md) for a complete Gradle setup and an ASM transformation example.

### Native class transformations

A mod can register an exact-target transformation during initialization:

~~~java
context.transformations().register(
    "client-bootstrap",
    "net.minecraft.client.Minecraft",
    100,
    (transform, originalClass) -> {
        context.logger().info("Transforming " + transform.targetClassName());
        // Return valid class-file bytes. Use a bytecode library for real edits.
        return originalClass;
    }
);
~~~

Lower priorities run first. Contributions targeting the same class are composed in priority, mod ID, and transformation ID order. The agent bundles ASM 9.11.1 (`asm`, `asm-tree`, `asm-commons`, `asm-util`, and `asm-analysis`) and makes those classes parent-first so mods share one runtime ASM copy. A transformer can use ASM to edit bytecode. VantaLoader parses every output and rejects a changed target class identity; full JVM verification still depends on the game and target class hierarchy. Mods that need computed stack-map frames can use `HierarchyClassWriter` with the Minecraft class loader; it reads class headers as resources rather than loading or initializing game classes. Its resolution is only as complete as the resources exposed by that loader. Official Mojang mappings are loaded from the instance's `minecraft/client_mappings.txt`. Mods may register named class targets, and the transformation context exposes read-only class, method, and field name resolution. Member lookup requires the exact named JVM descriptor to disambiguate overloads. An exact-version 1.21.11 client-tick adapter demonstrates bytecode injection against fixture classes. The CI launch verifies native mod initialization and menu rendering, but does not enter a world or execute player tick hooks. Automatic bytecode remapping and additional version adapters remain unimplemented. See the [transformation pipeline](docs/architecture/transformation-pipeline.md) for limitations and the next implementation stages.

### Capability negotiation

Native mods can check optional integrations before registering against them:

~~~java
import dev.itsdropper.vantaloader.api.MinecraftCapability;

if (context.minecraftCapabilities().supports(MinecraftCapability.CLIENT_LIFECYCLE_EVENTS)) {
    // Register client lifecycle integration only when the runtime provides it.
}
~~~

The current agent reports class transformation support only after installing Instrumentation and reports official mappings only after loading the configured mapping file. The standalone runtime and older/custom ModContext implementations default to an empty set. This prevents the loader from advertising unimplemented hooks simply because a Minecraft version parsed successfully. See [Minecraft compatibility](docs/architecture/minecraft-compatibility.md).

## Architecture

VantaLoader owns its native runtime; it is not a renamed Fabric Loader distribution. The first integration milestone is a Java agent that initializes native entrypoints before Minecraft's main class. It installs an exact-target JVM transformation service before native mod initialization. The bundled ASM backend is exercised against real compiled fixture classes, and the API includes resource-based hierarchy resolution for frame computation. CI verifies a real Minecraft 1.21.11 process launch, native mod initialization, title-screen integration, and Mods-menu rendering. The Auto Sprint transformer is tested against a fixture class and registered in that process, but the visual smoke test does not enter a world or execute the player tick hook. Mixin interoperability is not implemented.

SpongePowered Mixin is intended to be supported through an interoperability adapter where practical. Native VantaLoader transformation APIs will not require mod developers to depend on Mixin-specific types.

Arbitrary Java class hot replacement is not promised. Resource reload, component lifecycle reload, and class transformation have different JVM constraints and will be implemented as distinct capabilities.

## Documentation

- [Documentation index](docs/README.md)
- [Architecture overview](docs/architecture/overview.md)
- [Vanta Launcher integration contract](docs/architecture/launcher-contract.md)
- [Transformation pipeline](docs/architecture/transformation-pipeline.md)
- [Minecraft compatibility](docs/architecture/minecraft-compatibility.md)
- [Native mod quickstart](docs/modding/getting-started.md)
- [Native mod metadata](docs/modding/mod-metadata.md)
- [Building from source](docs/development/building.md)
- [Roadmap](docs/roadmap.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## Security

VantaLoader is not a sandbox for untrusted Java code. A mod runs with the permissions of the operating-system user running the process. Install mods only from sources you trust.

## License

MIT. Third-party dependencies remain subject to their respective licenses.
