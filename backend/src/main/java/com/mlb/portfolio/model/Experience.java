package com.mlb.portfolio.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("experiences")
@CompoundIndex(name = "experience_public_order", def = "{'published': 1, 'sortOrder': 1, 'startDate': -1}")
public class Experience {
    @Id private String id;
    @Version private Long version;
    private String role;
    private String company;
    private String employmentType;
    private String workMode;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean current;
    private String summary;
    private List<String> highlights = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private int sortOrder;
    private boolean published;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public Long getVersion() { return version; }
    public String getRole() { return role; }
    public String getCompany() { return company; }
    public String getEmploymentType() { return employmentType; }
    public String getWorkMode() { return workMode; }
    public String getLocation() { return location; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isCurrent() { return current; }
    public String getSummary() { return summary; }
    public List<String> getHighlights() { return highlights; }
    public List<String> getTechnologies() { return technologies; }
    public int getSortOrder() { return sortOrder; }
    public boolean isPublished() { return published; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRole(String value) { role = value; }
    public void setCompany(String value) { company = value; }
    public void setEmploymentType(String value) { employmentType = value; }
    public void setWorkMode(String value) { workMode = value; }
    public void setLocation(String value) { location = value; }
    public void setStartDate(LocalDate value) { startDate = value; }
    public void setEndDate(LocalDate value) { endDate = value; }
    public void setCurrent(boolean value) { current = value; }
    public void setSummary(String value) { summary = value; }
    public void setHighlights(List<String> value) {
        highlights = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setTechnologies(List<String> value) {
        technologies = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public void setSortOrder(int value) { sortOrder = value; }
    public void setPublished(boolean value) { published = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
