package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("admin_sessions")
public class AdminSession {
    @Id private String id;
    @Indexed(unique = true) private String tokenIdDigest;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
    @Indexed(expireAfter = "0s") private Instant purgeAfter;

    protected AdminSession() { }

    public AdminSession(String tokenIdDigest, Instant createdAt, Instant expiresAt) {
        this.tokenIdDigest = tokenIdDigest;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.purgeAfter = expiresAt;
    }

    public String getId() { return id; }
    public String getTokenIdDigest() { return tokenIdDigest; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getPurgeAfter() { return purgeAfter; }
    public void setRevokedAt(Instant value) { revokedAt = value; }
}

