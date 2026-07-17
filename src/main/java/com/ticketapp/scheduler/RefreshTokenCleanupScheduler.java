package com.ticketapp.scheduler;

import com.ticketapp.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * RefreshTokenCleanupScheduler
 *
 * Deletes refresh_tokens rows once their expires_at has passed — expired
 * rows carry no security value (they can never be used to rotate or be
 * replayed, since JwtUtil.parseRefreshToken already rejects an expired
 * JWT before the DB row is even looked up), so keeping them around
 * indefinitely would just grow the table forever with dead data.
 *
 * Runs once every 24 hours by default. Revoked-but-not-yet-expired rows
 * are intentionally left alone — they're kept until real expiry as an
 * audit trail (e.g. for investigating a reuse-detection event).
 */
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
