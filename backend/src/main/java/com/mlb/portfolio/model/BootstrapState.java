package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("bootstrap_state")
public class BootstrapState {
    @Id private String id;
    private Instant completedAt;

    protected BootstrapState() { }

    public BootstrapState(String id, Instant completedAt) {
        this.id = id;
        this.completedAt = completedAt;
    }

    public String getId() { return id; }
    public Instant getCompletedAt() { return completedAt; }
}

