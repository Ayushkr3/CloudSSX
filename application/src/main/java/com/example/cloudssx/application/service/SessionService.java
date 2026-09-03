package com.example.cloudssx.application.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final Map<String, String> passwordHashes = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public synchronized void register(String username, String password) {
        validate(username, password);
        if (passwordHashes.containsKey(username)) {
            throw new IllegalArgumentException("Username is already registered");
        }
        passwordHashes.put(username, encoder.encode(password));
    }

    public String signIn(String username, String password) {
        String hash = passwordHashes.get(username);
        if (hash == null || !encoder.matches(password, hash)) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, username);
        return token;
    }

    public String userFor(String token) {
        String username = token == null ? null : sessions.get(token);
        if (username == null) {
            throw new SecurityException("Authentication is required");
        }
        return username;
    }

    public void signOut(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private void validate(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_-]{3,64}")) {
            throw new IllegalArgumentException("Username must contain 3-64 letters, numbers, underscores, or hyphens");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }
}
