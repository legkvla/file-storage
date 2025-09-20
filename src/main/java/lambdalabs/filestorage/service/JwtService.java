package lambdalabs.filestorage.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret:mySecretKey123456789012345678901234567890}")
    private String secretKey;

    @Value("${jwt.expiration:3600000}") // 1 hour in milliseconds
    private long jwtExpiration;

    /**
     * Generate JWT token for user
     */
    public String generateToken(String userId, String userIdentity) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userIdentity", userIdentity);
        return createToken(claims, userId);
    }

    /**
     * Create JWT token with claims
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract user ID from token
     */
    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    /**
     * Extract user identity from token
     */
    public String extractUserIdentity(String token) {
        return extractClaim(token, claims -> claims.get("userIdentity", String.class));
    }

    /**
     * Extract expiration date from token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract specific claim from token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if token is expired
     */
    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validate token
     */
    public Boolean validateToken(String token, String userId) {
        try {
            final String extractedUserId = extractUserId(token);
            return (extractedUserId.equals(userId) && !isTokenExpired(token));
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get signing key
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Get token expiration time in milliseconds
     */
    public long getJwtExpiration() {
        return jwtExpiration;
    }

    /**
     * Get JWT secret key
     */
    public String getJwtSecret() {
        return secretKey;
    }

    /**
     * Check if token is valid (not expired and properly formatted)
     */
    public Boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
}
