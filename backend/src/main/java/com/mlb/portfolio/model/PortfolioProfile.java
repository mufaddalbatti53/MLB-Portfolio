package com.mlb.portfolio.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("portfolio_profiles")
public class PortfolioProfile {
    public record Education(String qualification, String institution, String location, String score) { }

    @Id private String id = "owner";
    @Version private Long version;
    private String fullName;
    private String brand;
    private String tagline;
    private String publicEmail;
    private String phone;
    private String location;
    private String websiteUrl;
    private String githubUrl;
    private String linkedinUrl;
    private String instagramHandle;
    private List<Education> education = new ArrayList<>();
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getFullName() { return fullName; }
    public String getBrand() { return brand; }
    public String getTagline() { return tagline; }
    public String getPublicEmail() { return publicEmail; }
    public String getPhone() { return phone; }
    public String getLocation() { return location; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getInstagramHandle() { return instagramHandle; }
    public List<Education> getEducation() { return education; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFullName(String value) { fullName = value; }
    public void setBrand(String value) { brand = value; }
    public void setTagline(String value) { tagline = value; }
    public void setPublicEmail(String value) { publicEmail = value; }
    public void setPhone(String value) { phone = value; }
    public void setLocation(String value) { location = value; }
    public void setWebsiteUrl(String value) { websiteUrl = value; }
    public void setGithubUrl(String value) { githubUrl = value; }
    public void setLinkedinUrl(String value) { linkedinUrl = value; }
    public void setInstagramHandle(String value) { instagramHandle = value; }
    public void setEducation(List<Education> value) { education = new ArrayList<>(value); }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}

