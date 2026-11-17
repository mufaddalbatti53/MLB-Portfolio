package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.AdminExperienceResponse;
import com.mlb.portfolio.dto.ExperienceRequest;
import com.mlb.portfolio.dto.ExperienceResponse;
import com.mlb.portfolio.mapper.ExperienceMapper;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.repository.ExperienceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExperienceService {
    private final ExperienceRepository repository;
    private final ExperienceMapper mapper;
    private final ContentSanitizer sanitizer;

    public ExperienceService(
            ExperienceRepository repository,
            ExperienceMapper mapper,
            ContentSanitizer sanitizer) {
        this.repository = repository;
        this.mapper = mapper;
        this.sanitizer = sanitizer;
    }

    public List<ExperienceResponse> publicList() {
        return repository.findByPublishedTrueOrderBySortOrderAscStartDateDesc()
            .stream()
            .map(mapper::toPublic)
            .toList();
    }

    public Page<AdminExperienceResponse> adminList(int page, int size) {
        return repository.findAllByOrderBySortOrderAscStartDateDesc(PageRequest.of(page, size))
            .map(mapper::toAdmin);
    }

    public AdminExperienceResponse adminGet(String id) {
        return mapper.toAdmin(requireExperience(id));
    }

    public AdminExperienceResponse create(ExperienceRequest request) {
        return save(new Experience(), request);
    }

    public AdminExperienceResponse update(String id, ExperienceRequest request) {
        return save(requireExperience(id), request);
    }

    public void delete(String id) {
        repository.delete(requireExperience(id));
    }

    private AdminExperienceResponse save(Experience experience, ExperienceRequest request) {
        validateDates(request);
        experience.setRole(sanitizer.plainText(request.role(), 120, false));
        experience.setCompany(sanitizer.plainText(request.company(), 120, false));
        experience.setEmploymentType(sanitizer.plainText(request.employmentType(), 80, false));
        experience.setWorkMode(sanitizer.plainText(request.workMode(), 80, false));
        experience.setLocation(sanitizer.plainText(request.location(), 120, false));
        experience.setStartDate(request.startDate());
        experience.setEndDate(request.current() ? null : request.endDate());
        experience.setCurrent(request.current());
        experience.setSummary(sanitizer.plainText(request.summary(), 1500, true));
        experience.setHighlights(request.highlights().stream()
            .map(value -> sanitizer.plainText(value, 300, false))
            .toList());
        experience.setTechnologies(request.technologies().stream()
            .map(value -> sanitizer.plainText(value, 60, false))
            .toList());
        experience.setSortOrder(request.sortOrder());
        experience.setPublished(request.published());
        Instant now = Instant.now();
        if (experience.getCreatedAt() == null) experience.setCreatedAt(now);
        experience.setUpdatedAt(now);
        return mapper.toAdmin(repository.save(experience));
    }

    private static void validateDates(ExperienceRequest request) {
        if (request.current() && request.endDate() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current experience cannot have an end date");
        }
        if (!request.current() && request.endDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date is required for past experience");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot precede start date");
        }
    }

    private Experience requireExperience(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Experience not found"));
    }
}
