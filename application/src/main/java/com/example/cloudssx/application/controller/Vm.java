package com.example.cloudssx.application.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;



@RestController
public class Vm {
    @PostMapping("/Vm")
    public String CreateVMSession(@RequestBody String entity) {
        
        
        return entity;
    }
    
    @PostMapping("/heartbeat")
    public ResponseEntity<String> HeartBeat(Authentication auth) {
        
        return ResponseEntity.ok("Success");
    }
    
}
