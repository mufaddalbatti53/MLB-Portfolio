package com.mlb.portfolio.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.exception.RateLimitExceededException;
import com.mlb.portfolio.repository.RateLimitCounterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {
    @Test
    void rejectsAttemptAboveConfiguredWindowCount() {
        RateLimitCounterRepository repository = mock(RateLimitCounterRepository.class);
        when(repository.increment(anyString(), any(), any())).thenReturn(6);
        RateLimitService service = new RateLimitService(repository,
            "this-is-a-valid-example-test-secret-123456", 5,
            Clock.fixed(Instant.parse("2026-11-16T12:30:00Z"), ZoneOffset.UTC));
        var error = assertThrows(RateLimitExceededException.class,
            () -> service.check("203.0.113.4"));
        assertEquals(1800, error.getRetryAfterSeconds());
        verify(repository).increment(argThat(id -> id.startsWith("contact:") &&
            !id.contains("203.0.113.4")), any(), any());
    }
    @Test
    void refusesWeakSecret() {
        assertThrows(IllegalArgumentException.class,
            () -> new RateLimitService(mock(RateLimitCounterRepository.class), "short", 5,
                Clock.systemUTC()));
    }
}

