# Native Mod Metadata — Schema 1

<badge>Implemented in runtime 0.1.0</badge>

Every native VantaLoader mod is a JAR containing a UTF-8 vanta.mod.json file at the archive root.

## Example

~~~json
{
  "schemaVersion": 1,
  "id": "example",
  "name": "Example Mod",
  "version": "1.0.0",
  "entrypoints": {
    "main": "com.example.ExampleMod"
  },
  "depends": {
    "corelib": ">=1.0.0 <2.0.0"
  }
}
~~~

## Fields

| Field | Required | Meaning |
|---|---:|---|
| schemaVersion | Yes | Must be integer 1. |
| id | Yes | Lowercase identifier matching [a-z][a-z0-9_-]{0,63}. |
| name | Yes | Display name, up to 128 characters. |
| version | Yes | MAJOR.MINOR.PATCH, optional SemVer prerelease/build metadata. |
| entrypoints.main | Yes | Fully qualified Java class implementing VantaMod. |
| depends | No | Object mapping required mod IDs to version constraints. |

Unknown fields are rejected in schema 1. This prevents misspelled fields from silently producing a different runtime configuration. Future schema versions can add fields through explicit changes.

## Version constraints

A constraint is a whitespace- or comma-separated conjunction:

- 1.2.3 — exact version.
- >=1.2.0 — greater than or equal.
- >1.2.0 — greater than.
- <=2.0.0 — less than or equal.
- <2.0.0 — less than.
- * — any version.

For example, >=1.2.0 <2.0.0 accepts versions in that interval. OR expressions, caret ranges, tilde ranges, and partial-version wildcards are not supported in schema 1.

## Entrypoint lifecycle

The main class must be present in the mod's own JAR or in one of its declared dependencies, implement dev.itsdropper.vantaloader.api.VantaMod, and have a public no-argument constructor.

VantaLoader invokes onInitialize(ModContext) after required dependencies have initialized. During shutdown it invokes onShutdown() in reverse initialization order.

## Runtime limitations

Schema 1 is for the first standalone native runtime. It does not yet describe Minecraft versions, client/server environments, Mixin configurations, access wideners, nested dependencies, or reload capabilities. Those fields will be introduced only with corresponding runtime implementations. Each mod receives its own class loader. It can load its own classes and classes/resources from explicitly declared dependencies; unrelated mods are not searched. Java platform classes and the VantaLoader API are parent-first so all entrypoints share the same API type identity. Dependency lookup order is deterministic. This is namespace isolation, not a security boundary: Java code in a mod still runs with the operating-system user's permissions.
