package com.wrapzzo.backend.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditNameListenerTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserRepository> provider = mock(ObjectProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditNameListener listener = new AuditNameListener(provider);

    private static class Row extends AuditNames {}

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(UUID principal, String name) {
        when(provider.getObject()).thenReturn(users);
        if (name == null) {
            when(users.findById(principal)).thenReturn(Optional.empty());
        } else {
            User user = new User();
            user.setName(name);
            when(users.findById(principal)).thenReturn(Optional.of(user));
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void createStampsBothNamesAndUpdateOnlyTheLastChanger() {
        signInAs(UUID.randomUUID(), "Asha");
        Row row = new Row();
        listener.onCreate(row);
        assertThat(row.getCreatedByName()).isEqualTo("Asha");
        assertThat(row.getUpdatedByName()).isEqualTo("Asha");

        signInAs(UUID.randomUUID(), "Ravi");
        listener.onUpdate(row);
        assertThat(row.getCreatedByName()).isEqualTo("Asha");
        assertThat(row.getUpdatedByName()).isEqualTo("Ravi");
    }

    @Test
    void nobodySignedInOrNotAUserLeavesTheNamesAlone() {
        Row row = new Row();
        row.setUpdatedByName("Asha");
        listener.onUpdate(row);
        assertThat(row.getUpdatedByName()).isEqualTo("Asha");

        signInAs(UUID.randomUUID(), null);
        listener.onUpdate(row);
        assertThat(row.getUpdatedByName()).isEqualTo("Asha");
    }
}
