package com.mlb.portfolio.service;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContentSanitizer {
    public String plainText(String input, int maxLength, boolean multiline) {
        String normalized = Normalizer.normalize(Objects.requireNonNull(input), Normalizer.Form.NFKC);
        String cleanHtml = Jsoup.clean(normalized, Safelist.none());
        String text = Parser.unescapeEntities(cleanHtml, false)
            .replace("\r\n", "\n").replace('\r', '\n');
        text = multiline ? text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            : text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ");
        text = text.strip();
        if (text.isBlank() || text.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid text field");
        }
        return text;
    }

    public String email(String email) {
        String value = Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.contains("<") || value.contains(">")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
        }
        return value;
    }

    public String publicHttpsUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

