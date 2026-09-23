package com.towerofeternity.game.systems;

import com.towerofeternity.domain.player.Player;
import com.towerofeternity.network.protocol.MoveCommandPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Game/Systems] Hệ thống xử lý chuyển động trên Server.
 * Chịu trách nhiệm thẩm định tính hợp lệ (Anti-Cheat) và cập nhật tọa độ Authoritative.
 */
@Slf4j
@Component
public class MovementSystem {

    public static final float WALK_SPEED = 5.0f;
    public static final float SPRINT_SPEED = 8.5f;
    public static final float DASH_SPEED = 18.0f;

    /**
     * Thẩm định và cập nhật chuyển động cho một người chơi dựa trên lệnh Intent gửi lên.
     * @param player Thực thể người chơi trên Server
     * @param cmd Lệnh di chuyển từ Client
     * @param dt Thời gian của một Tick (ví dụ 0.05 giây = 50ms)
     */
    public void processMovement(Player player, MoveCommandPacket cmd, float dt) {
        if (player == null || cmd == null) return;

        float dirX = cmd.getDirX();
        float dirY = cmd.getDirY();

        // Kiểm tra nếu không có di chuyển
        if (Math.abs(dirX) < 0.001f && Math.abs(dirY) < 0.001f) {
            player.setState("IDLE");
            return;
        }

        // 1. Chống hack tốc độ: Chuẩn hóa vector nếu độ dài > 1.0
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        float normX = dirX / (length > 0.001f ? length : 1.0f);
        float normY = dirY / (length > 0.001f ? length : 1.0f);

        // 2. Xác định vận tốc tối đa cho phép
        float speed = WALK_SPEED;
        String newState = "MOVE";

        if (cmd.isDash()) {
            speed = DASH_SPEED;
            newState = "DASH";
        } else if (cmd.isSprint()) {
            speed = SPRINT_SPEED;
            newState = "SPRINT";
        }

        // 3. Tính toán tọa độ mới của Server
        float newX = player.getX() + normX * speed * dt;
        float newY = player.getY() + normY * speed * dt;

        player.setX(newX);
        player.setY(newY);
        player.setState(newState);
        player.setLastActiveTime(System.currentTimeMillis());
    }
}
