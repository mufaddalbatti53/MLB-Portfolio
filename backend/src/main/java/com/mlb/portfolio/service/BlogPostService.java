package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.BlogPostResponse;
import com.mlb.portfolio.model.BlogPost;
import com.mlb.portfolio.repository.BlogPostRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlogPostService {
    private final BlogPostRepository repository;
    private final ContentSanitizer sanitizer;
    private final Clock clock = Clock.systemUTC();
    public BlogPostService(BlogPostRepository repository, ContentSanitizer sanitizer) {
        this.repository = repository;
        this.sanitizer = sanitizer;
    }
    public List<BlogPostResponse> list(int limit) {
        return repository.findByPublishedTrueAndPublishedAtLessThanEqualOrderByPublishedAtDesc(
            Instant.now(clock), PageRequest.of(0, limit)).stream().map(this::map).toList();
    }
    public BlogPostResponse get(String slug) {
        return repository.findBySlugAndPublishedTrueAndPublishedAtLessThanEqual(slug, Instant.now(clock))
            .map(this::map).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
    }
    private BlogPostResponse map(BlogPost p) {
        return new BlogPostResponse(p.getSlug(), p.getTitle(), p.getExcerpt(),
            sanitizer.publicHttpsUrl(p.getArticleUrl()),
            p.getTags() == null ? List.of() : List.copyOf(p.getTags()),
            sanitizer.publicHttpsUrl(p.getCoverImageUrl()), p.getCoverImageAlt(), p.getPublishedAt());
    }
}

