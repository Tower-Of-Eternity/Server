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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [Game Layer] Trái tim của Game Server: 20Hz Game Loop (Vòng lặp mô phỏng thế giới).
 * Mỗi 50ms chạy đúng 1 lần:
 * 1. Xử lý các hành động đơn lẻ (Discrete Actions: Dash) từ hàng đợi pendingActions (không bị ghi đè).
 * 2. Cập nhật Continuous Input (WASD) từ latestContinuousInputs.
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

    // Tách riêng luồng Continuous Input và Discrete Actions
    private final Map<String, MoveCommandPacket> latestContinuousInputs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<MoveCommandPacket> pendingActions = new ConcurrentLinkedQueue<>();

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
     * Tách biệt: One-shot Action (Dash) đưa vào queue, Continuous Input đưa vào map.
     */
    public void enqueueInput(MoveCommandPacket command) {
        if (command == null || command.getPlayerId() == null) {
            return;
        }

        if (command.isDash()) {
            pendingActions.add(command);
            return; // Dash là one-shot action, không ghi đè vào continuous movement state
        }

        latestContinuousInputs.put(command.getPlayerId(), command);
    }

    /**
     * Xóa bộ đệm input khi người chơi thoát game.
     */
    public void removePlayerInput(String playerId) {
        latestContinuousInputs.remove(playerId);
    }

    /**
     * Nhịp đập chính của thế giới trò chơi.
     */
    private void tick() {
        try {
            long tick = currentTick.incrementAndGet();
            float dt = TICK_RATE_MS / 1000f; // 0.05s

            // 1. Simulation: Xử lý các hành động đơn lẻ (Discrete Actions: DASH) trước
            // Đảm bảo lệnh Dash không bao giờ bị ghi đè bởi packet di chuyển đến cùng tick
            Set<String> actionProcessedPlayers = new HashSet<>();
            while (!pendingActions.isEmpty()) {
                MoveCommandPacket actionCmd = pendingActions.poll();
                Player player = gameWorld.getPlayer(actionCmd.getPlayerId());
                if (player != null) {
                    movementSystem.update(player, actionCmd, dt);
                    actionProcessedPlayers.add(player.getId());
                }
            }

            // 2. Simulation: Xử lý di chuyển liên tục (Continuous Movement) cho những người chơi còn lại
            for (Player player : gameWorld.getAllPlayers()) {
                if (!actionProcessedPlayers.contains(player.getId())) {
                    MoveCommandPacket continuousInput = latestContinuousInputs.get(player.getId());
                    movementSystem.update(player, continuousInput, dt);
                }
            }

            // 3. Snapshot: Phát sóng trạng thái cho toàn bộ Client nếu có người chơi
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
