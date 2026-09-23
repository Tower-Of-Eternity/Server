package com.towerofeternity.domain.world;

import com.towerofeternity.domain.player.Player;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Domain Layer] Quản lý toàn bộ thực thể thế giới trong trò chơi.
 * Thread-safe với ConcurrentHashMap để hỗ trợ truy xuất đồng thời giữa WebSocket handler và GameLoop.
 */
@Component
public class GameWorld {

    private final Map<String, Player> players = new ConcurrentHashMap<>();

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
    }

    public Player getPlayer(String playerId) {
        return players.get(playerId);
    }

    public Collection<Player> getAllPlayers() {
        return players.values();
    }

    public int getPlayerCount() {
        return players.size();
    }
}
