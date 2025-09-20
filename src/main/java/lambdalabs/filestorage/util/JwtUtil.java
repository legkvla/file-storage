package lambdalabs.filestorage.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lambdalabs.filestorage.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtUtil {

    @Autowired
    private JwtService jwtService;

    /**
     * Extract user identity from JWT token
     */
    public String extractIdentity(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userIdentity", String.class);
    }

    /**
     * Extract user ID from JWT token
     */
    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", String.class);
    }

    /**
     * Extract all claims from JWT token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get signing key for JWT validation
     */
    private SecretKey getSigningKey() {
        String secret = jwtService.getJwtSecret();
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
