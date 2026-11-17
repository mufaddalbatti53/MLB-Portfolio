package com.mlb.portfolio.dto;

import java.time.Instant;
import java.util.List;

public record BlogPostResponse(String slug, String title, String excerpt, String articleUrl,
                               List<String> tags, String coverImageUrl, String coverImageAlt,
                               Instant publishedAt) { }

