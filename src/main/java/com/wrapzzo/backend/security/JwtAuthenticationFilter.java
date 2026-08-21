package com.wrapzzo.backend.security;

import com.wrapzzo.backend.service.AccessControlCacheService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the access token's signature and expiry, then builds this
 * request's authorities depending on the principal type. A User's
 * authorities are resolved fresh from Redis (never from the token itself).
 * A Vendor gets one fixed authority and no lookup at all, since every
 * vendor's capability set is identical -- see Vendor's doc comment. Either
 * way, ownership-of-a-specific-resource checks (a vendor touching only its
 * own work) still have to happen in the relevant service, not here.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    public static final String VENDOR_AUTHORITY = "ROLE_VENDOR";

    private final JwtService jwtService;
    private final AccessControlCacheService accessControlCacheService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                PrincipalClaims claims = jwtService.parseAccessToken(token);
                var authentication =
                        switch (claims) {
                            case UserPrincipalClaims u -> new UsernamePasswordAuthenticationToken(
                                    u.userId(), null, userAuthorities(u));
                            case VendorPrincipalClaims v -> new UsernamePasswordAuthenticationToken(
                                    v.vendorId(), null, Set.of(new SimpleGrantedAuthority(VENDOR_AUTHORITY)));
                        };
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private Set<GrantedAuthority> userAuthorities(UserPrincipalClaims claims) {
        Set<String> keys = accessControlCacheService.resolveEffectiveKeys(claims.userId(), claims.roleId());
        return keys.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }
}
