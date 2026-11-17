package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.ContactMessage;
import java.time.Instant;
import java.util.Optional;

public interface ContactQueueRepository {
    Optional<ContactMessage> claimNext(Instant now);
}

