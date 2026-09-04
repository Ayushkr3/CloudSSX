package com.example.cloudssx.application.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.cloudssx.application.model.VmRequests.BrowserInput;
import com.example.cloudssx.application.model.VmRequests.VmResponse;

@Service
public class VmService {
    private final Map<String, ManagedVm> vms = new ConcurrentHashMap<>();
    private final String controllerPath;
    private final String streamTemplate;

    public VmService(@Value("${cloudssx.vm-controller-path}") String controllerPath,
            @Value("${cloudssx.stream.whep-template}") String streamTemplate) {
        this.controllerPath = controllerPath;
        this.streamTemplate = streamTemplate;
    }

    public VmResponse create(String owner, String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("VM name must contain 1-64 letters, numbers, underscores, or hyphens");
        }
        String id = UUID.randomUUID().toString();
        String uri = "cloudssx-" + id;
        Path executable = Path.of(controllerPath).toAbsolutePath();
        Process process = new ProcessBuilder(executable.toString()).directory(executable.getParent().toFile()).start();
        SharedMemoryInputBridge bridge = null;
        try {
            bridge = new SharedMemoryInputBridge(uri, process.getOutputStream());
            process.getOutputStream().write((uri + " ").getBytes());
            process.getOutputStream().flush();
            String response = readHandshake(process, Duration.ofSeconds(10));
            if (!"Fine".equals(response)) {
                throw new IOException("VM controller rejected the IPC channel: " + response);
            }
            ManagedVm vm = new ManagedVm(id, owner, name, uri, process, bridge);
            vms.put(id, vm);
            return response(vm);
        } catch (Exception exception) {
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            process.destroyForcibly();
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not start VM controller", exception);
        }
    }

    public Collection<VmResponse> list(String owner) {
        return vms.values().stream().filter(vm -> vm.owner.equals(owner)).map(this::response).toList();
    }

    public VmResponse get(String owner, String id) {
        return response(owned(owner, id));
    }

    public void sendInput(String owner, String id, BrowserInput input) throws IOException {
        ManagedVm vm = owned(owner, id);
        if (!vm.process.isAlive()) {
            throw new IllegalStateException("VM controller is not running");
        }
        if (input == null || input.type() == null) {
            throw new IllegalArgumentException("Input type is required");
        }
        switch (input.type()) {
            case "move" -> vm.bridge.sendMouseMove(value(input.deltaX()), value(input.deltaY()));
            case "button" -> vm.bridge.sendMouseButton(value(input.button()), Boolean.TRUE.equals(input.pressed()));
            case "key" -> vm.bridge.sendKey(value(input.keyCode()), Boolean.TRUE.equals(input.pressed()));
            default -> throw new IllegalArgumentException("Unsupported input type");
        }
    }

    public void delete(String owner, String id) throws IOException {
        ManagedVm vm = owned(owner, id);
        vms.remove(id, vm);
        vm.bridge.close();
        vm.process.destroy();
        if (vm.process.isAlive()) {
            vm.process.destroyForcibly();
        }
    }

    @PreDestroy
    public void stopAll() {
        for (ManagedVm vm : vms.values()) {
            try {
                vm.bridge.close();
            } catch (IOException ignored) {
            }
            vm.process.destroyForcibly();
        }
        vms.clear();
    }

    private ManagedVm owned(String owner, String id) {
        ManagedVm vm = vms.get(id);
        if (vm == null || !vm.owner.equals(owner)) {
            throw new IllegalArgumentException("VM was not found");
        }
        return vm;
    }

    private VmResponse response(ManagedVm vm) {
        return new VmResponse(vm.id, vm.name, vm.process.isAlive() ? "RUNNING" : "STOPPED",
                streamTemplate.formatted(vm.uri));
    }

    private static int value(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("Input value is required");
        }
        return value;
    }

    private static String readHandshake(Process process, Duration timeout) throws IOException {
        BufferedInputStream input = new BufferedInputStream(process.getInputStream());
        long deadline = System.nanoTime() + timeout.toNanos();
        StringBuilder response = new StringBuilder();
        while (System.nanoTime() < deadline && response.length() < 5) {
            if (input.available() > 0) {
                response.append((char) input.read());
                if ("Fine".contentEquals(response) || "NFine".contentEquals(response)) {
                    return response.toString();
                }
            } else if (!process.isAlive()) {
                break;
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for VM controller", exception);
                }
            }
        }
        throw new IOException("Timed out waiting for VM controller handshake");
    }

    private static final class ManagedVm {
        private final String id;
        private final String owner;
        private final String name;
        private final String uri;
        private final Process process;
        private final SharedMemoryInputBridge bridge;

        private ManagedVm(String id, String owner, String name, String uri, Process process, SharedMemoryInputBridge bridge) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.uri = uri;
            this.process = process;
            this.bridge = bridge;
        }
    }
}
