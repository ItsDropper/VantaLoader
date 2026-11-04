# Transformation Pipeline

## Status

This document records design constraints and a proposed direction. No VantaTransform engine is implemented yet.

## Intended responsibility

VantaTransform is the working name for VantaLoader's native transformation coordination layer. It should provide a consistent API and diagnostics for transformations contributed by native VantaLoader mods and compatibility adapters.

The initial goal is not to invent a replacement for every bytecode tool. It is to own transformation planning, ordering, validation, conflict reporting, and lifecycle policy.

## Proposed stages

1. Collect contributions from discovered mods and enabled adapters.
2. Validate targets, required capabilities, compatibility constraints, and duplicate identifiers.
3. Construct a deterministic plan from explicit dependencies and ordering constraints.
4. Apply transformations at the JVM/Minecraft lifecycle point where target classes can still be transformed.
5. Report outcomes, contributors, targets, timing, and failures.

The exact stages and backend interfaces must be validated against the launch technology selected for each supported Minecraft version.

## Mixin interoperability

SpongePowered Mixin should initially be integrated through an adapter where practical. The adapter must preserve Mixin's own configuration and semantics rather than pretending arbitrary Mixin configurations can be translated losslessly into a new native format.

The native API and adapter should have clear ownership boundaries. Mixin-specific annotations and configuration should not become mandatory dependencies for native VantaLoader mods.

## Ordering and conflicts

- Produce stable ordering for equivalent inputs.
- Require explicit ordering only where it affects correctness.
- Detect duplicate or incompatible contributions when possible.
- Report conflicts with contributing mod IDs and target classes.
- Avoid silently choosing a winner for ambiguous transformations.

Some conflicts can only be discovered while applying or verifying a transformation. Diagnostics must distinguish detected conflicts from conflicts that cannot be determined statically.

## Reload constraints

Class transformation usually occurs as classes are defined. JVM instrumentation supports only constrained forms of retransformation or redefinition. Ordinary class redefinition is not a general mechanism for changing class structure, superclass relationships, or existing object layouts. Existing Mixin transformations are not automatically reversible.

Therefore:

- Implement resource/data reload independently of class transformation.
- Give reloadable components explicit lifecycle and cleanup contracts.
- Mark structural bytecode changes restart-required unless a specific, tested runtime path proves otherwise.
- Validate a reload request before applying partial changes.
- Preserve a known runtime state on failure wherever the underlying operation allows it.

## Required diagnostics

A useful transformation report should eventually include the mod ID, transformation identifier, target class/method, backend, lifecycle stage, ordering constraints, outcome, and concise failure details.

Do not expose sensitive local paths or environment values by default.
