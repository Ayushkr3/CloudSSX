package com.example.cloudssx.application;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class MediaServer {
    private Process media_process = null;
    @PostConstruct
    public void start() throws IOException{
            media_process = new ProcessBuilder("bins\\mediamtx\\mediamtx.exe","bins\\mediamtx\\mediamtx.yml").start();
    }
    @PreDestroy
    public void end() throws IOException{
        if(media_process!=null){
            media_process.destroy();
        }
    }
}
