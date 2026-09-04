package com.example.cloudssx.application.model;

public final class VmRequests {
    private VmRequests() {
    }

    public record CreateVm(String name) {
    }

    public record VmResponse(String id, String name, String status, String streamUrl) {
    }

    public record BrowserInput(String type, Integer deltaX, Integer deltaY, Integer button, Boolean pressed, Integer keyCode) {
    }
}
