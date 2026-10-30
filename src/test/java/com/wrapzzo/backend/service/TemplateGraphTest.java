package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateGraphTest {

    private final Map<UUID, Set<UUID>> edges = new HashMap<>();
    private final Map<UUID, Set<String>> keys = new HashMap<>();

    private UUID template(String... directKeys) {
        UUID id = UUID.randomUUID();
        keys.put(id, Set.of(directKeys));
        return id;
    }

    private void include(UUID parent, UUID... included) {
        edges.computeIfAbsent(parent, k -> new java.util.HashSet<>()).addAll(List.of(included));
    }

    private TemplateGraph graph() {
        return new TemplateGraph(edges, keys);
    }

    @Test
    void resolvesDirectKeysAndEverythingIncluded() {
        UUID base = template("order.view");
        UUID mid = template("campaign.view");
        UUID top = template("user.view");
        include(top, mid);
        include(mid, base);

        assertThat(graph().resolve(List.of(top))).containsExactlyInAnyOrder("user.view", "campaign.view", "order.view");
        assertThat(graph().resolve(List.of(base))).containsExactly("order.view");
    }

    @Test
    void unionsSeveralRootsAndSharedIncludes() {
        UUID shared = template("order.view");
        UUID a = template("user.view");
        UUID b = template("campaign.view");
        include(a, shared);
        include(b, shared);

        assertThat(graph().resolve(List.of(a, b))).containsExactlyInAnyOrder("user.view", "campaign.view", "order.view");
    }

    @Test
    void aTemplateWithNoEntryGrantsNothing() {
        assertThat(graph().resolve(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void aLoopThatSlippedIntoStorageResolvesWithoutHanging() {
        UUID a = template("user.view");
        UUID b = template("order.view");
        include(a, b);
        include(b, a);

        assertThat(graph().resolve(List.of(a))).containsExactlyInAnyOrder("user.view", "order.view");
    }

    @Test
    void resolutionStopsPastTheDepthLimit() {
        UUID[] chain = new UUID[TemplateGraph.MAX_DEPTH + 2];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = template("key" + i);
            if (i > 0) {
                include(chain[i - 1], chain[i]);
            }
        }

        Set<String> resolved = graph().resolve(List.of(chain[0]));

        assertThat(resolved).contains("key0", "key" + TemplateGraph.MAX_DEPTH).doesNotContain("key" + (TemplateGraph.MAX_DEPTH + 1));
    }

    @Test
    void refusesSelfAndLoopingInclusions() {
        UUID a = template();
        UUID b = template();
        UUID c = template();
        include(a, b);
        include(b, c);

        assertThat(graph().wouldCreateCycle(a, a)).isTrue();
        assertThat(graph().wouldCreateCycle(c, a)).isTrue();
        assertThat(graph().wouldCreateCycle(a, c)).isFalse();
    }

    @Test
    void refusesAChainDeeperThanTheLimitCountingBothDirections() {
        UUID[] chain = new UUID[TemplateGraph.MAX_DEPTH + 1];
        for (int i = 0; i < chain.length; i++) {
            chain[i] = template();
            if (i > 0) {
                include(chain[i - 1], chain[i]);
            }
        }
        UUID extraTop = template();
        UUID extraBottom = template();

        assertThat(graph().wouldCreateCycle(extraTop, chain[0])).isTrue();
        assertThat(graph().wouldCreateCycle(chain[chain.length - 1], extraBottom)).isTrue();
        assertThat(graph().wouldCreateCycle(chain[1], extraBottom)).isFalse();
    }

    @Test
    void allowsReferencesJudgesTheReplacementNotTheOldInclusions() {
        UUID a = template();
        UUID b = template();
        UUID c = template();
        include(a, b);
        include(b, c);

        assertThat(graph().allowsReferences(a, List.of(c))).isTrue();
        assertThat(graph().allowsReferences(c, List.of(a))).isFalse();
        // a's old inclusion of b no longer counts once a is replaced with just c
        assertThat(graph().allowsReferences(b, List.of(a))).isFalse();
        assertThat(graph().allowsReferences(c, List.of(b))).isFalse();
    }
}
