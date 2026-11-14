package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Only whoever holds price.view -- the company admin -- may see what a gift costs. */
class PricePolicyTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccessControlCacheService keys = mock(AccessControlCacheService.class);
    private final PricePolicy policy = new PricePolicy(users, keys);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean canSeeWith(Set<String> held) {
        UUID id = UUID.randomUUID();
        User user = mock(User.class);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(keys.resolveEffectiveKeys(user)).thenReturn(held);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id, null, java.util.List.of()));
        return policy.canSee();
    }

    @Test
    void theCompanyAdminSeesPrices() {
        assertThat(canSeeWith(Set.of("price.view"))).isTrue();
    }

    @Test
    void everyoneWhoOrdersOrApprovesDoesNot() {
        assertThat(canSeeWith(Set.of("order.view", "quick_gift.create", "gift_store.view", "approval.decide", "vendor_identity.view"))).isFalse();
        assertThat(canSeeWith(Set.of())).isFalse();
    }
}
