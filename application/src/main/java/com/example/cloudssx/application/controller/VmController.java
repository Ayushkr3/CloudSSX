package com.example.cloudssx.application.controller;

import java.io.IOException;
import java.util.Collection;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.cloudssx.application.model.VmRequests.CreateVm;
import com.example.cloudssx.application.model.VmRequests.VmResponse;
import com.example.cloudssx.application.service.SessionService;
import com.example.cloudssx.application.service.VmService;

@RestController
@RequestMapping("/api/vms")
public class VmController {
    private final VmService vms;
    private final SessionService sessions;

    public VmController(VmService vms, SessionService sessions) {
        this.vms = vms;
        this.sessions = sessions;
    }

    @GetMapping
    public Collection<VmResponse> list(HttpServletRequest request) {
        return vms.list(user(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VmResponse create(HttpServletRequest request, @RequestBody CreateVm body) throws IOException {
        return vms.create(user(request), body.name());
    }

    @GetMapping("/{id}")
    public VmResponse get(HttpServletRequest request, @PathVariable String id) {
        return vms.get(user(request), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable String id) throws IOException {
        vms.delete(user(request), id);
    }

    private String user(HttpServletRequest request) {
        return sessions.userFor(AuthController.token(request));
    }
}
