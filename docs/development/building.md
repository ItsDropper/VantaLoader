# Building from Source

## Requirements

- JDK 21.
- Gradle 8.14 or a compatible Gradle 8.x installation.
- Git.

The build uses the Java toolchain to compile against Java 21. Ensure a JDK, not only a Java runtime, is available.

Check the environment:

~~~bash
java --version
gradle --version
git --version
~~~

## Build

From the repository root:

~~~bash
gradle clean build
gradle test
~~~

The initial repository contains build scaffolding only. The test task may have no tests until test infrastructure and runtime code are introduced.

## Gradle Wrapper

The Gradle Wrapper is not included in the initial scaffold. It should be added before the project depends on a specific Gradle distribution for contributor or CI workflows. Once committed, the wrapper should be the documented and preferred way to run builds.

## Dependencies

The initial build deliberately has no Minecraft, ASM, Mixin, or third-party runtime dependencies. Add dependencies only when an implementation requires them, with a recorded version, license review, and compatibility rationale.

## Troubleshooting

- If Gradle cannot find Java, verify JAVA_HOME points to a JDK 21 installation.
- If Gradle reports an unsupported Java version, check both the Gradle version and active JDK.
- If a build fails after changing dependency or build configuration, retry with gradle --refresh-dependencies clean build and retain the original error output for diagnosis.
