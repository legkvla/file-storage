package lambdalabs.filestorage;

import lambdalabs.filestorage.controller.FileController;
import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.model.Visibility;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import lambdalabs.filestorage.service.GridFsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
    public void testUploadFile() throws Exception {
        // Mock data
        FileMetadata mockMetadata = new FileMetadata();
        mockMetadata.setId("test-id");
        mockMetadata.setFilename("test.txt");
        mockMetadata.setSize(100L);
        mockMetadata.setVisibility(Visibility.PUBLIC);

        when(fileMetadataRepository.save(any(FileMetadata.class))).thenReturn(mockMetadata);

        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.txt", 
                "text/plain", 
                "Hello World".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .param("visibility", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("test.txt"))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
    }

    @Test
    public void testGetFileMetadata() throws Exception {
        FileMetadata mockMetadata = new FileMetadata();
        mockMetadata.setId("test-id");
        mockMetadata.setFilename("test.txt");
        mockMetadata.setSize(100L);
        mockMetadata.setVisibility(Visibility.PUBLIC);

        when(fileMetadataRepository.findById("test-id")).thenReturn(Optional.of(mockMetadata));

        mockMvc.perform(get("/api/files/test-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id"))
                .andExpect(jsonPath("$.filename").value("test.txt"));
    }

    @Test
    public void testGetFileMetadataNotFound() throws Exception {
        when(fileMetadataRepository.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/files/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
