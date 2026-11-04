# Architecture Overview

## Status

This is a design document, not a description of an implemented runtime. Interfaces and module boundaries may change as working code is developed.

## Objective

VantaLoader is intended to be an independent Minecraft: Java Edition mod loader. It should own its bootstrap, mod descriptor, lifecycle, dependency model, and transformation coordination instead of being a renamed wrapper around another loader.

It should also provide interoperability boundaries for existing technologies. In particular, SpongePowered Mixin should be considered a compatibility backend rather than something developers must abandon on day one.

## Proposed components

### Bootstrap and game integration

Identify the target Minecraft runtime, prepare the launch environment, validate required inputs, and hand control to the loader runtime. Isolate Minecraft-version-specific integration behind explicit adapters; game internals and launch details can change between releases.

### Mod discovery and metadata

Discover candidate mod artifacts, parse and validate descriptors, check loader/game compatibility, and report malformed inputs before initialization. Metadata is declarative input, not a security boundary. Java mods execute inside Minecraft with the permissions of the current user.

### Dependency and ordering engine

Resolve required, optional, and suggested dependencies; detect missing or incompatible versions; and produce deterministic initialization order. Cycles and ambiguous constraints must produce actionable diagnostics.

### Lifecycle and event system

Define explicit discovery, preparation, initialization, runtime, and shutdown phases. Registrations should have ownership and cleanup semantics so reloadable components can unregister resources they own.

### Transformation coordinator

Collect transformation contributions, validate targets and ordering, detect conflicts where possible, and coordinate supported transformations before affected classes are defined. Diagnostics should identify the contributing mod, target, stage, and failure reason.

### Compatibility adapters

Isolate third-party systems such as SpongePowered Mixin. Avoid leaking implementation-specific types throughout the native API unless that exposure is an intentional contract.

### Reload coordinator

Initially prioritize resources and explicitly reloadable components. Distinguish reloadable changes from class-structural changes that require a full game restart.

### Diagnostics

Provide structured startup reports, dependency explanations, transformation traces, compatibility results, and actionable crash context. Avoid collecting or transmitting user data; telemetry is not a project goal.

## Design principles

1. Correctness before compatibility claims: every supported version or mod needs a reproducible test.
2. Explicit boundaries between core, game-version adapters, transformation backends, and optional integrations.
3. Determinism: equivalent inputs should produce reproducible resolution and ordering.
4. No hidden network behavior, telemetry, or mandatory accounts.
5. Errors should identify the mod, dependency, target, or lifecycle phase involved.
6. Reloadable components must declare lifecycle and cleanup behavior.
7. Running arbitrary Java mods is not made safe by metadata or loader-level permissions.

## Deliberate non-goals

- Reimplementing all of Minecraft.
- Claiming universal compatibility with Fabric, Forge, NeoForge, Quilt, or their mods.
- Guaranteeing hot replacement of arbitrary Java classes or Mixin transformations.
- Treating declared mod capabilities as an operating-system security boundary.
- Adding telemetry or mandatory accounts.

## Package direction

Possible separation, not a committed package API:

- bootstrap — launch preparation and runtime handoff.
- metadata — descriptor parsing and validation.
- dependency — constraints, graph construction, and resolution.
- lifecycle — phases, entrypoints, and owned registrations.
- transform — transformation contracts and coordination.
- compat.mixin — SpongePowered Mixin adapter.
- reload — reload planning and supported operations.
- diagnostics — reports and structured error context.

Do not create empty abstractions solely to fill packages. Introduce boundaries when the first implementation needs them.
