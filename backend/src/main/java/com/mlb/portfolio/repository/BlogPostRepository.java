package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.BlogPost;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BlogPostRepository extends MongoRepository<BlogPost, String> {
    List<BlogPost> findByPublishedTrueAndPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant now, Pageable page);
    Optional<BlogPost> findBySlugAndPublishedTrueAndPublishedAtLessThanEqual(String slug, Instant now);
    Optional<BlogPost> findBySlug(String slug);
    Page<BlogPost> findAllByOrderByCreatedAtDesc(Pageable page);
    long countByPublishedTrueAndPublishedAtLessThanEqual(Instant now);
}

