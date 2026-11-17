package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.LoginRequest;
import com.mlb.portfolio.model.AdminSession;
import com.mlb.portfolio.repository.AdminSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {
    private final AdminSessionRepository sessions;
    private final AbuseProtectionService abuse;
    private final PasswordEncoder encoder;
    private final String username;
    private final String hash;
    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    public AdminAuthService(AdminSessionRepository sessions, AbuseProtectionService abuse, PasswordEncoder encoder,
        @Value("${mlb.security.admin-username}") String username,
        @Value("${mlb.security.admin-password-hash}") String hash,
        @Value("${mlb.security.jwt-secret}") String secret,
        @Value("${mlb.security.jwt-issuer:mlb-portfolio}") String issuer,
        @Value("${mlb.security.jwt-ttl-minutes:15}") long minutes) {
        this.sessions=sessions;this.abuse=abuse;this.encoder=encoder;this.username=username;this.hash=hash;this.issuer=issuer;
        byte[] signingBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if(signingBytes.length<32 || minutes<1 || minutes>30) throw new IllegalStateException("JWT security settings are invalid");
        this.key=new SecretKeySpec(signingBytes,"HmacSHA256");this.ttl=Duration.ofMinutes(minutes);
    }
    public String login(LoginRequest request,String ip){
        abuse.check("admin-login-ip",ip,5,900);
        abuse.check("admin-login-account",request.username().toLowerCase(java.util.Locale.ROOT),8,900);
        // Reduce username timing leaks
        boolean valid=encoder.matches(request.password(),hash) && MessageDigest.isEqual(
            username.getBytes(StandardCharsets.UTF_8),request.username().getBytes(StandardCharsets.UTF_8));
        if(!valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        Instant now=Instant.now();String tokenId=UUID.randomUUID().toString();Instant expires=now.plus(ttl);
        String token=Jwts.builder().id(tokenId).subject("admin").issuer(issuer).issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expires)).signWith(key).compact();
        sessions.save(new AdminSession(digest(tokenId),now,expires));
        return token;
    }
    public String validate(String token){
        try {
            Claims claims=Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
            if(!"admin".equals(claims.getSubject()) || claims.getId()==null) return null;
            return sessions.existsByTokenIdDigestAndRevokedAtIsNullAndExpiresAtAfter(digest(claims.getId()),Instant.now())
                ? claims.getId() : null;
        } catch(io.jsonwebtoken.JwtException | IllegalArgumentException bad){return null;}
    }
    public void logout(String token){
        try {
            Claims c=Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token).getPayload();
            sessions.findByTokenIdDigest(digest(c.getId())).ifPresent(s->{s.setRevokedAt(Instant.now());sessions.save(s);});
        } catch(io.jsonwebtoken.JwtException | IllegalArgumentException ignored){ }
    }
    public Duration ttl(){return ttl;}
    private String digest(String s){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
}
