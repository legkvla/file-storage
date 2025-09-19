package lambdalabs.filestorage.repository;

import jakarta.validation.constraints.NotNull;
import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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

    public FileMetadata save(FileMetadata fileMetadata) {
        return mongoTemplate.save(fileMetadata, COLLECTION_NAME);
    }


    public List<FileMetadata> findAllVisibleToUser(String userId, int skip, int limit, String sortField, boolean desc) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("visibility").is(Visibility.PUBLIC),
                Criteria.where("ownerId").is(userId)
        ));
        query.skip(skip).limit(limit);

        Sort.Direction direction = desc ? Sort.Direction.DESC : Sort.Direction.ASC;
        query.with(Sort.by(direction, sortField));
        
        return mongoTemplate.find(query, FileMetadata.class, COLLECTION_NAME);
    }

    public Optional<FileMetadata> findByIdVisibleToUser(String id, String userId) {
        Query query = new Query(Criteria.where("id").is(id).orOperator(
                    Criteria.where("visibility").is(Visibility.PUBLIC),
                    Criteria.where("ownerId").is(userId)
                )
        );
        return Optional.ofNullable(mongoTemplate.findOne(query, FileMetadata.class, COLLECTION_NAME));
    }


    public List<FileMetadata> findByVisibilityVisibleToUser(Visibility visibility, String userId, int skip, int limit, String sortField, boolean desc) {
        return getFileMetadataList(skip, limit, sortField, desc,
                Criteria.where("visibility").is(visibility).orOperator(
                        Criteria.where("visibility").is(Visibility.PUBLIC),
                        Criteria.where("ownerId").is(userId)
                ));
    }

    private List<FileMetadata> getFileMetadataList(int skip, int limit, String sortField, boolean desc, Criteria criteriaDefinition) {
        Query query = new Query(criteriaDefinition);
        query.skip(skip).limit(limit);

        Sort.Direction direction = desc ? Sort.Direction.DESC : Sort.Direction.ASC;
        query.with(Sort.by(direction, sortField));

        return mongoTemplate.find(query, FileMetadata.class, COLLECTION_NAME);
    }


    public List<FileMetadata> findByTagVisibleToUser(
            @NotNull String tag, String userId, int skip, int limit, String sortField, boolean desc) {
        return getFileMetadataList(skip, limit, sortField, desc,
                Criteria.where("tags").in(tag.toLowerCase()).orOperator(
                    Criteria.where("visibility").is(Visibility.PUBLIC),
                    Criteria.where("ownerId").is(userId)
                )
        );
    }


    public List<FileMetadata> findByVisibilityAndTagVisibleToUser(
            Visibility visibility, @NotNull String tag, String userId, int skip, int limit, String sortField,
            boolean desc) {
        return getFileMetadataList(skip, limit, sortField, desc,
                Criteria.where("visibility").is(visibility)
                        .and("tags").in(tag.toLowerCase())
                        .orOperator(
                                Criteria.where("visibility").is(Visibility.PUBLIC),
                                Criteria.where("ownerId").is(userId)
                        )
        );
    }

    public boolean deleteByIdAndOwner(String id, String ownerId) {
        return mongoTemplate.remove(
                new Query(Criteria.where("id").is(id).and("ownerId").is(ownerId)),
                FileMetadata.class, COLLECTION_NAME).
                getDeletedCount() > 0;
    }

    public long count() {
        return mongoTemplate.count(new Query(), FileMetadata.class, COLLECTION_NAME);
    }

    public boolean existsByFilenameAndOwnerId(String filename, String ownerId) {
        Query query = new Query(Criteria.where("filename").is(filename).and("ownerId").is(ownerId));
        return mongoTemplate.exists(query, FileMetadata.class, COLLECTION_NAME);
    }

    public boolean existsByMd5AndOwnerId(String md5, String ownerId) {
        Query query = new Query(Criteria.where("md5").is(md5).and("ownerId").is(ownerId));
        return mongoTemplate.exists(query, FileMetadata.class, COLLECTION_NAME);
    }
}
