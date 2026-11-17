package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.BlogPost;
import java.time.Instant;
import java.util.List;
public record AdminBlogResponse(String id, Long version, String slug, String title, String excerpt,
    String articleUrl, List<String> tags, String coverImageUrl, String coverImageAlt,
    boolean published, Instant publishedAt, Instant updatedAt) {
    public static AdminBlogResponse from(BlogPost p){return new AdminBlogResponse(p.getId(),p.getVersion(),p.getSlug(),p.getTitle(),
        p.getExcerpt(),p.getArticleUrl(),p.getTags(),p.getCoverImageUrl(),p.getCoverImageAlt(),p.isPublished(),
        p.getPublishedAt(),p.getUpdatedAt());}
}
