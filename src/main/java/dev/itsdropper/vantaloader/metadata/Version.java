package dev.itsdropper.vantaloader.metadata;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic version value used by VantaLoader metadata. */
public record Version(int major, int minor, int patch, String prerelease)
        implements Comparable<Version> {
    private static final String NUMERIC_IDENTIFIER = "(?:0|[1-9][0-9]*)";
    private static final String NON_NUMERIC_IDENTIFIER =
            "(?=[0-9A-Za-z-]*[A-Za-z-])[0-9A-Za-z-]+";
    private static final String PRERELEASE_IDENTIFIER =
            "(?:" + NUMERIC_IDENTIFIER + "|" + NON_NUMERIC_IDENTIFIER + ")";
    private static final Pattern PRERELEASE_PATTERN = Pattern.compile(
            PRERELEASE_IDENTIFIER + "(?:\\." + PRERELEASE_IDENTIFIER + ")*");
    private static final Pattern PATTERN = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-(" + PRERELEASE_IDENTIFIER + "(?:\\." + PRERELEASE_IDENTIFIER + ")*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$"
    );

    public Version {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components cannot be negative");
        }
        if (prerelease != null && !PRERELEASE_PATTERN.matcher(prerelease).matches()) {
            throw new IllegalArgumentException("Invalid semantic-version prerelease: " + prerelease);
        }
    }

    public static Version parse(String value) {
        Objects.requireNonNull(value, "value");
        Matcher matcher = PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version '" + value
                    + "'. Expected MAJOR.MINOR.PATCH with valid optional prerelease/build metadata.");
        }
        try {
            return new Version(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                    matcher.group(4));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Version component is too large: " + value, exception);
        }
    }

    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;
        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;
        result = Integer.compare(patch, other.patch);
        if (result != 0) return result;
        if (prerelease == null && other.prerelease == null) return 0;
        if (prerelease == null) return 1;
        if (other.prerelease == null) return -1;
        return comparePrerelease(prerelease, other.prerelease);
    }

    private static int comparePrerelease(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            boolean an = a[i].matches("[0-9]+");
            boolean bn = b[i].matches("[0-9]+");
            int compared;
            if (an && bn) compared = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
            else if (an != bn) compared = an ? -1 : 1;
            else compared = a[i].compareTo(b[i]);
            if (compared != 0) return compared;
        }
        return Integer.compare(a.length, b.length);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (prerelease == null ? "" : "-" + prerelease);
    }
}
