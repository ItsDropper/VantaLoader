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

- [ ] Select and document the first Minecraft version target.
- [ ] Research the selected version's official launch metadata and runtime requirements.
- [ ] Implement a reproducible launch profile and bootstrap handoff through Vanta Launcher.
- [ ] Launch an unmodified Minecraft client through VantaLoader.
- [ ] Preserve structured diagnostics across bootstrap and game startup.

**Exit criteria:** a clean Minecraft instance launches through VantaLoader without mods.

## Stage 3 — Minecraft mod integration and transformations

- [ ] Define Minecraft-version adapter boundaries.
- [ ] Implement the first in-game lifecycle and event hooks.
- [x] Specify native transformation contracts.
- [x] Implement deterministic exact-target dispatch and transformation ordering.
- [x] Bundle ASM and test transformed fixture class loading.
- [ ] Add hierarchy-aware frame computation, conflict reports, and Minecraft target mappings.
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
