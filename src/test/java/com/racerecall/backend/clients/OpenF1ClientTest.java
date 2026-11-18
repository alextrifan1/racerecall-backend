package com.racerecall.backend.clients;

import com.racerecall.backend.models.SessionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OpenF1ClientTest {

    // Shape copied from a real OpenF1 response, including fields we don't map (year, circuit_key)
    private static final String SESSIONS_JSON = """
            [
              {
                "session_key": 9693, "session_name": "Race",
                "date_start": "2025-03-16T04:00:00+00:00", "date_end": "2025-03-16T06:00:00+00:00",
                "country_name": "Australia", "location": "Melbourne", "session_type": "Race",
                "circuit_short_name": "Melbourne", "meeting_key": 1254,
                "year": 2025, "circuit_key": 10
              }
            ]
            """;

    // Every request the client sends is recorded here
    private final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
    // Responses the fake server returns, in order (a Supplier, because a response can only be read once)
    private final Deque<Supplier<ClientResponse>> responses = new ConcurrentLinkedDeque<>();

    private OpenF1Client client;

    @BeforeEach
    void setUp() {
        ExchangeFunction fakeServer = request -> {
            requests.add(request);
            return Mono.just(responses.removeFirst().get());
        };
        client = new OpenF1Client(WebClient.builder().exchangeFunction(fakeServer));
    }

    private void respondWithJson(String body) {
        responses.add(() -> ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private void respondWithStatus(HttpStatus status) {
        responses.add(() -> ClientResponse.create(status).build());
    }

    @Test
    @DisplayName("fetchSessions sends GET /v1/sessions?year=... to OpenF1")
    void fetchSessions_callsSessionsEndpointWithYear() {
        respondWithJson("[]");

        client.fetchSessions(2025);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.GET);
        assertThat(requests.get(0).url()).hasToString("https://api.openf1.org/v1/sessions?year=2025");
    }

    @Test
    @DisplayName("fetchSessions maps snake_case JSON into SessionDto and ignores unknown fields")
    void fetchSessions_mapsJsonIntoDtos() {
        respondWithJson(SESSIONS_JSON);

        List<SessionDto> sessions = client.fetchSessions(2025);

        assertThat(sessions)
                .extracting(SessionDto::sessionKey, SessionDto::sessionName, SessionDto::meetingKey, SessionDto::countryName)
                .containsExactly(tuple(9693, "Race", 1254, "Australia"));
    }

    @Test
    @DisplayName("fetchSessions retries after a 429 and returns the data from the retry")
    void fetchSessions_tooManyRequestsThenOk_retries() {
        respondWithStatus(HttpStatus.TOO_MANY_REQUESTS);
        respondWithJson(SESSIONS_JSON);

        List<SessionDto> sessions = client.fetchSessions(2025);

        assertThat(sessions).hasSize(1);
        assertThat(requests).hasSize(2);   // original + 1 retry
    }

    @Test
    @DisplayName("fetchSessions does not retry a 500 and returns an empty list (error is swallowed)")
    void fetchSessions_serverError_returnsEmptyListWithoutRetry() {
        respondWithStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        List<SessionDto> sessions = client.fetchSessions(2025);

        assertThat(sessions).isEmpty();
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("fetchSessionByKey returns null when OpenF1 has no such session (root cause of the NPE in SessionService)")
    void fetchSessionByKey_noSuchSession_returnsNull() {
        respondWithJson("[]");

        assertThat(client.fetchSessionByKey(1)).isNull();
    }
}