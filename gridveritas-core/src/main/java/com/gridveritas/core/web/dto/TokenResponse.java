package com.gridveritas.core.web.dto;

public class TokenResponse {

    private String token;
    private String tokenType = "Bearer";
    private String role;
    private long expiresInSeconds;

    public TokenResponse() {
    }

    public TokenResponse(String token, String role, long expiresInSeconds) {
        this.token = token;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
