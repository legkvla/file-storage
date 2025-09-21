package lambdalabs.filestorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lambdalabs.filestorage.dto.UpdateFileRequest;
import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.SortBy;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.CurrentUserService;
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
import jakarta.validation.Valid;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
@Tag(name = "File Management", description = "File upload, download, and management endpoints")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private GridFsService gridFsService;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * Upload a file using raw InputStream
     */
    @Operation(summary = "Upload file", description = "Upload a file using raw InputStream")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "File uploaded successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FileMetadata.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "409", description = "Conflict - File with this filename already exists for the user")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> uploadFileStream(
            @RequestParam("filename") String filename,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            InputStream fileStream) {
        
        // Get current user ID
        String currentUserId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        String currentUserIdentity = currentUserService.getCurrentUserIdentity()
                .orElse("unknown");
        
        logger.info("File upload request: filename={}, contentType={}, visibility={}, tags={}, user={}, userId={}", 
                   filename, contentType, visibility, tags, currentUserIdentity, currentUserId);
        
        // Check if filename already exists for this user
        if (fileMetadataRepository.existsByFilenameAndOwnerId(filename, currentUserId)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Filename already exists");
            error.put("message", "A file with this filename already exists for your account");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        
        try {
            // Store file in GridFS using streaming
            ObjectId gridFsId = gridFsService.storeFileStreaming(fileStream, filename, contentType);

            // Create metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setFilename(filename);
            metadata.setVisibility(visibility);
            metadata.setTags(tags);
            metadata.setOwnerId(currentUserId);
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

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String id) {
        logger.info("File download request: metadataId={}", id);
        
        // Get current user ID
        String currentUserId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        // Find file with ownership check
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findByIdVisibleToUser(id, currentUserId);
        
        if (metadataOpt.isEmpty()) {
            logger.warn("File metadata not found or access denied: metadataId={}, userId={}", id, currentUserId);
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

    @GetMapping("/{id}")
    public ResponseEntity<FileMetadata> getFileMetadata(@PathVariable String id) {
        // Get current user ID
        String currentUserId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        // Find file with ownership check
        Optional<FileMetadata> metadata = fileMetadataRepository.findByIdVisibleToUser(id, currentUserId);
        return metadata.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List files", description = "List files visible to the current user with optional filtering, pagination, and sorting")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Files retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    public List<FileMetadata> listFiles(
            @RequestParam(value = "visibility", required = false) Visibility visibility,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "sort", defaultValue = "ID") SortBy sortBy,
            @RequestParam(value = "desc", defaultValue = "false") boolean desc) {

        String currentUserId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new SecurityException("User not authenticated"));

        // Validate pagination parameters
        if (skip < 0) {
            skip = 0;
        }
        if (limit <= 0 || limit > 1000) {
            limit = 50; // Default limit with max cap
        }

        // Convert enum to string for repository
        String sortField = sortBy.name().toLowerCase();

        if (visibility != null && tag != null) {
            return fileMetadataRepository.findByVisibilityAndTagVisibleToUser(visibility, tag, currentUserId, skip, limit, sortField, desc);
        } else if (visibility != null) {
            return fileMetadataRepository.findByVisibilityVisibleToUser(visibility, currentUserId, skip, limit, sortField, desc);
        } else if (tag != null) {
            return fileMetadataRepository.findByTagVisibleToUser(tag, currentUserId, skip, limit, sortField, desc);
        } else {
            return fileMetadataRepository.findAllVisibleToUser(currentUserId, skip, limit, sortField, desc);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable String id) {
        // Get current user ID
        String currentUserId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        // Find file with ownership check
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findByIdVisibleToUser(id, currentUserId);
        
        if (metadataOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = metadataOpt.get();

        if (!currentUserId.equals(metadata.getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        try {
            // Delete from GridFS
            gridFsService.deleteFile(metadata.getGridFsId());
            
            // Delete metadata (with ownership check)
            boolean deleted = fileMetadataRepository.deleteByIdAndOwner(id, currentUserId);
            
            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                logger.error("Failed to delete file metadata: metadataId={}, userId={}", id, currentUserId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Error deleting file: metadataId={}, userId={}", id, currentUserId, e);
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
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not own the file"),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "409", description = "Conflict - File with this filename already exists for the user")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateFileMetadata(
            @PathVariable String id,
            @Valid @RequestBody UpdateFileRequest updateRequest) {
        
        // Get current user ID
        String currentUserId = currentUserService.getCurrentUserId()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
        
        // Find file with ownership check
        Optional<FileMetadata> existingOpt = fileMetadataRepository.findByIdVisibleToUser(id, currentUserId);
        
        if (existingOpt.isEmpty()) {
            logger.warn("File not found or access denied for update: metadataId={}, userId={}", id, currentUserId);
            return ResponseEntity.notFound().build();
        }

        FileMetadata existing = existingOpt.get();

        if (!currentUserId.equals(existing.getOwnerId())) {
            logger.warn("User attempted to update file they don't own: metadataId={}, userId={}, ownerId={}", 
                       id, currentUserId, existing.getOwnerId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Check if new filename already exists for this user (if filename is being updated)
        if (updateRequest.getFilename() != null && !updateRequest.getFilename().equals(existing.getFilename())) {
            if (fileMetadataRepository.existsByFilenameAndOwnerId(updateRequest.getFilename(), currentUserId)) {
                logger.warn("File update failed - filename already exists: filename={}, userId={}", updateRequest.getFilename(), currentUserId);
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
                   id, saved.getFilename(), currentUserId);
        return ResponseEntity.ok(saved);
    }

    @Operation(summary = "Get current user info", description = "Get information about the currently authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/current-user")
    public ResponseEntity<?> getCurrentUserInfo() {

        String userIdentity = currentUserService.getCurrentUserIdentity()
                .orElse("unknown");

        String userId = currentUserService.getCurrentUserId()
                .orElse("unknown");

        Map<String, Object> response = new HashMap<>();
        response.put("identity", userIdentity);
        response.put("userId", userId);
        
        return ResponseEntity.ok(response);
    }
}
