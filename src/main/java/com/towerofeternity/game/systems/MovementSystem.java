package com.towerofeternity.game.systems;

import com.towerofeternity.domain.player.Player;
import com.towerofeternity.network.protocol.MoveCommandPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Game/Systems] Hệ thống xử lý chuyển động trên Server.
 * Chịu trách nhiệm:
 * 1. Cập nhật các bộ đếm thời gian (Timers) độc lập với Input.
 * 2. Khóa hướng lướt gió (Dash Direction Lock).
 * 3. Thẩm định tính hợp lệ (Anti-Cheat) và cập nhật tọa độ Authoritative.
 */
@Slf4j
@Component
public class MovementSystem {

    public static final float WALK_SPEED = 5.0f;
    public static final float SPRINT_SPEED = 8.5f;
    public static final float DASH_SPEED = 18.0f;
    public static final float DASH_DURATION = 0.2f;    // Thời gian lướt 0.2s
    public static final float DASH_COOLDOWN = 0.8f;    // Hồi chiêu lướt 0.8s

    /**
     * Cập nhật chuyển động và trạng thái người chơi trong một Tick (50ms).
     * Hàm này được GameLoop gọi liên tục mỗi tick, kể cả khi cmd == null.
     * 
     * @param player Thực thể người chơi trên Server
     * @param cmd Lệnh di chuyển từ Client (có thể null nếu client không gửi gì trong tick này)
     * @param dt Thời gian của một Tick (0.05 giây = 50ms)
     */
    public void update(Player player, MoveCommandPacket cmd, float dt) {
        if (player == null) return;

        // 1. CẬP NHẬT TIMER ĐỘC LẬP VỚI INPUT
        // Dù người chơi không gửi input, hồi chiêu và thời gian lướt vẫn phải đếm ngược!
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
                // Hợp lệ: Kích hoạt lướt trên Server
                player.setDashing(true);
                player.setDashTimer(DASH_DURATION);
                player.setDashCooldownTimer(DASH_COOLDOWN);

                // KHÓA HƯỚNG LƯỚT: Ghi nhận hướng tại thời điểm kích hoạt Dash
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

        // 3. CẬP NHẬT SEQUENCE ACK (Nếu có cmd)
        if (cmd != null && cmd.getSequenceNumber() > player.getLastProcessedSequence()) {
            player.setLastProcessedSequence(cmd.getSequenceNumber());
        }

        // 4. TÍNH TOÁN TỌA ĐỘ DI CHUYỂN
        if (player.isDashing()) {
            // Khi đang Dash: Di chuyển cố định theo DashDirection đã khóa, bất chấp input hiện tại
            float newX = player.getX() + player.getDashDirectionX() * DASH_SPEED * dt;
            float newY = player.getY() + player.getDashDirectionY() * DASH_SPEED * dt;

            player.setX(newX);
            player.setY(newY);
            player.setState("DASH");
            player.setLastActiveTime(System.currentTimeMillis());
            return;
        }

        // Nếu không Dash: Xử lý di chuyển thông thường từ cmd
        if (cmd != null) {
            float dirX = cmd.getDirX();
            float dirY = cmd.getDirY();

            if (Math.abs(dirX) > 0.001f || Math.abs(dirY) > 0.001f) {
                float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
                float normX = dirX / (length > 0.001f ? length : 1.0f);
                float normY = dirY / (length > 0.001f ? length : 1.0f);

                float speed = cmd.isSprint() ? SPRINT_SPEED : WALK_SPEED;
                String newState = cmd.isSprint() ? "SPRINT" : "MOVE";

                float newX = player.getX() + normX * speed * dt;
                float newY = player.getY() + normY * speed * dt;

                player.setX(newX);
                player.setY(newY);
                player.setState(newState);
                player.setLastActiveTime(System.currentTimeMillis());
                return;
            }
        }

        // Nếu không có input di chuyển: Chuyển về IDLE
        player.setState("IDLE");
    }
}
