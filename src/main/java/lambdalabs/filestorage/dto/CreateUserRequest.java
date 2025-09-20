package lambdalabs.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateUserRequest {
    @NotBlank(message = "Identity is required")
    private String identity;

    public CreateUserRequest() {}

    public CreateUserRequest(String identity) {
        this.identity = identity;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
