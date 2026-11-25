package dev.itsdropper.vantaloader.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts Loom's intermediary-namespace Mixin reference maps to the exact
 * obfuscated names used by the running Minecraft client.
 */
public final class IntermediaryMappings {
    private final Map<String, String> intermediaryToOfficialClasses;
    private final Map<String, String> intermediaryToOfficialMethods;
    private final Map<String, String> intermediaryToOfficialFields;

    private IntermediaryMappings(Map<String, String> classes,
                                 Map<String, String> methods,
                                 Map<String, String> fields) {
        intermediaryToOfficialClasses = Map.copyOf(classes);
        intermediaryToOfficialMethods = Map.copyOf(methods);
        intermediaryToOfficialFields = Map.copyOf(fields);
    }

    public static IntermediaryMappings read(InputStream source) throws IOException {
        if (source == null) throw new NullPointerException("source");
        Map<String, String> classes = new HashMap<>();
        Map<String, String> sourceToIntermediaryClasses = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        Map<String, String> fields = new HashMap<>();
        java.util.List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }
        if (lines.isEmpty()) throw new IOException("Empty intermediary mappings");
        String[] headerParts = lines.get(0).split("\\t", -1);
        if (headerParts.length < 5 || !"tiny".equals(headerParts[0]) || !"2".equals(headerParts[1])) {
            throw new IOException("Expected Tiny v2 intermediary mappings");
        }
        int intermediaryNamespace = -1;
        int officialNamespace = -1;
        for (int i = 3; i < headerParts.length; i++) {
            if ("intermediary".equals(headerParts[i])) intermediaryNamespace = i - 3;
            if ("official".equals(headerParts[i])) officialNamespace = i - 3;
        }
        if (intermediaryNamespace < 0 || officialNamespace < 0) {
            throw new IOException("Intermediary mappings must contain official and intermediary namespaces");
        }

