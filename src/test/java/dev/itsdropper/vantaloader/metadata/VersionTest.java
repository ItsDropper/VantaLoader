package dev.itsdropper.vantaloader.metadata;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VersionTest {
    @Test void comparesNumericComponentsRatherThanLexically() {
        assertTrue(Version.parse("1.10.0").compareTo(Version.parse("1.9.0")) > 0);
    }
    @Test void releaseIsNewerThanPrerelease() {
        assertTrue(Version.parse("1.0.0").compareTo(Version.parse("1.0.0-rc.1")) > 0);
    }
    @Test void comparesPrereleaseIdentifiersAccordingToSemver() {
        assertTrue(Version.parse("1.0.0-alpha.2").compareTo(Version.parse("1.0.0-alpha.10")) < 0);
        assertTrue(Version.parse("1.0.0-alpha").compareTo(Version.parse("1.0.0-alpha.1")) < 0);
        assertTrue(Version.parse("1.0.0-1").compareTo(Version.parse("1.0.0-alpha")) < 0);
    }
    @Test void evaluatesConjoinedConstraints() {
        VersionConstraint c = VersionConstraint.parse(">=1.2.0 <2.0.0");
        assertTrue(c.test(Version.parse("1.8.4")));
        assertFalse(c.test(Version.parse("2.0.0")));
        assertFalse(c.test(Version.parse("1.1.9")));
    }
    @Test void wildcardAcceptsEveryVersion() {
        assertTrue(VersionConstraint.parse("*").test(Version.parse("0.0.1")));
    }
    @Test void acceptsValidBuildMetadataWithoutChangingPrecedence() {
        assertEquals(0, Version.parse("1.2.3+build.007").compareTo(Version.parse("1.2.3+other")));
    }
    @Test void rejectsMalformedVersions() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3-01"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3-alpha..1"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3+build."));
    }
    @Test void rejectsInvalidPrereleaseInDirectConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Version(1, 2, 3, "01"));
    }
}
