package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.ProjectResponse;
import com.mlb.portfolio.service.ProjectService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service = service; }
    @GetMapping
    public List<ProjectResponse> list(@RequestParam(defaultValue = "6") @Min(1) @Max(24) int limit) {
        return service.list(limit);
    }
    @GetMapping("/{slug:[a-z0-9-]+}")
    public ProjectResponse get(@PathVariable String slug) { return service.get(slug); }
}

