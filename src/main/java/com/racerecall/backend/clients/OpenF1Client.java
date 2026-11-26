package com.racerecall.backend.clients;

import com.racerecall.backend.exceptions.OpenF1UnavailableException;
import com.racerecall.backend.models.DriverDto;
import com.racerecall.backend.models.DriverResultDto;
import com.racerecall.backend.models.SessionDto;
import com.racerecall.backend.models.WeatherDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import java.time.Duration;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class OpenF1Client {

    private final WebClient webClient;

    public OpenF1Client(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.openf1.org/v1").build();
    }

    /**
     * GET request to OpenF1 with the shared error policy:
     * - a 429 is retried up to 3 times, 1 second apart
     * - any other failure (or retries running out) becomes OpenF1UnavailableException,
     *   so the caller knows the data could not be fetched, and @Cacheable stores nothing
     */
    private <T> Flux<T> get(String uriTemplate, Class<T> type, Object ... uriVaiables) {
        return webClient.get()
                .uri(uriTemplate, uriVaiables)
                .retrieve()
                .bodyToFlux(type)
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1))
                    .filter(OpenF1Client::isTooManyRequests))
                .onErrorMap(e -> new OpenF1UnavailableException("OpenF1 request failed: " + uriTemplate, e));
    }

    private static boolean isTooManyRequests(Throwable e) {
        return e instanceof WebClientResponseException wce && wce.getStatusCode().value() == 429;
    }

    @Cacheable("sessionsByYear")
    public List<SessionDto> fetchSessions(int year) {
        System.out.println("Fetching data from OpenF1 for year: " + year);
        return get("/sessions?year={year}", SessionDto.class, year).collectList().block();
    }


    @Cacheable("resultsBySession")
    public List<DriverResultDto> fetchSessionResults(int sessionKey) {
        System.out.println("Fetching race results for session: " + sessionKey);
        return get("/session_result?session_key={key}", DriverResultDto.class, sessionKey).collectList().block();
    }

    @Cacheable("gridBySession")
    public List<DriverResultDto> fetchStartingGrid(int sessionKey) {
        System.out.println("Fetching starting grid for session: " + sessionKey);
        return get("/starting_grid?session_key={key}", DriverResultDto.class, sessionKey).collectList().block();
    }

    @Cacheable("driversByMeeting")
    public List fetchDriversByMeeting(int meetingKey) {
        System.out.println("Fetching drivers for meeting: " + meetingKey);
        return get("/drivers?meeting_key={key}", DriverDto.class, meetingKey).collectList().block();
    }

    @Cacheable("weatherBySession")
    public List<WeatherDto> fetchWeather(int sessionKey) {
        System.out.println("Fetching weather data for session: " + sessionKey);
        return get("/weather?session_key={key}", WeatherDto.class, sessionKey).collectList().block();
    }

    @Cacheable("sessionByKey")
    public SessionDto fetchSessionByKey(int sessionKey) {
        System.out.println("Fetching single session info for: " + sessionKey);
        return get("/sessions?session_key={key}", SessionDto.class, sessionKey).blockFirst();
    }

    @Cacheable("driversBySession")
    public List<DriverDto> fetchDrivers(int sessionKey) {
        System.out.println("Fetching drivers for session: " + sessionKey);
        return get("/drivers?session_key={key}", DriverDto.class, sessionKey).collectList().block();
    }
}
