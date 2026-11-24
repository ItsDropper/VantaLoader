# Vanta Mod API (VMA) Reference

**API version:** 1.1.0  
**Status:** Pre-alpha; the contracts below are implemented in the matching API JAR. Minecraft-facing coverage remains adapter-specific.

VMA is the native mod development contract for VantaLoader. Its design is to make ordinary mod development declarative and type-safe, while retaining ASM and Mixin as optional advanced escape hatches. It is not a compatibility facade for Fabric API; existing Fabric mods do not become compatible automatically.

## Developer tooling

The included Gradle plugin configures Java compilation against the standalone VantaLoader API JAR and validates the required schema-1 mod descriptor before compiling source. It is currently distributed as a Gradle composite build; see [plugin setup](../../gradle-plugin/README.md).

## Core entrypoint and context

- `VantaMod`: main mod lifecycle (`onInitialize`, `onShutdown`).
- `ModContext`: mod identity, loader/game versions, environment, game/config paths, logger, capability snapshot, events, typed config, resource reload registrations, and advanced transformation registration.
- `VantaModApi`: API semantic version and required-capability validation.
- `MinecraftVersion`, `MinecraftCapabilities`, `MinecraftCapability`: explicit version identity and conservative runtime capability negotiation.

## Event system

`EventBus` supports typed event subscriptions, named `EventPriority` bands (`HIGHEST`, `HIGH`, `NORMAL`, `LOW`, `LOWEST`), explicit numeric priorities, deterministic equal-priority ordering, removable subscriptions, and mod-owned cleanup. The two-argument `subscribe(eventType, listener)` overload uses `NORMAL`; the enum overload is preferred for ordinary mods, while numeric priorities remain available for precise interop. The verified Minecraft 1.21.11 adapter emits `ClientTickEvent` START/END and `KeyInputEvent` events. Raw keyboard input exposes the GLFW-compatible key code, scan code, action, modifier bitmask, window handle, and opaque keyboard-handler reference. The mod-scoped `KeyBindingRegistry` builds press/repeat/release bindings on top of that event and automatically unregisters them with the mod. Callbacks run on Minecraft's input thread and must not block it. User remapping and a persistent key-map screen are not implemented yet. The client handle remains opaque; stable cross-version player/world facades and a remappable high-level keybinding registry are not yet implemented.

## Persistent configuration

`ModConfig` stores typed `ConfigKey` values in an isolated per-mod JSON file. Supported value types are string, boolean, integer, long, decimal, and enum. Integer and decimal keys support bounds. Writes are validated; reads reject invalid persisted values; saves use temporary-file replacement; reload discards unsaved state.

## Inter-mod services

`ModContext.services()` exposes a typed service registry shared by the active native-mod runtime. Providers register a stable `namespace:path` `ServiceKey<T>` in their own mod-ID namespace. Consumers use the same key to discover an optional service with `find(...)` or require it with `require(...)`. Duplicate IDs and type mismatches fail explicitly; a provider's registrations are removed when it shuts down or startup rolls back. Declare a normal mod dependency on providers whose service is required so dependency ordering initializes the provider first.

```java
public interface ProfileProvider {
    Optional<Profile> load(String id);
}

private static final ServiceKey<ProfileProvider> PROFILES =
        ServiceKey.of("profilemod:profiles", ProfileProvider.class);

// Provider mod, during onInitialize:
context.services().register(PROFILES, this::loadProfile);

// Consumer mod, during onInitialize (with profilemod declared as a dependency):
ProfileProvider profiles = context.services().require(PROFILES);
profiles.load("default").ifPresent(this::applyProfile);
```

Services are in-process Java objects, not a network protocol. Keep service interfaces small, version them deliberately, and avoid retaining service instances beyond the provider's lifetime.

## Background tasks

`ModTasks` provides a serial, mod-owned asynchronous executor. Tasks run with the owning mod class loader as the thread context class loader. The runtime cancels outstanding work and waits up to five seconds for the worker to stop before closing the mod class loader. Use it for I/O and background computation; do not access Minecraft client objects from the worker thread.

## Resource lifecycle

`ResourceReloadRegistry` watches mod-owned directories. `ResourceReloadListener` prepares reversible `ResourceReloadPlan` instances. All affected plans prepare before commits; failure triggers rollback. This is file-resource reload, not Minecraft resource-pack reload.

## Accessors and transformations

- `Accessor`, `Invoker`, and `AccessorFactory` provide typed MethodHandle-backed views without injecting mod interfaces into Minecraft classes.
- `TransformationRegistry` provides exact-target ordered class transformations for advanced integrations.
- `ClassTransformContext`, `MinecraftMappingView`, and `HierarchyClassWriter` provide mapping and bytecode utilities.
- SpongePowered Mixin is available through a narrow verified interoperability path.

Most mods should not need these bytecode facilities for ordinary behavior. Prefer supported typed events, config, and resource services.

## In-game integration

`ModMenuApi`, `ModMenuEntry`, and `ModMenuHeader` integrate with VantaLoader's in-game mod list. The mod settings factory intentionally avoids compile-time Minecraft dependencies; the mod's screen implementation remains version-specific.

## Explicitly not stable/implemented yet

The current public API does not claim stable universal abstractions for command registration, player/world/entity access, registries, rendering, networking, server lifecycle, user-remappable keybinding settings, or Minecraft resource-pack reload. These are planned VMA domains. They will be added with actual adapter implementations and in-game proof rather than no-op interfaces.

## Compatibility rule

A mod must check `ModContext.minecraftCapabilities()` or call `VantaModApi.requireCapabilities(...)` before using optional game integrations. A parsed version string is not proof that a hook exists. API JAR and agent JAR must come from the same VantaLoader build.
