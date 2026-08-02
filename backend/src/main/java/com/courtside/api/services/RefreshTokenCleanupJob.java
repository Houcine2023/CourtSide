package com.courtside.api.services;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.repositories.RefreshTokenRepository;

/**
 * Housekeeping: refresh tokens are never deleted at runtime (revoked rows are what
 * make reuse detection possible), so the table would grow forever.
 *
 * This job deletes rows that expired more than 30 days ago — long past any usefulness
 * as a theft signal.
 *
 * @Scheduled needs @EnableScheduling on the application class, otherwise this method
 * silently never runs. (Classic trap: the annotation compiles and does nothing.)
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** cron = second minute hour day month weekday -> every day at 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteLongExpiredTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
        long deleted = refreshTokenRepository.deleteByExpiresAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Refresh-token cleanup: {} row(s) deleted (expired before {})", deleted, cutoff);
        }
    }
}
