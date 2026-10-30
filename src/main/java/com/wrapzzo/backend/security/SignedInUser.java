package com.wrapzzo.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Any signed-in company or platform user (not a vendor). For endpoints whose authorization
 * depends on the record itself -- "you are the requester's manager" -- and is enforced in the
 * service; say which rule applies in the endpoint's doc comment.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('USER')")
public @interface SignedInUser {
}
