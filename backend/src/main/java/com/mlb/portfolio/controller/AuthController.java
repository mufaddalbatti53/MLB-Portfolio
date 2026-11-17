package com.mlb.portfolio.controller;

import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.LoginRequest;
import com.mlb.portfolio.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AdminAuthService service;
    private final ClientIpResolver ip;

    public AuthController(AdminAuthService service, ClientIpResolver ip) {
        this.service = service;
        this.ip = ip;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request) {
        String jwt = service.login(login, ip.resolve(request));
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of(
                "status", "authenticated",
                "token", jwt,
                "tokenType", "Bearer",
                "expiresInSeconds", String.valueOf(service.ttl().toSeconds())));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("authenticated", true, "role", "ADMIN");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
            ? header.substring(7).trim()
            : null;
        if (token != null && !token.isBlank()) service.logout(token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("status", "signed_out"));
    }
}

