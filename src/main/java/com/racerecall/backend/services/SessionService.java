package com.racerecall.backend.services;

import com.racerecall.backend.clients.OpenF1Client;
import com.racerecall.backend.models.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public SessionDetailsDto getSessionDetails(int sessionKey) {
        SessionDto sessionInfo = openF1Client.fetchSessionByKey(sessionKey);
        List<DriverResultDto> allResults = openF1Client.fetchSessionResults(sessionKey);
        List<DriverResultDto> startingGrid = openF1Client.fetchStartingGrid(sessionKey);
        List<WeatherDto> weatherList = openF1Client.fetchWeather(sessionKey);

        //driver
        List<DriverDto> drivers = openF1Client.fetchDrivers(sessionKey);
        Map<Integer, DriverDto> driverMap = drivers.stream()    //just in case the API accidentally returns duplicates
                .collect(Collectors.toMap(DriverDto::driverNumber, d -> d, (existing, replacement) -> existing));

        List<DriverResultDto> enrichedPodium = allResults.stream()
                .filter(driver -> driver.position() != null && driver.position() <= 3)
                .map(driver -> {
                    DriverDto info = driverMap.get(driver.driverNumber());
                    return new DriverResultDto(
                            driver.position(),
                            driver.driverNumber(),
                            info != null ? info.broadcastName() : "Unknown Driver",
                            info != null ? info.teamName() : "Unknown Team"
                    );
                })
                .toList();

        List<DriverResultDto> enrichedGrid = startingGrid.stream()
                .map(driver -> {
                    DriverDto info = driverMap.get(driver.driverNumber());
                    return new DriverResultDto(
                            driver.position(),
                            driver.driverNumber(),
                            info != null ? info.broadcastName() : "Unknown Driver",
                            info != null ? info.teamName() : "Unknown Team"
                    );
                })
                .toList();

        WeatherDto weatherSnapshot = weatherList.isEmpty() ? null : weatherList.get(0);

        return new SessionDetailsDto(
                sessionInfo,
                enrichedPodium,
                enrichedGrid,
                weatherSnapshot
        );
    }
}
