package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyAccessControlRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AccessControlCacheServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TemplateRepository templateRepository = mock(TemplateRepository.class);
    private final AccessControlRepository accessControlRepository = mock(AccessControlRepository.class);
    private final CompanyAccessControlRepository companyAccessControlRepository = mock(CompanyAccessControlRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private AccessControlCacheService service;
    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);
    private final Role role = mock(Role.class);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        service = new AccessControlCacheService(
                userRepository, templateRepository, accessControlRepository, companyAccessControlRepository, redisTemplate);
        when(user.getId()).thenReturn(userId);
        when(user.getRole()).thenReturn(role);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void anEmptyResultIsNotCached_soAGrantMadeLaterIsSeenAtOnce() {
        when(role.isPlatformSuperAdmin()).thenReturn(false);
        when(role.getId()).thenReturn(UUID.randomUUID());
        when(templateRepository.findTemplateIdsByRoleId(any())).thenReturn(List.of());
        when(templateRepository.findTemplateIdsByUserId(userId)).thenReturn(List.of());

        assertThat(service.resolveEffectiveKeys(userId)).isEmpty();

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void aNonEmptyResultIsCached() {
        AccessControl key = mock(AccessControl.class);
        when(key.getKey()).thenReturn("dashboard.view");
        when(role.isPlatformSuperAdmin()).thenReturn(true);
        when(accessControlRepository.findByType(any())).thenReturn(List.of(key));

        assertThat(service.resolveEffectiveKeys(userId)).isEqualTo(Set.of("dashboard.view"));

        verify(values).set(anyString(), anyString(), any(Duration.class));
    }
}
