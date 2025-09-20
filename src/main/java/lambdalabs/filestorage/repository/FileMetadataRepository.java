package lambdalabs.filestorage.repository;

import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FileMetadataRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "file_metadata";

    /**
     * Save a file metadata document
     */
    public FileMetadata save(FileMetadata fileMetadata) {
        FileMetadata saved = mongoTemplate.save(fileMetadata, COLLECTION_NAME);
        return saved;
    }

    /**
     * Find all file metadata documents
     */
    public List<FileMetadata> findAll() {
        return mongoTemplate.findAll(FileMetadata.class, COLLECTION_NAME);
    }

    /**
     * Find file metadata by ID
     */
    public Optional<FileMetadata> findById(String id) {
        FileMetadata fileMetadata = mongoTemplate.findById(id, FileMetadata.class, COLLECTION_NAME);
        return Optional.ofNullable(fileMetadata);
    }

    /**
     * Find files by visibility
     */
    public List<FileMetadata> findByVisibility(Visibility visibility) {
        Query query = new Query(Criteria.where("visibility").is(visibility));
        return mongoTemplate.find(query, FileMetadata.class, COLLECTION_NAME);
    }

    /**
     * Find files by tag (case-insensitive)
     */
    public List<FileMetadata> findByTagContainingIgnoreCase(String tag) {
        Query query = new Query(Criteria.where("tags").in(tag));
        return mongoTemplate.find(query, FileMetadata.class, COLLECTION_NAME);
    }

    /**
     * Find files by visibility and tag
     */
    public List<FileMetadata> findByVisibilityAndTagContainingIgnoreCase(Visibility visibility, String tag) {
        Query query = new Query(Criteria.where("visibility").is(visibility)
                .and("tags").in(tag));
        List<FileMetadata> results = mongoTemplate.find(query, FileMetadata.class, COLLECTION_NAME);
        return results;
    }

    /**
     * Delete file metadata by ID
     */
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, FileMetadata.class, COLLECTION_NAME);
    }

    /**
     * Check if file metadata exists by ID
     */
    public boolean existsById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        return mongoTemplate.exists(query, FileMetadata.class, COLLECTION_NAME);
    }

    /**
     * Count all file metadata documents
     */
    public long count() {
        return mongoTemplate.count(new Query(), FileMetadata.class, COLLECTION_NAME);
    }
}
