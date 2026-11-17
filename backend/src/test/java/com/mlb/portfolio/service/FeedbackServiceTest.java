package com.mlb.portfolio.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.dto.FeedbackRequest;
import com.mlb.portfolio.model.SiteFeedback;
import com.mlb.portfolio.repository.SiteFeedbackRepository;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {
    @Test void honeypotNeverPersists() {
        var repository=mock(SiteFeedbackRepository.class);
        var throttle=mock(AbuseProtectionService.class);
        var sanitizer=mock(ContentSanitizer.class);
        var service=new FeedbackService(repository,throttle,sanitizer,90);
        var response=service.submit(new FeedbackRequest(5,SiteFeedback.Category.GENERAL,"Good","spam.example"),"203.0.113.4");
        assertEquals("Thanks for your feedback.",response.message());
        verify(throttle).check(eq("feedback"),anyString(),eq(10),eq(3600L));
        verifyNoInteractions(repository);
    }
}
