package com.mlb.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record AdminProjectResponse(
    String id,
    Long version,
    String slug,
    String title,
    String summary,
    String description,
    String problemStatement,
    String architectureOverview,
    List<String> architectureFlow,
    List<String> keyMetrics,
    List<String> technologies,
    String githubUrl,
    String liveUrl,
    String imageUrl,
    String imageAlt,
    boolean featured,
    boolean published,
    int sortOrder,
    Instant updatedAt
) { }

