package lambdalabs.filestorage.controller;

import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.GridFsService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@RestController
@RequestMapping("/api/large-files")
public class LargeFileUploadController {

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private GridFsService gridFsService;

    /**
     * Upload large files using streaming approach
     * This endpoint is optimized for large files and streams data directly to GridFS
     */
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FileMetadata> uploadLargeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "tags", required = false) Set<String> tags) {
        
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Log file info for debugging
            System.out.println("Uploading file: " + file.getOriginalFilename() + 
                             ", Size: " + file.getSize() + " bytes");

            // Store file in GridFS using streaming
            // MultipartFile.getInputStream() provides streaming access
            ObjectId gridFsId = gridFsService.storeFileStreaming(file);

            // Create metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setFilename(file.getOriginalFilename());
            metadata.setSize(file.getSize());
            metadata.setVisibility(visibility);
            metadata.setTags(tags);
            metadata.setGridFsId(gridFsId);

            // Save metadata
            FileMetadata savedMetadata = fileMetadataRepository.save(metadata);

            System.out.println("File uploaded successfully with ID: " + savedMetadata.getId());
            return ResponseEntity.ok(savedMetadata);
        } catch (IOException e) {
            System.err.println("Error uploading file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload file using raw InputStream (for custom clients that can stream)
     * This is the most memory-efficient approach for very large files
     */
    @PostMapping("/upload-stream")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<FileMetadata> uploadFileStream(
            @RequestParam("filename") String filename,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") Visibility visibility,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            HttpServletRequest request) {
        
        try {
            // Get the raw InputStream from the request
            InputStream fileStream = request.getInputStream();
            
            System.out.println("Streaming upload for file: " + filename);

            // Store file in GridFS using streaming
            ObjectId gridFsId = gridFsService.storeFileStreaming(fileStream, filename, contentType);

            // Create metadata (we don't know the size for streaming uploads)
            FileMetadata metadata = new FileMetadata();
            metadata.setFilename(filename);
            metadata.setVisibility(visibility);
            metadata.setTags(tags);
            metadata.setGridFsId(gridFsId);

            // Save metadata
            FileMetadata savedMetadata = fileMetadataRepository.save(metadata);

            System.out.println("Streaming upload completed with ID: " + savedMetadata.getId());
            return ResponseEntity.ok(savedMetadata);
        } catch (IOException e) {
            System.err.println("Error in streaming upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get upload status and configuration info
     */
    @GetMapping("/config")
    public ResponseEntity<UploadConfig> getUploadConfig() {
        UploadConfig config = new UploadConfig();
        config.setMaxFileSize("1GB");
        config.setSupportedMethods(new String[]{"multipart/form-data", "raw-stream"});
        config.setChunkSize("256KB");
        return ResponseEntity.ok(config);
    }

    /**
     * Configuration class for upload settings
     */
    public static class UploadConfig {
        private String maxFileSize;
        private String[] supportedMethods;
        private String chunkSize;

        public String getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(String maxFileSize) { this.maxFileSize = maxFileSize; }

        public String[] getSupportedMethods() { return supportedMethods; }
        public void setSupportedMethods(String[] supportedMethods) { this.supportedMethods = supportedMethods; }

        public String getChunkSize() { return chunkSize; }
        public void setChunkSize(String chunkSize) { this.chunkSize = chunkSize; }
    }
}
