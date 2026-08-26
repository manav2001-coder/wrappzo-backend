package com.wrapzzo.backend.security;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates short-lived access tokens for both principal types
 * (User and Vendor). Deliberately carries no permission data — just who the
 * principal is. Per-request authorization for Users is resolved fresh from
 * the Redis-backed AccessControlCacheService, never from claims baked in at
 * issuance time; Vendors get a fixed authority with no lookup at all, since
 * every vendor's capability set is identical (see Vendor's doc comment).
 */
@Component
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE_ID = "roleId";
    private static final String CLAIM_COMPANY_ID = "companyId";
    private static final String CLAIM_DESIGNATION = "designation";
    private static final String TYPE_USER = "USER";
    private static final String TYPE_VENDOR = "VENDOR";

    private final SecretKey signingKey;
    private final Duration accessTokenExpiry;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-minutes}") long accessTokenExpiryMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = Duration.ofMinutes(accessTokenExpiryMinutes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, TYPE_USER)
                .claim(CLAIM_ROLE_ID, user.getRole().getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiry)))
                .signWith(signingKey);
        if (user.getCompany() != null) {
            builder.claim(CLAIM_COMPANY_ID, user.getCompany().getId().toString());
        }
        if (user.getDesignation() != null) {
            builder.claim(CLAIM_DESIGNATION, user.getDesignation());
        }
        return builder.compact();
    }

    public String generateVendorAccessToken(Vendor vendor) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(vendor.getId().toString())
                .claim(CLAIM_TYPE, TYPE_VENDOR)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiry)))
                .signWith(signingKey)
                .compact();
    }

    /** @throws io.jsonwebtoken.JwtException if the token is missing, malformed, expired, or fails signature verification */
    public PrincipalClaims parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        String type = claims.get(CLAIM_TYPE, String.class);
        UUID subject = UUID.fromString(claims.getSubject());

        if (TYPE_VENDOR.equals(type)) {
            return new VendorPrincipalClaims(subject);
        }

        UUID roleId = UUID.fromString(claims.get(CLAIM_ROLE_ID, String.class));
        String companyIdClaim = claims.get(CLAIM_COMPANY_ID, String.class);
        UUID companyId = companyIdClaim != null ? UUID.fromString(companyIdClaim) : null;
        String designation = claims.get(CLAIM_DESIGNATION, String.class);
        return new UserPrincipalClaims(subject, roleId, companyId, designation);
    }

    public Duration getAccessTokenExpiry() {
        return accessTokenExpiry;
    }
}
