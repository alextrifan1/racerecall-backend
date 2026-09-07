package com.racerecall.backend.controllers;

import com.racerecall.backend.models.SessionDetailsDto;
import com.racerecall.backend.models.SessionDto;
import com.racerecall.backend.services.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessions")
@CrossOrigin(origins = {"http://localhost:5173", "https://racerecall-frontend-7g4u5txv4-zero-parades.vercel.app"})
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionDto> getSession(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return sessionService.getPaginatedSessions(year, page, size);
    }

    @GetMapping("/{sessionKey}/details")
    public SessionDetailsDto getSessionDetails(@PathVariable("sessionKey") int sessionkey) {
        return sessionService.getSessionDetails(sessionkey);
    }

}
