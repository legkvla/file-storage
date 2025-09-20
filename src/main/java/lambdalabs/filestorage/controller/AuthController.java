package lambdalabs.filestorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lambdalabs.filestorage.dto.AuthRequest;
import lambdalabs.filestorage.dto.AuthResponse;
import lambdalabs.filestorage.dto.CreateUserRequest;
import lambdalabs.filestorage.model.User;
import lambdalabs.filestorage.repository.UserRepository;
import lambdalabs.filestorage.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    /**
     * Generate authentication token for user
     */
    @Operation(summary = "Generate JWT token", description = "Generate a JWT token for an existing user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token generated successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "User not found or inactive"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/token")
    public ResponseEntity<?> generateToken(@Valid @RequestBody AuthRequest authRequest) {
        logger.info("Token generation request for identity: {}", authRequest.getIdentity());

        try {
            // Find user by identity
            User user = userRepository.findByIdentity(authRequest.getIdentity())
                    .orElse(null);

            if (user == null) {
                logger.warn("User not found for identity: {}", authRequest.getIdentity());
                Map<String, String> error = new HashMap<>();
                error.put("error", "User not found");
                error.put("message", "No user found with the provided identity");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }

            // Generate JWT token
            String token = jwtService.generateToken(user.getId(), user.getIdentity());
            long expiresIn = jwtService.getJwtExpiration();

            AuthResponse response = new AuthResponse(token, expiresIn, user.getId(), user.getIdentity());
            
            logger.info("Token generated successfully for user: {} (ID: {})", 
                       user.getIdentity(), user.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating token for identity: {}", authRequest.getIdentity(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Token generation failed");
            error.put("message", "An error occurred while generating the token");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Create a new user
     */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest createUserRequest) {
        logger.info("User creation request for identity: {}", createUserRequest.getIdentity());

        try {
            // Check if user already exists
            if (userRepository.existsByIdentity(createUserRequest.getIdentity())) {
                logger.warn("User already exists with identity: {}", createUserRequest.getIdentity());
                Map<String, String> error = new HashMap<>();
                error.put("error", "User already exists");
                error.put("message", "A user with this identity already exists");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }

            // Create new user
            User user = new User();
            user.setIdentity(createUserRequest.getIdentity());

            User savedUser = userRepository.save(user);

            logger.info("User created successfully: {} (ID: {})", 
                       savedUser.getIdentity(), savedUser.getId());

            // Return user information
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedUser.getId());
            response.put("identity", savedUser.getIdentity());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            logger.error("Error creating user with identity: {}", createUserRequest.getIdentity(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User creation failed");
            error.put("message", "An error occurred while creating the user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get user information by identity (for testing purposes)
     */
    @GetMapping("/users/{identity}")
    public ResponseEntity<?> getUserByIdentity(@PathVariable String identity) {
        logger.info("User lookup request for identity: {}", identity);

        try {
            User user = userRepository.findByIdentity(identity).orElse(null);

            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            // Return user information
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("identity", user.getIdentity());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving user with identity: {}", identity, e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User retrieval failed");
            error.put("message", "An error occurred while retrieving the user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * List all users (for admin purposes)
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        logger.info("User list request");

        try {
            var users = userRepository.findAll();
            
            var response = users.stream().map(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("identity", user.getIdentity());
                return userInfo;
            }).toList();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrieving users list", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Users retrieval failed");
            error.put("message", "An error occurred while retrieving the users list");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
