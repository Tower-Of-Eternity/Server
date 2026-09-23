package com.towerofeternity.network.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [Network/Protocol] Lệnh di chuyển do Client gửi lên.
 * Đại diện cho Ý đồ (Intent), KHÔNG PHẢI tọa độ đã tính toán.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MoveCommandPacket {
    private String playerId; // Server gán từ Session ID
    private String type;
    private float dirX;
    private float dirY;
    private boolean isSprint;
    private boolean isDash;
    private long clientTime;
}
