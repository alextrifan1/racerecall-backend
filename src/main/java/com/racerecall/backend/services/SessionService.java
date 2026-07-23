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
        List<SessionDto> allSessions = openF1Client.fetchSessions(year);

        if (allSessions.isEmpty()) {
            return allSessions;
        }

        List<Integer> uniqueMeetings = allSessions.stream()
                .map(SessionDto::meetingKey)
                .distinct()
                .toList();

        int startMeeting = Math.min(page * size, uniqueMeetings.size());
        int endMeeting = Math.min((page + 1) * size, uniqueMeetings.size());

        if (startMeeting >= uniqueMeetings.size()) {
            return List.of();
        }

        List<Integer> paginatedMeetingKeys = uniqueMeetings.subList(startMeeting, endMeeting);

        return allSessions.stream()
                .filter(session -> paginatedMeetingKeys.contains(session.meetingKey()))
                .toList();
    }

    public SessionDetailsDto getSessionDetails(int sessionKey) {
        SessionDto sessionInfo = openF1Client.fetchSessionByKey(sessionKey);
        List<DriverResultDto> allResults = openF1Client.fetchSessionResults(sessionKey);
        List<DriverResultDto> startingGrid = openF1Client.fetchStartingGrid(sessionKey);
        List<WeatherDto> weatherList = openF1Client.fetchWeather(sessionKey);

        //driver
        List<DriverDto> drivers = openF1Client.fetchDriversByMeeting(sessionInfo.meetingKey());
        System.out.println("Total drivers fetched: " + drivers.size());

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
