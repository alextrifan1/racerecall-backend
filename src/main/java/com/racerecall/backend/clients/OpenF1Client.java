package com.racerecall.backend.clients;

import com.racerecall.backend.models.DriverDto;
import com.racerecall.backend.models.DriverResultDto;
import com.racerecall.backend.models.SessionDto;
import com.racerecall.backend.models.WeatherDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class OpenF1Client {

    private final WebClient webClient;

    public OpenF1Client(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.openf1.org/v1").build();
    }

    @Cacheable("sessionsByYear")
    public List<SessionDto> fetchSessions(int year) {
        System.out.println("Fetching data from OpenF1 for year: " + year);

        return webClient.get()
                .uri("/sessions?year={year}", year)
                .retrieve()
                .bodyToFlux(SessionDto.class)
                .onErrorResume(WebClientResponseException.Unauthorized.class, e -> {
                    System.err.println("OpenF1 API locked (Live Session Ongoing) - Returning empty list.");
                    return Flux.empty();
                })
                .collectList()
                .block();
    }

    @Cacheable("resultsBySession")
    public List<DriverResultDto> fetchSessionResults(int sessionKey) {
        System.out.println("Fetching race results for session: " + sessionKey);

        return webClient.get()
                .uri("/session_result?session_key={key}", sessionKey)
                .retrieve()
                .bodyToFlux(DriverResultDto.class)
                .onErrorResume(WebClientResponseException.class, e -> Flux.empty())
                .collectList()
                .block();
    }

    @Cacheable("gridBySession")
    public List<DriverResultDto> fetchStartingGrid(int sessionKey) {
        System.out.println("Fetching starting grid for session: " + sessionKey);

        return webClient.get()
                .uri("/starting_grid?session_key={key}", sessionKey)
                .retrieve()
                .bodyToFlux(DriverResultDto.class)
                .onErrorResume(WebClientResponseException.class, e -> Flux.empty())
                .collectList()
                .block();
    }

    @Cacheable("weatherBySession")
    public List<WeatherDto> fetchWeather(int sessionKey) {
        System.out.println("Fetching weather data for session: " + sessionKey);

        return webClient.get()
                .uri("/weather?session_key={key}", sessionKey)
                .retrieve()
                .bodyToFlux(WeatherDto.class)
                .onErrorResume(WebClientResponseException.class, e -> Flux.empty())
                .collectList()
                .block();
    }

    @Cacheable("sessionByKey")
    public SessionDto fetchSessionByKey(int sessionKey) {
        System.out.println("Fetching single session info for: " + sessionKey);

        return webClient.get()
                .uri("/sessions?session_key={key}", sessionKey)
                .retrieve()
                .bodyToFlux(SessionDto.class)
                .onErrorResume(e -> Flux.empty())
                .blockFirst();
    }

    @Cacheable("driversBySession")
    public List<DriverDto> fetchDrivers(int sessionKey) {
        System.out.println("Fetching drivers for session: " + sessionKey);

        return webClient.get()
                .uri("/drivers?session_key={key}", sessionKey)
                .retrieve()
                .bodyToFlux(DriverDto.class)
                .onErrorResume(e -> Flux.empty())
                .collectList()
                .block();
    }
}
