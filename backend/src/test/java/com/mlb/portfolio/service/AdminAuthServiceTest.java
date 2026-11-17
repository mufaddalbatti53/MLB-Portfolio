package com.mlb.portfolio.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mlb.portfolio.dto.LoginRequest;
import com.mlb.portfolio.repository.AdminSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AdminAuthServiceTest {
    @Test void issuesRevocableShortLivedJwtOnValidCredentials() {
        var sessions=mock(AdminSessionRepository.class);
        var throttle=mock(AbuseProtectionService.class);
        var encoder=new BCryptPasswordEncoder(12);
        var hash=encoder.encode("Strong secret used only in test");
        String key="independent-testing-key-with-32-bytes-minimum";
        when(sessions.existsByTokenIdDigestAndRevokedAtIsNullAndExpiresAtAfter(anyString(),any())).thenReturn(true);
        var auth=new AdminAuthService(sessions,throttle,encoder,"mlbadmin",hash,key,"mlb-portfolio",15);
        String token=auth.login(new LoginRequest("mlbadmin","Strong secret used only in test"),"203.0.113.7");
        assertNotNull(auth.validate(token));
        verify(sessions).save(any());
        verify(throttle).check(eq("admin-login-ip"),anyString(),eq(5),eq(900L));
    }
    @Test void refusesIncorrectPassword() {
        var encoder=new BCryptPasswordEncoder(12);
        var auth=new AdminAuthService(mock(AdminSessionRepository.class),mock(AbuseProtectionService.class),encoder,
            "mlbadmin",encoder.encode("Real test-only secret"),
            "independent-testing-key-with-32-bytes-minimum","mlb-portfolio",15);
        var error=assertThrows(ResponseStatusException.class,
            ()->auth.login(new LoginRequest("mlbadmin","incorrect"),"203.0.113.7"));
        assertEquals(HttpStatus.UNAUTHORIZED,error.getStatusCode());
    }
}
