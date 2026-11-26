package com.racerecall.backend.clients;

import com.racerecall.backend.exceptions.OpenF1UnavailableException;
import com.racerecall.backend.models.SessionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringJUnitConfig(OpenF1ClientCachingTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh cache for every test
class OpenF1ClientCachingTest {

    private static final String ONE_SESSION_JSON = """
            [{"session_key": 9693, "session_name": "Race", "meeting_key": 1254}]
            """;

    /** Fake OpenF1: counts calls and returns whatever status/body the test sets. */
    static class FakeOpenF1 implements ExchangeFunction {
        final AtomicInteger calls = new AtomicInteger();
        volatile HttpStatus status = HttpStatus.OK;
        volatile String body = "[]";

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        }
    }

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        FakeOpenF1 fakeOpenF1() {
            return new FakeOpenF1();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("sessionsByYear");
        }

        @Bean
        OpenF1Client openF1Client(FakeOpenF1 fake) {
            return new OpenF1Client(WebClient.builder().exchangeFunction(fake));
        }
    }

    @Autowired
    private OpenF1Client client;   // this is the caching PROXY, not the raw object

    @Autowired
    private FakeOpenF1 fakeOpenF1;

    @Test
    @DisplayName("a successful response is cached: the second call does not hit OpenF1")
    void successfulResponse_isCached() {
        fakeOpenF1.body = ONE_SESSION_JSON;

        client.fetchSessions(2025);
        List<SessionDto> second = client.fetchSessions(2025);

        assertThat(second).hasSize(1);
        assertThat(fakeOpenF1.calls).hasValue(1);
    }

    @Test
    @DisplayName("a failed response is NOT cached: after OpenF1 recovers, the data is fetched again")
    void failedResponse_isNotCached() {
        // OpenF1 is down
        fakeOpenF1.status = HttpStatus.INTERNAL_SERVER_ERROR;
        assertThatThrownBy(() -> client.fetchSessions(2025))
                .isInstanceOf(OpenF1UnavailableException.class);

        // OpenF1 recovers
        fakeOpenF1.status = HttpStatus.OK;
        fakeOpenF1.body = ONE_SESSION_JSON;
        List<SessionDto> afterRecovery = client.fetchSessions(2025);

        // the app asks again and gets the real data
        assertThat(afterRecovery).hasSize(1);
        assertThat(fakeOpenF1.calls).hasValue(2);
    }
}