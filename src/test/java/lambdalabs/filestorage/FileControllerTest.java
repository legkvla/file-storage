package lambdalabs.filestorage;

import lambdalabs.filestorage.controller.FileController;
import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.GridFsService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileMetadataRepository fileMetadataRepository;

    @MockBean
    private GridFsService gridFsService;

    @Test
    public void testUploadFileStream() throws Exception {
        // Mock data
        FileMetadata mockMetadata = new FileMetadata();
        mockMetadata.setId("test-id");
        mockMetadata.setFilename("test.txt");
        mockMetadata.setVisibility(Visibility.PUBLIC);
        mockMetadata.setOwnerId("test-user-id");
        mockMetadata.setMd5("d41d8cd98f00b204e9800998ecf8427e");

        ObjectId mockObjectId = new ObjectId();

        when(fileMetadataRepository.existsByFilenameAndOwnerId("test.txt", "test-user-id")).thenReturn(false);
        when(fileMetadataRepository.existsByMd5AndOwnerId("d41d8cd98f00b204e9800998ecf8427e", "test-user-id")).thenReturn(false);
        when(gridFsService.storeFileStreaming(any(), any(), any())).thenReturn(mockObjectId);
        when(gridFsService.calculateMD5FromGridFS(mockObjectId)).thenReturn("d41d8cd98f00b204e9800998ecf8427e");
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenReturn(mockMetadata);

        mockMvc.perform(post("/api/files/upload")
                .header("User-Id", "test-user-id")
                .param("filename", "test.txt")
                .param("contentType", "text/plain")
                .param("visibility", "PUBLIC")
                .content("Hello World"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("test.txt"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.md5").value("d41d8cd98f00b204e9800998ecf8427e"));
    }

    @Test
    public void testUploadFileStreamDuplicateFilename() throws Exception {
        when(fileMetadataRepository.existsByFilenameAndOwnerId("test.txt", "test-user-id")).thenReturn(true);

        mockMvc.perform(post("/api/files/upload")
                .header("User-Id", "test-user-id")
                .param("filename", "test.txt")
                .param("contentType", "text/plain")
                .param("visibility", "PUBLIC")
                .content("Hello World"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Filename already exists"))
                .andExpect(jsonPath("$.message").value("A file with this filename already exists for your account"));
    }

    @Test
    public void testUploadFileStreamDuplicateMd5() throws Exception {
        ObjectId mockObjectId = new ObjectId();

        when(fileMetadataRepository.existsByFilenameAndOwnerId("test.txt", "test-user-id")).thenReturn(false);
        when(fileMetadataRepository.existsByMd5AndOwnerId("d41d8cd98f00b204e9800998ecf8427e", "test-user-id")).thenReturn(true);
        when(gridFsService.storeFileStreaming(any(), any(), any())).thenReturn(mockObjectId);
        when(gridFsService.calculateMD5FromGridFS(mockObjectId)).thenReturn("d41d8cd98f00b204e9800998ecf8427e");

        mockMvc.perform(post("/api/files/upload")
                .header("User-Id", "test-user-id")
                .param("filename", "test.txt")
                .param("contentType", "text/plain")
                .param("visibility", "PUBLIC")
                .content("Hello World"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("File already exists"))
                .andExpect(jsonPath("$.message").value("A file with the same content already exists in your account"));
    }

    @Test
    public void testGetFileMetadata() throws Exception {
        FileMetadata mockMetadata = new FileMetadata();
        mockMetadata.setId("test-id");
        mockMetadata.setFilename("test.txt");
        mockMetadata.setVisibility(Visibility.PUBLIC);
        mockMetadata.setOwnerId("test-user-id");

        when(fileMetadataRepository.findByIdVisibleToUser("test-id", "test-user-id")).thenReturn(Optional.of(mockMetadata));

        mockMvc.perform(get("/api/files/test-id")
                .header("User-Id", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.filename").value("test.txt"));
    }

    @Test
    public void testGetFileMetadataNotFound() throws Exception {
        when(fileMetadataRepository.findByIdVisibleToUser("nonexistent", "test-user-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/files/nonexistent")
                .header("User-Id", "test-user-id"))
                .andExpect(status().isNotFound());
    }
}
