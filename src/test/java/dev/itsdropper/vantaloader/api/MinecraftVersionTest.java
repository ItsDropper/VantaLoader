package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftVersionTest {
    @Test
    void parsesLegacyAndModernReleaseVersions() {
        MinecraftVersion legacy = MinecraftVersion.parse("1.8");
        assertEquals(MinecraftVersion.Kind.RELEASE, legacy.kind());
        assertEquals(1, legacy.major());
        assertEquals(8, legacy.minor());
        assertEquals(0, legacy.patch());

        MinecraftVersion modern = MinecraftVersion.parse("1.21.11");
        assertEquals(MinecraftVersion.Kind.RELEASE, modern.kind());
        assertEquals(1, modern.major());
        assertEquals(21, modern.minor());
        assertEquals(11, modern.patch());

        MinecraftVersion yearBased = MinecraftVersion.parse("26.1");
        assertEquals(MinecraftVersion.Kind.RELEASE, yearBased.kind());
        assertEquals(26, yearBased.major());
        assertEquals(1, yearBased.minor());
    }

    @Test
    void parsesWeeklySnapshotsWithoutTreatingThemAsReleaseNumbers() {
        MinecraftVersion snapshot = MinecraftVersion.parse("24w14a");
        assertEquals(MinecraftVersion.Kind.SNAPSHOT, snapshot.kind());
        assertEquals(2024, snapshot.snapshotYear());
        assertEquals(14, snapshot.snapshotWeek());
        assertEquals('a', snapshot.snapshotLetter());
        assertTrue(snapshot.isSnapshot());
    }

    @Test
    void preservesUnknownAndMalformedVersionStrings() {
        MinecraftVersion unknown = MinecraftVersion.parse("unknown");
        assertEquals(MinecraftVersion.Kind.UNKNOWN, unknown.kind());
        assertEquals("unknown", unknown.raw());
        assertFalse(unknown.isKnown());

        assertEquals(MinecraftVersion.Kind.UNKNOWN, MinecraftVersion.parse("not-a-version").kind());
        assertEquals(MinecraftVersion.Kind.UNKNOWN, MinecraftVersion.parse(null).kind());
        assertEquals(MinecraftVersion.Kind.UNKNOWN, MinecraftVersion.parse("").kind());
        assertEquals(MinecraftVersion.Kind.UNKNOWN, MinecraftVersion.parse("   ").kind());
        assertEquals(MinecraftVersion.Kind.UNKNOWN, MinecraftVersion.parse("99w99z").kind());
        assertEquals(MinecraftVersion.Kind.UNKNOWN, MinecraftVersion.parse("24w00a").kind());
    }

    @Test
    void doesNotPretendDifferentMinecraftNumberingSchemesAreComparable() {
        assertFalse(Comparable.class.isAssignableFrom(MinecraftVersion.class));
    }
}
