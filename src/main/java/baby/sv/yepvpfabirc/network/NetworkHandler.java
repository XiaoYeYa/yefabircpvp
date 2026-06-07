package baby.sv.yepvpfabirc.network;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class NetworkHandler {

    public static final String MOD_PROTOCOL_VERSION = "1.0.0-af2025";

    // 待验证玩家: UUID → 加入时间戳(tick)
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long> pendingVersionChecks = new java.util.concurrent.ConcurrentHashMap<>();

    public static final Identifier SKILL_USE_ID = Identifier.of("yepvpfabirc", "skill_use");
    public static final Identifier HUD_SYNC_ID = Identifier.of("yepvpfabirc", "hud_sync");
    public static final Identifier GREETING_RESPONSE_ID = Identifier.of("yepvpfabirc", "greeting_response");
    public static final Identifier GAME_START_SCREEN_ID = Identifier.of("yepvpfabirc", "game_start_screen");
    public static final Identifier REVEAL_SCREEN_ID = Identifier.of("yepvpfabirc", "reveal_screen");
    public static final Identifier SPECTATOR_SWITCH_ID = Identifier.of("yepvpfabirc", "spectator_switch");
    public static final Identifier MAP_REQUEST_ID = Identifier.of("yepvpfabirc", "map_request");
    public static final Identifier MAP_DATA_ID = Identifier.of("yepvpfabirc", "map_data");
    public static final Identifier MAP_TELEPORT_ID = Identifier.of("yepvpfabirc", "map_teleport");
    public static final Identifier HIDDEN_PLAYERS_ID = Identifier.of("yepvpfabirc", "hidden_players");
    public static final Identifier JANE_SHOP_ID = Identifier.of("yepvpfabirc", "jane_shop");
    public static final Identifier JANE_SHOP_BUY_ID = Identifier.of("yepvpfabirc", "jane_shop_buy");
    public static final Identifier VERSION_CHECK_ID = Identifier.of("yepvpfabirc", "version_check");
    public static final Identifier VERSION_RESPONSE_ID = Identifier.of("yepvpfabirc", "version_response");

    public record SkillUsePayload(int skillSlot) implements CustomPayload {
        public static final Id<SkillUsePayload> ID = new Id<>(SKILL_USE_ID);
        public static final PacketCodec<PacketByteBuf, SkillUsePayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeVarInt(value.skillSlot),
                buf -> new SkillUsePayload(buf.readVarInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record HudSyncPayload(String jsonData) implements CustomPayload {
        public static final Id<HudSyncPayload> ID = new Id<>(HUD_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, HudSyncPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.jsonData),
                buf -> new HudSyncPayload(buf.readString())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GameStartScreenPayload(String jsonData) implements CustomPayload {
        public static final Id<GameStartScreenPayload> ID = new Id<>(GAME_START_SCREEN_ID);
        public static final PacketCodec<PacketByteBuf, GameStartScreenPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.jsonData),
                buf -> new GameStartScreenPayload(buf.readString())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RevealScreenPayload(String jsonData) implements CustomPayload {
        public static final Id<RevealScreenPayload> ID = new Id<>(REVEAL_SCREEN_ID);
        public static final PacketCodec<PacketByteBuf, RevealScreenPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.jsonData),
                buf -> new RevealScreenPayload(buf.readString())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GreetingResponsePayload(boolean accepted) implements CustomPayload {
        public static final Id<GreetingResponsePayload> ID = new Id<>(GREETING_RESPONSE_ID);
        public static final PacketCodec<PacketByteBuf, GreetingResponsePayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBoolean(value.accepted),
                buf -> new GreetingResponsePayload(buf.readBoolean())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record MapRequestPayload(boolean dummy) implements CustomPayload {
        public static final Id<MapRequestPayload> ID = new Id<>(MAP_REQUEST_ID);
        public static final PacketCodec<PacketByteBuf, MapRequestPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBoolean(value.dummy),
                buf -> new MapRequestPayload(buf.readBoolean())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record MapDataPayload(byte[] data) implements CustomPayload {
        public static final Id<MapDataPayload> ID = new Id<>(MAP_DATA_ID);
        public static final PacketCodec<PacketByteBuf, MapDataPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeByteArray(value.data),
                buf -> new MapDataPayload(buf.readByteArray(2097152)) // 2MB max
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // HELI全视之眼期间地图传送
    public record MapTeleportPayload(int x, int z) implements CustomPayload {
        public static final Id<MapTeleportPayload> ID = new Id<>(MAP_TELEPORT_ID);
        public static final PacketCodec<PacketByteBuf, MapTeleportPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeInt(value.x); buf.writeInt(value.z); },
                buf -> new MapTeleportPayload(buf.readInt(), buf.readInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // 完全隐身玩家列表(S2C): 装备+手持+模型全部隐藏
    public record HiddenPlayersPayload(byte[] data) implements CustomPayload {
        public static final Id<HiddenPlayersPayload> ID = new Id<>(HIDDEN_PLAYERS_ID);
        public static final PacketCodec<PacketByteBuf, HiddenPlayersPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeByteArray(value.data),
                buf -> new HiddenPlayersPayload(buf.readByteArray())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // JANE商店数据(S2C): bounty + 三项升级等级
    public record JaneShopPayload(int bounty, int power, int quickCharge, int multishot) implements CustomPayload {
        public static final Id<JaneShopPayload> ID = new Id<>(JANE_SHOP_ID);
        public static final PacketCodec<PacketByteBuf, JaneShopPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeVarInt(value.bounty); buf.writeVarInt(value.power); buf.writeVarInt(value.quickCharge); buf.writeVarInt(value.multishot); },
                buf -> new JaneShopPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // JANE商店购买(C2S): 0=power, 1=quickCharge, 2=multishot
    public record JaneShopBuyPayload(int upgradeType) implements CustomPayload {
        public static final Id<JaneShopBuyPayload> ID = new Id<>(JANE_SHOP_BUY_ID);
        public static final PacketCodec<PacketByteBuf, JaneShopBuyPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeVarInt(value.upgradeType),
                buf -> new JaneShopBuyPayload(buf.readVarInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // 版本检查(S2C): 服务端发送协议版本
    public record VersionCheckPayload(String version) implements CustomPayload {
        public static final Id<VersionCheckPayload> ID = new Id<>(VERSION_CHECK_ID);
        public static final PacketCodec<PacketByteBuf, VersionCheckPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.version),
                buf -> new VersionCheckPayload(buf.readString())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // 版本响应(C2S): 客户端回复自己的协议版本
    public record VersionResponsePayload(String version) implements CustomPayload {
        public static final Id<VersionResponsePayload> ID = new Id<>(VERSION_RESPONSE_ID);
        public static final PacketCodec<PacketByteBuf, VersionResponsePayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.version),
                buf -> new VersionResponsePayload(buf.readString())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // direction: 1=next, -1=prev, 0=free camera
    public record SpectatorSwitchPayload(int direction) implements CustomPayload {
        public static final Id<SpectatorSwitchPayload> ID = new Id<>(SPECTATOR_SWITCH_ID);
        public static final PacketCodec<PacketByteBuf, SpectatorSwitchPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeVarInt(value.direction),
                buf -> new SpectatorSwitchPayload(buf.readVarInt())
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(SkillUsePayload.ID, SkillUsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GreetingResponsePayload.ID, GreetingResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VersionResponsePayload.ID, VersionResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VersionCheckPayload.ID, VersionCheckPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpectatorSwitchPayload.ID, SpectatorSwitchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapRequestPayload.ID, MapRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapTeleportPayload.ID, MapTeleportPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HudSyncPayload.ID, HudSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapDataPayload.ID, MapDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GameStartScreenPayload.ID, GameStartScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RevealScreenPayload.ID, RevealScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HiddenPlayersPayload.ID, HiddenPlayersPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JaneShopPayload.ID, JaneShopPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JaneShopBuyPayload.ID, JaneShopBuyPayload.CODEC);

        // 版本响应处理
        ServerPlayNetworking.registerGlobalReceiver(VersionResponsePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!payload.version().equals(MOD_PROTOCOL_VERSION)) {
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal(
                            "§c§l版本不匹配！\n§r§e服务端版本: " + MOD_PROTOCOL_VERSION +
                            "\n§e你的版本: " + payload.version() +
                            "\n\n§f请安装愚人活动最新模组！"));
                } else {
                    pendingVersionChecks.remove(player.getUuid());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SkillUsePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GameManager gm = GameManager.getInstance();
                if (!gm.isGameActive()) return;
                PlayerData data = gm.getPlayerData(player.getUuid());
                if (data == null || !data.isAlive()) return;
                SkillHandler.handleSkill(player, data, payload.skillSlot());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MapRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GameManager gm = GameManager.getInstance();
                if (!gm.isGameActive()) return;
                net.minecraft.server.world.ServerWorld world = player.getEntityWorld();
                byte[] mapData = baby.sv.yepvpfabirc.game.MapDataCollector.buildMapData(world, player.getUuid());
                ServerPlayNetworking.send(player, new MapDataPayload(mapData));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MapTeleportPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GameManager gm = GameManager.getInstance();
                if (!gm.isGameActive()) return;
                PlayerData data = gm.getPlayerData(player.getUuid());
                if (data == null || !data.isAlive()) return;
                // HELI: 地图点击传送到最近被揭露的玩家
                if (data.getRole() == Role.HELI) {
                    gm.handleHeliMapTeleport(player, data, payload.x(), payload.z());
                }
                // POPCORN: 地图点击触发轨道镭射
                if (data.getRole() == Role.POPCORN) {
                    gm.popcornTriggerLaser(player, data, payload.x(), payload.z());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JaneShopBuyPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GameManager gm = GameManager.getInstance();
                if (!gm.isGameActive()) return;
                PlayerData data = gm.getPlayerData(player.getUuid());
                if (data == null || !data.isAlive() || data.getRole() != Role.JANE) return;
                gm.handleJaneShopBuy(player, data, payload.upgradeType());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SpectatorSwitchPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                GameManager gm = GameManager.getInstance();
                if (!gm.isGameActive()) return;
                if (!player.isSpectator()) return;
                gm.handleSpectatorSwitch(player, payload.direction());
            });
        });
    }

    public static void syncHudToPlayer(ServerPlayerEntity player, String jsonData) {
        ServerPlayNetworking.send(player, new HudSyncPayload(jsonData));
    }

    public static void sendGameStartScreen(ServerPlayerEntity player, String jsonData) {
        ServerPlayNetworking.send(player, new GameStartScreenPayload(jsonData));
    }

    public static void sendRevealScreen(ServerPlayerEntity player, String jsonData) {
        ServerPlayNetworking.send(player, new RevealScreenPayload(jsonData));
    }

    public static void sendMapData(ServerPlayerEntity player, byte[] data) {
        ServerPlayNetworking.send(player, new MapDataPayload(data));
    }

    public static void sendHiddenPlayers(ServerPlayerEntity player, byte[] data) {
        ServerPlayNetworking.send(player, new HiddenPlayersPayload(data));
    }

    public static void sendVersionCheck(ServerPlayerEntity player) {
        pendingVersionChecks.put(player.getUuid(), GameManager.getInstance().getServerTime());
        ServerPlayNetworking.send(player, new VersionCheckPayload(MOD_PROTOCOL_VERSION));
    }

    // 每tick检查超时(5秒=100tick未回复则踢出)
    public static void tickVersionChecks(net.minecraft.server.MinecraftServer server) {
        long currentTick = server.getOverworld().getTime();
        var iter = pendingVersionChecks.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (currentTick - entry.getValue() > 100) { // 5秒超时
                iter.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal(
                            "§c§l未检测到愚人活动模组！\n\n§f请安装愚人活动最新模组！"));
                }
            }
        }
    }

    public static void sendJaneShopData(ServerPlayerEntity player, PlayerData data) {
        ServerPlayNetworking.send(player, new JaneShopPayload(
                data.getBounty(), data.getCrossbowPower(), data.getCrossbowQuickCharge(), data.getCrossbowMultishot()));
    }
}
