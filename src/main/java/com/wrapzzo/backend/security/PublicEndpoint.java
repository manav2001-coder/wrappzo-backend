package com.wrapzzo.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Deliberately reachable without a permission: sign-in flows and token-gated public links
 * (SecurityConfig permits their paths). A marker for readers and for EndpointAuthorizationTest,
 * which fails on any endpoint that is neither guarded nor marked public.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
}
