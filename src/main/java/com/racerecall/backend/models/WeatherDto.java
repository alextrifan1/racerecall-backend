package com.racerecall.backend.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WeatherDto (
        @JsonProperty("air_temperature") Double airTemperature,
        @JsonProperty("track_temperature") Double trackTemperature,
        @JsonProperty("rainfall") Integer rainfall
) {}
