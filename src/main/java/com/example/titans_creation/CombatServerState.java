package com.example.titans_creation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端保存的战斗模式状态。
 *
 * <p>战斗模式本身是客户端开关（见 {@code CombatClientState}），
 * 但生成机制中的「玩家状态」条件需要服务端也能判断，因此客户端在切换战斗模式时
 * 通过 {@link CombatNetwork.CombatModePacket} 把状态同步到服务端并缓存在这里。
 */
public final class CombatServerState {

    private static final Map<UUID, Boolean> COMBAT_MODE = new ConcurrentHashMap<>();

    private CombatServerState() {
    }

    /** 记录某玩家的战斗模式开关状态。 */
    public static void setCombatMode(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled) {
            COMBAT_MODE.put(playerId, Boolean.TRUE);
        } else {
            COMBAT_MODE.remove(playerId);
        }
    }

    /** 该玩家是否处于战斗模式（未同步过则视为关闭）。 */
    public static boolean isCombatMode(ServerPlayer player) {
        return player != null && COMBAT_MODE.containsKey(player.getUUID());
    }

    /** 玩家离线时清理，避免状态残留。 */
    public static void clear(UUID playerId) {
        if (playerId != null) {
            COMBAT_MODE.remove(playerId);
        }
    }
}
