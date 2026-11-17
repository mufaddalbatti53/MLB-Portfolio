package com.mlb.portfolio.service;

import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.ContactMessage.NotificationStatus;
import com.mlb.portfolio.repository.ContactMessageRepository;
import com.mlb.portfolio.repository.ContactQueueRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mlb.mail.enabled", havingValue = "true")
public class ContactNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ContactNotificationService.class);
    private final ContactMessageRepository repository;
    private final ContactQueueRepository queue;
    private final JavaMailSender sender;
    private final String from;
    private final String to;

    public ContactNotificationService(ContactMessageRepository repository, ContactQueueRepository queue,
           JavaMailSender sender, @Value("${mlb.mail.from}") String from,
           @Value("${mlb.mail.notify-to}") String to) {
        if (from.isBlank() || to.isBlank()) throw new IllegalArgumentException("Mail addresses must be configured");
        this.repository = repository;
        this.queue = queue;
        this.sender = sender;
        this.from = from;
        this.to = to;
    }
    @Scheduled(fixedDelayString = "${mlb.mail.poll-interval-ms:30000}")
    public void deliverPending() {
        for (int i = 0; i < 10; i++) {
            var next = queue.claimNext(Instant.now());
            if (next.isEmpty()) return;
            ContactMessage contact = next.get();
            try {
                SimpleMailMessage email = new SimpleMailMessage();
                email.setFrom(from);
                email.setTo(to);
                email.setSubject("MLB inquiry: " + contact.getSubject());
                email.setText("Reference: " + contact.getReferenceId()
                    + "\nName: " + contact.getName() + "\nEmail: " + contact.getEmail()
                    + "\n\n" + contact.getMessage());
                sender.send(email);
                contact.setNotificationStatus(NotificationStatus.SENT);
                contact.setNextNotificationAt(null);
            } catch (RuntimeException ex) {
                log.warn("Contact notification failed for receipt {} (attempt {})",
                    contact.getReferenceId(), contact.getNotificationAttempts());
                contact.setNotificationStatus(NotificationStatus.FAILED);
                if (contact.getNotificationAttempts() < 5) {
                    long backoff = 1L << Math.min(contact.getNotificationAttempts(), 5);
                    contact.setNextNotificationAt(Instant.now().plusSeconds(backoff * 60));
                } else {
                    contact.setNextNotificationAt(null);
                }
            }
            contact.setNotificationLeaseUntil(null);
            repository.save(contact);
        }
    }
}

