package com.towerofeternity.game.systems;

import com.towerofeternity.domain.player.Player;
import com.towerofeternity.network.protocol.MoveCommandPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Game/Systems] Hệ thống xử lý chuyển động trên Server.
 * Chịu trách nhiệm:
 * 1. Cập nhật các bộ đếm thời gian (Timers) độc lập với Input.
 * 2. Xử lý Continuous Input mượt mà với cơ chế Timeout thực sự dựa trên receivedAt.
 * 3. Khóa hướng lướt gió (Dash Direction Lock).
 * 4. Thẩm định tính hợp lệ (Anti-Cheat) và cập nhật tọa độ Authoritative.
 */
@Slf4j
@Component
public class MovementSystem {

    public static final float WALK_SPEED = 5.0f;
    public static final float SPRINT_SPEED = 8.5f;
    public static final float DASH_SPEED = 18.0f;
    public static final float DASH_DURATION = 0.2f;    // Thời gian lướt 0.2s
    public static final float DASH_COOLDOWN = 0.8f;    // Hồi chiêu lướt 0.8s
    private static final long INPUT_TIMEOUT_MS = 300L; // Quá 300ms kể từ packet cuối nhận từ mạng thì dừng lại (IDLE)

    /**
     * Cập nhật chuyển động và trạng thái người chơi trong một Tick (50ms).
     * 
     * @param player Thực thể người chơi trên Server
     * @param cmd Lệnh di chuyển từ Client
     * @param receivedAt Thời điểm packet mạng thực sự được nhận (System.currentTimeMillis())
     * @param dt Thời gian của một Tick (0.05 giây = 50ms)
     */
    public void update(Player player, MoveCommandPacket cmd, long receivedAt, float dt) {
        if (player == null) return;

        // 1. CẬP NHẬT TIMER ĐỘC LẬP VỚI INPUT
        if (player.getDashCooldownTimer() > 0) {
            player.setDashCooldownTimer(Math.max(0, player.getDashCooldownTimer() - dt));
        }

        if (player.isDashing()) {
            player.setDashTimer(player.getDashTimer() - dt);
            if (player.getDashTimer() <= 0) {
                player.setDashing(false);
            }
        }

        // 2. XỬ LÝ LỆNH DASH MỚI (Nếu có cmd)
        if (cmd != null && cmd.isDash()) {
            if (!player.isDashing() && player.getDashCooldownTimer() <= 0) {
                player.setDashing(true);
                player.setDashTimer(DASH_DURATION);
                player.setDashCooldownTimer(DASH_COOLDOWN);

                // Khóa hướng lướt tại thời điểm kích hoạt Dash
                float dirX = cmd.getDirX();
                float dirY = cmd.getDirY();
                float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
                if (length > 0.001f) {
                    player.setDashDirectionX(dirX / length);
                    player.setDashDirectionY(dirY / length);
                } else {
                    player.setDashDirectionX(1.0f);
                    player.setDashDirectionY(0.0f);
                }
            }
        }

        // 3. CẬP NHẬT SEQUENCE ACK (Latest Applied Sequence)
        if (cmd != null && cmd.getSequenceNumber() > player.getLastProcessedSequence()) {
            player.setLastProcessedSequence(cmd.getSequenceNumber());
        }

        // 4. TÍNH TOÁN TỌA ĐỘ DI CHUYỂN
        // 4.1. Nếu đang Dash: Di chuyển theo hướng đã khóa bất chấp input
        if (player.isDashing()) {
            float newX = player.getX() + player.getDashDirectionX() * DASH_SPEED * dt;
            float newY = player.getY() + player.getDashDirectionY() * DASH_SPEED * dt;

            player.setX(newX);
            player.setY(newY);
            player.setState("DASH");
            return;
        }

        // 4.2. Di chuyển thông thường (Continuous Movement)
        // KIỂM TRA TIMEOUT THỰC SỰ: Dựa trên receivedAt của packet mạng, tránh bug chạy mãi khi client ngắt kết nối
        if (receivedAt == 0L || System.currentTimeMillis() - receivedAt > INPUT_TIMEOUT_MS) {
            player.setMoveDirX(0f);
            player.setMoveDirY(0f);
            player.setSprinting(false);
            player.setState("IDLE");
            return;
        }

        // Nếu chưa timeout: cập nhật hướng mới từ cmd nếu có
        if (cmd != null) {
            player.setMoveDirX(cmd.getDirX());
            player.setMoveDirY(cmd.getDirY());
            player.setSprinting(cmd.isSprint());
        }

        float dirX = player.getMoveDirX();
        float dirY = player.getMoveDirY();

        if (Math.abs(dirX) > 0.001f || Math.abs(dirY) > 0.001f) {
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            float normX = dirX / (length > 0.001f ? length : 1.0f);
            float normY = dirY / (length > 0.001f ? length : 1.0f);

            float speed = player.isSprinting() ? SPRINT_SPEED : WALK_SPEED;
            String newState = player.isSprinting() ? "SPRINT" : "MOVE";

            float newX = player.getX() + normX * speed * dt;
            float newY = player.getY() + normY * speed * dt;

            player.setX(newX);
            player.setY(newY);
            player.setState(newState);
        } else {
            player.setState("IDLE");
        }
    }
}
