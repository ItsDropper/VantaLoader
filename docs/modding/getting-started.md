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
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-commons:9.8")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

Put the API JAR in `libs/VantaLoader-api.jar`.

## Vanta Mod Gradle plugin

VantaLoader ships a Gradle plugin that applies the Java plugin, adds the API JAR as a compile-only dependency, and validates the native descriptor before Java compilation. During pre-alpha it is consumed as a composite build; see [gradle-plugin/README.md](../../gradle-plugin/README.md) for setup. The manual `compileOnly(files(...))` setup below remains supported.

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

This is a native mod-to-mod communication API, not a Minecraft event hook. The exact Minecraft 1.21.11 adapter exposes client tick START/END, immutable player/world snapshots, and raw keyboard input events when the launcher supplies the matching official mappings. High-level key bindings are available with declared defaults; command registration, registry mutation, and networking APIs are not yet implemented. The resource API reloads explicitly registered mod-owned files; it does not reload Minecraft resource packs or arbitrary game assets.

## Client player and world state

```java
context.events().subscribe(ClientGameTickEvent.class, EventPriority.NORMAL, event -> {
    if (event.phase() != ClientTickEvent.Phase.START || !event.snapshot().inWorld()) return;
    ClientPlayerSnapshot player = event.snapshot().player().orElseThrow();
    ClientWorldSnapshot world = event.snapshot().world().orElseThrow();
    context.logger().fine("Position: " + player.x() + ", " + player.y() + ", " + player.z());
    context.logger().fine("Dimension: " + world.dimensionId());
});
```

This API is currently client-only and verified against Minecraft 1.21.11. Snapshot reads happen on the client tick thread; do not retain live game objects or block the callback. Check `CLIENT_GAME_STATE` before depending on this integration.

## Client keyboard input

The exact Minecraft 1.21.11 adapter publishes immutable player/world snapshots and raw key input through the typed event bus. `ClientGameTickEvent` is emitted at both client tick phases; its `snapshot()` contains optional `player()` and `world()` values. These snapshots contain copied values, not live Minecraft objects. Events run on Minecraft's input thread; keep callbacks short and dispatch expensive work through `context.tasks()`. The current event reports raw GLFW-compatible values and does not yet provide user-remappable keybinding settings.

```java
context.events().subscribe(KeyInputEvent.class, EventPriority.NORMAL, event -> {
    if (event.pressed() && event.keyCode() == 82) {
        context.logger().info("The R key was pressed");
    }
});
```

## High-level key bindings

When the active adapter supports keyboard input, register a mod-owned binding instead of matching raw key codes yourself:

```java
if (context.keyBindings().isAvailable()) {
    context.keyBindings().register(new KeyBinding(
            "toggle-feature",
            "Toggle feature",
            82, // GLFW key code for R
            0,  // no modifiers
            action -> {
                if (action == KeyBindingAction.PRESSED) {
                    toggleFeature();
                }
            }
    ));
}
```

Bindings receive `PRESSED`, `REPEATED`, and `RELEASED` transitions and expose their pressed state through `KeyBindingRegistration.isPressed()`. Bindings can be remapped at runtime through `registration.remap(keyCode, modifiers)`; the mapping is persisted in the owning mod's VMA config. `registration.resetToDefault()` restores the declared key. A dedicated key-map settings screen is not implemented. Bindings are automatically removed when their mod shuts down.

## Inter-mod services

A mod can publish a typed service for dependent mods without using global static registries. Use a service key whose namespace is the provider's mod ID:

```java
public interface GreetingService {
    String greeting(String name);
}

private static final ServiceKey<GreetingService> GREETING =
        ServiceKey.of("greetingmod:greeting", GreetingService.class);

// Provider:
context.services().register(GREETING, name -> "Hello, " + name);

// Consumer (declare greetingmod as a required dependency):
String message = context.services().require(GREETING).greeting("Vanta");
```

Registrations are unique, type-checked, scoped to the provider, and removed automatically when it shuts down.

## Persistent VMA configuration

Each mod receives an isolated JSON configuration at `config/vantaloader/<mod-id>/vma-config.json`. Typed keys support strings, booleans, integers, longs, decimals, and enums. Integer/decimal keys can enforce bounds; reads and writes are validated. `save()` uses a temporary file and atomic replacement where supported; `reload()` discards unsaved changes. Unknown keys are preserved.

```java
private static final ConfigKey<Boolean> ENABLED = ConfigKey.bool("enabled", true);
private static final ConfigKey<Integer> RADIUS = ConfigKey.integer("radius", 8, 1, 32);

public void onInitialize(ModContext context) {
    ModConfig config = context.config();
    boolean enabled = config.get(ENABLED);
    config.set(RADIUS, 12);
    try { config.save(); } catch (IOException exception) { throw new UncheckedIOException(exception); }
}
```

## Resource reloads

Mods can watch a directory inside the game directory. A file change prepares replacement state first; VantaLoader prepares every affected registration before committing any of them. If preparation or commit fails, rollback runs in reverse order. Each plan must restore the state that was active before preparation/commit. Callbacks run on a dedicated daemon worker with the owning mod's context class loader; keep expensive work off Minecraft's render/tick thread.

```java
Path resources = context.configDirectory().resolve("resources");
context.resources().register("language-files", resources, reload -> {
    Map<String, String> replacement = loadTranslations(reload.directory());
    Map<String, String> previous = currentTranslations;
    return new ResourceReloadPlan() {
        @Override public void commit() { currentTranslations = replacement; }
        @Override public void rollback() { currentTranslations = previous; }
    };
});
```

The loader watches registered resource directories recursively. Changes to JARs in the instance `mods/` directory are reported as restart-required; Java classes, Mixin configurations, and transformation registrations are never hot-swapped. Loader integrations can inspect `ModRuntime.resourceReloadReport()` for completed/failed reloads and restart-required paths. Registrations are removed when their mod shuts down or startup rolls back.

## VMA-first development

Prefer high-level VMA contracts for ordinary behavior. Use typed persistent configuration, typed events, resource reload registrations, Mod Menu integration, and accessor views where appropriate. The [VMA Showcase](../../examples/vma-showcase/README.md) demonstrates settings and client events without ASM. The [Auto Sprint example](../../examples/auto-sprint/README.md) demonstrates the lower-level transformation escape hatch.

## ASM transformation example

The agent bundles ASM 9.8 and exposes it to mods at runtime. This example replaces a string constant while preserving the method's existing stack shape and frames:

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

The loader currently provides native entrypoints, dependency ordering, isolated mod class loaders, and an ASM-backed exact-target transformation pipeline. The exact Minecraft 1.21.11 adapter exposes client tick START/END events, immutable player/world snapshots, keyboard input, and title/pause-screen hooks. The in-world snapshot probe and mod-menu rendering are verified by the GitHub Actions Minecraft 1.21.11 integration suite. An experimental SpongePowered Mixin service and per-mod configuration registration path is present; third-party Mixin compatibility remains unverified. Automatic bytecode remapping and Fabric/Forge APIs are not provided. VantaLoader is not yet a general-purpose replacement for established Minecraft loaders.
