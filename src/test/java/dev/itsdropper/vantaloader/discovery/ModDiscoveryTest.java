package dev.itsdropper.vantaloader.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModDiscoveryTest {
    @TempDir Path temporaryDirectory;

    @Test void discoversNativeModJar() throws Exception {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        createModJar(mods.resolve("example.jar"), descriptor("example"));
        var discovered = new ModDiscovery().discover(mods);
        assertEquals(1, discovered.size());
        assertEquals("example", discovered.getFirst().descriptor().id());
    }

    @Test void rejectsJarWithoutDescriptor() throws Exception {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        try (var output = new ZipOutputStream(Files.newOutputStream(mods.resolve("foreign.jar")))) {
            output.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            output.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        IOException failure = assertThrows(IOException.class, () -> new ModDiscovery().discover(mods));
        assertTrue(failure.getMessage().contains("vanta.mod.json"));
    }

    @Test void rejectsDuplicateModIds() throws Exception {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        createModJar(mods.resolve("a.jar"), descriptor("same"));
        createModJar(mods.resolve("b.jar"), descriptor("same"));
        assertTrue(assertThrows(IOException.class, () -> new ModDiscovery().discover(mods))
                .getMessage().contains("Duplicate mod id"));
    }

    private static String descriptor(String id) {
        return """
                {"schemaVersion":1,"id":"%s","name":"%s","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"}}
                """.formatted(id, id);
    }

    private static void createModJar(Path path, String descriptor) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("vanta.mod.json"));
            output.write(descriptor.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
