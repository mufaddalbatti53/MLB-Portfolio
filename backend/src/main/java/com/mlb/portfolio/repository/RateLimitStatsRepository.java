package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.RateLimitCounter;
import java.time.Instant;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RateLimitStatsRepository extends MongoRepository<RateLimitCounter, String> {
    long countByBlockedUntilAfter(Instant now);
}

