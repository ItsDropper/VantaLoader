# Contributing to VantaLoader

VantaLoader is pre-alpha. Architecture and public APIs may change substantially.

## Before making a change

- Read the README, architecture overview, and roadmap.
- For substantial features or architectural changes, open an issue first to agree on the problem and direction.
- Do not assume a documented proposal is implemented or stable.
- Never include credentials, private server information, proprietary Minecraft files, or files copied from another project without permission.

## Development environment

- JDK 21.
- Gradle 8.14 or a compatible Gradle 8.x installation.
- Git.

The Gradle Wrapper is not yet included. Until it is added, use an installed Gradle distribution.

~~~bash
gradle --version
gradle clean build
gradle test
~~~

See docs/development/building.md for details.

## Code expectations

- Keep changes focused and explain the underlying problem.
- Prefer small, explicit interfaces over premature abstractions.
- Separate Minecraft-version-specific code from loader-wide contracts.
- Preserve deterministic behavior where practical.
- Validate external metadata and produce actionable errors.
- Do not claim runtime compatibility without reproducible tests.
- Do not promise arbitrary Java or Mixin hot replacement; document JVM and transformation constraints.
- Add or update tests for behavior changes when test infrastructure exists.
- Update documentation when changing architecture, metadata, user-visible behavior, or compatibility claims.
- Explain the license and maintenance status of any new runtime dependency.

## Style

- UTF-8, LF line endings, and four spaces for Java indentation.
- Follow standard Java naming conventions.
- Use descriptive names and avoid unrelated formatting changes.

## Pull requests

Explain the motivation, design, and trade-offs; link related issues; list only checks actually run; and call out breaking API or metadata changes. Keep generated output and local files out of commits.

By contributing, you agree that your contributions are submitted under the repository's MIT license, unless a different arrangement is agreed to in writing.
