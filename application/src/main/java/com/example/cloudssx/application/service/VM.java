package com.example.cloudssx.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
public class VM {
    Process process;
    InputStream is;
    OutputStream os;
    public void CreateNewVM(String uri) throws IOException{
        try {
            
            process = new ProcessBuilder("D:\\program\\vs\\CloudSSXvm\\x64\\Debug\\VMController.exe").start();   
            is = process.getInputStream();

            byte[] inp = new byte[1024];

            int n = is.read(inp);

            if (n > 0) {
                String s = new String(inp, 0, n);
                if (!s.equals("Fine")) {
                    process = null;
                    is = null;
                    os = null;
                }
            }
            else{
                return;
            }
            os = process.getOutputStream();
            os.write((uri+" ").getBytes());
            os.flush();
        } catch (Exception e) {
            process = null;
            is = null;
            os = null;
        }
    }
    public void KillVm(){
        if(process!=null){
            process.destroy();
        }
    }
    public Process getProcess() {
        return process;
    }
}
