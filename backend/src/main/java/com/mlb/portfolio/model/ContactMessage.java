package com.mlb.portfolio.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("contact_messages")
@CompoundIndex(name = "inbox_status_date", def = "{'inboxStatus': 1, 'createdAt': -1}")
public class ContactMessage {
    public enum NotificationStatus { PENDING, PROCESSING, SENT, FAILED }
    public enum InboxStatus { UNREAD, READ, ARCHIVED, SPAM }

    @Id private String id;
    @Indexed(unique = true) private String referenceId;
    private String name;
    private String email;
    private String subject;
    private String message;
    private Instant privacyAcceptedAt;
    private Instant createdAt;
    private InboxStatus inboxStatus = InboxStatus.UNREAD;
    private Instant reviewedAt;
    private NotificationStatus notificationStatus = NotificationStatus.PENDING;
    private int notificationAttempts;
    @Indexed private Instant nextNotificationAt;
    private Instant notificationLeaseUntil;
    @Indexed(expireAfter = "0s") private Instant expiresAt;

    protected ContactMessage() { }

    public ContactMessage(String referenceId, String name, String email, String subject,
                          String message, Instant createdAt, Instant expiresAt) {
        this.referenceId = referenceId;
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.message = message;
        this.createdAt = createdAt;
        this.privacyAcceptedAt = createdAt;
        this.expiresAt = expiresAt;
        this.nextNotificationAt = createdAt;
    }

    public String getId() { return id; }
    public String getReferenceId() { return referenceId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public Instant getPrivacyAcceptedAt() { return privacyAcceptedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public InboxStatus getInboxStatus() { return inboxStatus; }
    public Instant getReviewedAt() { return reviewedAt; }
    public NotificationStatus getNotificationStatus() { return notificationStatus; }
    public int getNotificationAttempts() { return notificationAttempts; }
    public Instant getNextNotificationAt() { return nextNotificationAt; }
    public Instant getNotificationLeaseUntil() { return notificationLeaseUntil; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setInboxStatus(InboxStatus value) { inboxStatus = value; }
    public void setReviewedAt(Instant value) { reviewedAt = value; }
    public void setNotificationStatus(NotificationStatus value) { notificationStatus = value; }
    public void setNotificationAttempts(int value) { notificationAttempts = value; }
    public void setNextNotificationAt(Instant value) { nextNotificationAt = value; }
    public void setNotificationLeaseUntil(Instant value) { notificationLeaseUntil = value; }
}

