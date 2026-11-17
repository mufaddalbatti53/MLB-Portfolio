package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.BlogPostResponse;
import com.mlb.portfolio.service.BlogPostService;
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
@RequestMapping("/api/v1/blog")
public class BlogController {
    private final BlogPostService service;
    public BlogController(BlogPostService service) { this.service = service; }
    @GetMapping
    public List<BlogPostResponse> list(@RequestParam(defaultValue = "3") @Min(1) @Max(24) int limit) {
        return service.list(limit);
    }
    @GetMapping("/{slug:[a-z0-9-]+}")
    public BlogPostResponse get(@PathVariable String slug) { return service.get(slug); }
}

