package com.example.cloudssx.application.service;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;

public final class SharedMemoryInputBridge implements AutoCloseable {
    private static final int PACKET_SIZE = 16;
    private static final int BUFFER_COUNT = 1024;
    private static final int SYNC_SIZE = 24;
    private static final long WRITE_OFFSET = 0;
    private static final long READ_OFFSET = 8;
    private static final long NOTIFY_OFFSET = 16;
    private HANDLE syncHandle;
    private HANDLE packetHandle;
    private Pointer sync;
    private Pointer packets;
    private OutputStream controllerInput;

    public SharedMemoryInputBridge(String uri, OutputStream controllerInput) throws IOException {
        this.controllerInput = controllerInput;
        syncHandle = Kernel32.INSTANCE.CreateFileMapping(WinBase.INVALID_HANDLE_VALUE, null,
                WinNT.PAGE_READWRITE, 0, SYNC_SIZE, "Local\\" + uri);
        packetHandle = Kernel32.INSTANCE.CreateFileMapping(WinBase.INVALID_HANDLE_VALUE, null,
                WinNT.PAGE_READWRITE, 0, PACKET_SIZE * BUFFER_COUNT, "Local\\kbm" + uri);
        if (syncHandle == null || packetHandle == null) {
            close();
            throw new IOException("Could not create input shared memory: " + Kernel32.INSTANCE.GetLastError());
        }
        sync = Kernel32.INSTANCE.MapViewOfFile(syncHandle, WinNT.FILE_MAP_ALL_ACCESS, 0, 0, SYNC_SIZE);
        packets = Kernel32.INSTANCE.MapViewOfFile(packetHandle, WinNT.FILE_MAP_ALL_ACCESS, 0, 0,
                PACKET_SIZE * BUFFER_COUNT);
        if (sync == null || packets == null) {
            close();
            throw new IOException("Could not map input shared memory: " + Kernel32.INSTANCE.GetLastError());
        }
        sync.setLong(WRITE_OFFSET, 0);
        sync.setLong(READ_OFFSET, 0);
        sync.setLong(NOTIFY_OFFSET, 0);
        packets.clear(PACKET_SIZE * BUFFER_COUNT);
    }

    public synchronized void send(int first, int second, int keyCode, int kind) throws IOException {
        ensureOpen();
        long write = sync.getLong(WRITE_OFFSET);
        long read = sync.getLong(READ_OFFSET);
        if (Long.compareUnsigned(write - read, BUFFER_COUNT) >= 0) {
            throw new IOException("VM input buffer is full");
        }
        long offset = Long.remainderUnsigned(write, BUFFER_COUNT) * PACKET_SIZE;
        packets.setInt(offset, first);
        packets.setInt(offset + 4, second);
        packets.setInt(offset + 8, keyCode);
        packets.setInt(offset + 12, kind);
        sync.setLong(WRITE_OFFSET, write + 1);
        if (sync.getLong(NOTIFY_OFFSET) == 0) {
            sync.setLong(NOTIFY_OFFSET, 1);
            controllerInput.write('i');
            controllerInput.flush();
        }
    }

    public void sendMouseMove(int deltaX, int deltaY) throws IOException {
        send(deltaX, -deltaY, -1, 0);
    }

    public void sendMouseButton(int button, boolean pressed) throws IOException {
        send(button, pressed ? 1 : 0, -1, 1);
    }

    public void sendKey(int keyCode, boolean pressed) throws IOException {
        send(pressed ? 1 : 0, 0, keyCode, 2);
    }

    private void ensureOpen() throws IOException {
        if (sync == null || packets == null || controllerInput == null) {
            throw new IOException("VM input bridge is closed");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        if (controllerInput != null) {
            try {
                controllerInput.write('e');
                controllerInput.flush();
                controllerInput.close();
            } catch (IOException exception) {
                failure = exception;
            }
            controllerInput = null;
        }
        if (sync != null) {
            Kernel32.INSTANCE.UnmapViewOfFile(sync);
            sync = null;
        }
        if (packets != null) {
            Kernel32.INSTANCE.UnmapViewOfFile(packets);
            packets = null;
        }
        if (syncHandle != null) {
            Kernel32.INSTANCE.CloseHandle(syncHandle);
            syncHandle = null;
        }
        if (packetHandle != null) {
            Kernel32.INSTANCE.CloseHandle(packetHandle);
            packetHandle = null;
        }
        if (failure != null) {
            throw failure;
        }
    }
}
