# Transformation Pipeline

## Status

**Status: JVM transformation backend implemented; first exact-version game hook fixture-tested**

VantaLoader bundles ASM 9.11.1 (core, tree, commons, util, and analysis) in its self-contained agent. A fixture integration test transforms, verifies, defines, and invokes a compiled class. The exact-version Minecraft 1.21.11 adapter also injects client tick START/END dispatch into a fixture class matching the target. This is not yet proof of a successful real Minecraft launch.

## Native transformation API

A mod receives a mod-scoped `TransformationRegistry` through `ModContext.transformations()`. It registers a transformation using:

- A unique transformation ID within that mod.
- An exact binary target class name.
- An integer priority; lower values run first.
- A callback receiving immutable contribution metadata and a defensive copy of the current class bytes.

Contributions are ordered deterministically by priority, mod ID, and transformation ID. Each transformation receives the previous contribution's output. Classes without an exact target match are not modified.

The JVM agent installs the transformer before native mod initialization. Mods register transformations during `onInitialize`, before Minecraft's main class starts. The backend does not request retransformation of classes that have already loaded.

## Validation and failure behavior

- Every non-empty transformation result is parsed by ASM and must retain the target class identity.
- Malformed class files, empty results, and changed class identities fail closed.
- A failed required transformation is logged with mod ID, transformation ID, and target class; the transformer returns deliberately invalid class bytes so the JVM cannot silently continue with an unpatched target.
- ASM parsing is not a substitute for full JVM verification or correct stack-map-frame computation.
- The bundled `HierarchyClassWriter` resolves common supertypes from class-file resources without defining game classes. Resolution is limited to resources visible to the supplied class loader.
- Semantic conflicts between individually valid transformations cannot yet be detected automatically.

## Ordering and duplicate registrations

- Stable order: priority, then mod ID, then transformation ID.
- Duplicate transformation IDs are rejected within one mod.
- Different mods may reuse the same transformation ID.
- Multiple contributions to one class are composed in deterministic order.
- Registration after a target class has already loaded does not cause that class to be retransformed.

## Minecraft adapter boundary

Adapters are selected by exact Minecraft version string. Unknown versions receive no adapter and no game lifecycle capability. The first adapter targets Minecraft 1.21.11's unobfuscated `net.minecraft.client.Minecraft.tick()V` method and emits typed `ClientTickEvent` START and END events through a static agent bridge.

The adapter is fixture-tested, not yet validated against a real game process. If the expected target method is missing, the transformation fails closed instead of silently claiming the hook worked. The client event exposes the Minecraft client as `Object` so the stable VantaLoader API does not bundle Minecraft classes.

## Remaining work

1. Verify the adapter against a clean real Minecraft 1.21.11 launch.
2. Add additional lifecycle hooks only after each exact target and descriptor is verified.
3. Add structured transformation reports and more precise conflict diagnostics.
4. Implement version-specific adapters for additional Minecraft releases.
5. Validate the existing experimental SpongePowered Mixin service against real third-party Mixin mods, then advertise `MIXIN_INTEROP` only after reproducible Minecraft integration tests.

## Reload constraints

Class transformation usually occurs as classes are defined. JVM instrumentation supports only constrained forms of retransformation or redefinition. Ordinary class redefinition is not a general mechanism for changing class structure, superclass relationships, or existing object layouts.

Therefore:

- Implement resource/data reload independently of class transformation.
- Give reloadable components explicit lifecycle and cleanup contracts.
- Mark structural bytecode changes restart-required unless a specific, tested runtime path proves otherwise.
- Preserve a known runtime state on failure wherever the underlying operation allows it.
