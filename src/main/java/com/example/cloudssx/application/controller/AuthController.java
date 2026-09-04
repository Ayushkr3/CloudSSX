package com.example.cloudssx.application.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cloudssx.application.model.AuthRequests.Credentials;
import com.example.cloudssx.application.model.AuthRequests.UserResponse;
import com.example.cloudssx.application.service.SessionService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String SESSION_COOKIE = "CLOUDSSX_SESSION";
    private final SessionService sessions;

    public AuthController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/sign-up")
    public UserResponse signUp(@RequestBody Credentials request) {
        sessions.register(request.username(), request.password());
        return new UserResponse(request.username());
    }

    @PostMapping("/sign-in")
    public org.springframework.http.ResponseEntity<UserResponse> signIn(@RequestBody Credentials request) {
        String token = sessions.signIn(request.username(), request.password());
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, token).httpOnly(true).sameSite("Strict")
                .path("/").build();
        return org.springframework.http.ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new UserResponse(request.username()));
    }

    @PostMapping("/sign-out")
    public org.springframework.http.ResponseEntity<Void> signOut(HttpServletRequest request) {
        sessions.signOut(token(request));
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, "").httpOnly(true).path("/").maxAge(0).build();
        return org.springframework.http.ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        return new UserResponse(sessions.userFor(token(request)));
    }

    public static String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
