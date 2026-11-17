package com.mlb.portfolio.service;

import com.mlb.portfolio.exception.RateLimitExceededException;
import com.mlb.portfolio.repository.RateLimitCounterRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final long WINDOW_SECONDS = 3600;
    private final RateLimitCounterRepository repository;
    private final byte[] key;
    private final int maxRequests;
    private final Clock clock;

    public RateLimitService(RateLimitCounterRepository repository,
            @Value("${mlb.security.rate-limit-hmac-key}") String secret,
            @Value("${mlb.contact.max-per-hour:5}") int maxRequests) {
        this(repository, secret, maxRequests, Clock.systemUTC());
    }
    RateLimitService(RateLimitCounterRepository repository, String secret, int maxRequests, Clock clock) {
        if (secret == null || secret.length() < 32 || maxRequests < 1) {
            throw new IllegalArgumentException("Rate-limit secret must have 32+ characters and max requests must be positive");
        }
        this.repository = repository;
        this.key = secret.getBytes(StandardCharsets.UTF_8).clone();
        this.maxRequests = maxRequests;
        this.clock = clock;
    }
    public void check(String clientIp) {
        Instant now = clock.instant();
        long window = Math.floorDiv(now.getEpochSecond(), WINDOW_SECONDS) * WINDOW_SECONDS;
        Instant start = Instant.ofEpochSecond(window);
        String id = "contact:" + window + ":" + hash(clientIp);
        int count = repository.increment(id, start, start.plusSeconds(WINDOW_SECONDS * 2));
        if (count > maxRequests) {
            repository.markBlocked(id, start.plusSeconds(WINDOW_SECONDS));
            throw new RateLimitExceededException(Math.max(1, window + WINDOW_SECONDS - now.getEpochSecond()));
        }
    }
    private String hash(String identity) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize rate limiting", ex);
        }
    }
}

