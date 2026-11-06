package dev.itsdropper.vantaloader.metadata;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict parser for the native vanta.mod.json schema. */
public final class DescriptorReader {
    public static final String FILE_NAME = "vanta.mod.json";
    private static final int MAX_DESCRIPTOR_CHARS = 256 * 1024;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    public ModDescriptor read(Reader reader) throws IOException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseReader(reader);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid JSON in " + FILE_NAME + ": " + exception.getMessage(), exception);
        }
        if (!parsed.isJsonObject()) throw new IOException(FILE_NAME + " must contain a JSON object");
        JsonObject root = parsed.getAsJsonObject();
        rejectUnknown(root, Set.of("schemaVersion", "id", "name", "version", "entrypoints",
                "description", "icon", "license", "website", "sources", "mixins",
                "depends", "recommends", "suggests", "conflicts", "breaks"), "root");

        int schema = requiredInt(root, "schemaVersion");
        if (schema != 1) throw new IOException("Unsupported schemaVersion " + schema + "; supported: 1");
        String id = requiredString(root, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IOException("Invalid mod id '" + id + "'. Use lowercase letters, digits, '_' or '-'");
        }
        String name = requiredString(root, "name");
        if (name.length() > 128) throw new IOException("Mod name exceeds 128 characters: " + id);

        Version version;
        try {
            version = Version.parse(requiredString(root, "version"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid version for mod '" + id + "': " + exception.getMessage(), exception);
        }

        JsonObject entrypoints = requiredObject(root, "entrypoints");
        rejectUnknown(entrypoints, Set.of("main"), "entrypoints");
        String main = requiredString(entrypoints, "main");
        if (!CLASS_PATTERN.matcher(main).matches()) {
            throw new IOException("Invalid main entrypoint class name for mod '" + id + "'");
        }

        String description = optionalString(root, "description", 2048);
        String icon = optionalString(root, "icon", 256);
        if (icon != null && !isSafeArchivePath(icon)) {
            throw new IOException("Invalid icon path for mod '" + id
                    + "': use a relative JAR resource path without '..' segments");
        }
        String license = optionalString(root, "license", 128);
        String website = optionalUrl(root, "website", id);
        String sources = optionalUrl(root, "sources", id);
        java.util.List<String> mixins = readMixinConfigs(root, id);

        Map<String, VersionConstraint> depends = readDependencies(root, "depends", id);
        Map<String, VersionConstraint> recommends = readDependencies(root, "recommends", id);
        Map<String, VersionConstraint> suggests = readDependencies(root, "suggests", id);
        Map<String, VersionConstraint> conflicts = readDependencies(root, "conflicts", id);
        Map<String, VersionConstraint> breaks = readDependencies(root, "breaks", id);

        for (String dependency : depends.keySet()) {
            if (conflicts.containsKey(dependency) || breaks.containsKey(dependency)) {
                throw new IOException("Mod '" + id + "' both requires and conflicts with/breaks '"
                        + dependency + "'");
            }
        }
        return new ModDescriptor(schema, id, name, version, main, description, icon, license,
                website, sources, mixins, depends, recommends, suggests, conflicts, breaks);
    }

    private static java.util.List<String> readMixinConfigs(JsonObject root, String modId) throws IOException {
        if (!root.has("mixins")) return java.util.List.of();
        JsonElement value = root.get("mixins");
        if (!value.isJsonArray()) throw new IOException("Field 'mixins' must be an array in " + FILE_NAME);
        JsonArray array = value.getAsJsonArray();
        java.util.List<String> result = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IOException("Every entry in 'mixins' must be a string in mod '" + modId + "'");
            }
            String path = element.getAsString().trim();
            if (!path.endsWith(".json") || !isSafeArchivePath(path)) {
                throw new IOException("Invalid Mixin config path '" + path + "' in mod '" + modId + "'");
            }
            if (!seen.add(path)) {
                throw new IOException("Duplicate Mixin config path '" + path + "' in mod '" + modId + "'");
            }
            result.add(path);
        }
        return java.util.List.copyOf(result);
    }

    private static Map<String, VersionConstraint> readDependencies(
            JsonObject root, String field, String modId) throws IOException {
        Map<String, VersionConstraint> result = new LinkedHashMap<>();
        if (!root.has(field)) return result;
        JsonElement value = root.get(field);
        if (!value.isJsonObject()) throw new IOException("Field '" + field + "' must be an object in " + FILE_NAME);
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            String dependencyId = entry.getKey();
            if (!ID_PATTERN.matcher(dependencyId).matches()) {
                throw new IOException("Invalid dependency id '" + dependencyId + "' in mod '" + modId + "'");
            }
            if (!entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IOException("Dependency constraint for '" + dependencyId
                        + "' in '" + field + "' must be a string in mod '" + modId + "'");
            }
            try {
                result.put(dependencyId, VersionConstraint.parse(entry.getValue().getAsString()));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid constraint for dependency '" + dependencyId
                        + "' in mod '" + modId + "': " + exception.getMessage(), exception);
            }
        }
        return result;
    }

    private static String optionalString(JsonObject object, String key, int maxLength) throws IOException {
        if (!object.has(key)) return null;
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Field '" + key + "' must be a string in " + FILE_NAME);
        }
        String result = value.getAsString().trim();
        if (result.isEmpty()) throw new IOException("Field '" + key + "' cannot be blank in " + FILE_NAME);
        if (result.length() > maxLength) {
            throw new IOException("Field '" + key + "' exceeds " + maxLength + " characters in " + FILE_NAME);
        }
        return result;
    }

    private static String optionalUrl(JsonObject object, String key, String modId) throws IOException {
        String value = optionalString(object, key, 2048);
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("URL must use http or https");
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid " + key + " URL for mod '" + modId + "': " + value, exception);
        }
        return value;
    }

    private static boolean isSafeArchivePath(String value) {
        if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\\\")) return false;
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) return false;
        }
        return true;
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed, String location)
            throws IOException {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IOException("Unknown field '" + field + "' in " + location
                        + " of " + FILE_NAME + "; check for spelling mistakes");
            }
        }
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IOException("Missing or invalid string field '" + key + "' in " + FILE_NAME);
        }
        return value.getAsString().trim();
    }

    private static int requiredInt(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Missing or invalid integer field '" + key + "' in " + FILE_NAME);
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IOException("Field '" + key + "' must be an integer in " + FILE_NAME, exception);
        }
    }

    private static JsonObject requiredObject(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IOException("Missing or invalid object field '" + key + "' in " + FILE_NAME);
        }
        return value.getAsJsonObject();
    }
}
