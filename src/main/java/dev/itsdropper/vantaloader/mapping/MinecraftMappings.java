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
import dev.itsdropper.vantaloader.api.MinecraftMappingView;

/**
 * Read-only index for Mojang's ProGuard-style client mappings.
 * Member lookups require the named JVM descriptor, avoiding ambiguous overload guesses.
 */
public final class MinecraftMappings implements MinecraftMappingView {
    private final Map<String, String> namedToRuntime;
    private final Map<String, String> runtimeToNamed;
    private final Map<String, String> methods;
    private final Map<String, String> fields;

    private MinecraftMappings(Map<String, String> namedToRuntime,
                              Map<String, String> runtimeToNamed,
                              Map<String, String> methods,
                              Map<String, String> fields) {
        this.namedToRuntime = Collections.unmodifiableMap(new HashMap<>(namedToRuntime));
        this.runtimeToNamed = Collections.unmodifiableMap(new HashMap<>(runtimeToNamed));
        this.methods = Collections.unmodifiableMap(new HashMap<>(methods));
        this.fields = Collections.unmodifiableMap(new HashMap<>(fields));
    }

    public static MinecraftMappings read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Minecraft client mappings file does not exist: " + file);
        }
        Map<String, String> forward = new HashMap<>();
        Map<String, String> reverse = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        Map<String, String> fields = new HashMap<>();
        java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        // First pass indexes every class. Member descriptors can refer to classes
        // declared later in the official mapping file.
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#") || Character.isWhitespace(line.charAt(0))) continue;
            if (!line.endsWith(":")) throw new IOException("Malformed class mapping at line " + (i + 1));
            String mapping = line.substring(0, line.length() - 1).trim();
            int separator = mapping.lastIndexOf(" -> ");
            if (separator <= 0 || separator + 4 >= mapping.length()) {
                throw new IOException("Malformed class mapping at line " + (i + 1));
            }
            String named = mapping.substring(0, separator).trim();
            String runtime = mapping.substring(separator + 4).trim();
            if (!isBinaryName(named) || !isBinaryName(runtime)) {
                throw new IOException("Invalid class name in mapping at line " + (i + 1));
            }
            String previousRuntime = forward.putIfAbsent(named, runtime);
            String previousNamed = reverse.putIfAbsent(runtime, named);
            if (previousRuntime != null || previousNamed != null) {
                throw new IOException("Duplicate or conflicting class mapping at line " + (i + 1));
            }
        }
        if (forward.isEmpty()) {
            throw new IOException("Minecraft client mappings contain no class mappings: " + file);
        }

        String currentOwner = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) {
                String mapping = line.substring(0, line.length() - 1).trim();
                int separator = mapping.lastIndexOf(" -> ");
                currentOwner = mapping.substring(0, separator).trim();
            } else if (currentOwner != null && line.contains(" -> ")) {
                parseMember(line.trim(), currentOwner, forward, methods, fields, i + 1);
            }
        }
        return new MinecraftMappings(forward, reverse, methods, fields);
    }

    private static void parseMember(String line, String owner, Map<String, String> classes,
                                    Map<String, String> methods, Map<String, String> fields,
                                    int lineNumber) throws IOException {
        int arrow = line.lastIndexOf(" -> ");
        if (arrow <= 0 || arrow + 4 >= line.length()) {
            throw new IOException("Malformed member mapping at line " + lineNumber);
        }
        String left = stripLineNumbers(line.substring(0, arrow).trim());
        String runtimeName = line.substring(arrow + 4).trim();
        if (!isMemberName(runtimeName)) throw new IOException("Invalid member name at line " + lineNumber);

        int open = left.indexOf('(');
        if (open >= 0) {
            int close = left.lastIndexOf(')');
            if (close < open) throw new IOException("Malformed method mapping at line " + lineNumber);
            String declaration = left.substring(0, open).trim();
            int split = declaration.lastIndexOf(' ');
            if (split <= 0) throw new IOException("Malformed method declaration at line " + lineNumber);
            String returnType = declaration.substring(0, split).trim();
            String name = declaration.substring(split + 1).trim();
            String arguments = left.substring(open + 1, close).trim();
            String descriptor = methodDescriptor(returnType, arguments, classes, lineNumber);
            putUnique(methods, memberKey(owner, name, descriptor), runtimeName, lineNumber);
        } else {
            int split = left.lastIndexOf(' ');
            if (split <= 0) throw new IOException("Malformed field mapping at line " + lineNumber);
            String type = left.substring(0, split).trim();
            String name = left.substring(split + 1).trim();
            String descriptor = typeDescriptor(type, classes, lineNumber);
            putUnique(fields, memberKey(owner, name, descriptor), runtimeName, lineNumber);
        }
    }

    private static String stripLineNumbers(String declaration) {
        int arrowSafe = declaration.indexOf(':');
        while (arrowSafe > 0) {
            String prefix = declaration.substring(0, arrowSafe);
            if (!prefix.matches("\\d+(?::\\d+)?")) break;
            declaration = declaration.substring(arrowSafe + 1).trim();
            arrowSafe = declaration.indexOf(':');
        }
        return declaration;
    }

    private static String methodDescriptor(String returnType, String arguments,
                                           Map<String, String> classes, int line) throws IOException {
        StringBuilder descriptor = new StringBuilder("(");
        if (!arguments.isBlank()) {
            for (String argument : arguments.split(",", -1)) {
                descriptor.append(typeDescriptor(argument.trim(), classes, line));
            }
        }
        return descriptor.append(')').append(typeDescriptor(returnType, classes, line)).toString();
    }

    private static String typeDescriptor(String type, Map<String, String> classes, int line)
            throws IOException {
        int dimensions = 0;
        while (type.endsWith("[]")) {
            dimensions++;
            type = type.substring(0, type.length() - 2).trim();
        }
        String base = switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "float" -> "F";
            case "long" -> "J";
            case "double" -> "D";
            default -> {
                if (!isBinaryName(type)) throw new IOException("Invalid member type at line " + line);
                yield "L" + classes.getOrDefault(type, type).replace('.', '/') + ";";
            }
        };
        if (dimensions > 0 && base.equals("V")) {
            throw new IOException("Void array type at line " + line);
        }
        return "[".repeat(dimensions) + base;
    }

    private static void putUnique(Map<String, String> map, String key, String value, int line)
            throws IOException {
        String previous = map.putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) {
            throw new IOException("Conflicting member mapping at line " + line);
        }
    }

    private static String memberKey(String owner, String name, String descriptor) {
        return owner + "#" + name + descriptor;
    }

    private static boolean isMemberName(String name) {
        return !name.isBlank() && !name.contains(" ") && !name.contains("/") && !name.contains(".");
    }

    /** Returns the obfuscated runtime name, or the input for non-Minecraft classes. */
    @Override
    public String runtimeClassName(String namedBinaryName) { return toRuntimeClassName(namedBinaryName); }

    @Override
    public String namedClassName(String runtimeBinaryName) { return toNamedClassName(runtimeBinaryName); }

    @Override
    public String runtimeMethodName(String ownerBinaryName, String namedMethodName, String namedDescriptor) {
        return toRuntimeMethodName(ownerBinaryName, namedMethodName, namedDescriptor);
    }

    @Override
    public String runtimeFieldName(String ownerBinaryName, String namedFieldName, String namedDescriptor) {
        return toRuntimeFieldName(ownerBinaryName, namedFieldName, namedDescriptor);
    }

    public String toRuntimeClassName(String namedBinaryName) {
        return namedToRuntime.getOrDefault(namedBinaryName, namedBinaryName);
    }

    /** Returns the Mojang-mapped binary name, or the input when it is not mapped. */
    public String toNamedClassName(String runtimeBinaryName) {
        return runtimeToNamed.getOrDefault(runtimeBinaryName, runtimeBinaryName);
    }

    /** Resolves a Mojang-named method using its exact named JVM descriptor. */
    public String toRuntimeMethodName(String ownerBinaryName, String namedMethodName,
                                      String namedDescriptor) {
        String runtimeDescriptor = remapDescriptor(namedDescriptor);
        return methods.getOrDefault(memberKey(ownerBinaryName, namedMethodName, runtimeDescriptor),
                namedMethodName);
    }

    /** Resolves a Mojang-named field using its exact named JVM descriptor. */
    public String toRuntimeFieldName(String ownerBinaryName, String namedFieldName,
                                     String namedDescriptor) {
        String runtimeDescriptor = remapDescriptor(namedDescriptor);
        return fields.getOrDefault(memberKey(ownerBinaryName, namedFieldName, runtimeDescriptor),
                namedFieldName);
    }

    private String remapDescriptor(String descriptor) {
        StringBuilder result = new StringBuilder(descriptor.length());
        for (int i = 0; i < descriptor.length();) {
            char character = descriptor.charAt(i++);
            result.append(character);
            if (character != 'L') continue;
            int end = descriptor.indexOf(';', i);
            if (end < 0) return descriptor;
            String named = descriptor.substring(i, end).replace('/', '.');
            result.append(toRuntimeClassName(named).replace('.', '/')).append(';');
            i = end + 1;
        }
        return result.toString();
    }

    public boolean containsNamedClass(String namedBinaryName) {
        return namedToRuntime.containsKey(namedBinaryName);
    }

    public int classCount() {
        return namedToRuntime.size();
    }

    public int methodCount() {
        return methods.size();
    }

    public int fieldCount() {
        return fields.size();
    }

    private static boolean isBinaryName(String name) {
        if (name.isBlank() || name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isWhitespace(character) || character == '/' || character == ';'
                    || character == '[' || character == ':' || character == '\\') {
                return false;
            }
        }
        return true;
    }
}
