package lambdalabs.filestorage;

import lambdalabs.filestorage.config.TestSecurityConfig;
import lambdalabs.filestorage.controller.FileController;
import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.CurrentUserService;
import lambdalabs.filestorage.service.GridFsService;
import lambdalabs.filestorage.service.JwtService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import(TestSecurityConfig.class)
public class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileMetadataRepository fileMetadataRepository;

    @MockBean
    private GridFsService gridFsService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtService jwtService;

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
        when(gridFsService.storeFileStreaming(any(), any(), any())).thenReturn(mockObjectId);
        when(gridFsService.calculateMD5FromGridFS(mockObjectId)).thenReturn("d41d8cd98f00b204e9800998ecf8427e");
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenReturn(mockMetadata);
        when(currentUserService.getCurrentUserId()).thenReturn(java.util.Optional.of("test-user-id"));
        when(currentUserService.getCurrentUserIdentity()).thenReturn(java.util.Optional.of("test-user"));

        mockMvc.perform(post("/api/files/upload")
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
    public void testGetFileMetadata() throws Exception {
        FileMetadata mockMetadata = new FileMetadata();
        mockMetadata.setId("test-id");
        mockMetadata.setFilename("test.txt");
        mockMetadata.setVisibility(Visibility.PUBLIC);
        mockMetadata.setOwnerId("test-user-id");

        when(fileMetadataRepository.findByIdVisibleToUser("test-id", "test-user-id")).thenReturn(Optional.of(mockMetadata));
        when(currentUserService.getCurrentUserId()).thenReturn(java.util.Optional.of("test-user-id"));

        mockMvc.perform(get("/api/files/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.filename").value("test.txt"));
    }

    @Test
    public void testGetFileMetadataNotFound() throws Exception {
        when(fileMetadataRepository.findByIdVisibleToUser("nonexistent", "test-user-id")).thenReturn(Optional.empty());
        when(currentUserService.getCurrentUserId()).thenReturn(java.util.Optional.of("test-user-id"));

        mockMvc.perform(get("/api/files/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
