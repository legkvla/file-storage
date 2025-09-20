package lambdalabs.filestorage.repository;

import lambdalabs.filestorage.model.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
}
