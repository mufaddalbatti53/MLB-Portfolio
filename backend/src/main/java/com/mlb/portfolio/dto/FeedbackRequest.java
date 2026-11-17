package com.mlb.portfolio.dto;

import com.mlb.portfolio.model.SiteFeedback.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@Min(1) @Max(5) int rating,
    @NotNull Category category, @Size(max=500) String comment,
    @Size(max=200) String website) { }
