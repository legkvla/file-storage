package lambdalabs.filestorage.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Request to update file metadata")
public class UpdateFileRequest {
    
    @NotBlank(message = "Filename cannot be blank")
    @Schema(description = "New filename for the file", example = "updated-document.pdf")
    private String filename;
    
    @Schema(description = "Set of tags for the file", example = "[\"important\", \"work\", \"draft\"]")
    private Set<String> tags;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
}
