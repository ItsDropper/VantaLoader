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
- [x] Implement exact-version adapter selection and verify the Minecraft 1.21.11 client tick adapter against the real client in CI.
- [x] Implement typed client tick START/END event dispatch and assert both phases from a native probe mod in the real Minecraft 1.21.11 runtime.
- [x] Specify native transformation contracts.
- [x] Implement deterministic exact-target dispatch and transformation ordering.
- [x] Implement and test both legacy Java 8 and Java 9+ Instrumentation transformer callback signatures.
- [x] Bundle ASM and test transformed fixture class loading.
- [x] Download and validate official client mappings in Vanta Launcher; parse class mappings and dispatch named class targets to obfuscated runtime classes.
- [x] Parse official method/field mappings and resolve member names using exact named JVM descriptors.
- [x] Add deterministic cross-mod shared-target diagnostics; hierarchy-aware frame computation is implemented through resource-only `HierarchyClassWriter` and covered by dedicated hierarchy tests.
- [ ] Add adapters for additional Minecraft releases, each backed by exact-version runtime evidence.
- [x] Implement an experimental SpongePowered Mixin service/bootstrap, mod-container classpath, and per-mod configuration discovery/registration.
- [x] Verify a separately packaged third-party-style Mixin mod against a real Minecraft 1.21.11 target in CI; publish only this narrow verified scope, not a broad compatibility claim.
- [x] Verify ordered transformations from multiple mods targeting the same real LocalPlayer class in a real singleplayer world; cover later-transform failure with fail-closed tests.
- [x] Verify Auto Sprint behavior in an actual Minecraft world (maintainer gameplay test) and exercise its transformed LocalPlayer class in a CI-created world.

**Exit criteria:** a native test mod changes a verified in-game behavior, transformation failures are diagnosable, and a separately packaged Mixin config applies a real target injection. Additional Minecraft versions remain a follow-on compatibility task.

## Stage 4 — Reload-aware development

- [x] Implement recursive file watching for mod-registered resource directories and a transactional prepare/commit/rollback reload contract.
- [x] Scope reload registrations to the owning mod and remove them on shutdown or startup rollback; stop the watcher before unloading mod class loaders.
- [x] Add a development file watcher, coalesced change batches, and bounded reload diagnostics.
- [x] Report mod JAR changes as restart-required; never hot-swap loaded classes, Mixin configs, or transformation registrations.
- [x] Test successful reload, preparation failure, commit failure rollback, registration cleanup, and restart-required detection.

**Exit criteria:** registered mod-owned resources reload transactionally without restarting; mod JAR changes are reported as restart-required. Minecraft resource packs and arbitrary game assets are outside this stage.

## Stage 5 — Vanta Mod API (VMA)

- [x] Establish VMA around lifecycle, typed events, capabilities, transformations, accessors, Mod Menu, and resource reload.
- [x] Add isolated persistent typed configuration with validation, atomic persistence, and reload semantics.
- [ ] Add game-facing commands, keybindings, player/world state, registries, rendering, and networking, each backed by verified version adapters.
- [ ] Add first-party Vanta Client reference integration after the relevant game APIs exist.
- [ ] Provide developer documentation, templates, and Gradle tooling.
- [ ] Add compatibility tests for external mods.

**Exit criteria:** third-party developers can build useful native mods without writing ASM for ordinary supported features; every advertised game-facing subsystem has a real implementation and in-game test.

## Stage 6 — Distribution and ecosystem

- [x] Add Vanta Launcher instance-creation, install, launch-agent, and repair integration for the experimental bootstrap.
- [x] Pin launcher instances to immutable commit-specific loader releases and update them through repair.
- [ ] Verify clean install, real Minecraft launch, relaunch, and repair end to end.
- [ ] Publish version manifests and mod-development resources.
- [ ] Evaluate Modrinth launcher and loader-recognition requirements with Modrinth.
- [ ] Publish only compatibility claims backed by tests.

**Exit criteria:** users can install, update, and launch a verified VantaLoader Minecraft instance through Vanta Launcher. Modrinth recognition remains an external milestone.
