# VantaLoader

**An independent Minecraft: Java Edition mod loader for the Vanta ecosystem.**

VantaLoader has moved from repository scaffolding into its first executable native-mod runtime. It discovers mod JARs, validates metadata, resolves dependencies, loads entrypoints, and manages initialization and shutdown.

> [!WARNING]
> **Pre-alpha runtime.** The standalone native runtime is implemented, and an experimental Java agent can initialize native VantaLoader entrypoints before Minecraft's main class. The agent is packaged for Vanta Launcher integration, but a real Minecraft launch has not yet been validated. There are no Minecraft event hooks, bytecode transformations, or Mixin interoperability yet, so this is not a general-purpose gameplay mod platform.

## Implemented

- Native vanta.mod.json descriptor parsing and schema validation.
- Mod JAR discovery from a configured directory.
- Semantic version values and basic version constraints.
- Required dependency validation and deterministic topological ordering.
- Dependency-aware per-mod class loaders with child-first mod classes and parent-first Java/VantaLoader API classes.
- Explicit visibility of a mod's declared dependencies; unrelated mods cannot see its implementation classes.
- Native VantaMod entrypoints and a small ModContext API.
- Dependency-order initialization and reverse-order shutdown.
- Initialization rollback, failure-tolerant reverse shutdown, and startup diagnostics.
- A buildable [Hello World example mod](examples/hello-mod/README.md).
- A self-contained Minecraft Java-agent artifact (`gradle agentJar`) with a `Premain-Class` entrypoint, bundled runtime dependencies, and manifest verification.
- Unit tests for metadata, discovery, dependency resolution, and lifecycle ordering.
- End-to-end integration tests that compile independent Java mod JARs, verify JAR-origin loading and lifecycle callbacks, and prove declared-dependency visibility, unrelated-mod isolation, and same-name class separation.

## Status

| Area | Status |
|---|---|
| Java runtime and build | Implemented |
| Mod JAR discovery | Implemented |
| Metadata validation | Implemented |
| Dependency resolution | Implemented |
| Native entrypoint lifecycle | Implemented |
| Minecraft Java-agent bootstrap | Experimental; real launch not verified |
| Minecraft-version adapters | Not implemented |
| VantaTransform | Not implemented |
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
gradle jar
cd examples/hello-mod
gradle build
~~~

See [the example instructions](examples/hello-mod/README.md) for running it with the standalone runtime.

Build the self-contained Minecraft agent artifact:

~~~bash
gradle agentJar
~~~

The artifact is written to `build/agent/VantaLoader-agent.jar`. The agent expects the Minecraft instance directory to be the process working directory and discovers native mod JARs in its `mods/` folder.

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
  "entrypoints": {
    "main": "com.example.ExampleMod"
  },
  "depends": {
    "corelib": ">=1.0.0 <2.0.0"
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

The entrypoint needs a public no-argument constructor. Mods initialize after their declared dependencies and shut down in reverse order.

## Architecture

VantaLoader owns its native runtime; it is not a renamed Fabric Loader distribution. The first integration milestone is a Java agent that initializes native entrypoints before Minecraft's main class. It does not currently transform game classes or expose Minecraft lifecycle events.

SpongePowered Mixin is intended to be supported through an interoperability adapter where practical. Native VantaLoader transformation APIs will not require mod developers to depend on Mixin-specific types.

Arbitrary Java class hot replacement is not promised. Resource reload, component lifecycle reload, and class transformation have different JVM constraints and will be implemented as distinct capabilities.

## Documentation

- [Documentation index](docs/README.md)
- [Architecture overview](docs/architecture/overview.md)
- [Transformation pipeline](docs/architecture/transformation-pipeline.md)
- [Native mod metadata](docs/modding/mod-metadata.md)
- [Building from source](docs/development/building.md)
- [Roadmap](docs/roadmap.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

## Security

VantaLoader is not a sandbox for untrusted Java code. A mod runs with the permissions of the operating-system user running the process. Install mods only from sources you trust.

## License

MIT. Third-party dependencies remain subject to their respective licenses.
