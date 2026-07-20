package com.racerecall.backend.models;

import java.util.List;

public record SessionDetailsDto (
        SessionDto sessionInfo,
        List<DriverResultDto> podium,
        List<DriverResultDto> startingGrid,
        WeatherDto weatherSnapshot
) {}
