package com.racerecall.backend.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionDto(
   @JsonProperty("session_key") int sessionKey,
   @JsonProperty("session_name") String sessionName,
   @JsonProperty("country_name") String countryName,
   @JsonProperty("date_start") String dateStart
) {}
