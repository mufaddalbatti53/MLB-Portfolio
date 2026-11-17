package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.PortfolioProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortfolioProfileRepository extends MongoRepository<PortfolioProfile, String> { }

