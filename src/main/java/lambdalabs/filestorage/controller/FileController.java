package lambdalabs.filestorage.controller;

import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.GridFsService;
import org.bson.types.ObjectId;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private GridFsService gridFsService;

    /**
     * Upload a file to GridFS and create metadata
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FileMetadata> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "tags", required = false) Set<String> tags) {
        
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Store file in GridFS
            ObjectId gridFsId = gridFsService.storeFile(file);

            // Create metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setFilename(file.getOriginalFilename());
            metadata.setSize(file.getSize());
            metadata.setVisibility(visibility);
            metadata.setTags(tags);
            metadata.setGridFsId(gridFsId);

            // Save metadata
            FileMetadata savedMetadata = fileMetadataRepository.save(metadata);

            return ResponseEntity.ok(savedMetadata);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download a file by metadata ID
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String id) {
        Optional<FileMetadata> metadataOpt = fileMetadataRepository.findById(id);
        
        if (metadataOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = metadataOpt.get();
        GridFsResource resource = gridFsService.getFile(metadata.getGridFsId());

        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(resource.getContentType()));
            headers.setContentDispositionFormData("attachment", metadata.getFilename());
            headers.setContentLength(resource.contentLength());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(resource.getInputStream()));
        } catch (IOException e) {
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
     * List all files with optional filtering
     */
    @GetMapping
    public List<FileMetadata> listFiles(
            @RequestParam(value = "visibility", required = false) Visibility visibility,
            @RequestParam(value = "tag", required = false) String tag) {
        
        List<FileMetadata> allFiles = fileMetadataRepository.findAll();
        
        if (visibility != null) {
            allFiles = allFiles.stream()
                    .filter(file -> file.getVisibility() == visibility)
                    .toList();
        }
        
        if (tag != null) {
            allFiles = allFiles.stream()
                    .filter(file -> file.getTags() != null && file.getTags().contains(tag))
                    .toList();
        }
        
        return allFiles;
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
