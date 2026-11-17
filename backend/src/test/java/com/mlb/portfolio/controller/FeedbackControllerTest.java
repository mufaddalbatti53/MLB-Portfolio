package com.mlb.portfolio.controller;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mlb.portfolio.config.ClientIpResolver;
import com.mlb.portfolio.dto.FeedbackResponse;
import com.mlb.portfolio.exception.GlobalExceptionHandler;
import com.mlb.portfolio.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FeedbackControllerTest {
    @Test void acceptsValidatedFeedback() throws Exception {
        var service=mock(FeedbackService.class);var resolver=mock(ClientIpResolver.class);
        when(resolver.resolve(any())).thenReturn("203.0.113.4");
        when(service.submit(any(),anyString())).thenReturn(new FeedbackResponse("Thanks for your feedback."));
        var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        var mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(service,resolver))
            .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":5,\"category\":\"UI_UX\",\"comment\":\"Beautiful\",\"website\":\"\"}"))
            .andExpect(status().isAccepted());
        verify(service).submit(any(),eq("203.0.113.4"));
    }
    @Test void rejectsInvalidRatingBeforePersistence() throws Exception {
        var service=mock(FeedbackService.class);var resolver=mock(ClientIpResolver.class);
        var validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();
        var mvc=MockMvcBuilders.standaloneSetup(new FeedbackController(service,resolver))
            .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/feedback").contentType(MediaType.APPLICATION_JSON)
            .content("{\"rating\":8,\"category\":\"BUG\",\"comment\":\"bad\",\"website\":\"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
