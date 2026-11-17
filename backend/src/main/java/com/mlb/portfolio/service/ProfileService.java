package com.mlb.portfolio.service;
import com.mlb.portfolio.dto.ProfileResponse;
import com.mlb.portfolio.model.PortfolioProfile;
import com.mlb.portfolio.repository.PortfolioProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ProfileService {
    private final PortfolioProfileRepository profiles;
    public ProfileService(PortfolioProfileRepository profiles){this.profiles=profiles;}
    public ProfileResponse publicProfile(){
        PortfolioProfile p=profiles.findById("owner").orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile unavailable"));
        return new ProfileResponse(p.getFullName(),p.getBrand(),p.getTagline(),p.getPublicEmail(),p.getPhone(),
            p.getLocation(),p.getWebsiteUrl(),p.getGithubUrl(),p.getLinkedinUrl(),p.getInstagramHandle(),p.getEducation());
    }
}
