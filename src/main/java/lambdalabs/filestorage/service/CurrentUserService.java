package lambdalabs.filestorage.service;

import lambdalabs.filestorage.model.User;
import lambdalabs.filestorage.repository.UserRepository;
import lambdalabs.filestorage.util.JwtUtil;
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

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get the current authenticated user
     */
    public Optional<User> getCurrentUser() {
        try {
            String token = getCurrentToken();
            if (token == null) {
                return Optional.empty();
            }

            String userId = jwtUtil.extractUserId(token);
            return userRepository.findById(userId);

        } catch (Exception e) {
            logger.warn("Failed to get current user: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the current user's identity
     */
    public Optional<String> getCurrentUserIdentity() {
        try {
            String token = getCurrentToken();
            if (token == null) {
                return Optional.empty();
            }

            return Optional.of(jwtUtil.extractIdentity(token));

        } catch (Exception e) {
            logger.warn("Failed to get current user identity: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the current user's ID
     */
    public Optional<String> getCurrentUserId() {
        try {
            String token = getCurrentToken();
            if (token == null) {
                return Optional.empty();
            }

            return Optional.of(jwtUtil.extractUserId(token));

        } catch (Exception e) {
            logger.warn("Failed to get current user ID: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extract JWT token from current request
     */
    private String getCurrentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String) {
            return (String) authentication.getCredentials();
        }
        return null;
    }
}
