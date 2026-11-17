package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.ExperienceResponse;
import com.mlb.portfolio.service.ExperienceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/experiences")
public class ExperienceController {
    private final ExperienceService service;

    public ExperienceController(ExperienceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExperienceResponse> list() {
        return service.publicList();
    }
}
