# Roadmap

This roadmap describes intended milestones, not promises or release dates. A milestone is complete only when its acceptance criteria are demonstrated by code and reproducible tests.

## Stage 0 — Repository foundations

- [x] Establish repository documentation and contribution policies.
- [x] Choose an initial license.
- [x] Add a minimal Java/Gradle project scaffold.
- [ ] Add and verify the Gradle Wrapper.
- [ ] Establish automated build checks and test conventions.

## Stage 1 — Executable foundation

- [ ] Choose and document the initial Minecraft version target.
- [ ] Establish a reproducible development launch environment.
- [ ] Implement a minimal bootstrap and runtime handoff.
- [ ] Produce structured startup diagnostics.
- [ ] Launch an unmodified target game through VantaLoader.

**Exit criteria:** a clean, repeatable launch of the selected Minecraft version with clear diagnostics and no mod loading required.

## Stage 2 — Mod discovery and lifecycle

- [ ] Finalize and implement the versioned mod descriptor.
- [ ] Discover and validate mod artifacts.
- [ ] Implement required and optional dependency resolution.
- [ ] Define deterministic lifecycle phases and entrypoint contracts.
- [ ] Load a minimal example mod.

**Exit criteria:** a test mod is discovered, validated, initialized, and shut down through documented contracts.

## Stage 3 — Transformations and interoperability

- [ ] Specify native transformation contracts.
- [ ] Implement transformation planning and ordering.
- [ ] Add conflict diagnostics and transformation tests.
- [ ] Evaluate and implement a SpongePowered Mixin adapter.
- [ ] Verify behavior against real Minecraft targets.

**Exit criteria:** native and adapter-backed transformations have repeatable tests and useful failure reports.

## Stage 4 — Reload-aware development

- [ ] Implement resource reload support.
- [ ] Define ownership and cleanup for reloadable components.
- [ ] Add a development file watcher and reload report.
- [ ] Classify transformation changes as reloadable or restart-required.
- [ ] Test failure and rollback behavior.

**Exit criteria:** supported resource/component changes reload without restarting; unsupported class changes are rejected clearly instead of being advertised as hot-reloadable.

## Stage 5 — Vanta ecosystem integration

- [ ] Build a native VantaLoader example mod.
- [ ] Port a small, isolated Vanta Client feature as the first-party reference integration.
- [ ] Expand the port after the public API is stable enough.
- [ ] Provide developer documentation, templates, and Gradle tooling.
- [ ] Add compatibility tests for external mods.

**Exit criteria:** Vanta Client has a reproducible native integration path and third-party developers can build a basic mod from the documentation.

## Stage 6 — Distribution and ecosystem

- [ ] Define release and compatibility support policies.
- [ ] Integrate VantaLoader into Vanta Launcher.
- [ ] Publish version manifests and mod-development resources.
- [ ] Evaluate Modrinth launcher and loader-recognition requirements with Modrinth.
- [ ] Publish only compatibility claims backed by tests.

**Exit criteria:** users can install, update, and launch a verified VantaLoader instance through Vanta Launcher. Modrinth recognition is a separate external milestone and is not guaranteed by this roadmap.

## Release discipline

Before a stable 1.0 release, the project should have a documented public API, repeatable builds, automated tests for core behavior, a version compatibility policy, dependency/license inventory, security reporting process, and migration guidance for breaking changes.
