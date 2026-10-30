package com.wrapzzo.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** A signed-in vendor, acting on their own data (CurrentVendor) -- vendors have one login each and no permission keys. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('VENDOR')")
public @interface VendorEndpoint {
}
