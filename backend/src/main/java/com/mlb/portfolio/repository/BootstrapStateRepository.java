package com.mlb.portfolio.repository;

import com.mlb.portfolio.model.BootstrapState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BootstrapStateRepository extends MongoRepository<BootstrapState, String> { }

