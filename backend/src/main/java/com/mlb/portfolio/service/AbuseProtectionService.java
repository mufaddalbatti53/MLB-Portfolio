package com.mlb.portfolio.service;

import com.mlb.portfolio.exception.RateLimitExceededException;
import com.mlb.portfolio.model.RateLimitCounter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class AbuseProtectionService {
    private final MongoTemplate mongo;
    private final byte[] secret;
    public AbuseProtectionService(MongoTemplate mongo, @Value("${mlb.security.rate-limit-hmac-key}") String key) {
        if (key == null || key.length() < 32) throw new IllegalStateException("RATE_LIMIT_HMAC_SECRET must have at least 32 characters");
        this.mongo=mongo; this.secret=key.getBytes(StandardCharsets.UTF_8).clone();
    }
    public void check(String scope, String identity, int maximum, long windowSeconds) {
        Instant now=Instant.now();
        long start=(now.getEpochSecond()/windowSeconds)*windowSeconds;
        String id=scope+":"+start+":"+digest(identity);
        Query query=Query.query(Criteria.where("_id").is(id));
        Instant until=Instant.ofEpochSecond(start+windowSeconds);
        Update update=new Update().inc("requestCount",1).setOnInsert("windowStartedAt", Instant.ofEpochSecond(start))
            .setOnInsert("expiresAt",until.plusSeconds(windowSeconds));
        RateLimitCounter counter=mongo.findAndModify(query,update,FindAndModifyOptions.options().upsert(true).returnNew(true),RateLimitCounter.class);
        if(counter==null) throw new IllegalStateException("Rate limit update failed");
        if(counter.getRequestCount()>maximum){
            mongo.updateFirst(query,new Update().set("blockedUntil",until),RateLimitCounter.class);
            throw new RateLimitExceededException(Math.max(1,until.getEpochSecond()-now.getEpochSecond()));
        }
    }
    private String digest(String identity) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(identity.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e){throw new IllegalStateException("HMAC failure",e);}
    }
}
