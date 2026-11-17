package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.ContactMessage.InboxStatus;
import com.mlb.portfolio.model.ContactMessage.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContactMessageRepository extends MongoRepository<ContactMessage, String> {
    Page<ContactMessage> findAllByOrderByCreatedAtDesc(Pageable page);
    Page<ContactMessage> findByInboxStatusOrderByCreatedAtDesc(InboxStatus status, Pageable page);
    long countByInboxStatus(InboxStatus status);
    long countByNotificationStatus(NotificationStatus status);
}

