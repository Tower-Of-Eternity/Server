package com.towerofeternity.network;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        log.info("[CONNECT] New player connected! Session ID: {}", session.getId());
        session.sendMessage(new TextMessage("Welcome to Tower of Eternity!"));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("[RECEIVE] Message from [{}]: {}", session.getId(), payload);
        
        session.sendMessage(new TextMessage("Server received: " + payload));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("[DISCONNECT] Player disconnected: {}", session.getId());
    }
}
