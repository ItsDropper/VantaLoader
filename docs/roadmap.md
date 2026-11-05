# Roadmap

Milestones describe intended work, not promises or release dates. A milestone is complete only when its acceptance criteria are demonstrated by code and reproducible tests.

## Stage 0 — Repository foundations

- [x] Establish repository documentation and contribution policies.
- [x] Choose an initial license.
- [x] Add Java/Gradle project scaffold.
- [x] Add automated build workflow and dependency update configuration.

## Stage 1 — Native loader runtime

- [x] Implement native mod JAR discovery.
- [x] Implement versioned metadata parsing and validation.
- [x] Implement semantic version comparison and dependency constraints.
- [x] Implement deterministic dependency ordering and cycle detection.
- [x] Implement native entrypoint loading and lifecycle callbacks.
- [x] Implement reverse-order shutdown and initialization rollback.
- [x] Replace the initial shared classloader with dependency-aware per-mod classloaders; test declared-dependency visibility, unrelated-mod isolation, and same-name class separation.
- [x] Verify implementation with a successful GitHub Actions build.
- [x] Add end-to-end fixture JAR test that compiles and packages a separate mod, then verifies JAR-origin loading and lifecycle callbacks.
- [x] Add a buildable Hello World mod project and standalone runtime instructions.

**Exit criteria:** a fixture mod JAR is discovered, validated, dependency-ordered, loaded, initialized, and shut down in automated tests.

## Stage 2 — Minecraft launch integration

- [ ] Define the supported Minecraft/JVM compatibility matrix, including a Java 8 agent target for legacy clients.
- [ ] Select and document the first Minecraft version target.
- [ ] Research the selected version's official launch metadata and runtime requirements.
- [ ] Implement a reproducible launch profile and bootstrap handoff through Vanta Launcher.
- [ ] Launch an unmodified Minecraft client through VantaLoader.
- [ ] Preserve structured diagnostics across bootstrap and game startup.

**Exit criteria:** a clean Minecraft instance launches through VantaLoader without mods.

## Stage 3 — Minecraft mod integration and transformations

- [x] Define the initial version identity boundary: parse legacy/modern release strings and weekly snapshots without imposing a false global ordering; expose it through a binary-compatible default ModContext method.
- [x] Define the mod-facing Minecraft capability contract and conservative runtime reporting.
- [x] Implement exact-version adapter selection and a fixture-tested Minecraft 1.21.11 client tick adapter; real-game verification remains pending.
- [x] Implement typed client tick START/END event dispatch in the 1.21.11 fixture adapter; real-game hook verification remains open.
- [x] Specify native transformation contracts.
- [x] Implement deterministic exact-target dispatch and transformation ordering.
- [x] Implement and test both legacy Java 8 and Java 9+ Instrumentation transformer callback signatures.
- [x] Bundle ASM and test transformed fixture class loading.
- [x] Download and validate official client mappings in Vanta Launcher; parse class mappings and dispatch named class targets to obfuscated runtime classes.
- [x] Parse official method/field mappings and resolve member names using exact named JVM descriptors.
- [ ] Add version-specific target adapters, hierarchy-aware frame computation, and conflict reports.
- [ ] Evaluate and implement a SpongePowered Mixin compatibility adapter.
- [ ] Verify behavior against real Minecraft targets.

**Exit criteria:** a native test mod changes a verified in-game behavior, and transformation failures are diagnosable.

## Stage 4 — Reload-aware development

- [ ] Implement resource reload support.
- [ ] Define ownership and cleanup for reloadable components.
- [ ] Add a development file watcher and reload report.
- [ ] Classify transformation changes as reloadable or restart-required.
- [ ] Test failure and rollback behavior.

**Exit criteria:** supported resources/components reload without restarting; unsupported class changes are rejected clearly.

## Stage 5 — Vanta ecosystem integration

- [ ] Port a small, isolated Vanta Client feature as the first-party reference integration.
- [ ] Expand the port after the public API is stable enough.
- [ ] Provide developer documentation, templates, and Gradle tooling.
- [ ] Add compatibility tests for external mods.

**Exit criteria:** Vanta Client has a reproducible native integration path and third-party developers can build a basic mod from documented contracts.

## Stage 6 — Distribution and ecosystem

- [x] Add Vanta Launcher instance-creation, install, launch-agent, and repair integration for the experimental bootstrap.
- [x] Pin launcher instances to immutable commit-specific loader releases and update them through repair.
- [ ] Verify clean install, real Minecraft launch, relaunch, and repair end to end.
- [ ] Publish version manifests and mod-development resources.
- [ ] Evaluate Modrinth launcher and loader-recognition requirements with Modrinth.
- [ ] Publish only compatibility claims backed by tests.

**Exit criteria:** users can install, update, and launch a verified VantaLoader Minecraft instance through Vanta Launcher. Modrinth recognition remains an external milestone.
