package dev.itsdropper.vantaloader.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed Minecraft game-version identity.
 *
 * <p>This type deliberately does not implement {@link Comparable}: release numbering
 * (for example, 1.21.x versus 26.x) and snapshot numbering are different schemes.
 * Version adapters must make compatibility decisions explicitly rather than relying
 * on a misleading global numeric ordering.</p>
 */
public record MinecraftVersion(
        String raw,
        Kind kind,
        int major,
        int minor,
        int patch,
        int snapshotYear,
        int snapshotWeek,
        char snapshotLetter
) {
    private static final Pattern RELEASE = Pattern.compile(
            "^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+][A-Za-z0-9._-]+)?$");
    private static final Pattern SNAPSHOT = Pattern.compile(
            "^(\\d{2})w(\\d{2})([a-z])$");

    public enum Kind {
        RELEASE,
        SNAPSHOT,
        UNKNOWN
    }

    public MinecraftVersion {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("raw version must not be blank");
        if (kind == null) throw new NullPointerException("kind");
        if (kind == Kind.RELEASE && (major < 0 || minor < 0 || patch < 0)) {
            throw new IllegalArgumentException("release components must be non-negative");
        }
        if (kind == Kind.SNAPSHOT && (snapshotYear < 0 || snapshotWeek < 1
                || snapshotWeek > 53 || snapshotLetter < 'a' || snapshotLetter > 'z')) {
            throw new IllegalArgumentException("invalid snapshot components");
        }
    }

    /**
     * Parses stable release strings (including legacy 1.x releases) and Mojang
     * weekly snapshot identifiers. Unrecognized values remain available as UNKNOWN.
     */
    public static MinecraftVersion parse(String value) {
        if (value == null || value.isBlank()) {
            return unknown("unknown");
        }

        Matcher release = RELEASE.matcher(value);
        if (release.matches()) {
            try {
                return new MinecraftVersion(value, Kind.RELEASE,
                        Integer.parseInt(release.group(1)),
                        Integer.parseInt(release.group(2)),
                        release.group(3) == null ? 0 : Integer.parseInt(release.group(3)),
                        0, 0, '\0');
            } catch (IllegalArgumentException ignored) {
                return unknown(value);
            }
        }

        Matcher snapshot = SNAPSHOT.matcher(value);
        if (snapshot.matches()) {
            try {
                return new MinecraftVersion(value, Kind.SNAPSHOT,
                        0, 0, 0,
                        2000 + Integer.parseInt(snapshot.group(1)),
                        Integer.parseInt(snapshot.group(2)),
                        snapshot.group(3).charAt(0));
            } catch (IllegalArgumentException ignored) {
                return unknown(value);
            }
        }
        return unknown(value);
    }

    private static MinecraftVersion unknown(String value) {
        return new MinecraftVersion(value, Kind.UNKNOWN, 0, 0, 0, 0, 0, '\0');
    }

    public boolean isKnown() {
        return kind != Kind.UNKNOWN;
    }

    public boolean isRelease() {
        return kind == Kind.RELEASE;
    }

    public boolean isSnapshot() {
        return kind == Kind.SNAPSHOT;
    }
}
