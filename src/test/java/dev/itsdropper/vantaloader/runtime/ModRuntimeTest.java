package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.metadata.DescriptorReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModRuntimeTest {
    @TempDir Path temporaryDirectory;

    @Test void initializesInGivenOrderAndStopsInReverseOrder() throws Exception {
        RuntimeEvents.EVENTS.clear();
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

    @Test void continuesShutdownAndClosesRuntimeWhenAModThrowsAnError() throws Exception {
        RuntimeEvents.EVENTS.clear();
        RuntimeEvents.failShutdown = true;
        Path jar = temporaryDirectory.resolve("failure-fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        var alpha = artifact(jar, "alpha", RuntimeTestEntrypoint.class.getName());
        var beta = artifact(jar, "beta", RuntimeTestEntrypoint.class.getName());
        ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("failure-game"));
        runtime.start(List.of(alpha, beta));

        Exception failure = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, runtime::stop);
        assertEquals("One or more mods failed to shut down", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length,
                "The failing mod's shutdown error must be retained as one suppressed failure");
        assertEquals(List.of("init:alpha", "init:beta", "stop", "stop"), RuntimeEvents.EVENTS);

        runtime.close();
        assertEquals(4, RuntimeEvents.EVENTS.size(), "A closed runtime must not shut mods down twice");
    }

    private static ModArtifact artifact(Path jar, String id, String entrypoint) throws Exception {
        String json = """
                {"schemaVersion":1,"id":"%s","name":"%s","version":"1.0.0",
                 "entrypoints":{"main":"%s"}}
                """.formatted(id, id, entrypoint);
        return new ModArtifact(jar, new DescriptorReader().read(new StringReader(json)));
    }
}
