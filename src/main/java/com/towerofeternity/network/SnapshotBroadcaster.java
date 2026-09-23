package com.towerofeternity.network;

/**
 * [Network Layer] Interface phát sóng Snapshot từ GameLoop tới các kết nối WebSocket.
 * Áp dụng Dependency Inversion Principle (DIP) để GameLoop không phụ thuộc trực tiếp vào WebSocketHandler.
 */
public interface SnapshotBroadcaster {
    void broadcast(String message);
}
