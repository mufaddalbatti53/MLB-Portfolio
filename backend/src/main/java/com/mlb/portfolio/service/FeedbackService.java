package com.mlb.portfolio.service;

import com.mlb.portfolio.dto.AdminFeedbackResponse;
import com.mlb.portfolio.dto.FeedbackRequest;
import com.mlb.portfolio.dto.FeedbackResponse;
import com.mlb.portfolio.model.SiteFeedback;
import com.mlb.portfolio.repository.SiteFeedbackRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeedbackService {
    private final SiteFeedbackRepository repository;
    private final AbuseProtectionService abuse;
    private final ContentSanitizer sanitizer;
    private final int retentionDays;
    public FeedbackService(SiteFeedbackRepository repository, AbuseProtectionService abuse, ContentSanitizer sanitizer,
        @Value("${mlb.feedback.retention-days:90}") int retentionDays) {
        this.repository=repository; this.abuse=abuse; this.sanitizer=sanitizer; this.retentionDays=retentionDays;
    }
    public FeedbackResponse submit(FeedbackRequest request,String clientIp){
        abuse.check("feedback",clientIp,10,3600);
        if(request.website()!=null && !request.website().isBlank()) return new FeedbackResponse("Thanks for your feedback.");
        Instant now=Instant.now();
        String comment=request.comment()==null ? "" : (request.comment().isBlank() ? "" : sanitizer.plainText(request.comment(), 500, true));
        repository.save(new SiteFeedback(request.rating(),request.category(),comment,now,now.plusSeconds(86400L*retentionDays)));
        return new FeedbackResponse("Thanks for your feedback.");
    }
    public Page<AdminFeedbackResponse> list(int page,int size){
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size))
            .map(f->new AdminFeedbackResponse(f.getId(),f.getRating(),f.getCategory(),f.getComment(),f.getCreatedAt()));
    }
    public void delete(String id){
        if(!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Feedback not found");
        repository.deleteById(id);
    }
}
