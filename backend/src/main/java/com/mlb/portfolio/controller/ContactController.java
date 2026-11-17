package com.mlb.portfolio.controller;

import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.ContactRequest;
import com.mlb.portfolio.dto.ContactResponse;
import com.mlb.portfolio.service.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {
    private final ContactService service;
    private final ClientIpResolver ipResolver;
    public ContactController(ContactService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContactResponse submit(@Valid @RequestBody ContactRequest request, HttpServletRequest servletRequest) {
        return service.submit(request, ipResolver.resolve(servletRequest));
    }
}

