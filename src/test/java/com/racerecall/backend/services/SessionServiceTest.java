package com.racerecall.backend.services;

import com.racerecall.backend.clients.OpenF1Client;
import com.racerecall.backend.exceptions.SessionNotFoundException;
import com.racerecall.backend.models.DriverDto;
import com.racerecall.backend.models.DriverResultDto;
import com.racerecall.backend.models.SessionDetailsDto;
import com.racerecall.backend.models.SessionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SessionServiceTest {

    private final static int YEAR = 2025;
    private final static int SESSION_KEY = 9999;
    private final static int MEETING_KEY = 1229;

    @Mock
    private OpenF1Client openF1Client;

    @InjectMocks
    private SessionService sessionService;

    private static SessionDto session(int sessionKey, int meetingKey) {
        return new SessionDto(sessionKey, "Race", "2025-03-16T04:00:00+00:00", "null", "Australia", "Melbourne", "Race", "Melbourne", meetingKey);
    }

    private static DriverResultDto result(Integer position, int driverNumber) {
        return new DriverResultDto(position, driverNumber, null, null);
    }

    @Nested
    @DisplayName("getPaginationSessions")
    class GetPaginatedSessions  {
        // 3 race weekends (meetings 100, 200, 300), 2 sessions each
        private final List<SessionDto> season = List.of(
                session(1, 100), session(2, 100),
                session(3, 200), session(4, 200),
                session(5, 300), session(6, 300)
        );

        @Test
        @DisplayName("first page return every session of the first N weekends")
        void firstPage_returnsFirstNWeekends() {
            when(openF1Client.fetchSessions(YEAR)).thenReturn(season);

            List<SessionDto> page = sessionService.getPaginatedSessions(YEAR, 0, 2);

            assertThat(page)
                    .extracting(SessionDto::sessionKey)
                    .containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("second page retruns the remaining weekend")
        void secondPage_returnsRemainingWeekend() {
            when(openF1Client.fetchSessions(YEAR)).thenReturn(season);

            List<SessionDto> page = sessionService.getPaginatedSessions(YEAR, 1, 2);

            assertThat(page)
                    .extracting(SessionDto::sessionKey)
                    .containsExactly(5, 6);
        }

        @Test
        @DisplayName("page past the returns an empty list")
        void pageBeyondEnd_retrunsEmptyList() {
            when(openF1Client.fetchSessions(YEAR)).thenReturn(season);

            List<SessionDto> page = sessionService.getPaginatedSessions(YEAR, 5, 2);

            assertThat(page).isEmpty();
        }

        @Test
        @DisplayName("no sessions returns an empty list")
        void noSessions_returnsEmptyList() {
            when(openF1Client.fetchSessions(YEAR)).thenReturn(List.of());

            assertThat(sessionService.getPaginatedSessions(YEAR, 0, 10)).isEmpty();
        }

        @Test
        @DisplayName("negative page throws (BUG: should be rejected with 400 instead)")
        void negativePage_throws() {
            when(openF1Client.fetchSessions(YEAR)).thenReturn(season);

            assertThatThrownBy(() -> sessionService.getPaginatedSessions(YEAR, -1, 2))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("getSessionDetails")
    class GetSessionDetails {
        @Test
        @DisplayName("podium contains only the top 3 finishers, enriched with driver name and team")
        void podium_containsTopThreeWithDriverInfo() {
            when(openF1Client.fetchSessionByKey(SESSION_KEY)).thenReturn(session(SESSION_KEY, MEETING_KEY));
            when(openF1Client.fetchSessionResults(SESSION_KEY)).thenReturn(List.of(
                    result(1, 1),
                    result(2, 4),
                    result(3, 16),
                    result(4, 81),
                    result(null, 44) //dnf
            ));
            when(openF1Client.fetchDriversByMeeting(MEETING_KEY)).thenReturn(List.of(
                    new DriverDto(1, "M VERSTAPPEN", "Red Bull Racing"),
                    new DriverDto(4, "L NORRIS", "McLaren"),
                    new DriverDto(16, "C LECLERC", "Ferrari")
            ));

            SessionDetailsDto details = sessionService.getSessionDetails(SESSION_KEY);

            assertThat(details.podium())
                    .extracting(DriverResultDto::position, DriverResultDto::broadcastName, DriverResultDto::teamName)
                    .containsExactly(
                            tuple(1, "M VERSTAPPEN", "Red Bull Racing"),
                            tuple(2, "L NORRIS", "McLaren"),
                            tuple(3, "C LECLERC", "Ferrari")
                    );
        }

        @Test
        @DisplayName("dirver missing from the drivers list is shown as Unknown")
        void podium_driverNotFound_usesUnknownPlaceholders() {
            when(openF1Client.fetchSessionByKey(SESSION_KEY)).thenReturn(session(SESSION_KEY, MEETING_KEY));
            when(openF1Client.fetchSessionResults(SESSION_KEY)).thenReturn(List.of(result(1, 99)));

            SessionDetailsDto details = sessionService.getSessionDetails(SESSION_KEY);

            assertThat(details.podium())
                    .extracting(DriverResultDto::broadcastName, DriverResultDto::teamName)
                    .containsExactly(tuple("Unknown Driver", "Unknown Team"));
        }

        @Test
        @DisplayName("no weather data gives null snapshot")
        void weather_noData_isNull() {
            when(openF1Client.fetchSessionByKey(SESSION_KEY)).thenReturn(session(SESSION_KEY, MEETING_KEY));

            SessionDetailsDto details = sessionService.getSessionDetails(SESSION_KEY);

            assertThat(details.weatherSnapshot()).isNull();
        }

        @Test
        @DisplayName("unknown session key throws SessionNotFoundException and fetches nothing else")
        void unknownSessionKey_throwsSessionNotFound() {
            // fetchSessionByKey is NOT stubbed -> the mock returns null, like the real client does

            assertThatThrownBy(() -> sessionService.getSessionDetails(SESSION_KEY))
                    .isInstanceOf(SessionNotFoundException.class)
                    .hasMessageContaining(String.valueOf(SESSION_KEY));

            verify(openF1Client, never()).fetchSessionResults(anyInt());
        }
    }
}
