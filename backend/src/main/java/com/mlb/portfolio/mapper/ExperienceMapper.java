package com.mlb.portfolio.mapper;

import com.mlb.portfolio.dto.AdminExperienceResponse;
import com.mlb.portfolio.dto.ExperienceResponse;
import com.mlb.portfolio.model.Experience;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExperienceMapper {
    public ExperienceResponse toPublic(Experience experience) {
        return new ExperienceResponse(
            experience.getRole(),
            experience.getCompany(),
            experience.getEmploymentType(),
            experience.getWorkMode(),
            experience.getLocation(),
            experience.getStartDate(),
            experience.getEndDate(),
            experience.isCurrent(),
            experience.getSummary(),
            safeList(experience.getHighlights()),
            safeList(experience.getTechnologies())
        );
    }

    public AdminExperienceResponse toAdmin(Experience experience) {
        return new AdminExperienceResponse(
            experience.getId(),
            experience.getVersion(),
            experience.getRole(),
            experience.getCompany(),
            experience.getEmploymentType(),
            experience.getWorkMode(),
            experience.getLocation(),
            experience.getStartDate(),
            experience.getEndDate(),
            experience.isCurrent(),
            experience.getSummary(),
            safeList(experience.getHighlights()),
            safeList(experience.getTechnologies()),
            experience.getSortOrder(),
            experience.isPublished(),
            experience.getUpdatedAt()
        );
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
