package dev.itsdropper.vantaloader.api;

import java.net.URL;

/** Immutable display data for one loaded native mod. */
public record ModMenuEntry(
        String id,
        String name,
        String version,
        String description,
        String license,
        String website,
        String sources,
        URL iconResource,
        boolean hasConfigScreen) { }
