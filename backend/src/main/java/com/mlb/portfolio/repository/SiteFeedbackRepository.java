package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.SiteFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SiteFeedbackRepository extends MongoRepository<SiteFeedback,String> {
    Page<SiteFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
