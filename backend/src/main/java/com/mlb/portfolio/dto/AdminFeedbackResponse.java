package com.mlb.portfolio.dto;

import com.mlb.portfolio.model.SiteFeedback.Category;
import java.time.Instant;
public record AdminFeedbackResponse(String id, int rating, Category category, String comment, Instant createdAt) { }
