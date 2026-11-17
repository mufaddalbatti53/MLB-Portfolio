package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.dto.ContactRequest;
import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.repository.ContactMessageRepository;
import org.junit.jupiter.api.Test;

class ContactServiceTest {
    private final ContactMessageRepository repository = mock(ContactMessageRepository.class);
    private final RateLimitService rateLimit = mock(RateLimitService.class);
    private final ContactService service = new ContactService(repository, rateLimit,
        new ContentSanitizer(), 90);

    @Test
    void honeypotIsAcknowledgedWithoutPersistence() {
        var request = new ContactRequest("Visitor", "visitor@example.org", "Hello", "Message", "spam.site", true);
        var result = service.submit(request, "198.51.100.8");
        assertNotNull(result.referenceId());
        verify(rateLimit).check("198.51.100.8");
        verifyNoInteractions(repository);
    }

    @Test
    void validMessageIsSanitizedBeforeStorage() {
        var request = new ContactRequest("Visitor", "VISITOR@EXAMPLE.ORG", "Hello",
            "<b>Thank you</b>", "", true);
        var result = service.submit(request, "198.51.100.9");
        assertNotNull(result.referenceId());
        verify(repository).save(argThat(saved ->
            "visitor@example.org".equals(saved.getEmail()) &&
            "Thank you".equals(saved.getMessage())));
    }
}

