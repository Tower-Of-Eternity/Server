package com.towerofeternity.network.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * [Network/Protocol] Bức ảnh toàn cảnh thế giới game tại một thời điểm Tick.
 * Server phát sóng định kỳ cho tất cả người chơi để đồng bộ hóa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldSnapshotPacket {

    @Builder.Default
    private String type = "WORLD_SNAPSHOT";

    private long tick;
    private List<PlayerSnapshotDTO> players;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerSnapshotDTO {
        private String playerId;
        private float x;
        private float y;
        private String state;
    }
}
