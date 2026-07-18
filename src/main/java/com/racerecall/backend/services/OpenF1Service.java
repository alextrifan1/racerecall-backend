package com.racerecall.backend.services;

import com.racerecall.backend.models.SessionDto;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class OpenF1Service {

    private final WebClient webClient;

    public OpenF1Service(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.openf1.org/v1").build();
    }

    public List<SessionDto> getSessionsByYear(int year) {
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

    public List<SessionDto> getPaginatedSession(int year, int page, int size) {
        List<SessionDto> allSessions = getSessionsByYear(year);

        if (allSessions.isEmpty()) {
            return allSessions;
        }

        int start = Math.min(page * size, allSessions.size());
        int end = Math.min((page + 1) * size, allSessions.size());

        return allSessions.subList(start, end);
    }
}
