package com.racerecall.backend.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionDto(
        @JsonProperty("session_key") Integer sessionKey,
        @JsonProperty("session_name") String sessionName,
        @JsonProperty("date_start") String dateStart,
        @JsonProperty("date_end") String dateEnd,
        @JsonProperty("country_name") String countryName,
        @JsonProperty("location") String location,
        @JsonProperty("session_type") String sessionType,
        @JsonProperty("circuit_short_name") String circuitShortName
) {}
