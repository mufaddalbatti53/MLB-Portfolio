package com.mlb.portfolio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("projects")
@CompoundIndex(name = "project_public_order", def = "{'published': 1, 'sortOrder': 1, 'createdAt': -1}")
public class Project {
    @Id private String id;
    @Version private Long version;
    @Indexed(unique = true) private String slug;
    private String title;
    private String summary;
    private String description;
    private String problemStatement;
    private String architectureOverview;
    private List<String> architectureFlow = new ArrayList<>();
    private List<String> keyMetrics = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private String githubUrl;
    private String liveUrl;
    private String imageUrl;
    private String imageAlt;
    private boolean featured;
    private boolean published;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public String getProblemStatement() { return problemStatement; }
    public String getArchitectureOverview() { return architectureOverview; }
    public List<String> getArchitectureFlow() { return architectureFlow; }
    public List<String> getKeyMetrics() { return keyMetrics; }
    public List<String> getTechnologies() { return technologies; }
    public String getGithubUrl() { return githubUrl; }
    public String getLiveUrl() { return liveUrl; }
    public String getImageUrl() { return imageUrl; }
    public String getImageAlt() { return imageAlt; }
    public boolean isFeatured() { return featured; }
    public boolean isPublished() { return published; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSlug(String value) { slug = value; }
    public void setTitle(String value) { title = value; }
    public void setSummary(String value) { summary = value; }
    public void setDescription(String value) { description = value; }
    public void setProblemStatement(String value) { problemStatement = value; }
    public void setArchitectureOverview(String value) { architectureOverview = value; }
    public void setArchitectureFlow(List<String> value) {
        architectureFlow = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setKeyMetrics(List<String> value) {
        keyMetrics = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setTechnologies(List<String> value) {
        technologies = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setGithubUrl(String value) { githubUrl = value; }
    public void setLiveUrl(String value) { liveUrl = value; }
    public void setImageUrl(String value) { imageUrl = value; }
    public void setImageAlt(String value) { imageAlt = value; }
    public void setFeatured(boolean value) { featured = value; }
    public void setPublished(boolean value) { published = value; }
    public void setSortOrder(int value) { sortOrder = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}

