package lambdalabs.filestorage;

import lambdalabs.filestorage.config.TestSecurityConfig;
import lambdalabs.filestorage.controller.AuthController;
import lambdalabs.filestorage.model.User;
import lambdalabs.filestorage.repository.UserRepository;
import lambdalabs.filestorage.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @Test
    public void testGenerateTokenSuccess() throws Exception {
        // Mock user
        User mockUser = new User();
        mockUser.setId("user-id");
        mockUser.setIdentity("test-user");

        when(userRepository.findByIdentity("test-user")).thenReturn(Optional.of(mockUser));
        when(jwtService.generateToken("user-id", "test-user")).thenReturn("mock-jwt-token");
        when(jwtService.getJwtExpiration()).thenReturn(3600000L);

        mockMvc.perform(post("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\": \"test-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600000))
                .andExpect(jsonPath("$.userId").value("user-id"))
                .andExpect(jsonPath("$.userIdentity").value("test-user"));
    }

    @Test
    public void testGenerateTokenUserNotFound() throws Exception {
        when(userRepository.findByIdentity("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\": \"nonexistent\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    public void testCreateUserSuccess() throws Exception {
        User mockUser = new User();
        mockUser.setId("new-user-id");
        mockUser.setIdentity("new-user");

        when(userRepository.existsByIdentity("new-user")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        mockMvc.perform(post("/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\": \"new-user\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-user-id"))
                .andExpect(jsonPath("$.identity").value("new-user"));
    }

    @Test
    public void testCreateUserAlreadyExists() throws Exception {
        when(userRepository.existsByIdentity("existing-user")).thenReturn(true);

        mockMvc.perform(post("/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\": \"existing-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User already exists"));
    }

    @Test
    public void testGetUserByIdentity() throws Exception {
        User mockUser = new User();
        mockUser.setId("user-id");
        mockUser.setIdentity("test-user");

        when(userRepository.findByIdentity("test-user")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(get("/auth/users/test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-id"))
                .andExpect(jsonPath("$.identity").value("test-user"));
    }

    @Test
    public void testGetUserByIdentityNotFound() throws Exception {
        when(userRepository.findByIdentity("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/users/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
