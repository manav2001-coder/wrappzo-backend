package com.wrapzzo.backend.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The template inclusion graph, as plain maps so the rules are testable without a database.
 * Resolution is an additive union -- an included template can only add keys -- and fails closed:
 * a branch that runs deeper than {@link #MAX_DEPTH} or loops back onto its own path contributes
 * nothing rather than looping. Writes are checked with {@link #wouldCreateCycle} first, so a stored
 * graph never trips either limit.
 */
public final class TemplateGraph {

    /** How many inclusion hops below a template are followed. */
    public static final int MAX_DEPTH = 5;

    private final Map<UUID, Set<UUID>> edges;
    private final Map<UUID, Set<String>> directKeys;

    public TemplateGraph(Map<UUID, Set<UUID>> edges, Map<UUID, Set<String>> directKeys) {
        this.edges = edges;
        this.directKeys = directKeys;
    }

    /** Every key the given templates grant, directly or through what they include. */
    public Set<String> resolve(Collection<UUID> roots) {
        Set<String> keys = new HashSet<>();
        for (UUID root : roots) {
            collect(root, 0, new HashSet<>(), keys);
        }
        return keys;
    }

    private void collect(UUID templateId, int depth, Set<UUID> path, Set<String> out) {
        if (depth > MAX_DEPTH || !path.add(templateId)) {
            return;
        }
        out.addAll(directKeys.getOrDefault(templateId, Set.of()));
        for (UUID included : edges.getOrDefault(templateId, Set.of())) {
            collect(included, depth + 1, path, out);
        }
        path.remove(templateId);
    }

    /**
     * Whether making {@code parent} include {@code candidate} would close a loop, or stack the
     * chain deeper than {@link #MAX_DEPTH} (counting what already includes parent, and what
     * candidate already includes).
     */
    public boolean wouldCreateCycle(UUID parent, UUID candidate) {
        if (parent.equals(candidate) || reaches(candidate, parent, new HashSet<>())) {
            return true;
        }
        return heightBelow(candidate, new HashSet<>()) + 1 + heightAbove(parent, new HashSet<>()) > MAX_DEPTH;
    }

    /**
     * Whether {@code templateId} may include exactly {@code candidates}, replacing what it includes
     * now: each one is checked against the graph with the earlier ones already added.
     */
    public boolean allowsReferences(UUID templateId, Collection<UUID> candidates) {
        Map<UUID, Set<UUID>> proposed = new HashMap<>();
        edges.forEach((from, to) -> proposed.put(from, new HashSet<>(to)));
        proposed.remove(templateId);
        TemplateGraph graph = new TemplateGraph(proposed, directKeys);
        for (UUID candidate : candidates) {
            if (graph.wouldCreateCycle(templateId, candidate)) {
                return false;
            }
            proposed.computeIfAbsent(templateId, k -> new HashSet<>()).add(candidate);
        }
        return true;
    }

    private boolean reaches(UUID from, UUID target, Set<UUID> seen) {
        if (from.equals(target)) {
            return true;
        }
        if (!seen.add(from)) {
            return false;
        }
        for (UUID next : edges.getOrDefault(from, Set.of())) {
            if (reaches(next, target, seen)) {
                return true;
            }
        }
        return false;
    }

    private int heightBelow(UUID node, Set<UUID> path) {
        if (!path.add(node)) {
            return 0;
        }
        int height = 0;
        for (UUID next : edges.getOrDefault(node, Set.of())) {
            height = Math.max(height, 1 + heightBelow(next, path));
        }
        path.remove(node);
        return height;
    }

    private int heightAbove(UUID node, Set<UUID> path) {
        if (!path.add(node)) {
            return 0;
        }
        int height = 0;
        for (Map.Entry<UUID, Set<UUID>> entry : edges.entrySet()) {
            if (entry.getValue().contains(node)) {
                height = Math.max(height, 1 + heightAbove(entry.getKey(), path));
            }
        }
        path.remove(node);
        return height;
    }
}
