package com.mlb.portfolio.config;

import com.mlb.portfolio.model.AdminSession;
import com.mlb.portfolio.model.BlogPost;
import com.mlb.portfolio.model.ContactMessage;
import com.mlb.portfolio.model.Experience;
import com.mlb.portfolio.model.Project;
import com.mlb.portfolio.model.SiteFeedback;
import com.mlb.portfolio.model.RateLimitCounter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoIndexConfig {
    @Bean
    @Order(0)
    public ApplicationRunner ensureIndexes(MongoTemplate mongo, MongoMappingContext context) {
        return args -> {
            MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(context);
            for (Class<?> type : new Class<?>[]{ Project.class, BlogPost.class, Experience.class,
                    ContactMessage.class, RateLimitCounter.class, AdminSession.class, SiteFeedback.class }) {
                var collection = mongo.indexOps(type);
                resolver.resolveIndexFor(type).forEach(collection::ensureIndex);
            }
        };
    }
}
