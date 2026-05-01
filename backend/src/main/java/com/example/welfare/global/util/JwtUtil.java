package com.example.welfare.global.util;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtUtil {
    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "uid";
    private static final String ISSUED_AT_MILLIS_CLAIM = "iatm";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${jwt.notification-expiration:2592000000}")
    private long notificationExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId) {
        return generateAccessToken(userId, List.of("ROLE_USER"));
    }

    public String generateAccessToken(Long userId, Collection<String> roles) {
        return buildToken(String.valueOf(userId), userId, accessExpiration, roles, true);
    }

    public String generateAccessToken(String userKey, Long userId) {
        return generateAccessToken(userKey, userId, List.of("ROLE_USER"));
    }

    public String generateAccessToken(String userKey, Long userId, Collection<String> roles) {
        return buildToken(userKey, userId, accessExpiration, roles, true);
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(String.valueOf(userId), userId, refreshExpiration, List.of(), false);
    }

    public String generateRefreshToken(String userKey, Long userId) {
        return buildToken(userKey, userId, refreshExpiration, List.of(), false);
    }

    public String generateNotificationToken(Long userId) {
        return buildToken(String.valueOf(userId), userId, notificationExpiration, List.of(), false);
    }

    public String generateNotificationToken(String userKey, Long userId) {
        return buildToken(userKey, userId, notificationExpiration, List.of(), false);
    }

    private String buildToken(
            String subject,
            Long userId,
            long expiration,
            Collection<String> roles,
            boolean includeIssuedAtMillis
    ) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key);

        if (userId != null) {
            builder.claim(USER_ID_CLAIM, userId);
        }
        if (roles != null && !roles.isEmpty()) {
            builder.claim(ROLES_CLAIM, roles);
        }
        if (includeIssuedAtMillis) {
            builder.claim(ISSUED_AT_MILLIS_CLAIM, now.getTime());
        }

        return builder.compact();
    }

    public Long getUserId(String token) {
        return extractUserId(getClaims(token));
    }

    public AuthenticatedUser getAuthenticatedUser(String token) {
        Claims claims = getClaims(token);
        return new AuthenticatedUser(extractUserId(claims), extractUserKey(claims));
    }

    public String getSubject(String token) {
        return getClaims(token).getSubject();
    }

    public List<String> getRoles(String token) {
        Object rolesClaim = getClaims(token).get(ROLES_CLAIM);
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public List<GrantedAuthority> getAuthorities(String token) {
        return getRoles(token).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public Long getUserIdAllowExpired(String token) {
        try {
            return extractUserId(getClaims(token));
        } catch (ExpiredJwtException e) {
            return extractUserId(e.getClaims());
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public String getSubjectAllowExpired(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public Date getExpirationAllowExpired(String token) {
        try {
            return getClaims(token).getExpiration();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getExpiration();
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public long getIssuedAtMillis(String token) {
        return extractIssuedAtMillis(getClaims(token));
    }

    public long getIssuedAtMillisAllowExpired(String token) {
        try {
            return extractIssuedAtMillis(getClaims(token));
        } catch (ExpiredJwtException e) {
            return extractIssuedAtMillis(e.getClaims());
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public boolean isExpired(String token) {
        try {
            getClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public void validate(String token) {
        try {
            getClaims(token);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Long extractUserId(Claims claims) {
        Object userIdClaim = claims.get(USER_ID_CLAIM);
        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }
        if (userIdClaim instanceof String value) {
            return Long.parseLong(value);
        }
        return Long.parseLong(claims.getSubject());
    }

    private String extractUserKey(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            return null;
        }
        if (subject.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return subject;
    }

    private long extractIssuedAtMillis(Claims claims) {
        Object issuedAtMillisClaim = claims.get(ISSUED_AT_MILLIS_CLAIM);
        if (issuedAtMillisClaim instanceof Number number) {
            return number.longValue();
        }
        if (issuedAtMillisClaim instanceof String value) {
            return Long.parseLong(value);
        }
        throw new CustomException(ErrorCode.INVALID_TOKEN);
    }
}
