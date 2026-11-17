package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.ContactRequest;
import com.mlb.portfolio.dto.ContactResponse;
import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.repository.ContactMessageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContactService {
    private final ContactMessageRepository repository;
    private final RateLimitService rateLimit;
    private final ContentSanitizer sanitizer;
    private final int retentionDays;

    public ContactService(ContactMessageRepository repository, RateLimitService rateLimit,
                          ContentSanitizer sanitizer,
                          @Value("${mlb.contact.retention-days:90}") int retentionDays) {
        if (retentionDays < 1 || retentionDays > 365) throw new IllegalArgumentException("Invalid retention days");
        this.repository = repository;
        this.rateLimit = rateLimit;
        this.sanitizer = sanitizer;
        this.retentionDays = retentionDays;
    }

    public ContactResponse submit(ContactRequest request, String clientIp) {
        rateLimit.check(clientIp);
        String reference = UUID.randomUUID().toString();
        if (request.website() != null && !request.website().isBlank()) {
            return new ContactResponse(reference, "Your message has been received.");
        }
        Instant now = Instant.now();
        ContactMessage message = new ContactMessage(reference,
            sanitizer.plainText(request.name(), 100, false),
            sanitizer.email(request.email()),
            sanitizer.plainText(request.subject(), 150, false),
            sanitizer.plainText(request.message(), 5000, true),
            now, now.plus(retentionDays, ChronoUnit.DAYS));
        repository.save(message);
        return new ContactResponse(reference, "Your message has been received.");
    }
}

