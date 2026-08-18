package com.menusaas.auth.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now or t.revoked = true")
    int deleteExpiredOrRevoked(Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId")
    void deleteByUserId(Long userId);
}