package baby.sv.yepvpfabirc.client.hud;

import net.minecraft.client.render.entity.state.EntityRenderState;

import java.util.*;

public class HiddenPlayersCache {
    private static final Set<UUID> hiddenPlayers = new HashSet<>();
    // 共享: EntityRenderer.getAndUpdateRenderState存入, LivingEntityRenderer.render读取
    private static final Map<EntityRenderState, UUID> stateToUuid = new WeakHashMap<>();

    public static void updateFromBytes(byte[] data) {
        hiddenPlayers.clear();
        if (data == null || data.length < 4) return;
        int count = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                    ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int offset = 4;
        for (int i = 0; i < count && offset + 16 <= data.length; i++) {
            long msb = 0, lsb = 0;
            for (int j = 7; j >= 0; j--) { msb |= ((long)(data[offset++] & 0xFF)) << (j * 8); }
            for (int j = 7; j >= 0; j--) { lsb |= ((long)(data[offset++] & 0xFF)) << (j * 8); }
            hiddenPlayers.add(new UUID(msb, lsb));
        }
    }

    public static boolean isHidden(UUID uuid) {
        return hiddenPlayers.contains(uuid);
    }

    public static void putStateUuid(EntityRenderState state, UUID uuid) {
        stateToUuid.put(state, uuid);
    }

    public static UUID getStateUuid(EntityRenderState state) {
        return stateToUuid.get(state);
    }

    public static void clear() {
        hiddenPlayers.clear();
    }
}
