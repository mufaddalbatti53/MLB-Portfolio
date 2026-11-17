package com.mlb.portfolio.controller;

import com.mlb.portfolio.dto.AdminFeedbackResponse;
import com.mlb.portfolio.service.FeedbackService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {
    private final FeedbackService service;
    public AdminFeedbackController(FeedbackService service){this.service=service;}
    @GetMapping public Page<AdminFeedbackResponse> list(@RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(50) int size){return service.list(page,size);}
    @DeleteMapping("/{id:[a-fA-F0-9]{24}}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id){service.delete(id);}
}
