package com.andrewsalygin;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> messages = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<WebSocketSession, String> users = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String room = getRoomFromSession(session);
        rooms.putIfAbsent(room, new CopyOnWriteArraySet<>());
        rooms.get(room).add(session);

        messages.putIfAbsent(room, new ArrayList<>());
        sendChatHistory(session, room);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String room = getRoomFromSession(session);
        JSONObject json = new JSONObject(message.getPayload());

        String username = json.getString("username");
        String text = json.getString("message");
        String formattedMessage = username + ": " + text;

        users.put(session, username);
        messages.get(room).add(formattedMessage);

        broadcast(room, formattedMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String room = getRoomFromSession(session);
        rooms.getOrDefault(room, new CopyOnWriteArraySet<>()).remove(session);
        users.remove(session);
    }

    private void broadcast(String room, String message) throws Exception {
        for (WebSocketSession session : rooms.getOrDefault(room, new CopyOnWriteArraySet<>())) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        }
    }

    private void sendChatHistory(WebSocketSession session, String room) throws Exception {
        for (String msg : messages.getOrDefault(room, new ArrayList<>())) {
            session.sendMessage(new TextMessage(msg));
        }
    }

    private String getRoomFromSession(WebSocketSession session) {
        return session.getUri().getQuery();
    }
}