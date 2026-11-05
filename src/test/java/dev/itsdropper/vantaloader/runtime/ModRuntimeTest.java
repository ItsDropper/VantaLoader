package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.MinecraftCapabilities;
import dev.itsdropper.vantaloader.api.MinecraftCapability;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.transformation.TransformationService;
import dev.itsdropper.vantaloader.metadata.DescriptorReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModRuntimeTest {
    @TempDir Path temporaryDirectory;

    @Test void passesVerifiedCapabilitiesIntoNativeModContext() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failInitializeMod = null;
        RuntimeEvents.lastCapabilities = null;
        Path jar = temporaryDirectory.resolve("capability-fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        var fixture = artifact(jar, "capability_fixture", RuntimeTestEntrypoint.class.getName());
        MinecraftCapabilities capabilities = MinecraftCapabilities.of(
                MinecraftCapability.CLASS_TRANSFORMATIONS,
                MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS);

        try (ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("capability-game"),
                new TransformationService(), capabilities)) {
            runtime.start(List.of(fixture));
            assertNotNull(RuntimeEvents.lastCapabilities);
            assertTrue(RuntimeEvents.lastCapabilities.supports(
                    MinecraftCapability.CLASS_TRANSFORMATIONS));
            assertTrue(RuntimeEvents.lastCapabilities.supports(
                    MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS));
            assertFalse(RuntimeEvents.lastCapabilities.supports(
                    MinecraftCapability.CLIENT_LIFECYCLE_EVENTS));
        }
    }

    @Test void rejectsDuplicateIdsBeforeConstructingAnyMods() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failInitializeMod = null;
        Path jar = temporaryDirectory.resolve("duplicate-id-fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        var first = artifact(jar, "duplicate", RuntimeTestEntrypoint.class.getName());
        var second = artifact(jar, "duplicate", RuntimeTestEntrypoint.class.getName());
        ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("duplicate-id-game"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> runtime.start(List.of(first, second)));
        assertEquals("Duplicate mod ID in runtime input: 'duplicate'", failure.getMessage());
        assertEquals(List.of(), RuntimeEvents.EVENTS);
        runtime.close();
    }

    @Test void rejectsMissingJarBeforeConstructingAnyMods() throws Exception {
        RuntimeEvents.EVENTS.clear();
        Path missing = temporaryDirectory.resolve("does-not-exist.jar");
        var artifact = artifact(missing, "missingjar", RuntimeTestEntrypoint.class.getName());
        ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("missing-jar-game"));

        assertThrows(IllegalArgumentException.class, () -> runtime.start(List.of(artifact)));
        assertEquals(List.of(), RuntimeEvents.EVENTS);
        runtime.close();
    }

    @Test void initializesInGivenOrderAndStopsInReverseOrder() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failInitializeMod = null;
        RuntimeEvents.failShutdown = false;
        Path jar = temporaryDirectory.resolve("fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        String entrypoint = RuntimeTestEntrypoint.class.getName();
        var alpha = artifact(jar, "alpha", entrypoint);
        var beta = artifact(jar, "beta", entrypoint);

        try (ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("game"))) {
            runtime.start(List.of(alpha, beta));
            assertEquals(List.of("init:alpha", "init:beta"), RuntimeEvents.EVENTS);
        }
        assertEquals(List.of("init:alpha", "init:beta", "stop", "stop"), RuntimeEvents.EVENTS);
    }

    @Test void rollsBackEveryConstructedModWhenInitializationFails() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failInitializeMod = "beta";
        RuntimeEvents.failShutdown = false;
        Path jar = temporaryDirectory.resolve("initialization-failure-fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        var alpha = artifact(jar, "alpha", RuntimeTestEntrypoint.class.getName());
        var beta = artifact(jar, "beta", RuntimeTestEntrypoint.class.getName());
        ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("initialization-failure-game"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> runtime.start(List.of(alpha, beta)));
        assertEquals("simulated initialization failure", failure.getMessage());
        assertEquals(List.of("init:alpha", "init:beta", "stop", "stop"), RuntimeEvents.EVENTS);
        assertThrows(IllegalStateException.class, () -> runtime.start(List.of(alpha, beta)));
        runtime.close();
        assertEquals(4, RuntimeEvents.EVENTS.size());
    }

    @Test void continuesShutdownAndClosesRuntimeWhenAModThrowsAnError() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failInitializeMod = null;
        RuntimeEvents.failShutdown = true;
        Path jar = temporaryDirectory.resolve("failure-fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        var alpha = artifact(jar, "alpha", RuntimeTestEntrypoint.class.getName());
        var beta = artifact(jar, "beta", RuntimeTestEntrypoint.class.getName());
        ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("failure-game"));
        runtime.start(List.of(alpha, beta));

        Exception failure = assertThrows(Exception.class, runtime::stop);
        assertEquals("One or more mods failed to shut down", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length,
                "The failing mod's shutdown error must be retained as one suppressed failure");
        assertEquals(List.of("init:alpha", "init:beta", "stop", "stop"), RuntimeEvents.EVENTS);

        runtime.close();
        assertEquals(4, RuntimeEvents.EVENTS.size(), "A closed runtime must not shut mods down twice");
    }


    @Test void propagatesFatalShutdownFailuresAfterClosingRuntime() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failInitializeMod = null;
        RuntimeEvents.failShutdown = false;
        RuntimeEvents.failFatalShutdown = true;
        Path jar = temporaryDirectory.resolve("fatal-failure-fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        var alpha = artifact(jar, "alpha", RuntimeTestEntrypoint.class.getName());
        var beta = artifact(jar, "beta", RuntimeTestEntrypoint.class.getName());
        ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("fatal-failure-game"));
        runtime.start(List.of(alpha, beta));

        OutOfMemoryError failure = assertThrows(OutOfMemoryError.class, runtime::stop);
        assertEquals("simulated fatal shutdown failure", failure.getMessage());
        assertEquals(List.of("init:alpha", "init:beta", "stop"), RuntimeEvents.EVENTS);
        runtime.close();
        assertEquals(3, RuntimeEvents.EVENTS.size(),
                "A runtime closed after a fatal shutdown failure must not invoke mods again");
    }

    private static ModArtifact artifact(Path jar, String id, String entrypoint) throws Exception {
        String json = """
                {"schemaVersion":1,"id":"%s","name":"%s","version":"1.0.0",
                 "entrypoints":{"main":"%s"}}
                """.formatted(id, id, entrypoint);
        return new ModArtifact(jar, new DescriptorReader().read(new StringReader(json)));
    }
}
