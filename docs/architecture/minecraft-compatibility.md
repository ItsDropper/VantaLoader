# Minecraft compatibility and capability negotiation

VantaLoader must not infer compatibility from a version string alone. Minecraft releases differ in launch layout, class names, descriptors, lifecycle timing, Java baseline, and available game systems. A parsed version is an identity, not proof that an integration works.

## Mod-facing contract

Native mods use `ModContext.minecraftVersionInfo()` to inspect the supplied game version and `ModContext.minecraftCapabilities()` to query facilities that the active runtime has actually verified.

```java
import dev.itsdropper.vantaloader.api.MinecraftCapability;

if (context.minecraftCapabilities().supports(
        MinecraftCapability.CLIENT_LIFECYCLE_EVENTS)) {
    // Register client lifecycle integration only when it is available.
}
```

The capability snapshot is immutable. `supportsAll` is appropriate when a feature needs several facilities; `supportsAny` is appropriate for alternate implementations. Unknown or unsupported facilities must remain absent. Mods should degrade gracefully rather than guessing from `minecraftVersion()`.

## Current capability reporting

- `CLASS_TRANSFORMATIONS`: reported only when the agent successfully installs the JVM instrumentation transformer.
- `OFFICIAL_MOJANG_MAPPINGS`: reported only when the configured mapping file is successfully loaded.
- `CLIENT_LIFECYCLE_EVENTS`: not implemented; never reported.
- `SERVER_LIFECYCLE_EVENTS`: not implemented; never reported.
- `RESOURCE_RELOAD`: not implemented; never reported.
- `MIXIN_INTEROP`: not implemented; never reported.

The standalone runtime has no Instrumentation instance and therefore reports no transformation capability. The default `ModContext` implementation also reports an empty set, preserving safe behavior for older/custom implementations.

## Adapter boundary

The next implementation layer should be a version adapter selected by exact Minecraft version identity, not a broad numeric comparison. Each adapter should own:

1. Version-specific launch and target-class identification.
2. Named-to-runtime class/member mapping behavior.
3. Lifecycle hook installation and event dispatch.
4. A declared capability set, populated only after each subsystem is installed successfully.
5. A diagnostic identity describing the exact version family and adapter revision.

Adapters should be grouped only where bytecode and lifecycle contracts are demonstrably equivalent. A single adapter spanning all `1.x` releases would be an unsupported assumption. Unknown versions should start with no game-integration capabilities and must not receive a guessed adapter.

## Java runtime compatibility is a separate axis

Minecraft version compatibility and JVM compatibility must be tracked independently. The current project compiles for Java 21. That prevents the current agent artifact from loading in a Java 8 Minecraft process, regardless of whether the bytecode mappings are correct. In addition, the agent currently implements the Java 9+ `ClassFileTransformer` overload. A Java 8-capable agent needs the Java 8 transformer entry point and a Java 8-compatible bytecode/API baseline across every class loaded before Minecraft starts.

Do not claim Minecraft 1.8 support until all of the following are verified together:

- The agent and every eagerly loaded dependency can run on the JVM required by the target Minecraft release.
- The agent installs its transformer through the JVM's supported Instrumentation method.
- A clean, unmodified target client launches with the agent.
- A fixture mod receives the correct target names and lifecycle callbacks.
- A gameplay transformation is verified against the real target class and does not break startup.

A multi-release JAR by itself does not solve this: the `Premain-Class` and its bootstrap dependency graph must already be loadable on the old JVM.

## Compatibility claim policy

VantaLoader native mods can target the stable VantaLoader API, but this does not make existing Fabric, Forge, Quilt, or other loader mods binary-compatible. Compatibility with those ecosystems requires explicit adapters and their respective APIs. Publish a Minecraft-version support claim only after clean launch and integration tests exist for that exact version or a proven adapter family.
