package lambdalabs.filestorage.config;

import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class MongoIndexConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoIndexConfig.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        createFileMetadataIndexes();
        createUserIndexes();
    }

    private void createFileMetadataIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(FileMetadata.class);
        
        try {
            // Tags index - multikey index for array field to support $in queries
            indexOps.createIndex(new Index().on("tags", org.springframework.data.domain.Sort.Direction.ASC));
            
            logger.info("MongoDB indexes created successfully for FileMetadata collection");

        } catch (Exception e) {
            logger.error("Error creating FileMetadata indexes: {}", e.getMessage(), e);
        }
    }

    private void createUserIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(User.class);
        
        try {
            // Create unique index on identity field
            indexOps.createIndex(new Index().on("identity", org.springframework.data.domain.Sort.Direction.ASC).unique());
            
            logger.info("MongoDB indexes created successfully for User collection");

        } catch (Exception e) {
            logger.error("Error creating User indexes: {}", e.getMessage(), e);
        }
    }
}