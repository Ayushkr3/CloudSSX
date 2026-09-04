package com.example.cloudssx.application.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.cloudssx.application.model.VmRequests.BrowserInput;
import com.example.cloudssx.application.service.SessionService;
import com.example.cloudssx.application.service.VmService;

@Component
public class VmInputWebSocketHandler extends TextWebSocketHandler {
    private static final Pattern INPUT_FIELD = Pattern.compile("\\\"(type|deltaX|deltaY|button|pressed|keyCode)\\\"\\s*:\\s*(\\\"[^\\\"]*\\\"|true|false|-?\\d+)");
    private final SessionService sessions;
    private final VmService vms;

    public VmInputWebSocketHandler(SessionService sessions, VmService vms) {
        this.sessions = sessions;
        this.vms = vms;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String id = pathValue(session);
        String owner = sessions.userFor(cookie(session, "CLOUDSSX_SESSION"));
        BrowserInput input = parseInput(message.getPayload());
        vms.sendInput(owner, id, input);
    }

    private static BrowserInput parseInput(String payload) throws IOException {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = INPUT_FIELD.matcher(payload);
        while (matcher.find()) {
            String value = matcher.group(2);
            values.put(matcher.group(1), value.startsWith("\"") ? value.substring(1, value.length() - 1) : value);
        }
        if (!values.containsKey("type")) {
            throw new IOException("Input type is required");
        }
        return new BrowserInput(values.get("type"), integer(values, "deltaX"), integer(values, "deltaY"),
                integer(values, "button"), bool(values, "pressed"), integer(values, "keyCode"));
    }

    private static Integer integer(Map<String, String> values, String name) {
        return values.containsKey(name) ? Integer.valueOf(values.get(name)) : null;
    }

    private static Boolean bool(Map<String, String> values, String name) {
        return values.containsKey(name) ? Boolean.valueOf(values.get(name)) : null;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }

    private static String pathValue(WebSocketSession session) throws IOException {
        String[] parts = session.getUri().getPath().split("/");
        if (parts.length < 5) {
            throw new IOException("Invalid VM input path");
        }
        return parts[3];
    }

    private static String cookie(WebSocketSession session, String name) {
        return session.getHandshakeHeaders().getFirst("Cookie") == null ? null
                : java.util.Arrays.stream(session.getHandshakeHeaders().getFirst("Cookie").split(";"))
                        .map(String::trim).map(part -> part.split("=", 2)).filter(pair -> pair.length == 2 && name.equals(pair[0]))
                        .map(pair -> pair[1]).findFirst().orElse(null);
    }
}
