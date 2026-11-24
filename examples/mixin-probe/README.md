# Third-Party Mixin Probe

This is a separate native mod JAR, not part of VantaLoader's implementation classes. It declares its own Mixin configuration in `vanta.mod.json`, contributes a SpongePowered Mixin targeting the exact Minecraft 1.21.11 title-screen runtime class, and emits a log marker only when the injected handler actually executes.

The in-game CI builds this JAR independently, launches it alongside the native examples, and requires both config registration and the runtime injection marker. This verifies the narrow Mixin service path used by this example; it does not establish compatibility with arbitrary Fabric/Forge mods or all Mixin features.

Build after building VantaLoader:

```sh
gradle build
gradle -p examples/mixin-probe clean build
```
