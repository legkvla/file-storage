package lambdalabs.filestorage.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Document(collection = "file_metadata")
public class FileMetadata {
    @Id
    private String id;

    @NotBlank
    @Indexed
    private String filename;

    @NotNull
    @Min(0)
    private Long size;

    @NotNull
    private Visibility visibility;

    @Indexed
    private Set<String> tags;

    private ObjectId gridFsId;

    public FileMetadata() {}

    public FileMetadata(String filename, Long size, Visibility visibility, Set<String> tags) {
        this.filename = filename;
        this.size = size;
        this.visibility = visibility;
        this.tags = tags;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public ObjectId getGridFsId() { return gridFsId; }
    public void setGridFsId(ObjectId gridFsId) { this.gridFsId = gridFsId; }
}
