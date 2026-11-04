# Transformation Pipeline

## Status

**Status: Initial JVM backend implemented**

VantaLoader exposes a native, exact-target class transformation API backed by Java Instrumentation. The agent bundles ASM 9.11.1 core, tree, commons, util, and analysis modules. A fixture integration test transforms a compiled class, runs ASM verification, defines the transformed bytes in an isolated class loader, and asserts changed behavior. This is a tested JVM transformation layer, not yet a Minecraft compatibility layer.

## Current API

A mod receives a mod-scoped `TransformationRegistry` through `ModContext.transformations()`. It can register a `ClassTransformer` using:

- A unique transformation ID within that mod.
- An exact binary target class name, such as `net.minecraft.client.Minecraft`.
- An integer priority; lower values run first.
- A callback receiving immutable contribution metadata and a defensive copy of the current class bytes.

Contributions are ordered deterministically by priority, mod ID, and transformation ID. Each transform receives the previous transform's output, allowing an explicit bytecode pipeline for the same target. Classes without an exact target match are not modified.

The JVM agent installs the service before native mod initialization. Mods register contributions during `onInitialize`, before Minecraft's main class starts. The backend does not request retransformation of classes that have already loaded. ASM packages are parent-first in native mod class loaders so mods use the same ASM classes bundled by the agent.

## Important limitations

- VantaLoader does not currently bundle ASM or another bytecode manipulation library. A mod must provide its own implementation or dependency for producing valid class-file bytes.
- The current API does not validate bytecode structure or verify stack-map frames. Invalid output can cause class loading or verification failures.
- Java Instrumentation treats transformer failures as failed transformations according to JVM behavior; the service logs the failing mod, contribution, and target. It cannot promise a process-wide rollback of classes already defined.
- Exact target matching is supported; target discovery, method-level selectors, conflict analysis, remapping, and transformation timing reports are not implemented.
- No Minecraft-version-specific targets or lifecycle hooks are implemented yet.
- SpongePowered Mixin interoperability is not implemented.

## Ordering and conflicts

- Stable ordering is priority, then mod ID, then transformation ID.
- Duplicate transformation IDs are rejected within a mod.
- Different mods may use the same transformation ID.
- Transformations targeting the same class are composed in the deterministic order above.
- Semantic conflicts between two valid bytecode transformations cannot yet be detected automatically.

## Intended next stages

1. Add bytecode validation and a supported ASM-based authoring API.
2. Add real JVM integration tests that transform and load fixture classes, not merely test byte-array callbacks.
3. Introduce Minecraft-version adapters and stable lifecycle events.
4. Add a Mixin compatibility adapter while preserving Mixin's native semantics.
5. Add structured transformation reports and compatibility diagnostics.

## Reload constraints

Class transformation usually occurs as classes are defined. JVM instrumentation supports only constrained forms of retransformation or redefinition. Ordinary class redefinition is not a general mechanism for changing class structure, superclass relationships, or existing object layouts. Existing Mixin transformations are not automatically reversible.

Therefore:

- Implement resource/data reload independently of class transformation.
- Give reloadable components explicit lifecycle and cleanup contracts.
- Mark structural bytecode changes restart-required unless a specific, tested runtime path proves otherwise.
- Preserve a known runtime state on failure wherever the underlying operation allows it.

## Diagnostics

Failures include the contributing mod ID, transformation ID, target class, and original exception. Local paths and environment values are not included by default.
