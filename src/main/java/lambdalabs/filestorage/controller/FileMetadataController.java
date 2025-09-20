package lambdalabs.filestorage.controller;

import lambdalabs.filestorage.model.FileMetadata;
import lambdalabs.filestorage.repository.FileMetadataRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
public class FileMetadataController {

    private final FileMetadataRepository repository;

    public FileMetadataController(FileMetadataRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<FileMetadata> list() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FileMetadata create(@Valid @RequestBody FileMetadata fileMetadata) {
        return repository.save(fileMetadata);
    }
}
