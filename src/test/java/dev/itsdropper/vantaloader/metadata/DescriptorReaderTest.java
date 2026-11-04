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
