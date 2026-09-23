package com.towerofeternity.game.systems;

import com.towerofeternity.domain.player.Player;
import com.towerofeternity.network.protocol.MoveCommandPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Game/Systems] Hệ thống xử lý chuyển động trên Server.
 * Chịu trách nhiệm thẩm định tính hợp lệ (Anti-Cheat), quản lý Dash Authoritative và cập nhật tọa độ.
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
     * Thẩm định và cập nhật chuyển động cho một người chơi dựa trên lệnh Intent gửi lên.
     * @param player Thực thể người chơi trên Server
     * @param cmd Lệnh di chuyển từ Client
     * @param dt Thời gian của một Tick (0.05 giây = 50ms)
     */
    public void processMovement(Player player, MoveCommandPacket cmd, float dt) {
        if (player == null || cmd == null) return;

        // 1. Cập nhật bộ đếm hồi chiêu Dash trên Server
        if (player.getDashCooldownTimer() > 0) {
            player.setDashCooldownTimer(Math.max(0, player.getDashCooldownTimer() - dt));
        }

        // 2. Cập nhật thời gian duy trì lướt Dash nếu đang lướt
        if (player.isDashing()) {
            player.setDashTimer(player.getDashTimer() - dt);
            if (player.getDashTimer() <= 0) {
                player.setDashing(false);
            }
        }

        // 3. Xử lý yêu cầu Lướt gió (Dash) từ Client - ANTI-CHEAT CHECK
        if (cmd.isDash()) {
            if (!player.isDashing() && player.getDashCooldownTimer() <= 0) {
                // Hợp lệ: Kích hoạt lướt trên Server
                player.setDashing(true);
                player.setDashTimer(DASH_DURATION);
                player.setDashCooldownTimer(DASH_COOLDOWN);
            } else {
                // Gian lận hoặc bấm phím quá nhanh khi hồi chiêu chưa xong -> Từ chối!
                // log.debug("[ANTI-CHEAT] Player {} bị từ chối Dash vì hồi chiêu còn {}s", player.getId(), player.getDashCooldownTimer());
            }
        }

        // 4. Cập nhật Sequence Number được xử lý (ACK)
        if (cmd.getSequenceNumber() > player.getLastProcessedSequence()) {
            player.setLastProcessedSequence(cmd.getSequenceNumber());
        }

        float dirX = cmd.getDirX();
        float dirY = cmd.getDirY();

        // Kiểm tra nếu không có di chuyển và không đang lướt
        if (Math.abs(dirX) < 0.001f && Math.abs(dirY) < 0.001f && !player.isDashing()) {
            player.setState("IDLE");
            return;
        }

        // 5. Chuẩn hóa vector di chuyển (chống hack chạy chéo hoặc phóng đại vector)
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        float normX = dirX / (length > 0.001f ? length : 1.0f);
        float normY = dirY / (length > 0.001f ? length : 1.0f);

        // 6. Quyết định vận tốc Server cho phép
        float speed = WALK_SPEED;
        String newState = "MOVE";

        if (player.isDashing()) {
            speed = DASH_SPEED;
            newState = "DASH";
        } else if (cmd.isSprint()) {
            speed = SPRINT_SPEED;
            newState = "SPRINT";
        }

        // 7. Tính toán tọa độ Authoritative của Server
        float newX = player.getX() + normX * speed * dt;
        float newY = player.getY() + normY * speed * dt;

        player.setX(newX);
        player.setY(newY);
        player.setState(newState);
        player.setLastActiveTime(System.currentTimeMillis());
    }
}
