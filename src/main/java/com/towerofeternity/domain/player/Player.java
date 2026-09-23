package com.towerofeternity.domain.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [Domain Layer] Thực thể Người chơi (Player Entity) đại diện cho Authoritative State trên Server.
 * Server giữ quyền quyết định tuyệt đối về tọa độ (x, y), trạng thái, hướng di chuyển và hồi chiêu của Player.
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

    // --- Continuous Movement State (Duy trì di chuyển liên tục, chống giật do lag/jitter) ---
    @Builder.Default
    private float moveDirX = 0f;

    @Builder.Default
    private float moveDirY = 0f;

    @Builder.Default
    private boolean isSprinting = false;

    // --- Authoritative Dash State (Server kiểm soát, khóa hướng khi lướt, chống hack lướt vô tận) ---
    @Builder.Default
    private float dashCooldownTimer = 0f;

    @Builder.Default
    private float dashTimer = 0f;

    @Builder.Default
    private boolean isDashing = false;

    @Builder.Default
    private float dashDirectionX = 0f;

    @Builder.Default
    private float dashDirectionY = 0f;

    // --- Input Sequence / ACK phục vụ Client Prediction & Server Reconciliation ---
    @Builder.Default
    private long lastProcessedSequence = 0L;
}
