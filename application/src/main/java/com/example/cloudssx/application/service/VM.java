package com.example.cloudssx.application.service;

import java.io.IOException;
public class VM {
    Process process;
    public void CreateNewVM() throws IOException{
        try {
            process = new ProcessBuilder("VM.exe").start();   
        } finally {
            process = null;
        }
    }
    public Process getProcess() {
        return process;
    }
}
