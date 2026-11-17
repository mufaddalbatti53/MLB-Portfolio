package com.mlb.portfolio.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trusted;

    public ClientIpResolver(@Value("${mlb.security.trusted-proxy-cidrs:}") String cidrs) {
        this.trusted = Arrays.stream(cidrs.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean trustedProxy = trusted.stream().anyMatch(matcher -> matcher.matches(remote));
        if (trustedProxy) {
            // Trust Nginx real IP
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && realIp.length() <= 45 && !realIp.contains(",")) {
                try {
                    InetAddress addr = InetAddress.getByName(realIp);
                    if (realIp.contains(":") || realIp.matches("[0-9.]+")) return addr.getHostAddress();
                } catch (Exception ignored) { }
            }
        }
        return remote;
    }
}

