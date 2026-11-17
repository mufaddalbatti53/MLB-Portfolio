package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("rate_limit_counters")
public class RateLimitCounter {
    public enum Scope { CONTACT, ADMIN_LOGIN_IP, ADMIN_LOGIN_ACCOUNT, FEEDBACK }

    @Id private String id;
    private Scope scope;
    private int requestCount;
    private Instant windowStartedAt;
    private Instant blockedUntil;
    @Indexed(expireAfter = "0s") private Instant expiresAt;

    public String getId() { return id; }
    public Scope getScope() { return scope; }
    public int getRequestCount() { return requestCount; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public Instant getExpiresAt() { return expiresAt; }
}

