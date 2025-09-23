package lambdalabs.filestorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lambdalabs.filestorage.dto.UpdateFileRequest;
import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.SortBy;
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
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/api/files")
@Tag(name = "File Management", description = "File upload, download, and management endpoints")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private GridFsService gridFsService;


    // Since we use User-Id passing auth approach - we expect sticky sessions,
    // So thats why we implemented locks on java level.
    // For other architecture - when we expect multiple nodes working with one user request - it will make sense
    // to implement normal mongo locks using findAndModify
    final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    @Operation(summary = "Upload file", description = "Upload a file using raw InputStream")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "File uploaded successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileMetadata.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing User-Id header"),
            @ApiResponse(responseCode = "409", description = "Conflict - File with this filename or content already exists for the user")
    })
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> uploadFileStream(
            @RequestHeader("User-Id") String userId,
            @RequestParam("filename") String filename,
            //we have contentType explicitly here because we would like user to specify it and 
            // it is not convenient with header approach because we want to distinguish
            // when user specified contentType or not
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            InputStream fileStream) {

        synchronized (locks.computeIfAbsent(userId, u -> new Object())) {
            if (fileMetadataRepository.existsByFilenameAndOwnerId(filename, userId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Filename already exists");
                error.put("message", "A file with this filename already exists for your account");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }

            try {
                // Derive contentType from filename if not provided
                String effectiveContentType = contentType;
                if (effectiveContentType == null || effectiveContentType.isBlank()) {
                    effectiveContentType = MediaTypeFactory.getMediaType(filename)
                            .map(MediaType::toString)
                            .orElse("application/octet-stream");
                }

                ObjectId gridFsId = gridFsService.storeFileStreaming(fileStream, filename, effectiveContentType);

                String md5Hash = gridFsService.calculateMD5FromGridFS(gridFsId);

                if (fileMetadataRepository.existsByMd5AndOwnerId(md5Hash, userId)) {
                    // Clean up the stored file since we're rejecting the upload
                    gridFsService.deleteFile(gridFsId);

                    Map<String, String> error = new HashMap<>();
                    error.put("error", "File already exists");
                    error.put("message", "A file with the same content already exists in your account");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
                }

                GridFsResource gridResource = gridFsService.getResource(gridFsId);

                FileMetadata metadata = new FileMetadata();
                metadata.setFilename(filename);
                metadata.setVisibility(visibility);
                metadata.setTags(tags);
                metadata.setOwnerId(userId);
                metadata.setGridFsId(gridFsId);
                metadata.setSize(gridResource.getGridFSFile().getLength());
                metadata.setMd5(md5Hash);
                metadata.setContentType(effectiveContentType);

                FileMetadata savedMetadata = fileMetadataRepository.save(metadata);

                return ResponseEntity.ok(savedMetadata);
            } catch (IOException e) {
                logger.error("File upload failed: filename={}, contentType={}", filename, contentType, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @RequestHeader("User-Id") String userId,
            @PathVariable String id) {
        logger.info("File download request: metadataId={}, userId={}", id, userId);

        // Find file with ownership check
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findByIdVisibleToUser(id, userId);

        if (metadataOpt.isEmpty()) {
            logger.warn("File metadata not found or access denied: metadataId={}, userId={}", id, userId);
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = metadataOpt.get();
        logger.debug("Found metadata: filename={}, gridFsId={}", metadata.getFilename(), metadata.getGridFsId());

        GridFsResource resource = gridFsService.getResource(metadata.getGridFsId());

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

    @GetMapping("/{id}")
    public ResponseEntity<FileMetadata> getFileMetadata(
            @RequestHeader("User-Id") String userId,
            @PathVariable String id) {
        // Find file with ownership check
        Optional<FileMetadata> metadata = fileMetadataRepository.findByIdVisibleToUser(id, userId);
        return metadata.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List files", description = "List files visible to the current user with optional filtering, pagination, and sorting")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Files retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing User-Id header")
    })
    @GetMapping
    public List<FileMetadata> listFiles(
            @RequestHeader("User-Id") String userId,
            @RequestParam(value = "visibility", required = false) Visibility visibility,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "sort", defaultValue = "ID") SortBy sortBy,
            @RequestParam(value = "desc", defaultValue = "false") boolean desc) {

        // Validate pagination parameters
        if (skip < 0) {
            skip = 0;
        }
        if (limit <= 0 || limit > 1000) {
            limit = 50; // Default limit with max cap
        }

        String sortField = sortBy.name().toLowerCase();

        if (visibility != null && tag != null) {
            return fileMetadataRepository.findByVisibilityAndTagVisibleToUser(visibility, tag, userId, skip, limit, sortField, desc);
        } else if (visibility != null) {
            return fileMetadataRepository.findByVisibilityVisibleToUser(visibility, userId, skip, limit, sortField, desc);
        } else if (tag != null) {
            return fileMetadataRepository.findByTagVisibleToUser(tag, userId, skip, limit, sortField, desc);
        } else {
            return fileMetadataRepository.findAllVisibleToUser(userId, skip, limit, sortField, desc);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @RequestHeader("User-Id") String userId,
            @PathVariable String id) {
        // Find file with ownership check
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findByIdVisibleToUser(id, userId);

        if (metadataOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = metadataOpt.get();

        if (!userId.equals(metadata.getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // Delete from GridFS
            gridFsService.deleteFile(metadata.getGridFsId());

            // Delete metadata (with ownership check)
            boolean deleted = fileMetadataRepository.deleteByIdAndOwner(id, userId);

            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                logger.error("Failed to delete file metadata: metadataId={}, userId={}", id, userId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Error deleting file: metadataId={}, userId={}", id, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update file metadata (only by owner) - PATCH allows partial updates
     */
    @Operation(summary = "Update file metadata", description = "Update filename and tags for a file (only by owner)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File metadata updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileMetadata.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing User-Id header"),
            @ApiResponse(responseCode = "403", description = "Forbidden - User does not own the file"),
            @ApiResponse(responseCode = "404", description = "File not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - File with this filename already exists for the user")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateFileMetadata(
            @RequestHeader("User-Id") String userId,
            @PathVariable String id,
            @Valid @RequestBody UpdateFileRequest updateRequest) {

        // Find file with ownership check
        Optional<FileMetadata> existingOpt = fileMetadataRepository.findByIdVisibleToUser(id, userId);

        if (existingOpt.isEmpty()) {
            logger.warn("File not found or access denied for update: metadataId={}, userId={}", id, userId);
            return ResponseEntity.notFound().build();
        }

        FileMetadata existing = existingOpt.get();

        if (!userId.equals(existing.getOwnerId())) {
            logger.warn("User attempted to update file they don't own: metadataId={}, userId={}, ownerId={}",
                    id, userId, existing.getOwnerId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Check if new filename already exists for this user (if filename is being updated)
        if (updateRequest.getFilename() != null && !updateRequest.getFilename().equals(existing.getFilename())) {
            if (fileMetadataRepository.existsByFilenameAndOwnerId(updateRequest.getFilename(), userId)) {
                logger.warn("File update failed - filename already exists: filename={}, userId={}", updateRequest.getFilename(), userId);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Filename already exists");
                error.put("message", "A file with this filename already exists for your account");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
            existing.setFilename(updateRequest.getFilename());
        }
        if (updateRequest.getTags() != null) {
            existing.setTags(updateRequest.getTags());
        }

        FileMetadata saved = fileMetadataRepository.save(existing);
        logger.info("File metadata updated: metadataId={}, filename={}, userId={}",
                id, saved.getFilename(), userId);
        return ResponseEntity.ok(saved);
    }

}
