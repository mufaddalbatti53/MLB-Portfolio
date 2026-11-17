package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.*;
import com.mlb.portfolio.model.*;
import com.mlb.portfolio.mapper.ProjectMapper;
import com.mlb.portfolio.repository.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminContentService {
    private final ProjectRepository projects;
    private final BlogPostRepository blogs;
    private final ContactMessageRepository messages;
    private final PortfolioProfileRepository profiles;
    private final RateLimitStatsRepository rateLimits;
    private final ContentSanitizer clean;
    private final ProjectMapper projectMapper;
    public AdminContentService(ProjectRepository projects,BlogPostRepository blogs,ContactMessageRepository messages,
        PortfolioProfileRepository profiles,RateLimitStatsRepository rateLimits,ContentSanitizer clean,ProjectMapper projectMapper){
        this.projects=projects;this.blogs=blogs;this.messages=messages;this.profiles=profiles;this.rateLimits=rateLimits;this.clean=clean;this.projectMapper=projectMapper;
    }
    public java.util.Map<String,Long> dashboard(){return java.util.Map.of(
        "totalProjects",projects.count(),"publishedBlogs",blogs.countByPublishedTrueAndPublishedAtLessThanEqual(Instant.now()),
        "unreadMessages",messages.countByInboxStatus(ContactMessage.InboxStatus.UNREAD),
        "activeRateLimitBlocks",rateLimits.countByBlockedUntilAfter(Instant.now()));}
    public Page<AdminProjectResponse> projects(int page,int size){return projects.findAllByOrderBySortOrderAscCreatedAtDesc(PageRequest.of(page,size)).map(projectMapper::toAdmin);}
    public AdminProjectResponse project(String id){return projectMapper.toAdmin(requireProject(id));}
    public AdminProjectResponse saveProject(String id,AdminProjectRequest request){
        Project p=id==null?new Project():requireProject(id);
        p.setSlug(request.slug());p.setTitle(clean.plainText(request.title(),120,false));
        p.setSummary(clean.plainText(request.summary(),300,false));
        p.setDescription(opt(request.description(),5000,true));
        p.setProblemStatement(opt(request.problemStatement(),1000,true));
        p.setArchitectureOverview(opt(request.architectureOverview(),2000,true));
        p.setArchitectureFlow(request.architectureFlow().stream().map(step->clean.plainText(step,160,false)).toList());
        p.setKeyMetrics(request.keyMetrics().stream().map(metric->clean.plainText(metric,120,false)).toList());
        p.setTechnologies(request.technologies().stream().map(t->clean.plainText(t,60,false)).toList());
        p.setGithubUrl(url(request.githubUrl()));p.setLiveUrl(url(request.liveUrl()));
        p.setImageUrl(url(request.imageUrl()));p.setImageAlt(opt(request.imageAlt(),180,false));
        p.setFeatured(request.featured());p.setPublished(request.published());p.setSortOrder(request.sortOrder());
        Instant now=Instant.now(); if(p.getCreatedAt()==null)p.setCreatedAt(now);p.setUpdatedAt(now);
        return projectMapper.toAdmin(projects.save(p));
    }
    public AdminProjectResponse patchProject(String id,Boolean published,Boolean featured,Integer sortOrder){
        Project p=requireProject(id);
        if(published==null && featured==null && sortOrder==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No update values");
        if(published!=null)p.setPublished(published);
        if(featured!=null)p.setFeatured(featured);
        if(sortOrder!=null){if(sortOrder<0||sortOrder>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order");p.setSortOrder(sortOrder);}
        p.setUpdatedAt(Instant.now());return projectMapper.toAdmin(projects.save(p));
    }
    public void reorder(List<String> ids){
        if(ids==null||ids.isEmpty()||ids.size()>100||ids.stream().distinct().count()!=ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid order list");
        for(int i=0;i<ids.size();i++){Project p=requireProject(ids.get(i));p.setSortOrder((i+1)*10);p.setUpdatedAt(Instant.now());projects.save(p);}
    }
    public void deleteProject(String id){requireProject(id);projects.deleteById(id);}
    private Project requireProject(String id){return projects.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Project not found"));}
    public Page<AdminBlogResponse> blogs(int page,int size){return blogs.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size)).map(AdminBlogResponse::from);}
    public AdminBlogResponse blog(String id){return AdminBlogResponse.from(requireBlog(id));}
    public AdminBlogResponse saveBlog(String id,AdminBlogRequest request){
        BlogPost b=id==null?new BlogPost():requireBlog(id);
        b.setSlug(request.slug());b.setTitle(clean.plainText(request.title(),150,false));
        b.setExcerpt(clean.plainText(request.excerpt(),400,false));
        String article=url(request.articleUrl());if(article==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Article URL must use HTTPS");
        b.setArticleUrl(article);
        b.setTags(request.tags().stream().map(t->clean.plainText(t,50,false)).toList());
        b.setCoverImageUrl(url(request.coverImageUrl()));b.setCoverImageAlt(opt(request.coverImageAlt(),180,false));
        b.setPublished(request.published());b.setPublishedAt(request.publishedAt()==null?Instant.now():request.publishedAt());
        Instant now=Instant.now();if(b.getCreatedAt()==null)b.setCreatedAt(now);b.setUpdatedAt(now);
        return AdminBlogResponse.from(blogs.save(b));
    }
    public AdminBlogResponse patchBlog(String id,Boolean published){
        if(published==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Missing published status");
        BlogPost b=requireBlog(id);b.setPublished(published);b.setUpdatedAt(Instant.now());
        return AdminBlogResponse.from(blogs.save(b));
    }
    public void deleteBlog(String id){requireBlog(id);blogs.deleteById(id);}
    private BlogPost requireBlog(String id){return blogs.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Blog not found"));}
    public Page<AdminMessageResponse> messages(int page,int size){return messages.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size)).map(AdminMessageResponse::from);}
    public AdminMessageResponse messageStatus(String id,ContactMessage.InboxStatus status){
        ContactMessage m=requireMessage(id);m.setInboxStatus(status);m.setReviewedAt(Instant.now());return AdminMessageResponse.from(messages.save(m));
    }
    public AdminMessageResponse retry(String id){
        ContactMessage m=requireMessage(id);
        if(m.getNotificationStatus()==ContactMessage.NotificationStatus.PROCESSING) throw new ResponseStatusException(HttpStatus.CONFLICT,"Delivery in progress");
        m.setNotificationAttempts(0);m.setNotificationStatus(ContactMessage.NotificationStatus.PENDING);
        m.setNextNotificationAt(Instant.now());return AdminMessageResponse.from(messages.save(m));
    }
    public void deleteMessage(String id){requireMessage(id);messages.deleteById(id);}
    private ContactMessage requireMessage(String id){return messages.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Message not found"));}
    public PortfolioProfile profile(){return profiles.findById("owner").orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile not found"));}
    public java.util.Map<String,Object> profileView(){return profileMap(profile());}
    public java.util.Map<String,Object> updateProfile(AdminProfileRequest body){
        PortfolioProfile p=profile();p.setFullName(clean.plainText(body.fullName(),100,false));
        p.setBrand(clean.plainText(body.brand(),20,false));p.setTagline(clean.plainText(body.tagline(),180,false));
        p.setPublicEmail(clean.email(body.publicEmail()));p.setPhone(opt(body.phone(),30,false));
        p.setLocation(clean.plainText(body.location(),120,false));p.setWebsiteUrl(url(body.websiteUrl()));
        p.setGithubUrl(url(body.githubUrl()));p.setLinkedinUrl(url(body.linkedinUrl()));
        p.setInstagramHandle(opt(body.instagramHandle(),40,false));p.setEducation(body.education());p.setUpdatedAt(Instant.now());
        return profileMap(profiles.save(p));
    }
    private java.util.Map<String,Object> profileMap(PortfolioProfile p){
        java.util.Map<String,Object> m=new java.util.LinkedHashMap<>();
        m.put("fullName",p.getFullName());m.put("brand",p.getBrand());m.put("tagline",p.getTagline());
        m.put("publicEmail",p.getPublicEmail());m.put("phone",p.getPhone());m.put("location",p.getLocation());
        m.put("websiteUrl",p.getWebsiteUrl());m.put("githubUrl",p.getGithubUrl());m.put("linkedinUrl",p.getLinkedinUrl());
        m.put("instagramHandle",p.getInstagramHandle());m.put("education",p.getEducation());return m;
    }
    private String opt(String raw,int max,boolean multi){return raw==null||raw.isBlank()?null:clean.plainText(raw,max,multi);}
    private String url(String raw){
        if(raw==null||raw.isBlank())return null;
        String valid=clean.publicHttpsUrl(raw);
        if(valid==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid HTTPS URL");
        return valid;
    }
}
