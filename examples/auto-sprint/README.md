# Vanta Auto Sprint

The first gameplay-oriented native VantaLoader example mod.

Auto Sprint starts sprinting while the forward key is held, provided the player is not using an item and sneak is not held. It only sets sprint when Minecraft has not already enabled it; normal game logic remains responsible for cancelling sprint when movement conditions stop being valid.

## Compatibility

- Minecraft 1.21.11
- VantaLoader with class transformations and official Mojang client mappings
- JDK 21

The mod disables itself on other Minecraft versions or when the required verified capabilities are unavailable. It does not depend on Fabric, Forge, or another loader.

## Configuration

After the first launch, edit:

`config/vantaloader/auto_sprint/enabled.properties`

Set `enabled=false` to disable the feature, or `enabled=true` to enable it. Restart Minecraft after changing the file.

## Build

Build VantaLoader first so its standalone API JAR exists:

```sh
gradle build
gradle -p examples/auto-sprint clean build
```

Copy the generated JAR from `build/libs/` into the instance's `mods/` directory.
