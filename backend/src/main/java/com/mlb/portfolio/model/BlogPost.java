package com.mlb.portfolio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("blog_posts")
@CompoundIndex(name = "blog_public_date", def = "{'published': 1, 'publishedAt': -1}")
public class BlogPost {
    @Id private String id;
    @Version private Long version;
    @Indexed(unique = true) private String slug;
    private String title;
    private String excerpt;
    private String articleUrl;
    private List<String> tags = new ArrayList<>();
    private String coverImageUrl;
    private String coverImageAlt;
    private boolean published;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getExcerpt() { return excerpt; }
    public String getArticleUrl() { return articleUrl; }
    public List<String> getTags() { return tags; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getCoverImageAlt() { return coverImageAlt; }
    public boolean isPublished() { return published; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSlug(String value) { slug = value; }
    public void setTitle(String value) { title = value; }
    public void setExcerpt(String value) { excerpt = value; }
    public void setArticleUrl(String value) { articleUrl = value; }
    public void setTags(List<String> value) { tags = new ArrayList<>(value); }
    public void setCoverImageUrl(String value) { coverImageUrl = value; }
    public void setCoverImageAlt(String value) { coverImageAlt = value; }
    public void setPublished(boolean value) { published = value; }
    public void setPublishedAt(Instant value) { publishedAt = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}

