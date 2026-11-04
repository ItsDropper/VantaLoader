# Building from Source

## Requirements

- JDK 21.
- Gradle 8.14 or a compatible Gradle 8.x installation.

## Build and test

~~~bash
gradle clean build
gradle test
~~~

The project uses the Gradle Kotlin DSL and Java 21 toolchain. Gson is resolved from Maven Central and JUnit 5 is used for tests.

## Run the native runtime

~~~bash
gradle run --args="--game-dir ./game --mods-dir ./mods"
~~~

The runtime creates the mods directory if it does not exist. It inspects every JAR in that directory for a root-level vanta.mod.json, validates descriptors, resolves required dependencies, and initializes entrypoints.

## Create an application distribution

~~~bash
gradle installDist
~~~

The distribution under build/install/vantaloader/ contains launch scripts and runtime dependencies. Use the generated script instead of invoking the application JAR directly because Gson is an external runtime dependency.

## Current limitation

This command runs the native VantaLoader mod runtime. It does not launch Minecraft yet. Minecraft launch integration is a separate roadmap milestone.
