# VantaLoader Hello World Mod

A minimal native mod demonstrating the VantaLoader entrypoint API.

## Build

From the repository root:

```bash
gradle jar
cd examples/hello-mod
gradle build
```

The mod JAR is generated at `examples/hello-mod/build/libs/vantaloader-hello-mod-1.0.0.jar`.

## Run

Copy the generated JAR into a test game's `mods/` directory, then run the standalone native runtime from the repository root:

```bash
gradle run --args="--game-dir ./example-game --mods-dir ./example-game/mods"
```

The loader should log the mod's initialization and shutdown messages.

**Scope:** this example targets the current standalone VantaLoader native runtime. It does not hook into Minecraft yet.
