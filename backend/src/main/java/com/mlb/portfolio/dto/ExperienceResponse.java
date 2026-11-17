package com.mlb.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record ExperienceResponse(
    String role,
    String company,
    String employmentType,
    String workMode,
    String location,
    LocalDate startDate,
    LocalDate endDate,
    boolean current,
    String summary,
    List<String> highlights,
    List<String> technologies
) { }
