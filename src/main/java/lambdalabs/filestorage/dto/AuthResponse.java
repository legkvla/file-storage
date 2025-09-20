package lambdalabs.filestorage.dto;

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private String userId;
    private String userIdentity;

    public AuthResponse() {}

    public AuthResponse(String token, long expiresIn, String userId, String userIdentity) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.userIdentity = userIdentity;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(String userIdentity) {
        this.userIdentity = userIdentity;
    }
}
