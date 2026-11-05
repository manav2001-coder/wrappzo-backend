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
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
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
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
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

    @Test
    void aDeletedUserIsNotLive() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(service.resolve(userId).active()).isFalse();
        assertThat(service.resolveEffectiveKeys(userId)).isEmpty();
    }

    @Test
    void aSuspendedUserIsNotLiveAndHoldsNoKeys() {
        AccessControl key = mock(AccessControl.class);
        when(key.getKey()).thenReturn("dashboard.view");
        when(role.isPlatformSuperAdmin()).thenReturn(true);
        when(accessControlRepository.findByType(any())).thenReturn(List.of(key));
        when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);

        assertThat(service.resolve(userId).active()).isFalse();
        assertThat(service.resolveEffectiveKeys(userId)).isEmpty();
    }

    @Test
    void everyoneInADeactivatedCompanyIsNotLive() {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(UUID.randomUUID());
        when(company.getStatus()).thenReturn(CompanyStatus.INACTIVE);
        when(user.getCompany()).thenReturn(company);
        when(role.isPlatformSuperAdmin()).thenReturn(false);

        assertThat(service.resolve(userId).active()).isFalse();

        // A blocked account is remembered, so a token held by one costs a cache read, not a lookup each time.
        verify(values).set(anyString(), org.mockito.ArgumentMatchers.startsWith("0|"), any(Duration.class));
    }

    @Test
    void aLiveUserIsLiveInAnActiveCompany() {
        AccessControl key = mock(AccessControl.class);
        when(key.getKey()).thenReturn("dashboard.view");
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(UUID.randomUUID());
        when(company.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(user.getCompany()).thenReturn(company);
        when(role.isPlatformSuperAdmin()).thenReturn(true);
        when(accessControlRepository.findByType(any())).thenReturn(List.of(key));

        assertThat(service.resolve(userId).active()).isTrue();
    }
}
