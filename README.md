# VantaLoader

**A modern Minecraft: Java Edition mod loader built for the Vanta ecosystem.**

VantaLoader is an independent loader project intended to provide a well-engineered runtime for Minecraft mods, with a particular focus on developer tooling, transformation diagnostics, compatibility, and reload-aware mod architecture.

> [!WARNING]
> **Early-stage project.** This repository is currently being scaffolded. VantaLoader is not yet a functional Minecraft mod loader, does not currently launch Minecraft, and has no verified Minecraft-version support. Features described below are project goals, not implemented capabilities.

## Project goals

- **Independent loader core** — VantaLoader should own its bootstrap, mod lifecycle, metadata, and runtime contracts.
- **VantaTransform** — a first-party transformation management layer with ordering, conflict reporting, and diagnostics.
- **Mixin interoperability** — investigate a compatibility adapter for SpongePowered Mixin rather than forcing existing developers to abandon it.
- **Reload-aware architecture** — make resource and explicitly reloadable component updates practical during development. Arbitrary Java class or Mixin hot replacement is not promised; JVM and transformation constraints must be respected.
- **Reliable mod management** — deterministic discovery, dependency validation, version constraints, and actionable errors.
- **Developer experience** — a documented API, Gradle tooling, templates, test utilities, and useful diagnostics.
- **Vanta ecosystem integration** — Vanta Client is intended to be a first-party, first-class VantaLoader mod.
- **Compatibility without overclaiming** — existing loader ecosystems may be bridged where technically and legally practical, but compatibility will only be advertised after testing.

## Current status

| Area | Status |
|---|---|
| Repository and build scaffolding | In progress |
| Loader bootstrap | Not implemented |
| Mod metadata and discovery | Not implemented |
| Dependency resolution | Not implemented |
| Mod lifecycle and event system | Not implemented |
| VantaTransform | Design stage |
| SpongePowered Mixin adapter | Planned |
| Reload system | Design stage |
| Minecraft version support | Not established |
| Vanta Client integration | Planned |

The [roadmap](docs/roadmap.md) tracks the intended development sequence. The [documentation index](docs/README.md) explains the current design direction.

## Development prerequisites

- JDK 21
- Gradle 8.14 or compatible Gradle 8.x installation

The project uses the Gradle Kotlin DSL. A checked-in Gradle Wrapper will be added once the wrapper distribution is established.

Build the project:

```bash
gradle clean build
```

Run tests:

```bash
gradle test
```

At this scaffolding stage, the build is intentionally dependency-light. Minecraft, ASM, Mixin, and other runtime dependencies will be introduced only with documented reasons and compatible version constraints.

## Repository layout

```text
.
├── docs/
│   ├── architecture/
│   ├── development/
│   ├── modding/
│   └── roadmap.md
├── src/
│   ├── main/java/
│   └── test/java/
├── .editorconfig
├── .gitignore
├── build.gradle.kts
├── CONTRIBUTING.md
├── LICENSE
├── SECURITY.md
└── settings.gradle.kts
```

## Documentation

- [Documentation index](docs/README.md)
- [Architecture overview](docs/architecture/overview.md)
- [Transformation pipeline](docs/architecture/transformation-pipeline.md)
- [Mod metadata proposal](docs/modding/mod-metadata.md)
- [Building from source](docs/development/building.md)
- [Roadmap](docs/roadmap.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

Specifications marked as proposals are not stable APIs. Expect changes before the first stable release.

## Contributing

Contributions, design feedback, and carefully documented bug reports are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a change. For security-sensitive reports, follow [SECURITY.md](SECURITY.md) instead of filing a public issue.

## License

VantaLoader is licensed under the [MIT License](LICENSE). Third-party dependencies, when introduced, remain subject to their own licenses.
