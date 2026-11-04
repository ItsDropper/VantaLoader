package dev.itsdropper.vantaloader.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only class-name index for Mojang's ProGuard-style client mappings.
 *
 * Member mappings are intentionally not guessed from line numbers: they need
 * descriptor-aware resolution and are handled by a later mapping layer.
 */
public final class MinecraftMappings {
    private final Map<String, String> namedToRuntime;
    private final Map<String, String> runtimeToNamed;

    private MinecraftMappings(Map<String, String> namedToRuntime,
                              Map<String, String> runtimeToNamed) {
        this.namedToRuntime = Collections.unmodifiableMap(namedToRuntime);
        this.runtimeToNamed = Collections.unmodifiableMap(runtimeToNamed);
    }

    public static MinecraftMappings read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Minecraft client mappings file does not exist: " + file);
        }
        Map<String, String> forward = new HashMap<>();
        Map<String, String> reverse = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#") || Character.isWhitespace(line.charAt(0))) {
                    continue;
                }
                if (!line.endsWith(":")) {
                    throw new IOException("Malformed class mapping at line " + lineNumber);
                }
                String mapping = line.substring(0, line.length() - 1).trim();
                int separator = mapping.lastIndexOf(" -> ");
                if (separator <= 0 || separator + 4 >= mapping.length()) {
                    throw new IOException("Malformed class mapping at line " + lineNumber);
                }
                String named = mapping.substring(0, separator).trim();
                String runtime = mapping.substring(separator + 4).trim();
                if (!isBinaryName(named) || !isBinaryName(runtime)) {
                    throw new IOException("Invalid class name in mapping at line " + lineNumber);
                }
                String previousRuntime = forward.putIfAbsent(named, runtime);
                String previousNamed = reverse.putIfAbsent(runtime, named);
                if (previousRuntime != null || previousNamed != null) {
                    throw new IOException("Duplicate or conflicting class mapping at line " + lineNumber);
                }
            }
        }
        if (forward.isEmpty()) {
            throw new IOException("Minecraft client mappings contain no class mappings: " + file);
        }
        return new MinecraftMappings(forward, reverse);
    }

    /** Returns the obfuscated runtime name, or the input for non-Minecraft classes. */
    public String toRuntimeClassName(String namedBinaryName) {
        return namedToRuntime.getOrDefault(namedBinaryName, namedBinaryName);
    }

    /** Returns the Mojang-mapped binary name, or the input when it is not mapped. */
    public String toNamedClassName(String runtimeBinaryName) {
        return runtimeToNamed.getOrDefault(runtimeBinaryName, runtimeBinaryName);
    }

    public boolean containsNamedClass(String namedBinaryName) {
        return namedToRuntime.containsKey(namedBinaryName);
    }

    public int classCount() {
        return namedToRuntime.size();
    }

    private static boolean isBinaryName(String name) {
        return name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    }
}
