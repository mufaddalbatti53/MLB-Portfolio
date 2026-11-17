package com.mlb.portfolio.mapper;

import com.mlb.portfolio.dto.AdminProjectResponse;
import com.mlb.portfolio.dto.ProjectResponse;
import com.mlb.portfolio.model.Project;
import com.mlb.portfolio.service.ContentSanitizer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {
    private final ContentSanitizer sanitizer;

    public ProjectMapper(ContentSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public ProjectResponse toPublic(Project project) {
        return new ProjectResponse(
            project.getSlug(),
            project.getTitle(),
            project.getSummary(),
            project.getDescription(),
            project.getProblemStatement(),
            project.getArchitectureOverview(),
            safeList(project.getArchitectureFlow()),
            safeList(project.getKeyMetrics()),
            safeList(project.getTechnologies()),
            sanitizer.publicHttpsUrl(project.getGithubUrl()),
            sanitizer.publicHttpsUrl(project.getLiveUrl()),
            sanitizer.publicHttpsUrl(project.getImageUrl()),
            project.getImageAlt(),
            project.isFeatured(),
            project.getCreatedAt()
        );
    }

    public AdminProjectResponse toAdmin(Project project) {
        return new AdminProjectResponse(
            project.getId(),
            project.getVersion(),
            project.getSlug(),
            project.getTitle(),
            project.getSummary(),
            project.getDescription(),
            project.getProblemStatement(),
            project.getArchitectureOverview(),
            safeList(project.getArchitectureFlow()),
            safeList(project.getKeyMetrics()),
            safeList(project.getTechnologies()),
            project.getGithubUrl(),
            project.getLiveUrl(),
            project.getImageUrl(),
            project.getImageAlt(),
            project.isFeatured(),
            project.isPublished(),
            project.getSortOrder(),
            project.getUpdatedAt()
        );
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

