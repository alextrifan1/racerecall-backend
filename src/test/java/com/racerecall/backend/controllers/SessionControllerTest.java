package com.racerecall.backend.controllers;

import com.racerecall.backend.exceptions.SessionNotFoundException;
import com.racerecall.backend.models.DriverResultDto;
import com.racerecall.backend.models.SessionDetailsDto;
import com.racerecall.backend.models.SessionDto;
import com.racerecall.backend.models.WeatherDto;
import com.racerecall.backend.services.CustomUserDetailsService;
import com.racerecall.backend.services.JwtService;
import com.racerecall.backend.services.SecurityConfig;
import com.racerecall.backend.services.SessionService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public class SessionControllerTest {

    private static final int SESSION_KEY = 9999;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private static SessionDto session(int sessionKey) {
        return new SessionDto(sessionKey, "Race", "2025-03-16T04:00:00+00:00", "2025-03-16T06:00:00+00:00",
                "Australia", "Melbourne", "Race", "Melbourne", 1229);
    }

    @Nested
    @DisplayName("GET /api/sessions")
    class GetSessions {

        @Test
        @DisplayName("no query params uses the defaults year=2026, page=0, size=10")
        void noParams_usesDefaults() throws Exception {
            when(sessionService.getPaginatedSessions(2026, 0, 10)).thenReturn(List.of(session(1)));

            mockMvc.perform(get("/api/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].session_key").value(1));
        }

        @ParameterizedTest(name = "page={0}, size={1} -> 400")
        @CsvSource({
                "-1, 10",   // negative page
                "0, 0",     // empty page
                "0, -5",    // negative size
                "0, 101"     // too large
        })
        @DisplayName("invalid pagination returns 400 and never reaches the service")
        void invalidPagination_returns400(int page, int size) throws Exception {
            mockMvc.perform(get("/api/sessions")
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(sessionService);
        }

        @Test
        @DisplayName("the exact request sent by the frontend is accepted")
        void frontendRequest_isAccepted() throws Exception {
            mockMvc.perform(get("/api/sessions")
                            .param("year", "2026")
                            .param("page", "0")
                            .param("size", "100"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("query params are passed to the service")
        void withParams_passesThemToService() throws Exception {
            mockMvc.perform(get("/api/sessions")
                            .param("year", "2025")
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk());

            verify(sessionService).getPaginatedSessions(2025, 1, 5);
        }

        @Test
        @DisplayName("non-numeric year returns 400 and never reaches the service")
        void nonNumericYear_returns400() throws Exception {
            mockMvc.perform(get("/api/sessions").param("year", "abc"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(sessionService);
        }
    }

    @Nested
    @DisplayName("GET /api/sessions/{sessionKey}/details")
    class GetSessionDetails {

        @Test
        @DisplayName("returns session info, podium and weather in the JSON shape the frontend expects")
        void returnsDetailsJson() throws Exception {
            SessionDetailsDto details = new SessionDetailsDto(
                    session(SESSION_KEY),
                    List.of(new DriverResultDto(1, 1, "M VERSTAPPEN", "Red Bull Racing")),
                    List.of(),
                    new WeatherDto(24.5, 38.0, 0)
            );
            when(sessionService.getSessionDetails(SESSION_KEY)).thenReturn(details);

            mockMvc.perform(get("/api/sessions/{key}/details", SESSION_KEY))
                    .andExpect(status().isOk())
                    // outer record has no @JsonProperty -> camelCase
                    .andExpect(jsonPath("$.sessionInfo.session_key").value(SESSION_KEY))
                    // nested records use @JsonProperty -> snake_case
                    .andExpect(jsonPath("$.podium[0].broadcast_name").value("M VERSTAPPEN"))
                    .andExpect(jsonPath("$.podium[0].team_name").value("Red Bull Racing"))
                    .andExpect(jsonPath("$.weatherSnapshot.air_temperature").value(24.5));
        }

        @Test
        @DisplayName("unknown session key returns 404 with a problem detail")
        void unknownSession_returns404() throws Exception {
            when(sessionService.getSessionDetails(SESSION_KEY))
                    .thenThrow(new SessionNotFoundException(SESSION_KEY));

            mockMvc.perform(get("/api/sessions/{key}/details", SESSION_KEY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Session not found"))
                    .andExpect(jsonPath("$.detail").value("No session found with key " + SESSION_KEY));
        }
    }

    @Nested
    @DisplayName("security")
    class Security {

        @Test
        @DisplayName("malformed token on public endpoint is ignored and request is allowed")
        void malformedTone_onPublicEndpoint_isIgnored() throws Exception {
            when(jwtService.extractUsername("not-a-jwt")).thenThrow(new MalformedJwtException("Malformed JWT"));

            mockMvc.perform(get("/api/sessions")
                            .header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isOk());

            verify(sessionService).getPaginatedSessions(2026, 0, 10);
        }

        @Test
        @DisplayName("token for a deleted user is ignored and the request succeeds")
        void tokenForDeletedUser_onPublicEndpoint_isIgnored() throws Exception {
            when(jwtService.extractUsername("old-token")).thenReturn("deleted-user");
            when(userDetailsService.loadUserByUsername("deleted-user")).thenThrow(new UsernameNotFoundException("User not found"));

            mockMvc.perform(get("/api/sessions")
                            .header("Authorization", "Bearer old-token"))
                    .andExpect(status().isOk());

            verify(userDetailsService).loadUserByUsername("deleted-user");
        }

        @Test
        @DisplayName("malformed token on a protected route is rejected, it does NOT grant access")
        void malformedToken_onProtectedRoute_isRejected() throws Exception {
            when(jwtService.extractUsername("not-a-jwt")).thenThrow(new MalformedJwtException("Malformed JWT"));

            mockMvc.perform(get("/api/something-private")
                            .header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unlisted route without a token is rejected with 403 (an API would normally use 401)")
        void protectedRoute_noToken_returns403() throws Exception {
            mockMvc.perform(get("/api/something-private"))
                    .andExpect(status().isForbidden());
        }
    }
}
