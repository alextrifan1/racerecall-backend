package com.racerecall.backend.services;

import com.racerecall.backend.repositories.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CleanupService {
    private final UserRepository userRepository;

    public CleanupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Runs every day at 3:00 AM server time
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        userRepository.deleteByCreatedAtBefore(cutoff);
    }
}
