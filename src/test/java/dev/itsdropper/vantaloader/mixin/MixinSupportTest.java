package dev.itsdropper.vantaloader.mixin;

import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.metadata.ModDescriptor;
import dev.itsdropper.vantaloader.metadata.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MixinSupportTest {
    @TempDir Path temporaryDirectory;

    @Test
    void discoversDeclaredAndConventionNamedConfigsInDeterministicOrder() throws Exception {
        Path jar = createModJar("sample.jar", "zeta.mixins.json", "nested/alpha.mixins.json");
        ModArtifact artifact = artifact("sample", jar, List.of("zeta.mixins.json"));

        assertEquals(List.of("nested/alpha.mixins.json", "zeta.mixins.json"),
                MixinSupport.discoverConfigNames(List.of(artifact)));
    }

    @Test
    void rejectsDeclaredConfigMissingFromItsOwningModJar() throws Exception {
        Path jar = createModJar("missing.jar");
        ModArtifact artifact = artifact("missing", jar, List.of("missing.mixins.json"));

        IOException failure = assertThrows(IOException.class,
                () -> MixinSupport.discoverConfigNames(List.of(artifact)));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("missing.mixins.json"));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("missing"));
    }

    @Test
    void rejectsUnsafeDeclaredConfigPathsBeforeMixinBootstrap() throws Exception {
        Path jar = createModJar("unsafe.jar", "../outside.mixins.json");
        ModArtifact artifact = artifact("unsafe", jar, List.of("../outside.mixins.json"));

        assertThrows(IOException.class,
                () -> MixinSupport.discoverConfigNames(List.of(artifact)));
    }

    @Test
    void rejectsBackslashesInMixinResourcePaths() throws Exception {
        Path jar = createModJar("backslash.jar", "nested\\escape.mixins.json");
        ModArtifact artifact = artifact("backslash", jar, List.of("nested\\\\escape.mixins.json"));

        assertThrows(IOException.class,
                () -> MixinSupport.discoverConfigNames(List.of(artifact)));
    }

    @Test
    void rejectsTheSameMixinConfigClaimedByDifferentMods() throws Exception {
        Path firstJar = createModJar("first.jar", "shared.mixins.json");
        Path secondJar = createModJar("second.jar", "shared.mixins.json");

        assertThrows(IOException.class, () -> MixinSupport.discoverConfigNames(List.of(
                artifact("first", firstJar, List.of("shared.mixins.json")),
                artifact("second", secondJar, List.of("shared.mixins.json")))));
    }

    private Path createModJar(String fileName, String... configs) throws IOException {
        Path jar = temporaryDirectory.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            for (String config : configs) {
                output.putNextEntry(new JarEntry(config));
                output.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }

    private static ModArtifact artifact(String id, Path jar, List<String> mixins) {
        ModDescriptor descriptor = new ModDescriptor(
                1, id, id, Version.parse("1.0.0"), "example." + id + ".Main",
                null, null, null, null, null, mixins,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        return new ModArtifact(jar, descriptor);
    }
}
