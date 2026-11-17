package com.mlb.portfolio.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
public record AdminBlogRequest(
    @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=100) String slug,
    @NotBlank @Size(max=150) String title, @NotBlank @Size(max=400) String excerpt,
    @NotBlank String articleUrl, @NotNull @Size(max=12) List<@NotBlank @Size(max=50) String> tags,
    String coverImageUrl, @Size(max=180) String coverImageAlt,
    boolean published, Instant publishedAt) { }
