package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("site_feedback")
@CompoundIndex(name="feedback_recent", def="{'createdAt': -1, 'category': 1}")
public class SiteFeedback {
    public enum Category { UI_UX, BUG, FEATURE_IDEA, GENERAL }
    @Id private String id;
    private int rating;
    private Category category;
    private String comment;
    private Instant createdAt;
    @Indexed(expireAfter="0s") private Instant expiresAt;
    protected SiteFeedback() { }
    public SiteFeedback(int rating, Category category, String comment, Instant createdAt, Instant expiresAt) {
        this.rating=rating; this.category=category; this.comment=comment;
        this.createdAt=createdAt; this.expiresAt=expiresAt;
    }
    public String getId(){return id;}
    public int getRating(){return rating;}
    public Category getCategory(){return category;}
    public String getComment(){return comment;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
}
