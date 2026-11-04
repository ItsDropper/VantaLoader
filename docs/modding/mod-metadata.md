# Mod Metadata Proposal

> [!WARNING]
> Draft for discussion only. No metadata schema is implemented or stable. Do not publish mods assuming this example is accepted by a released loader.

## Goals

A VantaLoader descriptor should be machine-readable, versioned, explicit about identity and compatibility, strictly validated, independent of Java implementation details, and extensible without silently changing existing field meanings.

## Illustrative descriptor

A future descriptor may look conceptually like this:

~~~json
{
  "schemaVersion": 1,
  "id": "examplemod",
  "version": "1.0.0",
  "name": "Example Mod",
  "environment": "client",
  "entrypoints": {
    "main": ["com.example.mod.ExampleMod"],
    "client": ["com.example.mod.client.ExampleModClient"]
  },
  "depends": {
    "vantaloader": ">=0.1.0 <0.2.0",
    "minecraft": "1.21.1"
  },
  "mixins": ["examplemod.mixins.json"],
  "reload": {
    "resources": true,
    "components": false
  }
}
~~~

This example is illustrative only. The final version-constraint grammar, environment vocabulary, entrypoint model, dependency semantics, and Mixin configuration rules have not been chosen.

## Proposed field intent

| Field | Intended meaning |
|---|---|
| schemaVersion | Descriptor schema version. |
| id | Stable, lowercase mod identifier. |
| version | Mod release version. |
| name | Human-readable display name. |
| environment | Intended runtime environment, subject to a defined vocabulary. |
| entrypoints | Classes or providers invoked at defined lifecycle phases. |
| depends | Required loader, game, and mod compatibility constraints. |
| mixins | Optional Mixin configuration resources when the adapter is available. |
| reload | Declared reload capabilities; claims to validate, not security permissions. |

## Validation requirements

A future implementation should reject or clearly diagnose missing/invalid identifiers, unsupported schemas, duplicate IDs, invalid constraints, missing dependencies, unresolved dependency cycles, missing entrypoints/resources, and capabilities unsupported by the current runtime.

Unknown-field handling must be specified before the format becomes stable. Silent interpretation of misspelled critical fields should be avoided.

## Security note

A descriptor does not make a mod trustworthy. Entrypoints are Java code and run with the permissions of the Minecraft process. The descriptor must never be presented as a sandbox or permission system.
