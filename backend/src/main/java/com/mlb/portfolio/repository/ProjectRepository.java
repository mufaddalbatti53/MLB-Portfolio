package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByPublishedTrueOrderBySortOrderAscCreatedAtDesc(Pageable page);
    Optional<Project> findBySlugAndPublishedTrue(String slug);
    Optional<Project> findBySlug(String slug);
    Page<Project> findAllByOrderBySortOrderAscCreatedAtDesc(Pageable page);
    long countByPublishedTrue();
}

