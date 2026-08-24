package com.menusaas.auth.service;

import com.menusaas.auth.security.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Purga periodica de refresh tokens vencidos o revocados hace mas de 30 dias.
 * Mantiene la tabla pequena sin perder el rastro reciente de sesiones
 * revocadas (util para auditar intentos de reuso).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 7 3 * * *", zone = "UTC")
    @Transactional
    public void purgeStaleTokens() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = refreshTokenRepository.deleteStaleTokens(cutoff);
        if (deleted > 0) {
            log.info("Purga de refresh tokens: {} filas eliminadas", deleted);
        }
    }
}
