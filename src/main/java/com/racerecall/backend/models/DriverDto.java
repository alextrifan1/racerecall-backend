package com.racerecall.backend.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DriverDto(
        @JsonProperty("driver_number") Integer driverNumber,
        @JsonProperty("broadcast_name") String broadcastName,
        @JsonProperty("team_name") String teamName
) {}