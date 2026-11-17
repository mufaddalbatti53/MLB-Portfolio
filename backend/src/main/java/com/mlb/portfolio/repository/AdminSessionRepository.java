package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.AdminSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminSessionRepository extends MongoRepository<AdminSession, String> {
    Optional<AdminSession> findByTokenIdDigest(String tokenIdDigest);
    boolean existsByTokenIdDigestAndRevokedAtIsNullAndExpiresAtAfter(String tokenIdDigest, Instant now);
}

