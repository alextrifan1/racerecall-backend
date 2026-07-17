package com.racerecall.backend.controllers;

import com.racerecall.backend.models.SessionDto;
import com.racerecall.backend.services.OpenF1Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "http://localhost:5173") // for the frontend
public class SessionController {

    private final OpenF1Service openF1Service;

    public SessionController(OpenF1Service openF1Service) {
        this.openF1Service = openF1Service;
    }

    @GetMapping
    public List<SessionDto> getSession(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return openF1Service.getPaginatedSession(year, page, size);
    }
}
