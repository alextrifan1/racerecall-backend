package com.racerecall.backend.clients;

import com.racerecall.backend.models.SessionDto;
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
}
