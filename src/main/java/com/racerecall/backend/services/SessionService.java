package com.racerecall.backend.services;

import com.racerecall.backend.clients.OpenF1Client;
import com.racerecall.backend.models.SessionDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionService {

    private final OpenF1Client openF1Client;

    public SessionService(OpenF1Client openF1Client) {
        this.openF1Client = openF1Client;
    }

    public List<SessionDto> getPaginatedSessions(int year, int page, int size) {
        // This cross-class call triggers the @Cacheable proxy
        List<SessionDto> allSessions = openF1Client.fetchSessions(year);

        if (allSessions.isEmpty()) {
            return allSessions;
        }

        int start = Math.min(page * size, allSessions.size());
        int end = Math.min((page + 1) * size, allSessions.size());

        return allSessions.subList(start, end);
    }
}
