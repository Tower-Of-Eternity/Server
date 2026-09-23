package com.towerofeternity.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.towerofeternity.domain.player.Player;
import com.towerofeternity.domain.world.GameWorld;
import com.towerofeternity.game.systems.MovementSystem;
import com.towerofeternity.network.SnapshotBroadcaster;
import com.towerofeternity.network.protocol.MoveCommandPacket;
import com.towerofeternity.network.protocol.WorldSnapshotPacket;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [Game Layer] Trái tim của Game Server: 20Hz Game Loop (Vòng lặp mô phỏng thế giới).
 * Mỗi 50ms chạy đúng 1 lần:
 * 1. Gom Input từ người chơi (Collect Inputs).
 * 2. Cập nhật các hệ thống mô phỏng thế giới (Systems Simulation).
 * 3. Chụp và phát sóng trạng thái toàn cảnh thế giới (Broadcast World Snapshot).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameLoop {

    private final GameWorld gameWorld;
    private final MovementSystem movementSystem;
    private final ObjectMapper objectMapper;

    @Lazy
    private final SnapshotBroadcaster snapshotBroadcaster;

    private final AtomicLong currentTick = new AtomicLong(0);
    private final Map<String, MoveCommandPacket> latestInputs = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private static final int TICK_RATE_MS = 50; // 50ms = 20 Ticks/giây (20Hz)

    @PostConstruct
    public void start() {
        log.info("[GAME LOOP] Khởi động vòng lặp game nhịp 20Hz (mỗi {}ms một Tick)...", TICK_RATE_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GameLoop-Thread");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::tick, 0, TICK_RATE_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("[GAME LOOP] Đã dừng vòng lặp game.");
        }
    }

    /**
     * Nhận lệnh từ WebSocket Handler và đưa vào bộ nhớ đệm của Tick.
     */
    public void enqueueInput(MoveCommandPacket command) {
        if (command != null && command.getPlayerId() != null) {
            latestInputs.put(command.getPlayerId(), command);
        }
    }

    /**
     * Xóa bộ đệm input khi người chơi thoát game.
     */
    public void removePlayerInput(String playerId) {
        latestInputs.remove(playerId);
    }

    /**
     * Nhịp đập chính của thế giới trò chơi.
     */
    private void tick() {
        try {
            long tick = currentTick.incrementAndGet();
            float dt = TICK_RATE_MS / 1000f; // 0.05s

            // 1. Simulation: Xử lý di chuyển cho tất cả người chơi
            for (Player player : gameWorld.getAllPlayers()) {
                MoveCommandPacket input = latestInputs.get(player.getId());
                if (input != null) {
                    movementSystem.processMovement(player, input, dt);
                }
            }

            // 2. Snapshot: Phát sóng trạng thái cho toàn bộ Client nếu có người chơi
            if (gameWorld.getPlayerCount() > 0) {
                broadcastSnapshot(tick);
            }

        } catch (Exception ex) {
            log.error("[GAME LOOP] Lỗi nghiêm trọng trong nhịp Tick: {}", ex.getMessage(), ex);
        }
    }

    private void broadcastSnapshot(long tick) {
        try {
            List<WorldSnapshotPacket.PlayerSnapshotDTO> playerDTOs = new ArrayList<>();
            for (Player p : gameWorld.getAllPlayers()) {
                playerDTOs.add(WorldSnapshotPacket.PlayerSnapshotDTO.builder()
                        .playerId(p.getId())
                        .x(p.getX())
                        .y(p.getY())
                        .state(p.getState())
                        .ackSequence(p.getLastProcessedSequence())
                        .build());
            }

            WorldSnapshotPacket snapshot = WorldSnapshotPacket.builder()
                    .tick(tick)
                    .players(playerDTOs)
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(snapshot);
            snapshotBroadcaster.broadcast(jsonPayload);

        } catch (Exception ex) {
            log.error("[GAME LOOP] Lỗi đóng gói Snapshot: {}", ex.getMessage());
        }
    }
}
