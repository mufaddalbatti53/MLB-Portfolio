package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.ContactMessage;
import java.time.Instant;
public record AdminMessageResponse(String id, String name, String email, String subject, String message,
    ContactMessage.InboxStatus inboxStatus, ContactMessage.NotificationStatus notificationStatus,
    int notificationAttempts, Instant createdAt) {
    public static AdminMessageResponse from(ContactMessage m){return new AdminMessageResponse(m.getId(),m.getName(),m.getEmail(),
        m.getSubject(),m.getMessage(),m.getInboxStatus(),m.getNotificationStatus(),m.getNotificationAttempts(),m.getCreatedAt());}
}
