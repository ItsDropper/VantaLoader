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
    @Test void evaluatesConjoinedConstraints() {
        VersionConstraint c = VersionConstraint.parse(">=1.2.0 <2.0.0");
        assertTrue(c.test(Version.parse("1.8.4")));
        assertFalse(c.test(Version.parse("2.0.0")));
        assertFalse(c.test(Version.parse("1.1.9")));
    }
    @Test void wildcardAcceptsEveryVersion() {
        assertTrue(VersionConstraint.parse("*").test(Version.parse("0.0.1")));
    }
    @Test void rejectsMalformedVersions() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2"));
    }
}
