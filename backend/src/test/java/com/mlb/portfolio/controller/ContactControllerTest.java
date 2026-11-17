package com.mlb.portfolio.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.ContactResponse;
import com.mlb.portfolio.exception.GlobalExceptionHandler;
import com.mlb.portfolio.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ContactControllerTest {
    private MockMvc mvc;
    private ContactService service;
    @BeforeEach
    void setUp() {
        service = mock(ContactService.class);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        when(resolver.resolve(any())).thenReturn("203.0.113.4");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ContactController(service, resolver))
            .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }
    @Test
    void acceptsValidRequest() throws Exception {
        when(service.submit(any(), anyString())).thenReturn(
            new ContactResponse("c81e8760-b0fb-4ad3-9f21-b7aa7d212f01", "Your message has been received."));
        mvc.perform(post("/api/v1/contact")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"name":"Visitor","email":"visitor@example.org","subject":"Hello","message":"Message","website":"","privacyAccepted":true}
                    """))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.referenceId").exists());
    }
    @Test
    void rejectsInvalidEmailWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/contact")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {"name":"Visitor","email":"invalid","subject":"Hello","message":"Message","website":"","privacyAccepted":true}
                    """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        verifyNoInteractions(service);
    }
}

