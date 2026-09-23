package com.towerofeternity.domain.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [Domain Layer] Thực thể Người chơi (Player Entity) đại diện cho Authoritative State trên Server.
 * Server giữ quyền quyết định tuyệt đối về tọa độ (x, y) và trạng thái của Player.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private String id;
    private float x;
    private float y;
    
    @Builder.Default
    private String state = "IDLE";
    
    @Builder.Default
    private long lastActiveTime = System.currentTimeMillis();
}
