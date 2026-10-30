package com.wrapzzo.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * The caller must hold this permission ({@link Permission.Keys}). On a controller class it
 * applies to every endpoint that doesn't declare its own. For "any of several", use
 * {@code @PreAuthorize("hasAnyAuthority(...)")} with Keys constants directly.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAuthority('{value}')")
public @interface RequiresPermission {

    String value();
}
