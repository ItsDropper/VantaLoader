package dev.itsdropper.vantaloader.dependency;

import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.metadata.Version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Validates dependencies and produces deterministic dependency-first order. */
public final class ModResolver {
    public List<ModArtifact> resolve(List<ModArtifact> artifacts) throws IOException {
        Map<String, ModArtifact> byId = new HashMap<>();
        for (ModArtifact artifact : artifacts) {
            String id = artifact.descriptor().id();
            if (byId.putIfAbsent(id, artifact) != null) throw new IOException("Duplicate mod id '" + id + "'");
        }
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String id : byId.keySet()) {
            indegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        }
        for (ModArtifact artifact : artifacts) {
            var descriptor = artifact.descriptor();
            validateIncompatibilities(descriptor.id(), "conflicts", descriptor.conflicts(), byId);
            validateIncompatibilities(descriptor.id(), "breaks", descriptor.breaks(), byId);
            for (var dependency : descriptor.depends().entrySet()) {
                ModArtifact target = byId.get(dependency.getKey());
                if (target == null) throw new IOException("Mod '" + descriptor.id()
                        + "' requires missing mod '" + dependency.getKey() + "' (" + dependency.getValue() + ")");
                Version actual = target.descriptor().version();
                if (!dependency.getValue().test(actual)) {
                    throw new IOException("Mod '" + descriptor.id() + "' requires '" + dependency.getKey()
                            + "' " + dependency.getValue() + " but found " + actual);
                }
                indegree.compute(descriptor.id(), (ignored, value) -> value + 1);
                dependents.get(dependency.getKey()).add(descriptor.id());
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        List<ModArtifact> ordered = new ArrayList<>(artifacts.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(byId.get(id));
            for (String dependent : dependents.get(id).stream().sorted().toList()) {
                int remaining = indegree.compute(dependent, (ignored, value) -> value - 1);
                if (remaining == 0) ready.add(dependent);
            }
        }
        if (ordered.size() != artifacts.size()) {
            List<String> cycle = indegree.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey).sorted().toList();
            throw new IOException("Dependency cycle detected among mods: " + String.join(", ", cycle));
        }
        return List.copyOf(ordered);
    }

    private static void validateIncompatibilities(
            String modId, String relationship,
            Map<String, dev.itsdropper.vantaloader.metadata.VersionConstraint> constraints,
            Map<String, ModArtifact> installed) throws IOException {
        for (var declaration : constraints.entrySet()) {
            ModArtifact target = installed.get(declaration.getKey());
            if (target != null && declaration.getValue().test(target.descriptor().version())) {
                throw new IOException("Mod '" + modId + "' " + relationship + " '"
                        + declaration.getKey() + "' " + declaration.getValue()
                        + " but version " + target.descriptor().version() + " is installed");
            }
        }
    }
}
