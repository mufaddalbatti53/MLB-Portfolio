package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.ProjectResponse;
import com.mlb.portfolio.mapper.ProjectMapper;
import com.mlb.portfolio.repository.ProjectRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectMapper mapper;

    public ProjectService(ProjectRepository repository, ProjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ProjectResponse> list(int limit) {
        return repository.findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(PageRequest.of(0, limit))
            .stream()
            .map(mapper::toPublic)
            .toList();
    }

    public ProjectResponse get(String slug) {
        return repository.findBySlugAndPublishedTrue(slug)
            .map(mapper::toPublic)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }
}

