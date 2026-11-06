# Native Mod Metadata — Schema 1

**Status: Implemented in the native metadata parser**

Every native VantaLoader mod is a JAR containing a UTF-8 `vanta.mod.json` file at the archive root. Existing descriptors remain valid; all display and optional dependency fields are additive.

## Example

~~~json
{
  "schemaVersion": 1,
  "id": "example",
  "name": "Example Mod",
  "version": "1.0.0",
  "description": "A concise explanation of what this mod does.",
  "icon": "assets/example/icon.png",
  "license": "MIT",
  "website": "https://example.com",
  "sources": "https://github.com/example/example-mod",
  "mixins": ["example.mixins.json"],
  "entrypoints": {
    "main": "com.example.ExampleMod"
  },
  "depends": {
    "corelib": ">=1.0.0 <2.0.0"
  },
  "recommends": {
    "maplib": ">=2.0.0"
  },
  "suggests": {
    "optional_ui": "*"
  },
  "conflicts": {
    "old_api": "<2.0.0"
  },
  "breaks": {
    "broken_addon": "*"
  }
}
~~~

## Fields

| Field | Required | Meaning |
|---|---:|---|
| schemaVersion | Yes | Must be integer 1. |
| id | Yes | Lowercase identifier matching `[a-z][a-z0-9_-]{0,63}`. |
| name | Yes | Display name, up to 128 characters. |
| version | Yes | Semantic version. |
| description | No | Short mod description, up to 2,048 characters. |
| icon | No | Relative path to an image resource inside the mod JAR. Absolute paths and `..` segments are rejected. |
| license | No | SPDX identifier or human-readable license label, up to 128 characters. |
| website | No | HTTP(S) project or documentation URL. |
| sources | No | HTTP(S) source repository URL. |
| mixins | No | Array of Mixin configuration resource paths inside the mod JAR. Paths must be safe relative `.json` paths. Files ending in `.mixins.json` are also discovered automatically. |
| entrypoints.main | Yes | Fully qualified Java class implementing VantaMod. |
| depends | No | Required mod IDs mapped to version constraints; missing or incompatible dependencies prevent loading. |
| recommends | No | Optional dependencies recommended for the intended experience. |
| suggests | No | Optional integrations that may improve the experience. |
| conflicts | No | Mods that must not be installed alongside this mod. |
| breaks | No | Versions of other mods known to be incompatible. |

All dependency maps use the same version-constraint syntax. `depends` participates in load ordering and must be present at a compatible version. `conflicts` and `breaks` are checked against installed mods and reject matching incompatible versions. `recommends` and `suggests` are informational metadata and do not block startup. A mod cannot both require and conflict with, or break, the same dependency.

Unknown fields are rejected in schema 1. This prevents misspelled fields from silently producing a different runtime configuration.

## Version constraints

A constraint is a whitespace- or comma-separated conjunction:

- `1.2.3` — exact version.
- `>=1.2.0` — greater than or equal.
- `>1.2.0` — greater than.
- `<=2.0.0` — less than or equal.
- `<2.0.0` — less than.
- `*` — any version.

For example, `>=1.2.0 <2.0.0` accepts versions in that interval. OR expressions, caret ranges, tilde ranges, and partial-version wildcards are not supported.

## Entrypoint lifecycle

The main class must be present in the mod's own JAR or in one of its declared dependencies, implement `dev.itsdropper.vantaloader.api.VantaMod`, and have a public no-argument constructor.

VantaLoader invokes `onInitialize(ModContext)` after required dependencies have initialized. During shutdown it invokes `onShutdown()` in reverse initialization order.

## Runtime limitations

The metadata parser exposes display information, dependency intent, and explicit Mixin configuration paths. Mixin configs declared in `mixins` and resources named `*.mixins.json` are registered through the bundled Sponge Mixin 0.8.7 runtime. The Mixin transformer runs before native VantaLoader ASM transformers. Access wideners are not implemented. Icon paths are validated and exposed to the mod menu API; the current built-in screen still renders a placeholder accent tile rather than loading image textures. Optional dependency categories are parsed and preserved, but do not yet affect resolution.

Each mod receives its own class loader. It can load its own classes and classes/resources from explicitly declared dependencies; unrelated mods are not searched. Java platform classes and the VantaLoader API are parent-first so all entrypoints share the same API type identity. This is namespace isolation, not a security boundary: Java code in a mod still runs with the operating-system user's permissions.

## Accessor API

Mods can define an interface with `@Accessor` getters/setters and `@Invoker` methods, then create a typed view over a game object with `AccessorFactory.create(AccessorType.class, target)`. Field and method names can be supplied explicitly in the annotation or inferred from `getX`, `isX`, `setX`, and `invokeX` method names. This uses `MethodHandles` and does not inject mod-owned interfaces into Minecraft classes, avoiding class-loader visibility problems. Private access remains subject to the JVM's module openness rules.

~~~java
interface PlayerAccessor {
    @Accessor("health") float getHealth();
    @Invoker("calculateDamage") float invokeCalculateDamage(float amount);
}

PlayerAccessor playerView = AccessorFactory.create(PlayerAccessor.class, player);
~~~

## In-game settings screen API

A mod opts into a settings button by implementing `dev.itsdropper.vantaloader.api.ModMenuApi` on its main entrypoint class:

~~~java
public final class ExampleMod implements VantaMod, ModMenuApi {
    @Override
    public Object createConfigScreen(Object parentScreen) {
        // Return the Minecraft Screen implementation for this game version.
        return createSettingsScreen(parentScreen);
    }
}
~~~

The loader exposes each loaded mod's display metadata, resolved icon resource, and whether it implements this API. Mods without the interface do not receive a settings button. The API avoids a hard compile-time dependency on Minecraft classes; a mod's game-version integration is responsible for returning the active game's screen object. On Minecraft 1.21.11, VantaLoader injects its built-in mod-list button into the title and pause screens and renders a translucent metadata panel. Screen rendering is version-specific and has not yet been verified in a live Minecraft client.
