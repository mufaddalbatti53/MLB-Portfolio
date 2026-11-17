package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.RateLimitCounter;
import java.time.Instant;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoRateLimitCounterRepository implements RateLimitCounterRepository {
    private final MongoTemplate mongo;

    public MongoRateLimitCounterRepository(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void markBlocked(String bucketId, Instant blockedUntil) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(bucketId)),
            new Update().set("blockedUntil", blockedUntil), RateLimitCounter.class);
    }

    @Override
    public int increment(String bucketId, Instant windowStart, Instant expiresAt) {
        Query query = Query.query(Criteria.where("_id").is(bucketId));
        Update update = new Update().inc("requestCount", 1)
            .setOnInsert("windowStartedAt", windowStart)
            .setOnInsert("expiresAt", expiresAt);
        RateLimitCounter updated = mongo.findAndModify(query, update,
            FindAndModifyOptions.options().upsert(true).returnNew(true), RateLimitCounter.class);
        if (updated == null) throw new IllegalStateException("Rate-limit update failed");
        return updated.getRequestCount();
    }
}

