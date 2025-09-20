package lambdalabs.filestorage.controller;

import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.GridFsService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private GridFsService gridFsService;

    /**
     * Upload a file using raw InputStream (for very large files or custom clients)
     */
    @PostMapping("/upload-stream")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FileMetadata> uploadFileStream(
            @RequestParam("filename") String filename,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            InputStream fileStream) {
        
        logger.info("File upload request: filename={}, contentType={}, visibility={}, tags={}", 
                   filename, contentType, visibility, tags);
        
        try {
            // Store file in GridFS using streaming
            ObjectId gridFsId = gridFsService.storeFileStreaming(fileStream, filename, contentType);

            // Create metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setFilename(filename);
            metadata.setVisibility(visibility);
            metadata.setTags(tags);
            metadata.setGridFsId(gridFsId);

            // Save metadata
            FileMetadata savedMetadata = fileMetadataRepository.save(metadata);
            
            logger.info("File uploaded successfully: filename={}, metadataId={}, gridFsId={}", 
                       filename, savedMetadata.getId(), gridFsId);

            return ResponseEntity.ok(savedMetadata);
        } catch (IOException e) {
            logger.error("File upload failed: filename={}, contentType={}", filename, contentType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download a file by metadata ID
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String id) {
        logger.info("File download request: metadataId={}", id);
        
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findById(id);
        
        if (metadataOpt.isEmpty()) {
            logger.warn("File metadata not found: metadataId={}", id);
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = metadataOpt.get();
        logger.debug("Found metadata: filename={}, gridFsId={}", metadata.getFilename(), metadata.getGridFsId());
        
        GridFsResource resource = gridFsService.getFile(metadata.getGridFsId());

        if (resource == null) {
            logger.warn("File not found in GridFS: metadataId={}, gridFsId={}", id, metadata.getGridFsId());
            return ResponseEntity.notFound().build();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(resource.getContentType()));
            headers.setContentDispositionFormData("attachment", metadata.getFilename());
            headers.setContentLength(resource.contentLength());

            logger.info("File download successful: filename={}, contentType={}, size={}", 
                       metadata.getFilename(), resource.getContentType(), resource.contentLength());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(resource.getInputStream()));
        } catch (IOException e) {
            logger.error("File download failed: metadataId={}, filename={}", id, metadata.getFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get file metadata by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable String id) {
        Optional<FileMetadata> metadata = fileMetadataRepository.findById(id);
        return metadata.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all files with optional filtering (optimized with MongoDB queries)
     */
    @GetMapping
    public List<FileMetadata> listFiles(
            @RequestParam(value = "visibility", required = false) Visibility visibility,
            @RequestParam(value = "tag", required = false) String tag) {
        
        // Use optimized MongoDB queries instead of Java filtering
        if (visibility != null && tag != null) {
            return fileMetadataRepository.findByVisibilityAndTagContainingIgnoreCase(visibility, tag);
        } else if (visibility != null) {
            return fileMetadataRepository.findByVisibility(visibility);
        } else if (tag != null) {
            return fileMetadataRepository.findByTagContainingIgnoreCase(tag);
        } else {
            return fileMetadataRepository.findAll();
        }
    }

    /**
     * Delete a file and its metadata
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable String id) {
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findById(id);
        
        if (metadataOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = metadataOpt.get();
        
        try {
            // Delete from GridFS
            gridFsService.deleteFile(metadata.getGridFsId());
            
            // Delete metadata
            fileMetadataRepository.deleteById(id);
            
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update file metadata
     */
    @PutMapping("/{id}")
    public ResponseEntity<FileMetadata> updateFileMetadata(
            @PathVariable String id,
            @RequestBody FileMetadata updatedMetadata) {
        
        Optional<FileMetadata> existingOpt = fileMetadataRepository.findById(id);
        
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileMetadata existing = existingOpt.get();
        
        // Update only allowed fields
        if (updatedMetadata.getVisibility() != null) {
            existing.setVisibility(updatedMetadata.getVisibility());
        }
        if (updatedMetadata.getTags() != null) {
            existing.setTags(updatedMetadata.getTags());
        }

        FileMetadata saved = fileMetadataRepository.save(existing);
        return ResponseEntity.ok(saved);
    }
}
