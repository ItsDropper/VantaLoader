package dev.itsdropper.vantaloader.metadata;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict parser for the initial vanta.mod.json schema. */
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
        rejectUnknown(root, Set.of("schemaVersion", "id", "name", "version", "entrypoints", "depends"), "root");

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

        Map<String, VersionConstraint> depends = new LinkedHashMap<>();
        if (root.has("depends")) {
            JsonObject dependencyObject = requiredObject(root, "depends");
            for (Map.Entry<String, JsonElement> entry : dependencyObject.entrySet()) {
                if (!ID_PATTERN.matcher(entry.getKey()).matches()) {
                    throw new IOException("Invalid dependency id '" + entry.getKey() + "' in mod '" + id + "'");
                }
                if (!entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IOException("Dependency constraint for '" + entry.getKey()
                            + "' must be a string in mod '" + id + "'");
                }
                try {
                    depends.put(entry.getKey(), VersionConstraint.parse(entry.getValue().getAsString()));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid constraint for dependency '" + entry.getKey()
                            + "' in mod '" + id + "': " + exception.getMessage(), exception);
                }
            }
        }
        return new ModDescriptor(schema, id, name, version, main, depends);
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
