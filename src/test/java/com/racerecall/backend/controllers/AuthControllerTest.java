package com.racerecall.backend.controllers;


import com.racerecall.backend.exceptions.UsernameAlreadyExistsException;
import com.racerecall.backend.models.AuthResponse;
import com.racerecall.backend.services.AuthService;
import com.racerecall.backend.services.CustomUserDetailsService;
import com.racerecall.backend.services.JwtService;
import com.racerecall.backend.services.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class AuthControllerTest {

    private static final String CREDENTIALS_JSON = """
            {
                "username": "testuser",
                "password": "testpassword"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @ParameterizedTest(name = "{0}")
    @
            CsvSource(delimiter = '|', value = {
            "blank username | ''   | secret123 | username",
            "short username | ab   | secret123 | username",
            "blank password | alex | ''        | password",
            "short password | alex | short     | password"
    })
    @DisplayName("POST /api/auth/register with invalid input returns 400 with the failing field")
    void register_invalidInput_returns400(String caseName, String username, String password, String field) throws Exception {
        String json = """
            {"username": "%s", "password": "%s"}
            """.formatted(username, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors." + field).exists());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/login is not validated: a short password still reaches the service")
    void login_shortPassword_isNotValidated() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username": "alex", "password": "short"}
                            """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 and the token, without needing a JWT")
    void register_success_returns201WithToken() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("jwt-token"));

        mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CREDENTIALS_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/register with a taken username returns 409 with a problem detail")
    void register_usernameTaken_returns409() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("alex"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Username already exists"))
                .andExpect(jsonPath("$.detail").value("Username 'alex' already exists."));
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 and the token")
    void login_success_returns200WithToken() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/login with bad credentials returns 401 and a generic message")
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid username or password"));
    }

}
