package com.example.cloudssx.application.model;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record Credentials(String username, String password) {
    }

    public record UserResponse(String username) {
    }
}
