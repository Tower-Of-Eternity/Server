package com.towerofeternity.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.towerofeternity.domain.player.Player;
import com.towerofeternity.domain.world.GameWorld;
import com.towerofeternity.game.GameLoop;
import com.towerofeternity.network.protocol.MoveCommandPacket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Network Layer] Xử lý kết nối WebSocket và chuyển giao gói tin tới GameLoop.
 * Hiện thực SnapshotBroadcaster để nhận Snapshot từ GameLoop và phát sóng tới Client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler implements SnapshotBroadcaster {

    private final GameWorld gameWorld;
    private final GameLoop gameLoop;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        activeSessions.put(session.getId(), session);

        // Khởi tạo Player mới trong GameWorld tại tọa độ gốc (0, 0)
        Player newPlayer = Player.builder()
                .id(session.getId())
                .x(0f)
                .y(0f)
                .state("IDLE")
                .lastActiveTime(System.currentTimeMillis())
                .build();

        gameWorld.addPlayer(newPlayer);
        log.info("[CONNECT] Người chơi mới tham gia! Session ID: {}, Tổng online: {}", session.getId(), gameWorld.getPlayerCount());

        session.sendMessage(new TextMessage("{\"type\":\"WELCOME\",\"playerId\":\"" + session.getId() + "\"}"));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String payload = message.getPayload();

        try {
            if (payload.contains("\"MOVE_CMD\"")) {
                MoveCommandPacket cmd = objectMapper.readValue(payload, MoveCommandPacket.class);
                cmd.setPlayerId(session.getId());
                gameLoop.enqueueInput(cmd);
            } else {
                log.info("[MESSAGE] Nhận từ [{}]: {}", session.getId(), payload);
            }
        } catch (Exception ex) {
            log.error("[NETWORK ERROR] Không thể giải mã gói tin từ [{}]: {}", session.getId(), ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        gameWorld.removePlayer(sessionId);
        gameLoop.removePlayerInput(sessionId);

        log.info("[DISCONNECT] Người chơi rời phòng: {}. Còn lại: {}", sessionId, gameWorld.getPlayerCount());
    }

    @Override
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);

        for (WebSocketSession session : activeSessions.values()) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("[BROADCAST ERROR] Không thể gửi tới session [{}]: {}", session.getId(), e.getMessage());
                }
            }
        }
    }
}
