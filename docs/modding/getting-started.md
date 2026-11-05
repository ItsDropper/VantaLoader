# Native Mod Development Quickstart

VantaLoader's native mod API is separate from Fabric, Forge, and NeoForge APIs. Those loader ecosystems are not currently compatible.

## Requirements

- JDK 21 and Gradle 8.14
- A matching `VantaLoader-api.jar` and `VantaLoader-agent.jar` from the same [published release](https://github.com/ItsDropper/VantaLoader/releases)

Keep the API JAR as a compile-only dependency. Do not bundle VantaLoader API or ASM classes in your mod JAR.

## Gradle

```kotlin
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/VantaLoader-api.jar"))
    compileOnly("org.ow2.asm:asm:9.11.1")
    compileOnly("org.ow2.asm:asm-commons:9.11.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

Put the API JAR in `libs/VantaLoader-api.jar`.

## Descriptor

Create `src/main/resources/vanta.mod.json`:

```json
{
  "schemaVersion": 1,
  "id": "examplemod",
  "name": "Example Mod",
  "version": "1.0.0",
  "entrypoints": {
    "main": "com.example.ExampleMod"
  }
}
```

## Entrypoint

```java
package com.example;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

public final class ExampleMod implements VantaMod {
    @Override
    public void onInitialize(ModContext context) {
        context.logger().info("Example Mod initialized");
    }

    @Override
    public void onShutdown() {
        // Release resources owned by this mod.
    }
}
```

Build with `gradle build`, then copy the resulting JAR into the target instance's `mods/` directory.

## Cross-mod events

Mods can exchange their own typed events through `ModContext.events()`. Dispatch is synchronous; lower priority values run first, equal priorities retain registration order, and listener failures are collected while remaining listeners still run. A mod's subscriptions are automatically removed when that mod shuts down or startup rolls back.

```java
public record ProfileLoaded(String profileId) { }

// Subscribe during onInitialize:
context.events().subscribe(ProfileLoaded.class, 0, event ->
    context.logger().info("Loaded profile " + event.profileId()));

// Publish from a mod when its own profile loading completes:
context.events().publish(new ProfileLoaded("default"));
```

This is a native mod-to-mod communication API, not a Minecraft event hook. It does not currently expose game ticks, input, rendering, resource reload, or other Minecraft lifecycle events.

## ASM transformation example

The agent bundles ASM 9.11.1 and exposes it to mods at runtime. This example replaces a string constant while preserving the method's existing stack shape and frames:

```java
context.transformations().register(
    "replace-title",
    "com.example.Target",
    100,
    (transform, originalBytes) -> {
        ClassReader reader = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(
                        access, name, descriptor, signature, exceptions);
                if (!"getTitle".equals(name)
                        || !"()Ljava/lang/String;".equals(descriptor)) {
                    return next;
                }
                return new MethodVisitor(Opcodes.ASM9, next) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(
                                "Original title".equals(value) ? "Vanta" : value);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
);
```

The example target is illustrative, not a verified Minecraft class. Use Mojang-mapped binary class names (dots, not slashes) for Minecraft targets and register before the target class loads. Vanta Launcher downloads the selected version's official client mappings and supplies them to the agent. The transformation context exposes `mappings().runtimeClassName(...)`, `runtimeMethodName(owner, name, namedDescriptor)`, and `runtimeFieldName(owner, name, namedDescriptor)`. Member resolution requires the exact named JVM descriptor, so overloaded methods are not guessed. This API resolves names; it does not automatically rewrite arbitrary ASM instructions or descriptors for a mod. For edits that change control flow or stack shape, valid stack-map frames must be computed with a hierarchy resolver that can see the target game's classes; the default system class loader is not sufficient for many Minecraft types.

## Current compatibility boundary

The loader currently provides native entrypoints, dependency ordering, isolated mod class loaders, and an ASM-backed exact-target transformation pipeline tested against fixture classes. It does not yet provide Minecraft lifecycle events, automatic bytecode remapping, Mixin interoperability, Fabric/Forge APIs, or a verified in-game hook. It is not yet a general-purpose replacement for established Minecraft loaders.
