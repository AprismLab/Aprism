package com.aprism.manifest;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.aprism.api.ModContainer;

/**
 * Resolves mod dependencies via topological sort. Detects missing dependencies,
 * version conflicts, and dependency cycles, ordering mods so that a mod's
 * dependencies are always loaded before it.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class DependencyResolver {

    /**
     * A directed dependency edge between two mods.
     *
     * @param from          the mod that depends on {@code to}
     * @param to            the mod that is depended upon
     * @param versionRange  the required version range of {@code to}
     */
    public record DependencyEdge(String from, String to, String versionRange) {
    }

    /**
     * Resolves the load order for the given manifests.
     *
     * @param manifests the manifests to resolve
     * @return the manifests' mod containers in dependency order
     * @throws DependencyResolutionException if a dependency is missing, conflicts, or forms a cycle
     */
    public List<ModContainer> resolve(Collection<AprismManifest> manifests) throws DependencyResolutionException {
        Map<String, AprismManifest> byId = new LinkedHashMap<>();
        for (AprismManifest m : manifests) {
            if (byId.put(m.id(), m) != null) {
                throw new DependencyResolutionException("Duplicate mod id: " + m.id());
            }
        }

        List<DependencyEdge> edges = new ArrayList<>();
        for (AprismManifest m : manifests) {
            if (m.depends() == null) {
                continue;
            }
            for (Map.Entry<String, String> dep : m.depends().entrySet()) {
                AprismManifest target = byId.get(dep.getKey());
                if (target == null) {
                    throw new DependencyResolutionException(
                            "Mod " + m.id() + " requires missing dependency " + dep.getKey());
                }
                if (!versionSatisfies(target.version(), dep.getValue())) {
                    throw new DependencyResolutionException(
                            "Mod " + m.id() + " requires " + dep.getKey() + " " + dep.getValue()
                                    + " but found " + target.version());
                }
                edges.add(new DependencyEdge(m.id(), dep.getKey(), dep.getValue()));
            }
        }

        List<String> ordered = topologicalSort(byId.keySet(), edges);
        List<ModContainer> result = new ArrayList<>(ordered.size());
        for (String id : ordered) {
            result.add(new SimpleModContainer(byId.get(id)));
        }
        return result;
    }

    /**
     * Performs a topological sort (Kahn's algorithm) over the dependency graph.
     *
     * @param nodes the set of mod ids
     * @param edges the dependency edges
     * @return the mod ids in dependency order
     * @throws DependencyResolutionException if a cycle is detected
     */
    private List<String> topologicalSort(Set<String> nodes, List<DependencyEdge> edges)
            throws DependencyResolutionException {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (String n : nodes) {
            inDegree.put(n, 0);
            adj.put(n, new ArrayList<>());
        }
        for (DependencyEdge e : edges) {
            adj.get(e.to()).add(e.from());
            inDegree.merge(e.from(), 1, Integer::sum);
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> en : inDegree.entrySet()) {
            if (en.getValue() == 0) {
                queue.add(en.getKey());
            }
        }

        List<String> sorted = new ArrayList<>(nodes.size());
        while (!queue.isEmpty()) {
            String n = queue.poll();
            sorted.add(n);
            for (String next : adj.get(n)) {
                int remaining = inDegree.merge(next, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(next);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new DependencyResolutionException("Dependency cycle detected among mods");
        }
        return sorted;
    }

    /**
     * Simplified version-range check. Accepts {@code *}, exact matches, and
     * basic prefix/range expressions.
     *
     * @param actual the available version
     * @param range  the required range expression
     * @return whether {@code actual} satisfies {@code range}
     */
    private boolean versionSatisfies(String actual, String range) {
        if (range == null || range.isEmpty() || range.equals("*")) {
            return true;
        }
        if (range.equals(actual)) {
            return true;
        }
        if (range.startsWith("[") || range.startsWith("(")) {
            String inner = range.replaceAll("[\\[\\]()]", "");
            String lower = inner.split(",")[0].trim();
            return !lower.isEmpty() && actual.startsWith(lower);
        }
        return actual.startsWith(range);
    }

    /**
     * Minimal {@link ModContainer} backed by a manifest. Used so that the
     * resolver can return mod containers before the full runtime is wired.
     *
     * @author BlockConnect@StarsailsClover
     */
    private record SimpleModContainer(AprismManifest manifest) implements ModContainer {
        @Override
        public String getId() {
            return manifest.id();
        }

        @Override
        public String getVersion() {
            return manifest.version();
        }

        @Override
        public String getDisplayName() {
            return manifest.displayName();
        }

        @Override
        public String getDescription() {
            return manifest.description();
        }

        @Override
        public Path getSourcePath() {
            return null;
        }

        @Override
        public Object getInstance() {
            return null;
        }

        @Override
        public <T> Optional<T> getInstance(Class<T> type) {
            return Optional.empty();
        }
    }
}
