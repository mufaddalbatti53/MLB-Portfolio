package com.mlb.portfolio.controller;
import com.mlb.portfolio.dto.ProfileResponse;
import com.mlb.portfolio.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service){this.service=service;}
    @GetMapping public ProfileResponse view(){return service.publicProfile();}
}
