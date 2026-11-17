package com.mlb.portfolio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ExperienceRequest(
    @NotBlank @Size(max = 120) String role,
    @NotBlank @Size(max = 120) String company,
    @NotBlank @Size(max = 80) String employmentType,
    @NotBlank @Size(max = 80) String workMode,
    @NotBlank @Size(max = 120) String location,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    boolean current,
    @NotBlank @Size(max = 1500) String summary,
    @NotNull @Size(max = 8) List<@NotBlank @Size(max = 300) String> highlights,
    @NotNull @Size(max = 15) List<@NotBlank @Size(max = 60) String> technologies,
    @Min(0) @Max(100000) int sortOrder,
    boolean published
) { }
