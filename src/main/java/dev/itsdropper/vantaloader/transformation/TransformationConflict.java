package dev.itsdropper.vantaloader.transformation;

import java.util.List;
import java.util.Objects;

/** A diagnostic that multiple independent mods contribute bytecode to one class. */
public record TransformationConflict(
        String runtimeTargetClassName,
        List<Contribution> contributions
) {
    public TransformationConflict {
        if (runtimeTargetClassName == null || runtimeTargetClassName.isBlank()) {
            throw new IllegalArgumentException("runtimeTargetClassName must not be blank");
        }
        contributions = List.copyOf(Objects.requireNonNull(contributions, "contributions"));
        if (contributions.size() < 2
                || contributions.stream().map(Contribution::modId).distinct().count() < 2) {
            throw new IllegalArgumentException("A potential conflict requires contributions from multiple mods");
        }
    }

    /** One ordered contribution to the shared runtime target. */
    public record Contribution(String modId, String transformationId, int priority) {
        public Contribution {
            if (modId == null || modId.isBlank()) throw new IllegalArgumentException("modId must not be blank");
            if (transformationId == null || transformationId.isBlank()) {
                throw new IllegalArgumentException("transformationId must not be blank");
            }
        }
    }
}
