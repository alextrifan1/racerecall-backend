package com.racerecall.backend.services;

import com.racerecall.backend.entities.User;
import com.racerecall.backend.exceptions.UsernameAlreadyExistsException;
import com.racerecall.backend.models.AuthRequest;
import com.racerecall.backend.models.AuthResponse;
import com.racerecall.backend.repositories.UserRepository;
import com.racerecall.backend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    private static final String USERNAME = "testuser";
    private static final String RAW_PASSWORD = "testpassword";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private final AuthRequest request = new AuthRequest(USERNAME, RAW_PASSWORD);

    @Nested
    @DisplayName("register")
    class Register {
        @Test
        @DisplayName("new user is saved with the encoded password and gets a token")
        void register_newUser_savesEncodedPasswordAndReturnsToken() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("hashed-password");
            when(jwtService.generateToken(any())).thenReturn("jwt-token");

            AuthResponse response = authService.register(request);

            assertThat(response.getToken()).isEqualTo("jwt-token");

            ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUser.capture());
            assertThat(savedUser.getValue().getUsername()).isEqualTo(USERNAME);
            assertThat(savedUser.getValue().getPassword())
                    .isEqualTo("hashed-password")
                    .isNotEqualTo(RAW_PASSWORD);
        }

        @Test
        @DisplayName("taken username throws and nothing is saved")
        void register_existingUsername_throwsAndDoesNotSave() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(new User(USERNAME, "someone-elses-hash")));

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(UsernameAlreadyExistsException.class)
                    .hasMessageContaining(USERNAME);

            verify(userRepository, never()).save(any());
            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("valid credentials are checked by the AuthenticationManager and return a token")
        void login_validCredentials_returnsToken() {
            // authenticationManager.authenticate is not stubbed: it returns null,
            // and AuthService ignores the return value, so "no exception" = success.
            when(jwtService.generateToken(USERNAME)).thenReturn("jwt-token");

            AuthResponse response = authService.login(request);

            assertThat(response.getToken()).isEqualTo("jwt-token");

            ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
            verify(authenticationManager).authenticate(attempt.capture());
            assertThat(attempt.getValue().getName()).isEqualTo(USERNAME);
            assertThat(attempt.getValue().getCredentials()).isEqualTo(RAW_PASSWORD);
        }

        @Test
        @DisplayName("bad credentials throw and no token is generated")
        void login_badCredentials_throwsAndGeneratesNoToken() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);

            verifyNoInteractions(jwtService);
        }
    }
}
