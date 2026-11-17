package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.PortfolioProfile.Education;
import jakarta.validation.constraints.*;
import java.util.List;
public record AdminProfileRequest(
    @NotBlank @Size(max=100) String fullName, @NotBlank @Size(max=20) String brand,
    @NotBlank @Size(max=180) String tagline, @NotBlank @Email @Size(max=254) String publicEmail,
    @Size(max=30) String phone, @NotBlank @Size(max=120) String location,
    String websiteUrl, String githubUrl, String linkedinUrl,
    @Size(max=40) String instagramHandle, @NotNull @Size(max=12) List<Education> education) { }
