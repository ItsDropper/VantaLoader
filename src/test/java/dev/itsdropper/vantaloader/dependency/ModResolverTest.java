package dev.itsdropper.vantaloader.dependency;

import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.metadata.DescriptorReader;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModResolverTest {
    private ModArtifact mod(String id, String version, String dependencies) throws Exception {
        String json = """
                {"schemaVersion":1,"id":"%s","name":"%s","version":"%s",
                 "entrypoints":{"main":"%s.Main"},"depends":%s}
                """.formatted(id, id, version, id, dependencies);
        return new ModArtifact(Path.of(id + ".jar"), new DescriptorReader().read(new StringReader(json)));
    }

    @Test void resolvesDependenciesBeforeDependentsDeterministically() throws Exception {
        var app = mod("app", "1.0.0", "{\"core\":\">=1.0.0 <2.0.0\"}");
        var core = mod("core", "1.2.0", "{}");
        var result = new ModResolver().resolve(List.of(app, core));
        assertEquals(List.of("core", "app"), result.stream().map(a -> a.descriptor().id()).toList());
    }

    @Test void rejectsMissingDependency() throws Exception {
        var app = mod("app", "1.0.0", "{\"missing\":\"*\"}");
        assertTrue(assertThrows(java.io.IOException.class,
                () -> new ModResolver().resolve(List.of(app))).getMessage().contains("missing mod"));
    }

    @Test void rejectsDependencyVersionMismatch() throws Exception {
        var app = mod("app", "1.0.0", "{\"core\":\">=2.0.0\"}");
        var core = mod("core", "1.5.0", "{}");
        assertTrue(assertThrows(java.io.IOException.class,
                () -> new ModResolver().resolve(List.of(app, core))).getMessage().contains("but found"));
    }

    @Test void rejectsDependencyCycles() throws Exception {
        var a = mod("alpha", "1.0.0", "{\"beta\":\"*\"}");
        var b = mod("beta", "1.0.0", "{\"alpha\":\"*\"}");
        assertTrue(assertThrows(java.io.IOException.class,
                () -> new ModResolver().resolve(List.of(a, b))).getMessage().contains("cycle"));
    }
}
