package dev.itsdropper.vantaloader.bootstrap;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VantaLoaderAgentTest {
    @Test
    void prefersTheExplicitLauncherInstanceDirectory() {
        Path resolved = VantaLoaderAgent.resolveGameDirectory(
                "instances/Survival World",
                "launcher-working-directory");

        assertEquals(Path.of("instances/Survival World").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void retainsWorkingDirectoryFallbackForOtherLaunchers() {
        Path resolved = VantaLoaderAgent.resolveGameDirectory(
                null,
                "standalone-instance");

        assertEquals(Path.of("standalone-instance").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void rejectsMissingInstanceDirectoryConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> VantaLoaderAgent.resolveGameDirectory(" ", " "));
    }
}
