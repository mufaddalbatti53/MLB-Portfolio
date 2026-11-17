package com.mlb.portfolio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminProjectRequest(
    @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 100) String slug,
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 300) String summary,
    @Size(max = 5000) String description,
    @Size(max = 1000) String problemStatement,
    @Size(max = 2000) String architectureOverview,
    @NotNull @Size(max = 8) List<@NotBlank @Size(max = 160) String> architectureFlow,
    @NotNull @Size(max = 4) List<@NotBlank @Size(max = 120) String> keyMetrics,
    @NotNull @Size(max = 15) List<@NotBlank @Size(max = 60) String> technologies,
    String githubUrl,
    String liveUrl,
    String imageUrl,
    @Size(max = 180) String imageAlt,
    boolean featured,
    boolean published,
    @Min(0) @Max(100000) int sortOrder
) { }

