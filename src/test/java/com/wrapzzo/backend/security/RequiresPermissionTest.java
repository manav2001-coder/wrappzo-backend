package com.wrapzzo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** The guard annotations really enforce what they say once Spring Security's method security is on (as in SecurityConfig). */
class RequiresPermissionTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
            return new AnnotationTemplateExpressionDefaults();
        }

        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    static class Guarded {
        @RequiresPermission(Permission.Keys.ORDER_VIEW)
        public String viewOrders() {
            return "ok";
        }

        @SignedInUser
        public String anySignedInUser() {
            return "ok";
        }

        @VendorEndpoint
        public String vendorsOnly() {
            return "ok";
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresPermissionAllowsOnlyHoldersOfThatPermission() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Guarded guarded = context.getBean(Guarded.class);

            signInWith("ROLE_USER", Permission.Keys.ORDER_VIEW);
            assertThat(guarded.viewOrders()).isEqualTo("ok");

            signInWith("ROLE_USER", Permission.Keys.ORDER_ARTWORK_REVIEW);
            assertThatThrownBy(guarded::viewOrders).isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
        }
    }

    @Test
    void signedInUserAndVendorEndpointSeparateTheTwoKindsOfLogin() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Guarded guarded = context.getBean(Guarded.class);

            signInWith("ROLE_USER");
            assertThat(guarded.anySignedInUser()).isEqualTo("ok");
            assertThatThrownBy(guarded::vendorsOnly).isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);

            signInWith("ROLE_VENDOR");
            assertThat(guarded.vendorsOnly()).isEqualTo("ok");
            assertThatThrownBy(guarded::anySignedInUser).isInstanceOfAny(AccessDeniedException.class, AuthorizationDeniedException.class);
        }
    }

    private static void signInWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "someone", null, List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
