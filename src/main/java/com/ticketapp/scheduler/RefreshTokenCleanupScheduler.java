package com.ticketapp.scheduler;

import com.ticketapp.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepo;

    @Scheduled(fixedDelayString = "${refresh.token.cleanup.interval.ms:86400000}")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepo.deleteExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("RefreshTokenCleanupScheduler: deleted {} expired refresh token row(s)", deleted);
        }
    }
}
