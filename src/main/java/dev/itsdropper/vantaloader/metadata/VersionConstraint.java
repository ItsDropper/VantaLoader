package dev.itsdropper.vantaloader.metadata;

import java.util.ArrayList;
import java.util.List;

/** Conjunction of exact, comparison, or wildcard version predicates. */
public final class VersionConstraint {
    private final List<Predicate> predicates;
    private final String source;

    private VersionConstraint(String source, List<Predicate> predicates) {
        this.source = source;
        this.predicates = List.copyOf(predicates);
    }

    public static VersionConstraint parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Version constraint cannot be blank");
        }
        String source = expression.trim();
        if (source.equals("*")) return new VersionConstraint(source, List.of());
        List<Predicate> predicates = new ArrayList<>();
        for (String token : source.split("[ ,]+")) {
            String operator;
            if (token.startsWith(">=") || token.startsWith("<=")) operator = token.substring(0, 2);
            else if (token.startsWith(">") || token.startsWith("<") || token.startsWith("=")) {
                operator = token.substring(0, 1);
            } else operator = "=";
            String versionText = operator.equals("=") && !token.startsWith("=")
                    ? token : token.substring(operator.length());
            if (versionText.isBlank()) {
                throw new IllegalArgumentException("Missing version in constraint token '" + token + "'");
            }
            Version expected = Version.parse(versionText);
            predicates.add(switch (operator) {
                case ">=" -> actual -> actual.compareTo(expected) >= 0;
                case ">" -> actual -> actual.compareTo(expected) > 0;
                case "<=" -> actual -> actual.compareTo(expected) <= 0;
                case "<" -> actual -> actual.compareTo(expected) < 0;
                case "=" -> actual -> actual.compareTo(expected) == 0;
                default -> throw new IllegalStateException("Unsupported operator: " + operator);
            });
        }
        return new VersionConstraint(source, predicates);
    }

    public boolean test(Version version) {
        return predicates.stream().allMatch(predicate -> predicate.test(version));
    }

    @Override public String toString() { return source; }

    @FunctionalInterface
    private interface Predicate { boolean test(Version version); }
}
