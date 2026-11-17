package com.mlb.portfolio.repository;

import java.time.Instant;

public interface RateLimitCounterRepository {
    int increment(String bucketId, Instant windowStart, Instant expiresAt);
    default void markBlocked(String bucketId, Instant blockedUntil) { }
}

