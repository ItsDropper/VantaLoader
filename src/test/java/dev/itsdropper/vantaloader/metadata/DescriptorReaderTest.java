package dev.itsdropper.vantaloader.metadata;

import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class DescriptorReaderTest {
    private final DescriptorReader reader = new DescriptorReader();

    @Test void readsValidDescriptor() throws Exception {
        String json = """
                {"schemaVersion":1,"id":"example","name":"Example Mod","version":"1.2.0",
                 "entrypoints":{"main":"example.ExampleMod"},
                 "depends":{"base":">=1.0.0 <2.0.0"}}
                """;
        ModDescriptor d = reader.read(new StringReader(json));
        assertEquals("example", d.id());
        assertEquals(Version.parse("1.2.0"), d.version());
        assertTrue(d.depends().get("base").test(Version.parse("1.4.0")));
        assertNull(d.description());
        assertNull(d.icon());
    }

    @Test void readsDisplayMetadataAndAllDependencyKinds() throws Exception {
        String json = """
                {"schemaVersion":1,"id":"example","name":"Example Mod","version":"1.2.0",
                 "entrypoints":{"main":"example.ExampleMod"},
                 "description":"A useful example.","icon":"assets/example/icon.png",
                 "license":"MIT","website":"https://example.com","sources":"https://github.com/example/mod",
                 "depends":{"corelib":">=1.0.0"},
                 "recommends":{"maplib":"*"},"suggests":{"optional_ui":">=1.0.0 <2.0.0"},
                 "conflicts":{"old_api":"<2.0.0"},"breaks":{"broken_addon":"*"}}
                """;
        ModDescriptor d = reader.read(new StringReader(json));
        assertEquals("A useful example.", d.description());
        assertEquals("assets/example/icon.png", d.icon());
        assertEquals("MIT", d.license());
        assertEquals("https://example.com", d.website());
        assertEquals("https://github.com/example/mod", d.sources());
        assertTrue(d.recommends().containsKey("maplib"));
        assertTrue(d.suggests().containsKey("optional_ui"));
        assertTrue(d.conflicts().containsKey("old_api"));
        assertTrue(d.breaks().containsKey("broken_addon"));
    }

    @Test void rejectsUnsafeIconPaths() {
        String json = """
                {"schemaVersion":1,"id":"example","name":"Example","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"},"icon":"../outside.png"}
                """;
        assertThrows(java.io.IOException.class, () -> reader.read(new StringReader(json)));
    }

    @Test void rejectsNonHttpMetadataUrls() {
        String json = """
                {"schemaVersion":1,"id":"example","name":"Example","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"},"website":"file:///etc/passwd"}
                """;
        assertThrows(java.io.IOException.class, () -> reader.read(new StringReader(json)));
    }

    @Test void rejectsRequiredAndConflictingDependency() {
        String json = """
                {"schemaVersion":1,"id":"example","name":"Example","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"},
                 "depends":{"core":"*"},"conflicts":{"core":"*"}}
                """;
        assertThrows(java.io.IOException.class, () -> reader.read(new StringReader(json)));
    }

    @Test void rejectsUnknownFieldsRatherThanIgnoringTypos() {
        String json = """
                {"schemaVersion":1,"id":"example","name":"Example","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"},"depend":{}}
                """;
        assertThrows(java.io.IOException.class, () -> reader.read(new StringReader(json)));
    }

    @Test void rejectsUnsupportedSchemaVersion() {
        String json = """
                {"schemaVersion":2,"id":"example","name":"Example","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"}}
                """;
        assertThrows(java.io.IOException.class, () -> reader.read(new StringReader(json)));
    }

    @Test void rejectsNonIntegerSchemaVersion() {
        String json = """
                {"schemaVersion":1.5,"id":"example","name":"Example","version":"1.0.0",
                 "entrypoints":{"main":"example.Main"}}
                """;
        assertThrows(java.io.IOException.class, () -> reader.read(new StringReader(json)));
    }
}
