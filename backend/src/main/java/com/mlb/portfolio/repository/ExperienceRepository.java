package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.Experience;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExperienceRepository extends MongoRepository<Experience, String> {
    List<Experience> findByPublishedTrueOrderBySortOrderAscStartDateDesc();
    Page<Experience> findAllByOrderBySortOrderAscStartDateDesc(Pageable pageable);
}
