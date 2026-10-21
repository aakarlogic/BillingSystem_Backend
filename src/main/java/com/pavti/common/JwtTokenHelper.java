package com.pavti.common;

import com.pavti.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Makes and checks access tokens (Health 360: LIMS/Common/JwtTokenHelper).
 * Claims: sub = userId, bid = businessId, role = role code, sid = session id.
 */
@Component
public class JwtTokenHelper {

    public record TokenClaims(long userId, Long businessId, String roleCode, long sessionId) {}

    private final SecretKey key;
    private final String issuer;
    private final int accessMinutes;

    public JwtTokenHelper(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("pavti.jwt.secret must be at least 32 characters.");
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = props.jwt().issuer();
        this.accessMinutes = props.jwt().accessTokenMinutes();
    }

    public String createAccessToken(long userId, Long businessId, String roleCode, long sessionId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("role", roleCode)
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessMinutes * 60L)));
        if (businessId != null) builder.claim("bid", businessId);
        return builder.signWith(key).compact();
    }

    /** Returns the claims, or throws 401 when the token is missing, wrong or expired. */
    public TokenClaims read(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw ApiException.unauthorized("Please sign in.");
        }
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(authorizationHeader.substring(7).trim())
                    .getPayload();
            Number bid = c.get("bid", Number.class);
            Number sid = c.get("sid", Number.class);
            return new TokenClaims(Long.parseLong(c.getSubject()), bid == null ? null : bid.longValue(),
                    c.get("role", String.class), sid == null ? 0 : sid.longValue());
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Your session has ended. Please sign in again.");
        }
    }
}
