package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.ContactMessage.NotificationStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoContactQueueRepository implements ContactQueueRepository {
    private final MongoTemplate mongo;
    public MongoContactQueueRepository(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public Optional<ContactMessage> claimNext(Instant now) {
        Criteria due = new Criteria().orOperator(
            new Criteria().andOperator(
                Criteria.where("notificationStatus").in(NotificationStatus.PENDING, NotificationStatus.FAILED),
                Criteria.where("nextNotificationAt").lte(now)),
            new Criteria().andOperator(
                Criteria.where("notificationStatus").is(NotificationStatus.PROCESSING),
                Criteria.where("notificationLeaseUntil").lte(now)));
        Query query = new Query(new Criteria().andOperator(
            due, Criteria.where("notificationAttempts").lt(5)))
            .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        Update update = new Update()
            .set("notificationStatus", NotificationStatus.PROCESSING)
            .set("notificationLeaseUntil", now.plus(2, ChronoUnit.MINUTES))
            .inc("notificationAttempts", 1);
        return Optional.ofNullable(mongo.findAndModify(query, update,
            FindAndModifyOptions.options().returnNew(true), ContactMessage.class));
    }
}

