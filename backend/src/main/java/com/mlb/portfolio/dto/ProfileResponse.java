package com.mlb.portfolio.dto;
import com.mlb.portfolio.model.PortfolioProfile.Education;
import java.util.List;
public record ProfileResponse(String fullName,String brand,String tagline,String publicEmail,
    String phone,String location,String websiteUrl,String githubUrl,String linkedinUrl,
    String instagramHandle,List<Education> education) { }
