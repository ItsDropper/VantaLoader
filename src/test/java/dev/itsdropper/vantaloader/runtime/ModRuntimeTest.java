package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.metadata.DescriptorReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModRuntimeTest {
    private static final List<String> EVENTS = new ArrayList<>();
    @TempDir Path temporaryDirectory;

    public static final class TestEntrypoint implements VantaMod {
        public TestEntrypoint() { }
        @Override public void onInitialize(ModContext context) {
            EVENTS.add("init:" + context.modId());
        }
        @Override public void onShutdown() {
            EVENTS.add("stop");
        }
    }

    @Test void initializesInGivenOrderAndStopsInReverseOrder() throws Exception {
        EVENTS.clear();
        Path jar = temporaryDirectory.resolve("fixture.jar");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(jar))) { }
        String entrypoint = TestEntrypoint.class.getName();
        var alpha = artifact(jar, "alpha", entrypoint);
        var beta = artifact(jar, "beta", entrypoint);

        try (ModRuntime runtime = new ModRuntime(temporaryDirectory.resolve("game"))) {
            runtime.start(List.of(alpha, beta));
            assertEquals(List.of("init:alpha", "init:beta"), EVENTS);
        }
        assertEquals(List.of("init:alpha", "init:beta", "stop", "stop"), EVENTS);
    }

    private static ModArtifact artifact(Path jar, String id, String entrypoint) throws Exception {
        String json = """
                {"schemaVersion":1,"id":"%s","name":"%s","version":"1.0.0",
                 "entrypoints":{"main":"%s"}}
                """.formatted(id, id, entrypoint);
        return new ModArtifact(jar, new DescriptorReader().read(new StringReader(json)));
    }
}
