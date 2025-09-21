package lambdalabs.filestorage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserService.class);

    @Autowired
    private JwtService jwtService;

    public Optional<String> getCurrentUserIdentity() {
        try {
            String token = getCurrentToken();
            if (token == null) {
                return Optional.empty();
            }

            return Optional.of(jwtService.extractUserIdentity(token));

        } catch (Exception e) {
            logger.warn("Failed to get current user identity: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> getCurrentUserId() {
        try {
            String token = getCurrentToken();
            if (token == null) {
                return Optional.empty();
            }

            return Optional.of(jwtService.extractUserId(token));

        } catch (Exception e) {
            logger.warn("Failed to get current user ID: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String getCurrentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String) {
            return (String) authentication.getCredentials();
        }
        return null;
    }
}
