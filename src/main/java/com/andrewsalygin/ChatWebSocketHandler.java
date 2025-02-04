package com.andrewsalygin;

import com.andrewsalygin.model.UserChat;
import com.andrewsalygin.model.UserNote;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ChatWebSocketHandler extends TextWebSocketHandler {
    // В каких комнатах сейчас пользователи
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> roomsWithUsers = new ConcurrentHashMap<>(Map.of("room1", new CopyOnWriteArraySet<>(), "room2", new CopyOnWriteArraySet<>()));
    // Какие есть сообщения в комнатах
    private static final ConcurrentHashMap<String, List<String>> messagesInRooms = new ConcurrentHashMap<>(Map.of("room1", new ArrayList<>(), "room2", new ArrayList<>()));
    // Логин пользователя и его текущие сессии
    private static final ConcurrentHashMap<String, List<WebSocketSession>> users = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UserChat, List<UserNote>> usersMessages = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Для добавления в комнату пользователя по его нажатию
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("room")) {
            String room = session.getUri().getQuery().split("room=")[1];
            roomsWithUsers.get(room).add(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JSONObject json = new JSONObject(message.getPayload());
        String messageType = json.getString("type");
        String query = session.getUri().getQuery();

        // Новая сессия пользователя
        if (messageType.equals("newSession")) {
            // Добавление сессии
            String username = json.getString("username");
            List<WebSocketSession> sessions = users.getOrDefault(username, new ArrayList<>());
            sessions.add(session);
            users.put(username, sessions);

            // Уведомление всех пользователей о новом пользователе
            broadcastRegisteredUsers();
        } else if (messageType.equals("get_group_messages")) { // Получить сообщения комнаты
            if (query != null && query.startsWith("room")) {
                String room = session.getUri().getQuery().split("room=")[1];
                List<String> messages = messagesInRooms.getOrDefault(room, List.of());
                String response = "messages:\n" + String.join("\n", messages);

                // Отправляем конкретному пользователю сообщения комнаты
                session.sendMessage(new TextMessage(response));
            }
        } else if (messageType.equals("send_group_message")) { // Отправка пользователем в комнате сообщения
            String room = json.getString("currentRoom");
            String username = json.getString("username");
            String text = json.getString("message");
            String formattedMessage = "\n" + username + ":" + text;
            messagesInRooms.get(room).add(formattedMessage);

            // Уведомление всех пользователей комнаты
            broadcastMessageToRoom(room, formattedMessage);
        } else if (messageType.equals("get_private_messages")) {
            String master = json.getString("master");
            String slave = json.getString("slave");
            UserChat userChat = new UserChat(master, slave);

            StringBuilder messages = new StringBuilder("messages:\n");
            for (UserNote userNote : usersMessages.getOrDefault(userChat, new ArrayList<>())) {
                messages.append(userNote.getSender()).append(": ").append(userNote.getMessage()).append("\n");
            }
            messages.deleteCharAt(messages.length() - 1);

            // Отправляем конкретному пользователю сообщения комнаты
            session.sendMessage(new TextMessage(messages.toString()));
        } else if (messageType.equals("send_private_message")) {
            String master = json.getString("master");
            String slave = json.getString("slave");
            String messageMaster = json.getString("message");
            UserChat userChat = new UserChat(master, slave);
            UserNote userNote = new UserNote(master, messageMaster);

            List<UserNote> userNotes = usersMessages.getOrDefault(userChat, new ArrayList<>());
            userNotes.add(userNote);
            usersMessages.put(userChat, userNotes);
            broadcastMessageToPrivateDialog(master + ": " + messageMaster, List.of(master, slave));
        }
    }

    private void broadcastMessageToPrivateDialog(String message, List<String> userNames) throws IOException {
        if (userNames.get(0).equals(userNames.get(1))) {
            for (WebSocketSession session : users.get(userNames.getFirst())) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("messages:\n" + message));
                }
            }
        } else {
            for (String userName : userNames) {
                for (WebSocketSession session : users.get(userName)) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("messages:\n" + message));
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {}

    private void broadcastMessageToRoom(String room, String message) throws Exception {
        for (WebSocketSession session : roomsWithUsers.getOrDefault(room, new CopyOnWriteArraySet<>())) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("messages:" + message));
            }
        }
    }

    private void broadcastRegisteredUsers() throws Exception {
        String usersList = String.join(",", users.keySet());
        for (List<WebSocketSession> sessions : users.values()) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("users:" + usersList));
                }
            }
        }
    }
}