        // Index all classes first because member descriptors may reference classes
        // declared later in the Tiny file. Tiny descriptors use namespace zero.
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\t", -1);
            if (!"c".equals(parts[0])) continue;
            int intermediaryIndex = 1 + intermediaryNamespace;
            int officialIndex = 1 + officialNamespace;
            if (parts.length <= Math.max(intermediaryIndex, officialIndex)) {
                throw new IOException("Malformed class mapping at line " + (i + 1));
            }
            String intermediaryName = parts[intermediaryIndex];
            String officialName = parts[officialIndex];
            classes.put(intermediaryName, officialName);
            sourceToIntermediaryClasses.put(parts[1], intermediaryName);
        }
        if (classes.isEmpty()) throw new IOException("Intermediary mappings contain no classes");

        String ownerIntermediary = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\\t", -1);
            if ("c".equals(parts[0])) {
                ownerIntermediary = parts[1 + intermediaryNamespace];
            } else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
                if (ownerIntermediary == null || parts.length <= 3 + Math.max(intermediaryNamespace, officialNamespace)) {
                    throw new IOException("Malformed member mapping at line " + (i + 1));
                }
                String descriptor = parts[2];
                if (intermediaryNamespace != 0) {
                    descriptor = remapDescriptorClasses(descriptor, sourceToIntermediaryClasses);
                }
                String intermediaryName = parts[3 + intermediaryNamespace];
                String officialName = parts[3 + officialNamespace];
                String key = ownerIntermediary + "#" + intermediaryName + descriptor;
                if (parts[1].equals("m")) methods.put(key, officialName);
                else fields.put(key, officialName);
            }
        }
        return new IntermediaryMappings(classes, methods, fields);
    }

    private static String remapDescriptorClasses(String descriptor, Map<String, String> classes)
            throws IOException {
        StringBuilder result = new StringBuilder(descriptor.length());
        for (int i = 0; i < descriptor.length();) {
            char character = descriptor.charAt(i++);
            result.append(character);
            if (character != 'L') continue;
            int end = descriptor.indexOf(';', i);
            if (end < 0) throw new IOException("Malformed Tiny member descriptor: " + descriptor);
            String sourceName = descriptor.substring(i, end);
            String intermediaryName = classes.get(sourceName);
            if (intermediaryName == null && sourceName.startsWith("net/minecraft/")) {
                throw new IOException("No intermediary class mapping for descriptor type " + sourceName);
            }
            result.append(intermediaryName == null ? sourceName : intermediaryName).append(';');
            i = end + 1;
        }
        return result.toString();
    }

    public byte[] remapRefmap(byte[] json, MinecraftMappings runtimeMappings) throws IOException {
        if (json == null) throw new NullPointerException("json");
        if (runtimeMappings == null) throw new NullPointerException("runtimeMappings");
        try {
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject mappings = root.getAsJsonObject("mappings");
            if (mappings != null) {
                for (Map.Entry<String, JsonElement> mixin : mappings.entrySet()) {
                    if (!mixin.getValue().isJsonObject()) continue;
                    JsonObject references = mixin.getValue().getAsJsonObject();
                    for (Map.Entry<String, JsonElement> reference : references.entrySet()) {
                        if (reference.getValue().isJsonPrimitive()
                                && reference.getValue().getAsJsonPrimitive().isString()) {
                            reference.setValue(new com.google.gson.JsonPrimitive(
                                    remapReference(reference.getValue().getAsString(), runtimeMappings)));
                        }
                    }
                }
            }
            return root.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new IOException("Cannot parse Mixin reference map", exception);
        }
    }

    private String remapReference(String reference, MinecraftMappings runtimeMappings) throws IOException {
        int ownerStart = reference.indexOf('L');
        int ownerEnd = ownerStart < 0 ? -1 : reference.indexOf(';', ownerStart);
        if (ownerStart < 0 || ownerEnd < 0) return reference;

        String intermediaryOwner = reference.substring(ownerStart + 1, ownerEnd);
        String officialOwner = intermediaryToOfficialClasses.get(intermediaryOwner);
        if (officialOwner == null) {
            if (intermediaryOwner.startsWith("net/minecraft/")) {
                throw new IOException("No official class mapping for Mixin reference owner " + intermediaryOwner);
            }
            return reference;
        }

        String tail = reference.substring(ownerEnd + 1);
        String officialOwnerBinaryName = officialOwner.replace('/', '.');
        String runtimeOwner = runtimeMappings.toRuntimeClassName(officialOwnerBinaryName).replace('.', '/');
        int methodStart = tail.indexOf('(');
        int fieldSeparator = tail.indexOf(':');
        if (methodStart >= 0) {
            String intermediaryName = tail.substring(0, methodStart);
            String intermediaryDescriptor = tail.substring(methodStart);
            String officialDescriptor = mapDescriptor(intermediaryDescriptor);
            String officialName = intermediaryToOfficialMethods.get(
                    intermediaryOwner + "#" + intermediaryName + intermediaryDescriptor);
            if (officialName == null) {
                throw new IOException("No official method mapping for " + intermediaryOwner + "#" + intermediaryName
                        + intermediaryDescriptor);
            }
            String runtimeName = runtimeMappings.toRuntimeMethodName(officialOwnerBinaryName, officialName, officialDescriptor);
            return "L" + runtimeOwner + ";" + runtimeName + runtimeMappings.runtimeDescriptor(officialDescriptor);
        }
        if (fieldSeparator >= 0) {
            String intermediaryName = tail.substring(0, fieldSeparator);
            String intermediaryDescriptor = tail.substring(fieldSeparator + 1);
            String officialDescriptor = mapDescriptor(intermediaryDescriptor);
            String officialName = intermediaryToOfficialFields.get(
                    intermediaryOwner + "#" + intermediaryName + intermediaryDescriptor);
            if (officialName == null) {
                throw new IOException("No official field mapping for " + intermediaryOwner + "#" + intermediaryName
                        + ":" + intermediaryDescriptor);
            }
            String runtimeName = runtimeMappings.toRuntimeFieldName(officialOwnerBinaryName, officialName, officialDescriptor);
            return "L" + runtimeOwner + ";" + runtimeName + ":" + runtimeMappings.runtimeDescriptor(officialDescriptor);
        }
        return "L" + runtimeOwner + ";" + mapDescriptor(tail);
    }

    private String mapDescriptor(String descriptor) throws IOException {
        StringBuilder result = new StringBuilder(descriptor.length());
        for (int i = 0; i < descriptor.length();) {
            char character = descriptor.charAt(i++);
            result.append(character);
            if (character != 'L') continue;
            int end = descriptor.indexOf(';', i);
            if (end < 0) throw new IOException("Malformed intermediary descriptor: " + descriptor);
            String intermediaryName = descriptor.substring(i, end);
            String officialName = intermediaryToOfficialClasses.get(intermediaryName);
            if (officialName == null) {
                if (intermediaryName.startsWith("net/minecraft/")) {
                    throw new IOException("No official class mapping for descriptor type " + intermediaryName);
                }
                officialName = intermediaryName;
            }
            result.append(officialName).append(';');
            i = end + 1;
        }
        return result.toString();
    }
}
