package baby.sv.yepvpfabirc.game;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.border.WorldBorder;
import baby.sv.yepvpfabirc.network.NetworkHandler;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;

public class GameManager {
    private static GameManager INSTANCE;

    private boolean gameActive = false;
    private boolean revealed = false;
    private boolean victorySceneActive = false; // 胜利演礼进行中, 阻止重复触发
    private boolean finalDuelActive = false; // 终局对决(禁止回血)
    private boolean randomRoleMode = false; // 随机分配职业模式
    private boolean fireMode = false; // 无限火力模式 (命数翻倍)
    private volatile boolean mapResetInProgress = false; // 地图重置进行中
    private long gameStartTick = 0;
    private long revealTick = 0;
    private long lastSaveTick = 0; // 上次存档时间
    private static final long REVEAL_DELAY = 6000; // 5 minutes before reveal
    private static final int MAP_RADIUS = 500; // 500半径(直径1000)
    // 地图刷新中心点(世界边界中心 + 地图渲染中心 + 出生高度参考)
    private static final double MAP_CENTER_X = 512.55;
    private static final double MAP_CENTER_Y = 151.00;
    private static final double MAP_CENTER_Z = 516.79;
    private static final long RESPAWN_INVINCIBLE_TICKS = 200; // 10秒无敌
    // 多阶段缩圈系统
    private int shrinkPhase = 0; // 0=未开始, 1/2/3=缩圈中, 4=终局对决, 5=最终缩圈
    // 每个阶段: {开始tick(相对游戏开始), 缩圈持续tick, 直径缩小量, 毒伤(半心/秒)}
    // Phase1: 5min开始, 5min缩, 直径-500, 1心/s=2hp/s
    // Phase2: 18min开始, 2min缩, 直径-250, 2心/s=4hp/s
    // Phase3: 24min开始, 1min缩, 直径-150, 4心/s=8hp/s
    // Phase4: 30min开始, 终局对决10min, 不缩圈, 禁回血
    // Phase5: 40min开始, 缩至0, 8心/s=16hp/s
    private static final long[] PHASE_START_TICK  = {6000, 21600, 28800, 36000, 48000}; // 5/18/24/30/40 min
    private static final long[] PHASE_DURATION    = {6000, 2400,  1200,  0,     6000};  // 5min/2min/1min/0/5min缩完
    private static final int[]  PHASE_DIAMETER    = {500,  250,   150,   0,     100};   // 直径缩小量(phase5缩剩余100)
    private static final float[] PHASE_POISON_DPS = {2.0f, 4.0f,  8.0f,  8.0f,  16.0f}; // 毒伤hp/s(1/2/4/4/8心)
    private float currentPoisonDps = 2.0f; // 当前毒伤
    private final Map<UUID, Float> lastPlayerHealth = new HashMap<>(); // 终局对决血量追踪
    // 职业出生点配置
    private final Map<Role, BlockPos> roleSpawnPoints = new HashMap<>();

    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<String, Role> playerNameToRole = new HashMap<>();
    private boolean debugMode = false; // 调试模式: 可随时切换职业

    // JIEZU/DASHA 完全隐身玩家集合0.(服务端维护,通过网络包同步到客户端)
    private final Set<UUID> fullyHiddenPlayers = new HashSet<>();

    // POPCORN: 旁观模式生成的躯壳实体 (playerUUID -> zombieEntity)
    private final Map<UUID, net.minecraft.entity.Entity> popcornBodyEntities = new HashMap<>();

    // ===== ALLAND 颜料弹系统 =====
    // 颜料弹注册: snowball entityId -> {color, ownerUuid}
    private final Map<Integer, int[]> paintProjectiles = new ConcurrentHashMap<>(); // entityId -> [color, ownerUuidHash]
    private final Map<Integer, UUID> paintProjectileOwners = new ConcurrentHashMap<>(); // entityId -> ownerUuid
    // 颜料区域
    private final List<PaintZone> paintZones = new ArrayList<>();
    // 延迟效果
    private final List<DelayedAction> delayedActions = new ArrayList<>();

    public static class PaintZone {
        public final BlockPos center;
        public final int color; // 0=红, 1=蓝, 2=绿, 3=黄
        public final long expiryTick;
        public final UUID ownerUuid;
        public final Map<BlockPos, net.minecraft.block.BlockState> originalBlocks;
        public boolean invisZone = false; // 绿+绿升级为隐身区域

        public PaintZone(BlockPos center, int color, long expiryTick, UUID ownerUuid, Map<BlockPos, net.minecraft.block.BlockState> originalBlocks) {
            this.center = center;
            this.color = color;
            this.expiryTick = expiryTick;
            this.ownerUuid = ownerUuid;
            this.originalBlocks = originalBlocks;
        }
    }

    public static class DelayedAction {
        public final long triggerTick;
        public final Runnable action;
        public DelayedAction(long triggerTick, Runnable action) {
            this.triggerTick = triggerTick;
            this.action = action;
        }
    }

    private BlockPos arenaCenter = null;
    private int arenaRadius = 50;

    // ===== 箱子战利品系统 =====
    private final Set<BlockPos> openedChests = new HashSet<>(); // 已生成过战利品的箱子位置

    // ===== Boss系统 =====
    private final List<BossData> activeBosses = new ArrayList<>();
    private final List<BossData> pendingBossMarkers = new ArrayList<>(); // 提前显示位置的Boss
    private boolean boss10Spawned = false, boss20Spawned = false, boss30Spawned = false;
    private boolean warden30Spawned = false;

    public static class BossData {
        public final UUID entityUuid;
        public final BlockPos spawnPos;
        public final String type; // "iron_golem" or "warden"
        public final long spawnTick;
        public BossData(UUID entityUuid, BlockPos spawnPos, String type, long spawnTick) {
            this.entityUuid = entityUuid;
            this.spawnPos = spawnPos;
            this.type = type;
            this.spawnTick = spawnTick;
        }
    }

    public List<BossData> getActiveBosses() { return activeBosses; }
    public List<BossData> getPendingBossMarkers() { return pendingBossMarkers; }

    private MinecraftServer server;
    private final LobbyManager lobbyManager = new LobbyManager();

    private GameManager() {}

    public static GameManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GameManager();
        }
        return INSTANCE;
    }

    public LobbyManager getLobbyManager() { return lobbyManager; }

    // ===== 地图编辑模式(管理员) =====
    private final java.util.Set<UUID> mapEditPlayers = new java.util.HashSet<>();

    public boolean isMapEditing(UUID uuid) {
        return mapEditPlayers.contains(uuid);
    }

    // 切换地图编辑模式: 进入则从大厅传送到地图(主世界)地表并切创造, 退出则传回大厅
    // 返回提示文本
    public Text toggleMapEdit(ServerPlayerEntity player) {
        if (gameActive) {
            return Text.literal("§c游戏进行中无法进入地图编辑模式, 请在大厅阶段使用。");
        }
        if (server == null) {
            return Text.literal("§c服务器未就绪。");
        }
        UUID uuid = player.getUuid();
        if (mapEditPlayers.contains(uuid)) {
            // 退出编辑模式 → 回大厅(teleportToLobby 会恢复冒险模式)
            mapEditPlayers.remove(uuid);
            lobbyManager.teleportToLobby(player);
            return Text.literal("§a已退出地图编辑模式, 传送回大厅。");
        } else {
            // 进入编辑模式 → 传送到地图(主世界)地表 + 创造模式
            ServerWorld overworld = server.getOverworld();
            int x = (int) MAP_CENTER_X, z = (int) MAP_CENTER_Z;
            int y = overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < overworld.getBottomY() + 1) y = (int) MAP_CENTER_Y; // 兜底高度
            mapEditPlayers.add(uuid);
            player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            player.teleport(overworld, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYaw(), player.getPitch(), false);
            return Text.literal("§6已进入地图编辑模式(创造模式)。再次输入命令退出并返回大厅。");
        }
    }

    public void clearMapEdit() {
        mapEditPlayers.clear();
    }

    public void exitMapEditOnDisconnect(UUID uuid) {
        mapEditPlayers.remove(uuid);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        loadRoleSpawnPoints();
        loadGameState(); // 启动时尝试加载存档
    }

    public MinecraftServer getServer() {
        return server;
    }

    public long getServerTime() {
        return server != null ? server.getOverworld().getTime() : 0;
    }

    public boolean isGameActive() {
        return gameActive;
    }

    public boolean isMapResetInProgress() {
        return mapResetInProgress;
    }

    // ===== ALLAND 颜料弹公共方法 =====
    public void registerPaintProjectile(int entityId, int color, UUID ownerUuid) {
        paintProjectiles.put(entityId, new int[]{color});
        paintProjectileOwners.put(entityId, ownerUuid);
    }
    public int[] getPaintProjectileColor(int entityId) { return paintProjectiles.remove(entityId); }
    public UUID getPaintProjectileOwner(int entityId) { return paintProjectileOwners.remove(entityId); }
    public List<PaintZone> getPaintZones() { return paintZones; }
    public void addDelayedAction(long triggerTick, Runnable action) { delayedActions.add(new DelayedAction(triggerTick, action)); }

    public void assignRole(String playerName, Role role) {
        playerNameToRole.put(playerName.toLowerCase(), role);
    }

    public Role getRoleForPlayer(String playerName) {
        return playerNameToRole.get(playerName.toLowerCase());
    }

    public void setArena(BlockPos center, int radius) {
        this.arenaCenter = center;
        this.arenaRadius = radius;
    }

    public BlockPos getArenaCenter() { return arenaCenter; }
    public int getArenaRadius() { return arenaRadius; }
    public boolean isFinalDuelActive() { return finalDuelActive; }
    public int getShrinkPhase() { return shrinkPhase; }
    public float getCurrentPoisonDps() { return currentPoisonDps; }
    public void setRoleSpawnPoint(Role role, BlockPos pos) {
        roleSpawnPoints.put(role, pos);
        saveRoleSpawnPoints();
    }
    public BlockPos getRoleSpawnPoint(Role role) { return roleSpawnPoints.get(role); }

    private java.nio.file.Path getRoleSpawnFile() {
        return server.getRunDirectory().resolve("config").resolve("yepvp_role_spawns.json");
    }

    private void loadGameState() {
        if (server == null) return;
        java.nio.file.Path file = server.getRunDirectory().resolve("config").resolve("yepvp_game_state.json");
        if (!java.nio.file.Files.exists(file)) return;
        try {
            String json = java.nio.file.Files.readString(file);
            JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            
            this.gameActive = root.get("gameActive").getAsBoolean();
            if (!this.gameActive) return;
            
            this.gameStartTick = root.get("gameStartTick").getAsLong();
            this.revealed = root.get("revealed").getAsBoolean();
            this.randomRoleMode = root.get("randomRoleMode").getAsBoolean();
            this.fireMode = root.get("fireMode").getAsBoolean();
            this.shrinkPhase = root.get("shrinkPhase").getAsInt();
            this.finalDuelActive = root.get("finalDuelActive").getAsBoolean();
            
            // 加载职业分配
            JsonObject rolesObj = root.getAsJsonObject("playerNameToRole");
            for (String name : rolesObj.keySet()) {
                playerNameToRole.put(name, Role.valueOf(rolesObj.get(name).getAsString()));
            }
            
            // 加载玩家数据 (仅加载关键命数和击杀等，其余运行时状态较难完全恢复)
            JsonObject playersObj = root.getAsJsonObject("players");
            for (String uuidStr : playersObj.keySet()) {
                UUID uuid = UUID.fromString(uuidStr);
                JsonObject pDataObj = playersObj.getAsJsonObject(uuidStr);
                Role role = Role.valueOf(pDataObj.get("role").getAsString());
                String name = pDataObj.get("name").getAsString();
                
                PlayerData pd = new PlayerData(uuid, name, role);
                pd.setLives(pDataObj.get("lives").getAsInt());
                
                // 加载 SHUBING 数据
                if (role == Role.SHUBING && pDataObj.has("shubing")) {
                    JsonObject sb = pDataObj.getAsJsonObject("shubing");
                    pd.setPaintingCount(sb.get("paintingCount").getAsInt());
                    
                    if (sb.has("placedPaintings")) {
                        JsonArray ppArray = sb.getAsJsonArray("placedPaintings");
                        for (JsonElement e : ppArray) {
                            JsonObject obj = e.getAsJsonObject();
                            BlockPos pos = obj.has("pos") ? new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()) : null;
                            PlayerData.ShubingPainting painting = new PlayerData.ShubingPainting(pos, UUID.fromString(obj.get("entityUuid").getAsString()), obj.get("horizontal").getAsBoolean());
                            painting.corridorIndex = obj.get("corridorIndex").getAsInt();
                            painting.corridorEnd = obj.get("corridorEnd").getAsInt();
                            if (obj.has("hologramTag")) painting.hologramTag = obj.get("hologramTag").isJsonNull() ? null : obj.get("hologramTag").getAsString();
                            pd.getPlacedPaintings().add(painting);
                        }
                    }
                    if (sb.has("corridors")) {
                        JsonArray cArray = sb.getAsJsonArray("corridors");
                        for (JsonElement e : cArray) {
                            JsonObject obj = e.getAsJsonObject();
                            BlockPos origin = new BlockPos(obj.get("ox").getAsInt(), obj.get("oy").getAsInt(), obj.get("oz").getAsInt());
                            PlayerData.ShubingCorridor corridor = new PlayerData.ShubingCorridor(origin, obj.get("vertical").getAsBoolean(), obj.get("p0").getAsInt(), obj.get("p1").getAsInt());
                            corridor.end0Sealed = obj.get("e0s").getAsBoolean();
                            corridor.end1Sealed = obj.get("e1s").getAsBoolean();
                            pd.getCorridors().add(corridor);
                        }
                    }
                }
                
                players.put(uuid, pd);
            }
            
            System.out.println("[YePVP] Game state loaded successfully.");
        } catch (Exception e) {
            System.err.println("[YePVP] Failed to load game state: " + e.getMessage());
        }
    }

    private void saveGameState() {
        if (server == null || !gameActive) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("gameActive", gameActive);
            root.addProperty("gameStartTick", gameStartTick);
            root.addProperty("revealed", revealed);
            root.addProperty("randomRoleMode", randomRoleMode);
            root.addProperty("fireMode", fireMode);
            root.addProperty("shrinkPhase", shrinkPhase);
            root.addProperty("finalDuelActive", finalDuelActive);
            
            JsonObject rolesObj = new JsonObject();
            for (Map.Entry<String, Role> entry : playerNameToRole.entrySet()) {
                rolesObj.addProperty(entry.getKey(), entry.getValue().name());
            }
            root.add("playerNameToRole", rolesObj);
            
            JsonObject playersObj = new JsonObject();
            for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
                JsonObject pDataObj = new JsonObject();
                PlayerData pd = entry.getValue();
                pDataObj.addProperty("name", pd.getPlayerName());
                pDataObj.addProperty("role", pd.getRole().name());
                pDataObj.addProperty("lives", pd.getLives());
                
                // 保存 SHUBING 数据
                if (pd.getRole() == Role.SHUBING) {
                    JsonObject sb = new JsonObject();
                    sb.addProperty("paintingCount", pd.getPaintingCount());
                    
                    JsonArray ppArray = new JsonArray();
                    for (PlayerData.ShubingPainting p : pd.getPlacedPaintings()) {
                        JsonObject obj = new JsonObject();
                        if (p.pos != null) {
                            obj.addProperty("pos", true);
                            obj.addProperty("x", p.pos.getX());
                            obj.addProperty("y", p.pos.getY());
                            obj.addProperty("z", p.pos.getZ());
                        }
                        obj.addProperty("entityUuid", p.entityUuid.toString());
                        obj.addProperty("horizontal", p.horizontal);
                        obj.addProperty("corridorIndex", p.corridorIndex);
                        obj.addProperty("corridorEnd", p.corridorEnd);
                        obj.addProperty("hologramTag", p.hologramTag);
                        ppArray.add(obj);
                    }
                    sb.add("placedPaintings", ppArray);
                    
                    JsonArray cArray = new JsonArray();
                    for (PlayerData.ShubingCorridor c : pd.getCorridors()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("ox", c.origin.getX());
                        obj.addProperty("oy", c.origin.getY());
                        obj.addProperty("oz", c.origin.getZ());
                        obj.addProperty("vertical", c.vertical);
                        obj.addProperty("p0", c.painting0Index);
                        obj.addProperty("p1", c.painting1Index);
                        obj.addProperty("e0s", c.end0Sealed);
                        obj.addProperty("e1s", c.end1Sealed);
                        cArray.add(obj);
                    }
                    sb.add("corridors", cArray);
                    pDataObj.add("shubing", sb);
                }
                
                playersObj.add(entry.getKey().toString(), pDataObj);
            }
            root.add("players", playersObj);
            
            java.nio.file.Path file = server.getRunDirectory().resolve("config").resolve("yepvp_game_state.json");
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, new com.google.gson.Gson().toJson(root));
        } catch (Exception e) {
            System.err.println("[YePVP] Failed to save game state: " + e.getMessage());
        }
    }

    private void saveRoleSpawnPoints() {
        if (server == null) return;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<Role, BlockPos> entry : roleSpawnPoints.entrySet()) {
                JsonObject pos = new JsonObject();
                pos.addProperty("x", entry.getValue().getX());
                pos.addProperty("y", entry.getValue().getY());
                pos.addProperty("z", entry.getValue().getZ());
                root.add(entry.getKey().name(), pos);
            }
            java.nio.file.Path file = getRoleSpawnFile();
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            System.err.println("[YePVP] Failed to save role spawn points: " + e.getMessage());
        }
    }

    private void loadRoleSpawnPoints() {
        if (server == null) return;
        java.nio.file.Path file = getRoleSpawnFile();
        if (!java.nio.file.Files.exists(file)) return;
        try {
            String json = java.nio.file.Files.readString(file);
            JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (String key : root.keySet()) {
                try {
                    Role role = Role.valueOf(key);
                    JsonObject pos = root.getAsJsonObject(key);
                    roleSpawnPoints.put(role, new BlockPos(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt()));
                } catch (IllegalArgumentException ignored) {}
            }
            System.out.println("[YePVP] Loaded " + roleSpawnPoints.size() + " role spawn points");
        } catch (Exception e) {
            System.err.println("[YePVP] Failed to load role spawn points: " + e.getMessage());
        }
    }

    public void setSpawnPoint(String playerName, BlockPos pos) {
        for (PlayerData pd : players.values()) {
            if (pd.getPlayerName().equalsIgnoreCase(playerName)) {
                pd.setSpawnPoint(pos);
                return;
            }
        }
    }

    // ==================== 职业类型装备系统 ====================
    // Saber: 铜甲、铜剑
    // Lancer: 铜甲、2末影珍珠
    // Rider: 铜甲、铜矛
    // Archer: 弓、64箭
    // Caster: 击退2木棒、1隐身药水、5末影珍珠
    // Berserker: 铜剑
    // Assassin: 1隐身药水、3末影珍珠
    // Avenger: 无
    private void giveBaseEquipment(ServerPlayerEntity player, Role role) {
        var world = player.getEntityWorld();
        var registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var knockback = registry.getOptional(Enchantments.KNOCKBACK).orElse(null);

        String roleClass = getRoleClass(role);
        switch (roleClass) {
            case "saber" -> {
                equipCopperArmor(player);
                player.getInventory().insertStack(new ItemStack(Items.COPPER_SWORD));
            }
            case "lancer" -> {
                equipCopperArmor(player);
                player.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL, 2));
            }
            case "rider" -> {
                equipCopperArmor(player);
                player.getInventory().insertStack(new ItemStack(Items.COPPER_SPEAR));
            }
            case "archer" -> {
                player.getInventory().insertStack(new ItemStack(Items.BOW));
                player.getInventory().insertStack(new ItemStack(Items.ARROW, 64));
            }
            case "caster" -> {
                ItemStack stick = new ItemStack(Items.STICK);
                if (knockback != null) stick.addEnchantment(knockback, 2);
                player.getInventory().insertStack(stick);
                player.getInventory().insertStack(makeSplashPotion(net.minecraft.potion.Potions.INVISIBILITY));
                player.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL, 5));
            }
            case "berserker" -> {
                player.getInventory().insertStack(new ItemStack(Items.COPPER_SWORD));
            }
            case "assassin" -> {
                player.getInventory().insertStack(makeSplashPotion(net.minecraft.potion.Potions.INVISIBILITY));
                player.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL, 3));
            }
            case "avenger" -> {
                // 无装备
            }
        }
    }

    private void equipCopperArmor(ServerPlayerEntity player) {
        player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.COPPER_HELMET));
        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.COPPER_CHESTPLATE));
        player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(Items.COPPER_LEGGINGS));
        player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new ItemStack(Items.COPPER_BOOTS));
    }

    private ItemStack makeSplashPotion(net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion> potion) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(potion));
        return stack;
    }

    private static String getRoleClass(Role role) {
        return switch (role) {
            case BOBBY, RETOUR, SANCHEZ, YUSUI -> "saber";
            case DASHA, JANE -> "lancer";
            case XLL, LAYI -> "rider";
            case ST, NIHAO, POPCORN -> "archer";
            case ALLAND, MACHA, SHUBING -> "caster";
            case DAMAI, JVJV, YOUZHA -> "berserker";
            case JIEZU, HELI -> "assassin";
            case MAYPOOR -> "rider";
            default -> "saber"; // 未知职业默认Saber
        };
    }

    public void startGame() {
        startGame(false, false);
    }

    public void startGameRandomNoDuo() {
        if (server == null) return;
        if (mapResetInProgress) {
            broadcastMessage("§c§l【系统】§r§c 地图正在重置中，请稍后再开始游戏！");
            return;
        }
        // 全随机模式: ban掉双人组(BOBBY+RETOUR)
        Set<Role> duoRoles = LobbyManager.getDuoRoles();
        List<Role> availableRoles = new ArrayList<>();
        for (Role r : Role.values()) {
            if (!duoRoles.contains(r)) {
                availableRoles.add(r);
            }
        }
        Collections.shuffle(availableRoles);

        List<ServerPlayerEntity> onlinePlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
        Collections.shuffle(onlinePlayers);

        playerNameToRole.clear();
        for (int i = 0; i < onlinePlayers.size(); i++) {
            Role role = availableRoles.get(i % availableRoles.size());
            playerNameToRole.put(onlinePlayers.get(i).getGameProfile().name().toLowerCase(), role);
        }

        broadcastMessage("§6§l【模式】§r§e 全随机职业模式！(已排除双人组: 罪人+但丁)");
        startGame(true, false);
    }

    public void startGame(boolean randomRoles, boolean fireMode) {
        if (server == null) return;
        if (mapResetInProgress) {
            broadcastMessage("§c§l【系统】§r§c 地图正在重置中，请稍后再开始游戏！");
            return;
        }

        // 游戏开始: 清空地图编辑模式状态(编辑者随正常流程进入游戏)
        clearMapEdit();

        // 删除旧存档文件（游戏开始意味着新一局）
        try {
            java.nio.file.Path stateFile = server.getRunDirectory().resolve("config").resolve("yepvp_game_state.json");
            if (java.nio.file.Files.exists(stateFile)) {
                java.nio.file.Files.delete(stateFile);
            }
        } catch (Exception ignored) {}

        // ===== 第三步: 初始化游戏状态 =====
        this.gameActive = true;
        lobbyManager.removeSidebar(server);
        this.revealed = false;
        this.randomRoleMode = randomRoles;
        this.fireMode = fireMode;
        gameStartTick = server.getOverworld().getTime();
        MapDataCollector.reset();
        revealTick = gameStartTick + REVEAL_DELAY;
        shrinkPhase = 0;
        finalDuelActive = false;
        currentPoisonDps = 2.0f;
        lastPlayerHealth.clear();
        openedChests.clear();
        activeBosses.clear();
        pendingBossMarkers.clear();
        boss10Spawned = false; boss20Spawned = false; boss30Spawned = false;
        warden30Spawned = false;
        players.clear();

        // 设置世界边界(直径1000, 半径500)
        setupWorldBorder();

        // 设置游戏规则: 死亡不掉落 + 立即重生
        net.minecraft.world.rule.GameRules gameRules = server.getOverworld().getGameRules();
        gameRules.setValue(net.minecraft.world.rule.GameRules.KEEP_INVENTORY, true, server);
        gameRules.setValue(net.minecraft.world.rule.GameRules.DO_IMMEDIATE_RESPAWN, true, server);

        // 始终根据映射表分配，手动分配作为覆盖
        Map<String, Role> manualOverrides = new HashMap<>(playerNameToRole);
        playerNameToRole.clear();

        if (randomRoleMode) {
            if (manualOverrides.isEmpty()) {
                // 纯随机分配(含双人组)
                List<ServerPlayerEntity> onlinePlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
                Collections.shuffle(onlinePlayers);
                List<Role> allRoles = new ArrayList<>(Arrays.asList(Role.values()));
                Collections.shuffle(allRoles);

                for (int i = 0; i < onlinePlayers.size(); i++) {
                    Role role = allRoles.get(i % allRoles.size());
                    playerNameToRole.put(onlinePlayers.get(i).getGameProfile().name().toLowerCase(), role);
                }
                broadcastMessage("§6§l【模式】§r§e 开启随机职业模式！");
            } else {
                // startGameRandomNoDuo 已预分配角色
                playerNameToRole.putAll(manualOverrides);
            }
        } else {
            // 先根据映射表自动分配在线玩家
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                String name = p.getGameProfile().name().toLowerCase();
                Role role = PLAYER_ROLE_MAP.get(name);
                if (role != null) {
                    playerNameToRole.put(name, role);
                }
            }
            // 手动分配覆盖映射表
            playerNameToRole.putAll(manualOverrides);
        }

        if (fireMode) {
            broadcastMessage("§c§l【模式】§r§e 开启无限火力模式！(命数翻倍)");
        }

        // 收集参赛玩家
        List<ServerPlayerEntity> participants = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Role role = playerNameToRole.get(player.getGameProfile().name().toLowerCase());
            if (role != null) {
                participants.add(player);
            } else {
                // 非参赛玩家自动成为观察者
                player.changeGameMode(GameMode.SPECTATOR);
                player.sendMessage(Text.literal("§7你不是参赛玩家，已设为观察者模式"), false);
            }
        }

        // 均匀分配出生点
        List<BlockPos> spawnPoints = generateSpawnPoints(participants.size());

        // 第一遍: 创建PlayerData并分配出生点
        for (int i = 0; i < participants.size(); i++) {
            ServerPlayerEntity player = participants.get(i);
            Role role = playerNameToRole.get(player.getGameProfile().name().toLowerCase());
            PlayerData data = new PlayerData(player.getUuid(), player.getGameProfile().name(), role);

            // 特殊命数设置
            int lives = 5; // 默认5命
            if (role == Role.BOBBY) {
                lives = 5; // 双人组共用5命
            } else if (role == Role.RETOUR) {
                lives = 5; // 双人组共用5命
            } else if (role == Role.YUSUI) {
                lives = 5;
            } else if (role == Role.DAMAI) {
                lives = 3;
            } else if (role == Role.DASHA) {
                lives = 4;
            } else if (role == Role.POPCORN) {
                lives = 1;
            } else if (role == Role.JVJV) {
                lives = 2;
            } else if (role == Role.JANE) {
                lives = 5; // 默认5命
            }

            if (fireMode && lives != Integer.MAX_VALUE) {
                lives *= 2;
            }
            data.setLives(lives);

            // 分配出生点: 优先使用职业配置的出生点
            BlockPos roleSpawn = roleSpawnPoints.get(role);
            if (roleSpawn != null) {
                data.setSpawnPoint(roleSpawn);
            } else if (i < spawnPoints.size()) {
                data.setSpawnPoint(spawnPoints.get(i));
            }

            players.put(player.getUuid(), data);
        }

        // BOBBY和RETOUR出生在一起: RETOUR使用BOBBY的出生点
        BlockPos bobbySpawn = null;
        for (PlayerData pd : players.values()) {
            if (pd.getRole() == Role.BOBBY && pd.getSpawnPoint() != null) {
                bobbySpawn = pd.getSpawnPoint();
                break;
            }
        }
        if (bobbySpawn != null) {
            for (PlayerData pd : players.values()) {
                if (pd.getRole() == Role.RETOUR) {
                    pd.setSpawnPoint(bobbySpawn);
                }
            }
        }

        // 第二遍: 初始化角色并发送消息
        for (int i = 0; i < participants.size(); i++) {
            ServerPlayerEntity player = participants.get(i);
            PlayerData data = players.get(player.getUuid());
            if (data == null) continue;
            Role role = data.getRole();

            initializePlayerForRole(player, data);

            player.sendMessage(Text.literal("§a§l【夜喵喵愚人节大乱斗】§r§a 游戏开始！"), false);
            player.sendMessage(Text.literal("§a你的职业: §e§l" + role.getDisplayName()), false);
            player.sendMessage(Text.literal("§7" + role.getDescription()), false);
            if (role == Role.BOBBY) {
                player.sendMessage(Text.literal("§a你有 §e无限 §a条命，但被杀后需要但丁复活你！与但丁距离>100格时每秒+1罪孽"), false);
            } else if (role == Role.RETOUR) {
                player.sendMessage(Text.literal("§c你只有 §e2 §c条命，珍惜生命！你有额外10心生命值。"), false);
            } else {
                player.sendMessage(Text.literal("§a你有 §e" + data.getLives() + " §a条命，祝你好运！"), false);
            }

        }

        // ST: 看不到其他玩家名字和边框 → 用Team系统实现
        setupStNametagHiding();

        // 发送开局说明UI给每个玩家
        sendGameStartScreenToAll();
    }

    private void sendGameStartScreenToAll() {
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
            if (p == null) continue;
            PlayerData pd = entry.getValue();
            JsonObject json = new JsonObject();
            json.addProperty("roleName", pd.getRole().getDisplayName());
            json.addProperty("roleDesc", pd.getRole().getDescription());
            json.addProperty("lives", pd.getLives());
            json.addProperty("playerName", pd.getPlayerName());

            // 规则列表
            JsonArray rules = new JsonArray();
            rules.add("§e地图半径 1000格，每5分钟缩小100格");
            rules.add("§e复活后10秒无敌，最后存活者获胜");
            rules.add("§e使用能力即刻被揭露，公布所有玩家职业");
            rules.add("§c管理者可随时揭露玩家职业");
            rules.add("§a每个职业有特殊能力，查看左侧能力栏");
            json.add("rules", rules);

            NetworkHandler.sendGameStartScreen(p, json.toString());
        }
    }

    // 固定玩家ID→职业映射表
    private static final Map<String, Role> PLAYER_ROLE_MAP = new HashMap<>();
    static {
        PLAYER_ROLE_MAP.put("unlondonbob", Role.BOBBY);
        PLAYER_ROLE_MAP.put("retour_", Role.RETOUR);
        PLAYER_ROLE_MAP.put("fenrir_m", Role.SANCHEZ);
        PLAYER_ROLE_MAP.put("123", Role.YUSUI);
        PLAYER_ROLE_MAP.put("gkbigshark", Role.DASHA);
        PLAYER_ROLE_MAP.put("xlrui_k", Role.XLL);
        PLAYER_ROLE_MAP.put("sand_tangerine", Role.ST);
        PLAYER_ROLE_MAP.put("nihao", Role.NIHAO);
        PLAYER_ROLE_MAP.put("popcorn", Role.POPCORN);
        PLAYER_ROLE_MAP.put("alian_d", Role.ALLAND);
        PLAYER_ROLE_MAP.put("matchaparfait", Role.MACHA);
        PLAYER_ROLE_MAP.put("gavinsiteruofu", Role.DAMAI);
        PLAYER_ROLE_MAP.put("meguminhamud", Role.JVJV);
        PLAYER_ROLE_MAP.put("spiderhachis", Role.JIEZU);
        PLAYER_ROLE_MAP.put("heli6112", Role.HELI);
        PLAYER_ROLE_MAP.put("_janfish17_", Role.JANE);
        PLAYER_ROLE_MAP.put("gofaltemrrane", Role.LAYI);
    }

    // 自动根据固定映射表分配职业给在线玩家
    private void autoAssignRandomRoles() {
        List<ServerPlayerEntity> onlinePlayers = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (onlinePlayers.isEmpty()) return;

        int assigned = 0;
        for (ServerPlayerEntity player : onlinePlayers) {
            String name = player.getGameProfile().name().toLowerCase();
            Role role = PLAYER_ROLE_MAP.get(name);
            if (role != null) {
                playerNameToRole.put(name, role);
                assigned++;
            }
        }

        if (assigned > 0) {
            broadcastMessage("§a§l【自动分配】§r§e 已根据ID映射表分配 §a" + assigned + " §e名玩家的职业！");
        } else {
            // 没有匹配到任何映射，降级为随机分配
            Role[] allRoles = Role.values();
            List<Role> availableRoles = new ArrayList<>(Arrays.asList(allRoles));
            Random rand = new Random();
            Collections.shuffle(availableRoles, rand);
            for (int i = 0; i < onlinePlayers.size(); i++) {
                ServerPlayerEntity player = onlinePlayers.get(i);
                Role role = availableRoles.get(i % availableRoles.size());
                playerNameToRole.put(player.getGameProfile().name().toLowerCase(), role);
            }
            broadcastMessage("§a§l【自动分配】§r§e 无匹配ID，已随机分配职业给所有在线玩家！");
        }
    }

    // 设置世界边界
    private void setupWorldBorder() {
        ServerWorld world = server.getOverworld();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(MAP_CENTER_X, MAP_CENTER_Z);
        border.setSize(MAP_RADIUS * 2); // 直径 = 半径*2
        border.setDamagePerBlock(0.0); // 伤害由tick()手动处理
        border.setSafeZone(0.0);
    }

    // 均匀分配出生点（圆形分布，避免大海和虚空）
    private List<BlockPos> generateSpawnPoints(int playerCount) {
        List<BlockPos> points = new ArrayList<>();
        if (playerCount == 0) return points;
        ServerWorld world = server.getOverworld();
        WorldBorder border = world.getWorldBorder();
        double spawnRadius = MAP_RADIUS * 0.7; // 生成在边界70%半径处
        double cx = border.getCenterX();
        double cz = border.getCenterZ();
        Random rand = new Random();
        for (int i = 0; i < playerCount; i++) {
            double angle = 2 * Math.PI * i / playerCount;
            int x = (int) (cx + spawnRadius * Math.cos(angle));
            int z = (int) (cz + spawnRadius * Math.sin(angle));
            // 尝试找安全位置（不在大海上），最多重试20次
            BlockPos safe = findSafeLandSpawn(world, x, z, rand);
            points.add(safe);
        }
        return points;
    }

    public BlockPos findSafeLandSpawnPublic(ServerWorld world, int baseX, int baseZ, Random rand) {
        return findSafeLandSpawn(world, baseX, baseZ, rand);
    }

    // 找到安全的陆地出生点（不在大海/河流/水中、不在虚空）
    private BlockPos findSafeLandSpawn(ServerWorld world, int baseX, int baseZ, Random rand) {
        for (int attempt = 0; attempt < 50; attempt++) {
            int range = 50 + attempt * 10; // 随重试次数扩大搜索范围
            int x = baseX + (attempt == 0 ? 0 : rand.nextInt(range * 2) - range);
            int z = baseZ + (attempt == 0 ? 0 : rand.nextInt(range * 2) - range);
            int y = findSafeY(world, x, z);
            if (y <= 0 || y < 55) continue; // 虚空或低于55格，跳过
            BlockPos pos = new BlockPos(x, y, z);
            // 检查生态群系：排除海洋和河流
            var biomeEntry = world.getBiome(pos);
            if (biomeEntry.matchesKey(BiomeKeys.OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.DEEP_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.COLD_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.DEEP_COLD_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.FROZEN_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.LUKEWARM_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.DEEP_LUKEWARM_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.WARM_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.RIVER) ||
                biomeEntry.matchesKey(BiomeKeys.FROZEN_RIVER)) {
                continue;
            }
            // 检查脚下和脚部位置不是水/冰
            net.minecraft.block.Block belowBlock = world.getBlockState(pos.down()).getBlock();
            net.minecraft.block.Block feetBlock = world.getBlockState(pos).getBlock();
            if (belowBlock == net.minecraft.block.Blocks.WATER ||
                belowBlock == net.minecraft.block.Blocks.ICE ||
                belowBlock == net.minecraft.block.Blocks.BLUE_ICE ||
                belowBlock == net.minecraft.block.Blocks.PACKED_ICE ||
                feetBlock == net.minecraft.block.Blocks.WATER) {
                continue;
            }
            return pos;
        }
        // 50次都没找到安全位置，强制返回一个高于虚空的位置
        int y = findSafeY(world, baseX, baseZ);
        if (y <= 0) y = 64;
        return new BlockPos(baseX, y, baseZ);
    }

    public int findSafeY(ServerWorld world, int x, int z) {
        BlockPos surface = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        int y = surface.getY();
        // 防止虚空（y<=0时返回安全高度）
        if (y <= 0) {
            // 尝试从高处往下扫描找到实心方块
            for (int scanY = 256; scanY > 0; scanY--) {
                if (!world.getBlockState(new BlockPos(x, scanY, z)).isAir()) {
                    return scanY + 1;
                }
            }
            return 64; // 实在找不到，返回默认高度
        }
        return y;
    }

    public void stopGame() {
        gameActive = false;
        revealed = false;
        victorySceneActive = false;
        shrinkPhase = 0;
        finalDuelActive = false;
        currentPoisonDps = 2.0f;
        lastPlayerHealth.clear();

        // 清理POPCORN躯壳实体+取消区块强制加载
        if (server != null) {
            ServerWorld world = server.getOverworld();
            for (Map.Entry<UUID, net.minecraft.entity.Entity> entry : popcornBodyEntities.entrySet()) {
                net.minecraft.entity.Entity body = entry.getValue();
                if (body != null) {
                    net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(body.getBlockPos());
                    world.setChunkForced(cp.x, cp.z, false);
                    body.discard();
                }
            }
        }
        popcornBodyEntities.clear();

        // 清理SANCHEZ狼群和LAYI导弹
        if (server != null) {
            ServerWorld world = server.getOverworld();
            for (PlayerData pd : players.values()) {
                // SANCHEZ: 清理头狼+狼群
                if (pd.getRole() == Role.SANCHEZ) {
                    if (pd.getAlphaWolfEntityUuid() != null) {
                        net.minecraft.entity.Entity e = world.getEntity(pd.getAlphaWolfEntityUuid());
                        if (e != null) e.discard();
                    }
                    for (UUID wolfId : pd.getPackWolfUuids()) {
                        net.minecraft.entity.Entity e = world.getEntity(wolfId);
                        if (e != null) e.discard();
                    }
                }
                // LAYI: 清理导弹实体
                if (pd.getRole() == Role.LAYI) {
                    for (UUID missileId : pd.getLayiMissileEntities()) {
                        net.minecraft.entity.Entity e = world.getEntity(missileId);
                        if (e != null) e.discard();
                    }
                }
            }
        }

        players.clear();
        playerNameToRole.clear();
        fullyHiddenPlayers.clear();
        // 清理ALLAND颜料系统
        paintProjectiles.clear();
        paintProjectileOwners.clear();
        restoreAllPaintZones();
        paintZones.clear();
        delayedActions.clear();
        // 清理Boss实体+取消区块强制加载
        if (server != null) {
            ServerWorld world = server.getOverworld();
            for (BossData bd : activeBosses) {
                if (bd.entityUuid != null) {
                    net.minecraft.entity.Entity e = world.getEntity(bd.entityUuid);
                    if (e != null) e.discard();
                }
                net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(bd.spawnPos);
                world.setChunkForced(cp.x, cp.z, false);
            }
        }
        activeBosses.clear();
        pendingBossMarkers.clear();
        openedChests.clear();
        boss10Spawned = false; boss20Spawned = false; boss30Spawned = false;
        warden30Spawned = false;
        // 同步空的隐身列表给所有客户端，解除所有隐身渲染
        if (server != null) {
            byte[] emptyData = new byte[]{0, 0, 0, 0}; // count=0
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                NetworkHandler.sendHiddenPlayers(p, emptyData);
            }
        }
        broadcastMessage("§a§l【夜喵喵愚人节大乱斗】§r§c 游戏结束！");

        // 删除游戏存档
        try {
            java.nio.file.Path stateFile = server.getRunDirectory().resolve("config").resolve("yepvp_game_state.json");
            if (java.nio.file.Files.exists(stateFile)) {
                java.nio.file.Files.delete(stateFile);
            }
        } catch (Exception ignored) {}

        // 传送所有玩家回大厅
        if (server != null) {
            lobbyManager.teleportAllToLobby(server);
            broadcastMessage("§b§l【系统】§r§a 已返回大厅，等待下一局游戏...");

            // 后台异步重置地图
            mapResetInProgress = true;
            final MinecraftServer srv = server;
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 等2秒确保玩家完全离开主世界
                    // 地图重置需要在主线程执行(涉及世界操作)
                    srv.execute(() -> {
                        broadcastMessage("§b§l【系统】§r§e 正在后台重置地图...");
                        boolean ok = lobbyManager.resetMainWorldMap(srv);
                        mapResetInProgress = false;
                        if (ok) {
                            broadcastMessage("§b§l【系统】§r§a 地图重置完成！可以开始下一局了！");
                        } else {
                            broadcastMessage("§b§l【系统】§r§c 地图重置失败，使用当前地图。");
                        }
                    });
                } catch (Exception e) {
                    mapResetInProgress = false;
                    System.err.println("[YePVP] Map reset error: " + e.getMessage());
                }
            }, "YePVP-MapReset").start();
        }
    }

    public void tick() {
        if (!gameActive || server == null) return;

        long currentTick = server.getOverworld().getTime();
        long elapsed = currentTick - gameStartTick;

        // ===== 多阶段缩圈 =====
        tickShrinkPhases(elapsed);

        // 非参赛玩家加入时自动观察者
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (players.get(p.getUuid()) == null && !p.isSpectator()) {
                p.changeGameMode(GameMode.SPECTATOR);
                p.sendMessage(Text.literal("§7游戏进行中，你已设为观察者模式"), false);
            }
        }

        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (player == null) continue;

            // 重生安全检查: 仅在刚重生后立即触发一次, 避免无限循环传送(终圈bug)
            if (!player.isSpectator() && pd.getNeedsBorderSafeTeleportUntilTick() > 0
                    && currentTick <= pd.getNeedsBorderSafeTeleportUntilTick()) {
                WorldBorder playerBorder = server.getOverworld().getWorldBorder();
                if (!playerBorder.contains(player.getBlockPos())) {
                    double bcx = playerBorder.getCenterX();
                    double bcz = playerBorder.getCenterZ();
                    double halfSize = playerBorder.getSize() / 2.0;
                    ServerWorld safeWorld = server.getOverworld();
                    BlockPos safePos;
                    if (halfSize < 15.0) {
                        // 圈太小: 直接传送到圈中心
                        int cy = findSafeY(safeWorld, (int) bcx, (int) bcz);
                        if (cy < 55) cy = (int) MAP_CENTER_Y;
                        safePos = new BlockPos((int) bcx, cy, (int) bcz);
                    } else {
                        // 在圈内随机选点(留5格余量), 然后用findSafeLandSpawn微调, 最后clamp回圈内
                        Random safeRand = new Random();
                        double safeRange = halfSize - 5.0;
                        int safeX = (int) (bcx + (safeRand.nextDouble() - 0.5) * 2 * safeRange);
                        int safeZ = (int) (bcz + (safeRand.nextDouble() - 0.5) * 2 * safeRange);
                        safeWorld.getChunk(safeX >> 4, safeZ >> 4);
                        BlockPos candidate = findSafeLandSpawn(safeWorld, safeX, safeZ, safeRand);
                        // clamp回圈内
                        int cx = (int) Math.max(bcx - safeRange, Math.min(bcx + safeRange, candidate.getX()));
                        int cz = (int) Math.max(bcz - safeRange, Math.min(bcz + safeRange, candidate.getZ()));
                        int cy = candidate.getY();
                        if (cx != candidate.getX() || cz != candidate.getZ()) {
                            cy = findSafeY(safeWorld, cx, cz);
                            if (cy < 55) cy = (int) MAP_CENTER_Y;
                        }
                        safePos = new BlockPos(cx, cy, cz);
                    }
                    player.teleport(safeWorld, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5,
                            Set.of(), player.getYaw(), player.getPitch(), false);
                    pd.setSpawnPoint(safePos);
                    sendTip(player, "§e§l你已被自动传送至圈内安全位置！");
                }
                // 立即清除标志: 只尝试一次, 不无限重试(否则终圈半径很小时会无限快递)
                pd.setNeedsBorderSafeTeleportUntilTick(0);
            }

            // 画中世界进入后20秒摔落免疫
            if (pd.getCorridorFallImmuneUntilTick() > currentTick) {
                player.fallDistance = 0;
            }

            // 毒圈伤害(自定义, 每秒)
            if (currentTick % 20 == 0 && currentPoisonDps > 0) {
                WorldBorder border = server.getOverworld().getWorldBorder();
                if (!border.contains(player.getBlockPos())) {
                    player.damage(server.getOverworld(), server.getOverworld().getDamageSources().outsideBorder(), currentPoisonDps);
                }
            }

            // 终局对决: 禁止回血
            if (finalDuelActive && !player.isSpectator()) {
                Float lastHp = lastPlayerHealth.get(pd.getPlayerUuid());
                if (lastHp != null && player.getHealth() > lastHp) {
                    player.setHealth(lastHp);
                }
                lastPlayerHealth.put(pd.getPlayerUuid(), player.getHealth());
            }

            // 晕眩定身: 每tick锁死速度(无法移动和攻击)
            if (pd.isStunned(currentTick)) {
                player.setVelocity(0, player.getVelocity().y, 0);
                player.velocityDirty = true;
            }

            // 复活无敌期间不给角色效果（保持无敌状态）
            if (pd.isInvincible(currentTick)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false, true));
                continue;
            }

            tickRoleAbilities(player, pd, currentTick);
        }

        // 昼夜加速: 5分钟完成一次昼夜交替(正常20分钟, 加速4倍, 每tick额外+3)
        ServerWorld overworld = server.getOverworld();
        overworld.setTimeOfDay(overworld.getTimeOfDay() + 3);

        // MACHA: 监守者生命周期管理(每秒检查一次)
        if (currentTick % 20 == 0) {
            tickMachaWardens(currentTick);
            tickYouzhaCats(currentTick);
        }

        // 隐身玩家同步(每10tick同步一次)
        if (currentTick % 10 == 0) {
            syncHiddenPlayers();
        }

        // 地图数据收集(每秒扫描, 渐进式覆盖全地图)
        if (currentTick % 20 == 0) {
            MapDataCollector.tickCollect(server.getOverworld());
            
            // 每3秒(60tick)保存一次状态
            if (currentTick % 60 == 0) {
                saveGameState();
            }
        }

        // 地图数据推送到所有玩家(每2秒, HUD小地图+全屏地图共用)
        // 优化: 共享的颜色块/全局附加数据每周期只构建+压缩一次, 所有玩家复用;
        //       仅玩家标记段(可见性因人而异)按玩家单独构建。避免"每2秒 × 人数 × 全图序列化+gzip"的主线程尖峰。
        if (currentTick % 40 == 0) {
            net.minecraft.server.world.ServerWorld mapWorld = server.getOverworld();
            byte[] colorMember = MapDataCollector.buildColorMemberGzipped(mapWorld);
            byte[] suffixMember = MapDataCollector.buildSuffixMemberGzipped(mapWorld);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                byte[] markersMember = MapDataCollector.buildMarkersMemberGzipped(mapWorld, p.getUuid());
                byte[] mapData = MapDataCollector.concatMembers(colorMember, markersMember, suffixMember);
                baby.sv.yepvpfabirc.network.NetworkHandler.sendMapData(p, mapData);
            }
        }

        // ALLAND颜料区域过期清理
        tickPaintZones(currentTick);
        // 延迟效果执行
        tickDelayedActions(currentTick);

        // Boss系统
        tickBossSystem(elapsed, currentTick);

        // 每10秒检测所有玩家重生点是否在圈内, 不在则重新分配
        if (currentTick % 200 == 0) {
            tickSpawnPointValidation();
        }

        // LAYI导弹独立tick: 不依赖蜡翼是否存活/飞行, 定时爆炸
        for (PlayerData pd : players.values()) {
            if (pd.getRole() != Role.LAYI) continue;
            if (pd.getLayiMissileEntities().isEmpty()) continue;
            ServerPlayerEntity layiPlayer = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            // layiPlayer可能为null(离线), 导弹仍继续倒计时爆炸
            tickLayiMissiles(layiPlayer, pd, currentTick);
        }

        checkGameEnd();
    }

    // 多阶段缩圈逻辑
    private void tickShrinkPhases(long elapsed) {
        // Phase 1-3: 缩圈
        for (int i = 0; i < 3; i++) {
            int phaseNum = i + 1;
            long startTick = PHASE_START_TICK[i];
            long duration = PHASE_DURATION[i];
            // 30秒预警
            if (elapsed == startTick - 600) {
                broadcastMessage("§e§l【警告】§r§e 第" + phaseNum + "阶段毒圈将在 §c30秒 §e后开始缩小！");
            }
            if (elapsed == startTick - 200) {
                broadcastMessage("§c§l【警告】§r§c 第" + phaseNum + "阶段毒圈将在 §e10秒 §c后缩小！请远离边缘！");
            }
            // 开始缩圈
            if (elapsed == startTick && shrinkPhase < phaseNum) {
                shrinkPhase = phaseNum;
                currentPoisonDps = PHASE_POISON_DPS[i];
                WorldBorder border = server.getOverworld().getWorldBorder();
                double newDiameter = border.getSize() - PHASE_DIAMETER[i];
                if (newDiameter < 10) newDiameter = 10;
                border.interpolateSize(border.getSize(), newDiameter, duration, server.getOverworld().getTime());
                broadcastMessage("§c§l【第" + phaseNum + "阶段缩圈】§r§c 世界边界正在缩小！目标直径: §e" + (int)newDiameter + "格 §c毒伤: §e" + ((int)(currentPoisonDps/2)) + "心/秒");
            }
        }

        // Phase 4: 终局对决(30min)
        if (elapsed == PHASE_START_TICK[3] - 600) {
            broadcastMessage("§4§l【警告】§r§c §l终局对决 §c将在 §e30秒 §c后开始！届时无法以任何方式恢复生命值！");
        }
        if (elapsed == PHASE_START_TICK[3] && shrinkPhase < 4) {
            shrinkPhase = 4;
            finalDuelActive = true;
            currentPoisonDps = PHASE_POISON_DPS[3];
            // 初始化所有玩家当前血量
            for (PlayerData pd : players.values()) {
                if (!pd.isAlive()) continue;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                if (p != null) lastPlayerHealth.put(pd.getPlayerUuid(), p.getHealth());
            }
            broadcastMessage("§4§l═══════ 终局对决 ═══════");
            broadcastMessage("§c§l所有玩家无法以任何方式恢复生命值！持续10分钟！");
            broadcastMessage("§4§l══════════════════════");
        }

        // Phase 5: 最终缩圈(40min)
        if (elapsed == PHASE_START_TICK[4] - 600) {
            broadcastMessage("§4§l【警告】§r§c 最终缩圈将在 §e30秒 §c后开始！整个地图将被毒圈覆盖！");
        }
        if (elapsed == PHASE_START_TICK[4] && shrinkPhase < 5) {
            shrinkPhase = 5;
            finalDuelActive = false; // 终局对决结束
            currentPoisonDps = PHASE_POISON_DPS[4];
            WorldBorder border = server.getOverworld().getWorldBorder();
            border.interpolateSize(border.getSize(), 10, PHASE_DURATION[4], server.getOverworld().getTime());
            broadcastMessage("§4§l【最终缩圈】§r§c 整个地图正在被毒圈覆盖！毒伤: §e8心/秒 §c无处可逃！");
        }
    }

    private void tickRoleAbilities(ServerPlayerEntity player, PlayerData data, long currentTick) {
        switch (data.getRole()) {
            case XLL -> tickXll(player, data, currentTick);
            case JIEZU -> tickJiezu(player, data, currentTick);
            case YUSUI -> tickYusui(player, data, currentTick);
            case DASHA -> tickDasha(player, data, currentTick);
            case NIHAO -> tickNihao(player, data, currentTick);
            case ST -> tickSt(player, data, currentTick);
            case DAMAI -> tickDamai(player, data, currentTick);
            case BOBBY -> tickBobby(player, data);
            case RETOUR -> tickRetour(player, data);
            case ALLAND -> tickAlland(player, data, currentTick);
            case HELI -> tickHeli(player, data, currentTick);
            case MACHA -> tickMacha(player, data, currentTick);
            case JVJV -> tickJvjv(player, data, currentTick);
            case POPCORN -> tickPopcorn(player, data, currentTick);
            case SANCHEZ -> tickSanchez(player, data, currentTick);
            case SHUBING -> tickShubing(player, data, currentTick);
            case JANE -> tickJane(player, data, currentTick);
            case YOUZHA -> tickYouzha(player, data, currentTick);
            case LAYI -> tickLayi(player, data, currentTick);
            case MAYPOOR -> tickMaypoor(player, data, currentTick);
            default -> {}
        }
    }

    // XLL: 玻璃正方体(失明+10%maxHP真伤/秒) + 玻璃长道上速度5/他人失明
    private void tickXll(ServerPlayerEntity player, PlayerData data, long currentTick) {
        ServerWorld world = player.getEntityWorld();

        // === 主动1: 玻璃正方体 ===
        if (data.isGlassCubeActive()) {
            BlockPos center = data.getGlassCubeCenter();
            if (center != null && currentTick < data.getGlassCubeEndTick()) {
                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (p == player) continue;
                    PlayerData pd = players.get(p.getUuid());
                    if (pd == null || !pd.isAlive()) continue;
                    if (Math.abs(p.getX() - center.getX()) <= 5 && Math.abs(p.getY() - center.getY()) <= 5 && Math.abs(p.getZ() - center.getZ()) <= 5) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, true));
                        if (currentTick % 20 == 0) {
                            // 每秒扣10%最大生命值(真实伤害)
                            float dmg = p.getMaxHealth() * 0.10f;
                            p.damage(world, world.getDamageSources().magic(), dmg);
                        }
                    }
                }
            } else if (currentTick >= data.getGlassCubeEndTick()) {
                if (center != null) {
                    SkillHandler.removeGlassCube(world, center);
                }
                data.setGlassCubeActive(false);
                data.setGlassCubeCenter(null);
            }
        }

        // === 被动: 常驻速度1 + 玻璃上效果 ===
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false, true)); // 常驻速度1
        // XLL自己在自己的任意玻璃方块上→速度3
        // 其他玩家在XLL的玻璃方块上→失明
        BlockPos feetPos = player.getBlockPos();
        boolean onOwnGlass = data.getGlassRoadBlocks().contains(feetPos)
                || data.getGlassRoadBlocks().contains(feetPos.down());
        if (onOwnGlass) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, false, false, true)); // 速度3(覆盖常驻速度1)
        }

        // 检查其他玩家是否站在XLL的玻璃上
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p == player) continue;
            PlayerData pd = players.get(p.getUuid());
            if (pd == null || !pd.isAlive()) continue;
            BlockPos otherFeet = p.getBlockPos();
            boolean onXllGlass = data.getGlassRoadBlocks().contains(otherFeet)
                    || data.getGlassRoadBlocks().contains(otherFeet.down());
            if (onXllGlass) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, true));
            }
        }
    }

    // JIEZU: 夜晚隐身+夜视+力量2+速度3+爬墙, 蛛网极速, 每1分钟获得蛛网, 50格探测, 30秒回白天
    private void tickJiezu(ServerPlayerEntity player, PlayerData data, long currentTick) {
        ServerWorld world = player.getEntityWorld();
        long dayTime = world.getTimeOfDay() % 24000;
        boolean isNight = dayTime >= 13000 && dayTime <= 23000;

        if (isNight) {
            // 夜晚: 完全隐身(通过自定义网络包实现,不用原版INVISIBILITY)+夜视+力量2(amp1)+速度3(amp2)
            // 隐身由syncHiddenPlayers()处理,客户端完全取消渲染(含装备+手持)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, false, false, true));

            // 爬墙: 靠墙时给予向上速度(与跑步速度一致，考虑重力抵消需设为~0.36)
            if (!player.isOnGround()) {
                BlockPos pp = player.getBlockPos();
                boolean touchingWall = !world.getBlockState(pp.north()).isAir() ||
                        !world.getBlockState(pp.south()).isAir() ||
                        !world.getBlockState(pp.east()).isAir() ||
                        !world.getBlockState(pp.west()).isAir();
                if (touchingWall && player.horizontalCollision) {
                    player.setVelocity(player.getVelocity().x, 0.2, player.getVelocity().z);
                    player.velocityDirty = true;
                    player.fallDistance = 0;
                }
            }
        }

        // 蛛网中极快移速: 检测是否在蛛网方块中
        BlockPos feetPos = player.getBlockPos();
        if (world.getBlockState(feetPos).isOf(net.minecraft.block.Blocks.COBWEB) ||
                world.getBlockState(feetPos.up()).isOf(net.minecraft.block.Blocks.COBWEB)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 5, 4, false, false, true));
        }

        // 每1分钟获得一个特殊蛛网(最多放3个, 但可以继续获取物品)
        if (currentTick - data.getLastWebGainTick() >= 1200) { // 1分钟=1200tick
            data.setLastWebGainTick(currentTick);
            ItemStack webItem = new ItemStack(Items.COBWEB, 1);
            webItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§8§l特殊蛛网").styled(s -> s.withItalic(false)));
            player.getInventory().insertStack(webItem);
            sendTip(player, "§8获得了一个特殊蛛网！(已放置" + data.getWebPositions().size() + "/3)");
        }

        // 每30秒获得一个蜘蛛茧(晕眩雪球, 命中敌人晕眩5秒)
        if (currentTick - data.getLastCocoonGainTick() >= 600) { // 30秒=600tick
            data.setLastCocoonGainTick(currentTick);
            ItemStack cocoon = new ItemStack(Items.SNOWBALL, 1);
            cocoon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§8§l蜘蛛茧").styled(s -> s.withItalic(false)));
            player.getInventory().insertStack(cocoon);
            sendTip(player, "§8获得了一个蜘蛛茧！(命中晕眩5秒)");
        }

        // 特殊蛛网50格内有玩家提示(每2秒检查一次)
        if (currentTick % 40 == 0 && !data.getWebPositions().isEmpty()) {
            for (BlockPos webPos : data.getWebPositions()) {
                for (PlayerData pd : players.values()) {
                    if (pd == data || !pd.isAlive()) continue;
                    ServerPlayerEntity other = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (other == null) continue;
                    double dist = other.getBlockPos().getSquaredDistance(webPos);
                    if (dist <= 2500) { // 50格²=2500
                        player.sendMessage(Text.literal("§c§l[蛛网警报] §r§e蛛网(" +
                                webPos.getX() + "," + webPos.getY() + "," + webPos.getZ() +
                                ") §c附近检测到玩家！"), false);
                        break; // 一个蛛网只提示一次
                    }
                }
            }
        }

        // 强制夜晚30秒后回白天
        if (data.getJiezuNightEndTick() > 0 && currentTick >= data.getJiezuNightEndTick()) {
            data.setJiezuNightEndTick(0);
            world.setTimeOfDay(6000); // 中午
            broadcastMessage("§e§l[节足动物] §r§a夜晚结束，时间恢复为白天！");
        }
    }

    // YUSUI: 速度2 + 每1分钟给予特殊雪球(仅场上无男娘时) + 力量=场上男娘数量
    private void tickYusui(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 被动: 速度2
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, false, false, true));

        // 被动: 每1分钟获得1枚男娘雪球 — 只在场上没有存活男娘时触发
        if (currentTick - data.getLastSnowballGiveTick() >= 1200) { // 1分钟
            boolean hasAliveNanniang = players.values().stream().anyMatch(pd -> pd.isNanniang() && pd.isAlive());
            if (!hasAliveNanniang) {
                data.setLastSnowballGiveTick(currentTick);
                ItemStack snowball = new ItemStack(Items.SNOWBALL, 1);
                snowball.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§l男娘雪球").styled(s -> s.withItalic(false)));
                player.getInventory().insertStack(snowball);
                sendTip(player, "§d获得了男娘雪球！");
            }
        }

        // 被动: 基于场上存活男娘数量获得等值力量
        int nanniangCount = 0;
        for (PlayerData pd : players.values()) {
            if (pd.isNanniang() && pd.isAlive()) nanniangCount++;
        }
        if (nanniangCount > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, nanniangCount - 1, false, false, true));
        }
    }

    // DASHA: 水中/雨中全buff(隐身+水下呼吸+速掘6+夜视+力量2+深海探索者3+海豚恩赐)，离水窒息，攻击/冲刺显形2秒
    private void tickDasha(ServerPlayerEntity player, PlayerData data, long currentTick) {
        boolean nowInWater = player.isTouchingWater() || player.isSubmergedInWater();
        boolean inRain = player.getEntityWorld().isRaining() && player.getEntityWorld().isSkyVisible(player.getBlockPos());
        boolean wetState = nowInWater || inRain;
        data.setInWater(wetState);

        if (wetState) {
            // 水中/雨中: 完全隐身+水下呼吸+水下速掘6+夜视+力量2+海豚恩赐
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 40, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 40, 5, false, false, true)); // 水下速掘6(amp5)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, false, false, true)); // 力量2(amp1)
            if (nowInWater) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 40, 2, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 3, false, false, true)); // 生命恢复4(amp3)
            }

            // 在水中恢复氧气
            if (nowInWater) {
                player.setAir(player.getMaxAir());
            }

            // 攻击/冲刺后显形2秒: 检测激流冲刺
            if (((baby.sv.yepvpfabirc.mixin.LivingEntityAccessor) player).getRiptideTicks() > 0) {
                data.setDashaVisibleUntilTick(currentTick + 40); // 2秒显形
            }

            // 显形判定: 如果在显形期内，给发光
            // 隐身由syncHiddenPlayers()处理,不用原版INVISIBILITY(装备也要隐藏)
            if (currentTick < data.getDashaVisibleUntilTick()) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 5, 0, false, false, true));
            }

            data.setLastOutOfWaterTime(0);
        } else {
            // 离水窒息: MC每tick自动回复氧气，必须强制覆盖
            if (data.getLastOutOfWaterTime() == 0) data.setLastOutOfWaterTime(currentTick);
            long outTime = currentTick - data.getLastOutOfWaterTime();
            if (outTime > 20) { // 1秒后开始窒息
                // 根据离水时间计算剩余氧气(maxAir=300, 每tick减2, 约7.5秒耗尽)
                int elapsed = (int)(outTime - 20);
                int air = Math.max(player.getMaxAir() - elapsed * 2, -20);
                player.setAir(air);
                if (air <= 0 && elapsed % 20 == 0) { // 氧气耗尽后每秒受2点溺水伤害
                    ServerWorld dw = (ServerWorld) player.getEntityWorld();
                    player.damage(dw, dw.getDamageSources().drown(), 2.0f);
                }
            }
        }
    }

    // NIHAO: 问好计时器 — 超时秒杀目标 + 给目标持续HUD倒计时 + 每10分钟全体问好(带helloback)
    private void tickNihao(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 初始化lastHelloBroadcastTick为游戏开始时间(确保开局10分钟后才触发第一次)
        if (data.getLastHelloBroadcastTick() < 0) {
            data.setLastHelloBroadcastTick(gameStartTick);
        }

        // 每10分钟自动全体问好(带helloback机制)
        if (data.getBroadcastHelloCodes().isEmpty()
                && currentTick - data.getLastHelloBroadcastTick() >= 12000) { // 10分钟=12000tick
            data.setLastHelloBroadcastTick(currentTick);
            int timer = data.getHelloTimerSeconds();
            java.util.Random rand = new java.util.Random();

            // 给所有存活的非NIHAO玩家生成验证码
            for (PlayerData pd : players.values()) {
                if (pd == data) continue;
                if (!pd.isAlive()) continue;
                if (pd.getRole() == null) continue;
                int code = 100000 + rand.nextInt(900000);
                data.getBroadcastHelloCodes().put(pd.getPlayerUuid(), code);
            }
            data.getBroadcastHelloResponded().clear();
            data.setBroadcastHelloExpiryTick(currentTick + timer * 20L);

            broadcastMessage("§e§l[全体问好] §r§a" + player.getGameProfile().name() + " §e向所有人说: §f§l你好！§r§c " + timer + "秒内回复！");

            // 给每个目标发送Title+验证码
            for (var entry : data.getBroadcastHelloCodes().entrySet()) {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
                if (target == null) continue;
                int code = entry.getValue();
                target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, timer * 20, 20));
                target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                        Text.literal("§c§l⚠ 全体问好！")));
                target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                        Text.literal("§e输入 §a/helloback " + code + " §e否则 §c§l" + timer + "秒后秒杀！")));
                ServerWorld w = (ServerWorld) target.getEntityWorld();
                w.playSound(null, target.getBlockPos(),
                        net.minecraft.sound.SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                        net.minecraft.sound.SoundCategory.MASTER, 1.0f, 1.0f);
                w.playSound(null, target.getBlockPos(),
                        net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                        net.minecraft.sound.SoundCategory.MASTER, 1.0f, 0.8f);
                target.sendMessage(Text.literal("§c§l⚠ 全体问好！§r§e" + timer + "秒内输入 §a/helloback " + code + " §e否则被秒杀！"), false);
            }
            ServerWorld pWorld = (ServerWorld) player.getEntityWorld();
            pWorld.playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                    net.minecraft.sound.SoundCategory.MASTER, 0.8f, 1.2f);
            sendTip(player, "§a全体问好已发出！倒计时: §e" + timer + "秒");
        }

        // 全体问好倒计时tick
        if (!data.getBroadcastHelloCodes().isEmpty() && data.getBroadcastHelloExpiryTick() > 0) {
            if (currentTick >= data.getBroadcastHelloExpiryTick()) {
                // 超时: 秒杀所有未回复的目标
                int killCount = 0;
                for (var entry : data.getBroadcastHelloCodes().entrySet()) {
                    if (data.getBroadcastHelloResponded().contains(entry.getKey())) continue;
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
                    if (target != null) {
                        PlayerData targetData = getPlayerData(target.getUuid());
                        if (targetData != null && targetData.isAlive()) {
                            ServerWorld world = server.getOverworld();
                            target.kill(world);
                            killCount++;
                            broadcastMessage("§c§l[问好] §r§e" + target.getGameProfile().name() + " §c没有及时回复全体问好，被秒杀！");
                        }
                    }
                }
                if (killCount > 0) {
                    data.addKill();
                    data.setHelloTimerSeconds(data.getHelloTimerSeconds() - 1);
                    broadcastMessage("§c§l[全体问好] §r§e" + player.getGameProfile().name() + " §a秒杀了 §c" + killCount + " §a人！倒计时缩短至" + data.getHelloTimerSeconds() + "秒");
                }
                data.clearBroadcastHello();
            } else {
                // 给所有未回复的目标持续显示HUD倒计时
                int secondsLeft = (int) ((data.getBroadcastHelloExpiryTick() - currentTick) / 20);
                for (var entry : data.getBroadcastHelloCodes().entrySet()) {
                    if (data.getBroadcastHelloResponded().contains(entry.getKey())) continue;
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
                    if (target != null) {
                        sendTip(target, "§c§l⚠ 全体问好！§e" + secondsLeft + "s §c内输入 §a/helloback " + entry.getValue() + " §c否则被秒杀！");
                    }
                }
            }
        }

        // === 单体问好计时 ===
        if (data.getHelloTargetUuid() == null) return;
        if (data.getHelloExpiryTick() <= 0) return;
        if (currentTick >= data.getHelloExpiryTick()) {
            // 超时: 秒杀目标
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(data.getHelloTargetUuid());
            if (target != null) {
                PlayerData targetData = getPlayerData(target.getUuid());
                if (targetData != null && targetData.isAlive()) {
                    ServerWorld world = server.getOverworld();
                    target.kill(world);
                    data.addKill();
                    data.setHelloTimerSeconds(data.getHelloTimerSeconds() - 1);
                    broadcastMessage("§c§l[问好] §r§e" + target.getGameProfile().name() + " §c没有及时回复问好，被秒杀！§e" + player.getGameProfile().name() + " §a倒计时缩短至" + data.getHelloTimerSeconds() + "秒");
                }
            }
            data.setHelloTargetUuid(null);
            data.setHelloCode(0);
            data.setHelloExpiryTick(0);
        } else {
            // 给目标持续显示醒目HUD倒计时提醒
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(data.getHelloTargetUuid());
            if (target != null) {
                int secondsLeft = (int)((data.getHelloExpiryTick() - currentTick) / 20);
                sendTip(target, "§c§l⚠ 你被问好了！§e" + secondsLeft + "s §c内输入 §a/helloback " + data.getHelloCode() + " §c否则被秒杀！");
            }
        }
    }

    // ST: 柚子厨 — 自瞄声波(4秒蓄力后一次性结算伤害)+冷却广播
    private void tickSt(ServerPlayerEntity player, PlayerData data, long currentTick) {
        if (data.getSonicAutoAimTarget() != null) {
            long elapsed = currentTick - data.getSonicAutoAimStartTick();
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(data.getSonicAutoAimTarget());
            PlayerData targetData = target != null ? players.get(target.getUuid()) : null;
            // 目标离线或死亡 → 中止, 不结算
            if (target == null || targetData == null || !targetData.isAlive()) {
                data.setSonicAutoAimTarget(null);
                data.setSonicAutoAimStartTick(0);
                sendTip(player, "§c目标已离线或死亡，自瞄中止！");
                return;
            }
            // 每tick转向面对目标(蓄力期间)
            double dx = target.getX() - player.getX();
            double dy = (target.getY() + target.getHeight() / 2) - (player.getY() + player.getStandingEyeHeight());
            double dz = target.getZ() - player.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDist)));
            player.setYaw(yaw);
            player.setPitch(pitch);
            player.setHeadYaw(yaw);
            player.teleport(player.getEntityWorld(), player.getX(), player.getY(), player.getZ(),
                    java.util.Set.of(), yaw, pitch, false);

            if (elapsed >= 80) { // 4秒蓄力结束 → 一次性结算伤害
                SkillHandler.executeSonicFinalDamage(player, data, target);
                data.setSonicAutoAimTarget(null);
                data.setSonicAutoAimStartTick(0);
                broadcastMessage("§d§l[Ciallo自瞄] §r§e" + player.getGameProfile().name() + " §d的声波结算命中 §e" + target.getGameProfile().name() + "§d！");
            } else {
                // 蓄力期间每秒: 音效+倒计时提示, 不造成伤害
                if (elapsed % 20 == 0) {
                    int secondsLeft = (int) ((80 - elapsed) / 20);
                    ServerWorld world = (ServerWorld) player.getEntityWorld();
                    world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 1.0f + (1.0f - secondsLeft / 8.0f));
                    double dist = Math.sqrt(player.squaredDistanceTo(target));
                    sendTip(player, "§d§l[声波蓄力中] §e" + target.getGameProfile().name() +
                            " §7距离" + (int)dist + "格 §c" + secondsLeft + "s后结算");
                    sendTip(target, "§c§l[警告] §d" + player.getGameProfile().name() + " §c声波锁定中！" + secondsLeft + "s后结算！");
                }
            }
        }

        // 声波冷却完毕广播(首次)
        long sonicCooldown = 2400L; // 2分钟
        if (!data.isSonicReadyNotified() && data.getLastSonicTick() > 0
                && currentTick - data.getLastSonicTick() >= sonicCooldown) {
            data.setSonicReadyNotified(true);
            broadcastMessage("§d§l[Ciallo声波已冷却] §r§e" + player.getGameProfile().name() + " §d的Ciallo声波已准备就绪！");
        }
    }

    // DAMAI: 墓碑→骷髅形态完整管理
    private void tickDamai(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 等待3秒后转化为骷髅形态
        if (data.isDamaiWaitingForSkeleton()) {
            BlockPos tomb = data.getTombstonePos();
            // 墓碑被毒圈吞没 → 提前结束, 扣命正常复活
            if (tomb != null && !player.getEntityWorld().getWorldBorder().contains(tomb)) {
                data.loseLife();
                endDamaiSkeletonForm(player, data);
                broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c墓碑被毒圈吞没，提前复活！剩余 §e" + data.getLives() + " §c命");
                return;
            }
            // 锁定在墓碑上方，防止SPECTATOR模式乱飞
            if (tomb != null) {
                double dx = player.getX() - (tomb.getX() + 0.5);
                double dy = player.getY() - (tomb.getY() + 1.5);
                double dz = player.getZ() - (tomb.getZ() + 0.5);
                if (dx * dx + dy * dy + dz * dz > 0.5) {
                    player.teleport(player.getEntityWorld(), tomb.getX() + 0.5, tomb.getY() + 1.5, tomb.getZ() + 0.5,
                            java.util.Set.of(), player.getYaw(), player.getPitch(), false);
                }
            }
            long elapsed = currentTick - data.getDamaiDeathTick();
            if (elapsed >= 60) { // 3秒
                data.setDamaiWaitingForSkeleton(false);
                data.setSkeletonForm(true);
                data.setSkeletonFormStartTick(currentTick);
                data.setSkeletonFormEndTick(currentTick + 1200); // 初始1分钟
                // 切回生存模式(freshStart=false保留物品)
                player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                initializePlayerForRole(player, data, false);
                // initializePlayerForRole会传送到spawnPoint, 这里再覆盖传送到墓碑
                if (tomb != null) {
                    ServerWorld world = player.getEntityWorld();
                    player.teleport(world, tomb.getX() + 0.5, tomb.getY() + 1, tomb.getZ() + 0.5,
                            java.util.Set.of(), player.getYaw(), player.getPitch(), false);
                }
                // 给予骷髅形态buff(1分钟)
                applyDamaiSkeletonBuffs(player, 1200);
                broadcastMessage("§5§l" + player.getGameProfile().name() + " §7(不死者) §5从墓碑中转化为骷髅形态！持续1分钟！");
            }
            return;
        }

        // 墓碑proximity检测: 有玩家靠近6格内时提醒大麦
        if (data.getTombstonePos() != null && (data.isDamaiWaitingForSkeleton() || data.isSkeletonForm())) {
            if (currentTick % 40 == 0) { // 每2秒
                BlockPos tomb = data.getTombstonePos();
                for (PlayerData pd : players.values()) {
                    if (pd == data || !pd.isAlive()) continue;
                    ServerPlayerEntity other = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (other == null) continue;
                    double dist = other.getBlockPos().getSquaredDistance(tomb);
                    if (dist <= 36) { // 6格
                        sendTip(player, "§c§l[墓碑警报] §r§e" + other.getGameProfile().name() + " §c在墓碑附近！距离: " + (int)Math.sqrt(dist) + "格");
                        ServerWorld w = (ServerWorld) player.getEntityWorld();
                        w.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.5f);
                        break;
                    }
                }
            }
        }

        // 骷髅形态持续中
        if (data.isSkeletonForm()) {
            BlockPos tomb = data.getTombstonePos();
            // 墓碑被毒圈吞没 → 提前复活并损失命数
            if (tomb != null && !player.getEntityWorld().getWorldBorder().contains(tomb)) {
                data.loseLife();
                endDamaiSkeletonForm(player, data);
                broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c墓碑被毒圈吞没，提前复活！剩余 §e" + data.getLives() + " §c命");
                return;
            }
            // 检查墓碑是否被破坏
            if (tomb != null) {
                ServerWorld world = player.getEntityWorld();
                if (!world.getBlockState(tomb).isOf(net.minecraft.block.Blocks.LODESTONE)) {
                    // 墓碑被破坏 → 提前复活并损失命数
                    data.loseLife();
                    endDamaiSkeletonForm(player, data);
                    broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c墓碑被破坏，提前复活！剩余 §e" + data.getLives() + " §c命");
                    return;
                }
            }

            // 到期 → 复活并损失命数
            if (currentTick >= data.getSkeletonFormEndTick()) {
                data.loseLife();
                endDamaiSkeletonForm(player, data);
                broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §e骷髅形态结束，复活！剩余 §e" + data.getLives() + " §c命");
            }
        }
    }

    // DAMAI骷髅形态buff应用(力量5,抗性5,速度2,跳跃2)
    private void applyDamaiSkeletonBuffs(ServerPlayerEntity player, int duration) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, duration, 1, false, false, true));
    }

    // 结束DAMAI骷髅形态: 移除墓碑、重置状态、重新初始化角色(或淘汰)
    private void endDamaiSkeletonForm(ServerPlayerEntity player, PlayerData data) {
        // 移除墓碑方块
        BlockPos tomb = data.getTombstonePos();
        if (tomb != null) {
            ServerWorld world = server.getOverworld();
            if (world.getBlockState(tomb).isOf(net.minecraft.block.Blocks.LODESTONE)) {
                world.setBlockState(tomb, net.minecraft.block.Blocks.AIR.getDefaultState());
            }
        }
        data.resetDamaiOnDeath();

        // 命数耗尽 → 淘汰
        if (data.getLives() <= 0) {
            player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            syncAllPlayersHud();
            checkGameEnd();
            return;
        }

        // 还有命 → 重新初始化(清除骷髅buff, 保留物品)
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.clearStatusEffects();
        initializePlayerForRole(player, data, false);
        // 10秒无敌
        long currentTick = server.getOverworld().getTime();
        data.setRespawnInvincibleUntil(currentTick + RESPAWN_INVINCIBLE_TICKS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 255, false, false, true));
        syncAllPlayersHud();
        checkGameEnd();
    }

    // BOBBY: 被动抗性2+虚弱2+击退 + 灵体管理 + 命数同步
    private void tickBobby(ServerPlayerEntity player, PlayerData data) {
        long currentTick = server.getOverworld().getTime();
        // 灵体中: 到期自动返回
        if (data.getBobbyGhostEndTick() > 0 && currentTick >= data.getBobbyGhostEndTick()) {
            endBobbyGhost(player, data);
            return;
        }
        // 灵体中不施加被动(已是旁观者)
        if (data.getBobbyGhostEndTick() > 0) {
            if (currentTick % 20 == 0) {
                long remaining = (data.getBobbyGhostEndTick() - currentTick) / 20;
                sendTip(player, "§b§l[灵体] §r§b" + remaining + "秒后返回");
            }
            return;
        }
        // 被动buff: 抗性2 + 虚弱2 (肉盾定位: 硬但输出低)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 1, false, false, true));
        // 命数同步: 每秒同步一次命数(取较低值)
        if (currentTick % 20 == 0) {
            syncDuoLives(data);
        }
    }

    // RETOUR: 被动激光(V键切换) + 命数同步
    private static final double RETOUR_LASER_RANGE = 30.0;
    private static final double RETOUR_LASER_RADIUS = 1.5; // 命中半径(碰撞箱)

    private void tickRetour(ServerPlayerEntity player, PlayerData data) {
        long currentTick = server.getOverworld().getTime();
        if (data.isRetourLaserActive()) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            net.minecraft.util.math.Vec3d eyePos = new net.minecraft.util.math.Vec3d(player.getX(), player.getEyeY(), player.getZ());
            net.minecraft.util.math.Vec3d lookVec = player.getRotationVector();

            // 射线方块碰撞检测(决定激光实际终点, 不穿墙)
            net.minecraft.util.math.Vec3d laserEnd = eyePos.add(lookVec.multiply(RETOUR_LASER_RANGE));
            net.minecraft.world.RaycastContext rayCtx = new net.minecraft.world.RaycastContext(
                    eyePos, laserEnd,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    player);
            net.minecraft.util.hit.BlockHitResult blockHit = world.raycast(rayCtx);
            double effectiveRange = RETOUR_LASER_RANGE;
            if (blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                effectiveRange = blockHit.getPos().distanceTo(eyePos);
            }
            net.minecraft.util.math.Vec3d actualEnd = eyePos.add(lookVec.multiply(effectiveRange));

            // 玩家命中检测(每5tick=0.25秒检查一次, 命中累积伤害)
            ServerPlayerEntity hit = null;
            double bestDist = effectiveRange;
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (other == player || other.isSpectator()) continue;
                PlayerData od = players.get(other.getUuid());
                if (od == null || !od.isAlive()) continue;
                net.minecraft.util.math.Vec3d toOther = new net.minecraft.util.math.Vec3d(
                        other.getX() - eyePos.x, other.getBodyY(0.5) - eyePos.y, other.getZ() - eyePos.z);
                double projection = toOther.dotProduct(lookVec);
                if (projection < 0 || projection > effectiveRange) continue;
                net.minecraft.util.math.Vec3d closestPoint = eyePos.add(lookVec.multiply(projection));
                double dx = other.getX() - closestPoint.x;
                double dy = other.getBodyY(0.5) - closestPoint.y;
                double dz = other.getZ() - closestPoint.z;
                double perpDistSq = dx * dx + dy * dy + dz * dz;
                if (perpDistSq < RETOUR_LASER_RADIUS * RETOUR_LASER_RADIUS && projection < bestDist) {
                    bestDist = projection;
                    hit = other;
                }
            }

            // 每秒造成1心(2hp)真伤
            if (hit != null && currentTick % 20 == 0) {
                hit.damage(world, world.getDamageSources().magic(), 2.0f);
            }

            // 渲染激光粒子(每tick): 沿射线均匀生成红色粉尘, 末端爆炸/火花
            double renderEnd = hit != null ? bestDist : effectiveRange;
            int particleCount = (int) (renderEnd * 4); // 每格4个粒子
            net.minecraft.particle.DustParticleEffect dust = new net.minecraft.particle.DustParticleEffect(
                    0xFF0000, 0.6f); // 鲜红
            for (int i = 0; i < particleCount; i++) {
                double t = (double) i / particleCount;
                double px = eyePos.x + lookVec.x * renderEnd * t;
                double py = eyePos.y + lookVec.y * renderEnd * t;
                double pz = eyePos.z + lookVec.z * renderEnd * t;
                world.spawnParticles(dust, px, py, pz, 1, 0, 0, 0, 0);
            }
            // 终端爆裂特效
            world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                    actualEnd.x, actualEnd.y, actualEnd.z, 3, 0.1, 0.1, 0.1, 0.05);

            // 状态提示(每秒刷新)
            if (currentTick % 20 == 0) {
                if (hit != null) {
                    sendTip(player, "§c§l[激光] §r§c命中 §e" + hit.getGameProfile().name() + " §7距离" + (int) bestDist + "格");
                } else {
                    sendTip(player, "§7§l[激光] §r§7扫描中... §8(范围30格)");
                }
                // 激光启动音效
                world.playSound(null, player.getBlockPos(),
                        net.minecraft.sound.SoundEvents.BLOCK_BEACON_AMBIENT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 0.3f, 2.0f);
            }
        }
        // 命数同步
        if (currentTick % 20 == 0) {
            syncDuoLives(data);
        }
    }

    // RETOUR: Z键 - 与罪人(Bobby)交换位置, 1分钟CD
    public void retourSwapWithBobby(ServerPlayerEntity retour, PlayerData data) {
        long currentTick = server.getOverworld().getTime();
        long cd = 1200; // 1分钟
        if (currentTick - data.getLastSkillZTime() < cd) {
            long remaining = (cd - (currentTick - data.getLastSkillZTime())) / 20;
            sendTip(retour, "§c§l[位置交换] §r§c冷却中... §e" + remaining + "s");
            return;
        }

        // 找到存活的罪人(BOBBY)
        ServerPlayerEntity bobby = null;
        PlayerData bobbyData = null;
        for (PlayerData pd : players.values()) {
            if (pd.getRole() == Role.BOBBY && pd.isAlive()) {
                bobby = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                bobbyData = pd;
                break;
            }
        }
        if (bobby == null || bobbyData == null) {
            sendTip(retour, "§c§l[位置交换] §r§c罪人不存在或已淘汰！");
            return;
        }
        // 罪人在灵体形态时禁止交换(避免BUG)
        if (bobbyData.getBobbyGhostEndTick() > currentTick) {
            sendTip(retour, "§c§l[位置交换] §r§c罪人正在灵体形态，无法交换！");
            return;
        }

        ServerWorld world = server.getOverworld();
        net.minecraft.util.math.Vec3d retourPos = new net.minecraft.util.math.Vec3d(retour.getX(), retour.getY(), retour.getZ());
        net.minecraft.util.math.Vec3d bobbyPos = new net.minecraft.util.math.Vec3d(bobby.getX(), bobby.getY(), bobby.getZ());

        // 交换位置
        retour.teleport(world, bobbyPos.x, bobbyPos.y, bobbyPos.z, java.util.Set.of(),
                retour.getYaw(), retour.getPitch(), false);
        bobby.teleport(world, retourPos.x, retourPos.y, retourPos.z, java.util.Set.of(),
                bobby.getYaw(), bobby.getPitch(), false);

        // 双方传送特效 + 音效
        world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                bobbyPos.x, bobbyPos.y + 1, bobbyPos.z, 50, 0.5, 1.0, 0.5, 0.5);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                retourPos.x, retourPos.y + 1, retourPos.z, 50, 0.5, 1.0, 0.5, 0.5);
        world.playSound(null, retour.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, bobby.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

        data.setLastSkillZTime(currentTick);
        broadcastMessage("§d§l[位置交换] §r§e" + retour.getGameProfile().name() + " §d与罪人 §e" + bobby.getGameProfile().name() + " §d交换了位置！");
    }

    // 双人组命数同步: 取较低命数同步给双方
    private void syncDuoLives(PlayerData data) {
        Role myRole = data.getRole();
        Role partnerRole = (myRole == Role.BOBBY) ? Role.RETOUR : Role.BOBBY;
        for (PlayerData pd : players.values()) {
            if (pd.getRole() == partnerRole && pd.isAlive()) {
                int minLives = Math.min(data.getLives(), pd.getLives());
                data.setLives(minLives);
                pd.setLives(minLives);
                break;
            }
        }
    }

    // BOBBY: 灵体开始(旁观者模式30s)
    public void startBobbyGhost(ServerPlayerEntity player, PlayerData data, long currentTick) {
        data.setBobbyGhostEndTick(currentTick + 600); // 30秒
        data.setBobbyGhostStartPos(new net.minecraft.util.math.Vec3d(player.getX(), player.getY(), player.getZ()));
        player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
        broadcastMessage("§b§l[灵体] §r§e" + player.getGameProfile().name() + " §b进入灵体状态！30秒后返回");
    }

    // BOBBY: 灵体结束(就地恢复，不传送)
    public void endBobbyGhost(ServerPlayerEntity player, PlayerData data) {
        data.setBobbyGhostEndTick(0);
        data.setBobbyGhostStartPos(null);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        broadcastMessage("§b§l[灵体] §r§e" + player.getGameProfile().name() + " §b灵体结束，就地恢复！");
    }

    // ==================== ALLAND 颜料区域管理 ====================

    private void tickPaintZones(long currentTick) {
        Iterator<PaintZone> it = paintZones.iterator();
        while (it.hasNext()) {
            PaintZone zone = it.next();
            if (currentTick >= zone.expiryTick) {
                // 恢复原始方块
                ServerWorld world = server.getOverworld();
                for (Map.Entry<BlockPos, net.minecraft.block.BlockState> e : zone.originalBlocks.entrySet()) {
                    net.minecraft.block.BlockState current = world.getBlockState(e.getKey());
                    // 只恢复仍然是混凝土的方块(防止覆盖后续修改)
                    if (current.getBlock() instanceof net.minecraft.block.ConcretePowderBlock || isPaintConcrete(current)) {
                        world.setBlockState(e.getKey(), e.getValue());
                    }
                }
                it.remove();
            }
        }
    }

    private void tickDelayedActions(long currentTick) {
        List<Runnable> toRun = new ArrayList<>();
        Iterator<DelayedAction> it = delayedActions.iterator();
        while (it.hasNext()) {
            DelayedAction da = it.next();
            if (currentTick >= da.triggerTick) {
                toRun.add(da.action);
                it.remove();
            }
        }
        for (Runnable r : toRun) {
            r.run();
        }
    }

    private void restoreAllPaintZones() {
        if (server == null) return;
        ServerWorld world = server.getOverworld();
        for (PaintZone zone : paintZones) {
            for (Map.Entry<BlockPos, net.minecraft.block.BlockState> e : zone.originalBlocks.entrySet()) {
                if (isPaintConcrete(world.getBlockState(e.getKey()))) {
                    world.setBlockState(e.getKey(), e.getValue());
                }
            }
        }
    }

    private static boolean isPaintConcrete(net.minecraft.block.BlockState state) {
        net.minecraft.block.Block b = state.getBlock();
        return b == net.minecraft.block.Blocks.RED_CONCRETE
            || b == net.minecraft.block.Blocks.BLUE_CONCRETE
            || b == net.minecraft.block.Blocks.LIME_CONCRETE
            || b == net.minecraft.block.Blocks.YELLOW_CONCRETE;
    }

    // 不可被染色的方块
    private static boolean isProtectedBlock(net.minecraft.block.BlockState state) {
        net.minecraft.block.Block b = state.getBlock();
        if (state.isAir()) return true;
        if (b == net.minecraft.block.Blocks.BEDROCK) return true;
        if (b == net.minecraft.block.Blocks.LODESTONE) return true; // 墓碑/备用躯体
        if (b == net.minecraft.block.Blocks.AMETHYST_BLOCK) return true; // 画作
        if (b == net.minecraft.block.Blocks.COBWEB) return true; // 蛛网
        if (b == net.minecraft.block.Blocks.SEA_LANTERN) return true; // 画中世界照明
        if (b == net.minecraft.block.Blocks.STONE_BRICKS) return true; // 画中世界通道
        if (b == net.minecraft.block.Blocks.DEEPSLATE_BRICKS) return true; // 画中世界地板
        if (b instanceof net.minecraft.block.StainedGlassBlock) return true; // XLL特殊玻璃
        if (b == net.minecraft.block.Blocks.GLASS) return true;
        if (isPaintConcrete(state)) return true; // 已染色的混凝土
        return false;
    }

    private static net.minecraft.block.Block getConcreteForColor(int color) {
        return switch (color) {
            case 0 -> net.minecraft.block.Blocks.RED_CONCRETE;
            case 1 -> net.minecraft.block.Blocks.BLUE_CONCRETE;
            case 2 -> net.minecraft.block.Blocks.LIME_CONCRETE;
            case 3 -> net.minecraft.block.Blocks.YELLOW_CONCRETE;
            default -> null;
        };
    }

    private static String getColorName(int color) {
        return switch (color) {
            case 0 -> "§c红";
            case 1 -> "§9蓝";
            case 2 -> "§a绿";
            case 3 -> "§e黄";
            default -> "?";
        };
    }

    // 颜料弹命中处理(由SnowballHitMixin调用)
    public void onPaintProjectileHit(BlockPos hitPos, int color, UUID ownerUuid) {
        ServerWorld world = server.getOverworld();
        long currentTick = world.getTime();

        // 检查是否命中已有颜料区域
        PaintZone hitZone = null;
        for (PaintZone zone : paintZones) {
            double dx = hitPos.getX() - zone.center.getX();
            double dz = hitPos.getZ() - zone.center.getZ();
            if (dx * dx + dz * dz <= 25) { // 半径5内
                hitZone = zone;
                break;
            }
        }

        if (hitZone != null) {
            // 命中已有区域 → 触发组合效果
            triggerPaintCombo(hitZone, color, ownerUuid, currentTick);
        } else {
            // 新区域: 染色半径5
            paintArea(hitPos, color, ownerUuid, currentTick);
        }

        // 单色颜料触发效果: 给区域内玩家buff(刷新持续时间, 不叠加等级)
        // StatusEffectInstance(effect, duration, amplifier) — amplifier=0即等级I
        // 200tick=10秒, 重复触发只刷新持续时间(MC原生行为: 同等级同效果会刷新duration)
        List<ServerPlayerEntity> affected = getPlayersInRadius(hitPos, 5);
        ServerPlayerEntity allandPlayer = server.getPlayerManager().getPlayer(ownerUuid);
        switch (color) {
            case 0 -> { // 红: 力量I 10秒(给自己, 不需要在区域内)
                if (allandPlayer != null) {
                    allandPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0, false, false, true));
                    sendTip(allandPlayer, "§c红颜料触发: 力量I §710秒");
                }
            }
            case 1 -> { // 蓝: 抗性I 10秒(给区域内自己)
                if (allandPlayer != null && affected.contains(allandPlayer)) {
                    allandPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, false, false, true));
                    sendTip(allandPlayer, "§9蓝颜料触发: 抗性I §710秒");
                }
            }
            case 2 -> { // 绿: 速度I 10秒(给区域内自己)
                if (allandPlayer != null && affected.contains(allandPlayer)) {
                    allandPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0, false, false, true));
                    sendTip(allandPlayer, "§a绿颜料触发: 速度I §710秒");
                }
            }
            case 3 -> { // 黄: 对区域内敌人减速I 10秒
                for (ServerPlayerEntity p : affected) {
                    if (p.getUuid().equals(ownerUuid)) continue; // 不影响自己
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 0, false, false, true));
                    sendTip(p, "§e黄颜料触发: 减速I §710秒");
                }
                if (allandPlayer != null) {
                    sendTip(allandPlayer, "§e黄颜料: 区域内敌人减速I §710秒");
                }
            }
        }
    }

    private void paintArea(BlockPos center, int color, UUID ownerUuid, long currentTick) {
        ServerWorld world = server.getOverworld();
        net.minecraft.block.Block paintBlock = getConcreteForColor(color);
        if (paintBlock == null) return;

        Map<BlockPos, net.minecraft.block.BlockState> originals = new HashMap<>();
        int radius = 5;
        // 找地表高度来涂色
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                // 从命中点向下搜索可涂色的表面方块
                for (int dy = 2; dy >= -2; dy--) {
                    BlockPos bp = center.add(x, dy, z);
                    net.minecraft.block.BlockState state = world.getBlockState(bp);
                    if (!isProtectedBlock(state)) {
                        originals.put(bp, state);
                        world.setBlockState(bp, paintBlock.getDefaultState());
                        break; // 只涂最上层
                    }
                }
            }
        }

        if (!originals.isEmpty()) {
            paintZones.add(new PaintZone(center, color, currentTick + 200, ownerUuid, originals)); // 10秒=200tick
        }
    }

    private void triggerPaintCombo(PaintZone existingZone, int newColor, UUID ownerUuid, long currentTick) {
        int oldColor = existingZone.color;
        BlockPos center = existingZone.center;
        ServerWorld world = server.getOverworld();
        int r = 5;

        // 获取区域内所有玩家
        List<ServerPlayerEntity> playersInZone = getPlayersInRadius(center, r);

        // 排序颜色使组合对称(min, max)
        int c1 = Math.min(oldColor, newColor);
        int c2 = Math.max(oldColor, newColor);

        if (c1 == 0 && c2 == 0) {
            // 红+红: 3颗心(6hp)真实伤害爆炸
            for (ServerPlayerEntity p : playersInZone) {
                p.damage(world, world.getDamageSources().magic(), 6.0f);
            }
            broadcastMessage("§c§l[颜料] §r" + getColorName(0) + "+" + getColorName(0) + " §f爆炸！区域内造成3颗心真实伤害！");
        } else if (c1 == 0 && c2 == 1) {
            // 红+蓝: 击飞10格(从中心向外水平加速+微弹起)
            for (ServerPlayerEntity p : playersInZone) {
                double dx = p.getX() - (center.getX() + 0.5);
                double dz = p.getZ() - (center.getZ() + 0.5);
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < 0.1) { dx = 1; dz = 0; dist = 1; } // 站在正中心时给个方向
                double scale = 2.5 / dist; // 归一化后乘以速度(约飞出10格)
                p.setVelocity(dx * scale, 0.5, dz * scale);
                p.velocityDirty = true;
                p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
            }
            broadcastMessage("§c§l[颜料] §r" + getColorName(0) + "+" + getColorName(1) + " §f击飞！区域内所有人被弹飞10格！");
        } else if (c1 == 0 && c2 == 2) {
            // 红+绿: 10秒着火
            for (ServerPlayerEntity p : playersInZone) {
                p.setOnFireFor(10);
            }
            broadcastMessage("§c§l[颜料] §r" + getColorName(0) + "+" + getColorName(2) + " §f烈焰！区域内所有人着火10秒！");
        } else if (c1 == 0 && c2 == 3) {
            // 红+黄: 无效果
            broadcastMessage("§c§l[颜料] §r" + getColorName(0) + "+" + getColorName(3) + " §7无效果...");
        } else if (c1 == 1 && c2 == 1) {
            // 蓝+蓝: 禁锢3秒
            for (ServerPlayerEntity p : playersInZone) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 255, false, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 255, false, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 128, false, false, true)); // 负跳跃=不能跳
            }
            broadcastMessage("§c§l[颜料] §r" + getColorName(1) + "+" + getColorName(1) + " §f禁锢！区域内所有人被冻结3秒！");
        } else if (c1 == 1 && c2 == 2) {
            // 蓝+绿: 如果ALLAND在区域内回血5颗心
            ServerPlayerEntity alland = server.getPlayerManager().getPlayer(ownerUuid);
            if (alland != null && playersInZone.contains(alland)) {
                alland.heal(10.0f);
                sendTip(alland, "§a蓝+绿回复！恢复5颗心！");
            }
            broadcastMessage("§c§l[颜料] §r" + getColorName(1) + "+" + getColorName(2) + " §f治愈！");
        } else if ((c1 == 1 && c2 == 3)) {
            // 蓝+黄: 延迟2秒闪电, 每有一个玩家就对所有人造成3颗心真实伤害
            broadcastMessage("§c§l[颜料] §r" + getColorName(1) + "+" + getColorName(3) + " §f雷暴！2秒后降下闪电！");
            BlockPos fCenter = center;
            addDelayedAction(currentTick + 40, () -> {
                List<ServerPlayerEntity> pz = getPlayersInRadius(fCenter, r);
                int count = pz.size();
                if (count > 0) {
                    ServerWorld w = server.getOverworld();
                    // 降闪电视觉效果
                    net.minecraft.entity.LightningEntity lightning = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w, net.minecraft.entity.SpawnReason.TRIGGERED);
                    if (lightning != null) {
                        lightning.refreshPositionAfterTeleport(fCenter.getX() + 0.5, fCenter.getY() + 1, fCenter.getZ() + 0.5);
                        lightning.setCosmetic(true);
                        w.spawnEntity(lightning);
                    }
                    for (ServerPlayerEntity p : pz) {
                        p.damage(w, w.getDamageSources().magic(), count * 6.0f); // 每人3颗心=6hp
                    }
                }
            });
        } else if (c1 == 2 && c2 == 2) {
            // 绿+绿: 升级为隐身区域
            existingZone.invisZone = true;
            broadcastMessage("§c§l[颜料] §r" + getColorName(2) + "+" + getColorName(2) + " §f隐身！该区域升级为隐身区域！");
            return; // 不移除旧区域
        } else if (c1 == 2 && c2 == 3) {
            // 绿+黄: 无效果
            broadcastMessage("§c§l[颜料] §r" + getColorName(2) + "+" + getColorName(3) + " §7无效果...");
        } else if (c1 == 3 && c2 == 3) {
            // 黄+黄: 延迟2秒闪电, 8颗心分摊
            broadcastMessage("§c§l[颜料] §r" + getColorName(3) + "+" + getColorName(3) + " §f裁决之雷！2秒后降下闪电！");
            BlockPos fCenter = center;
            addDelayedAction(currentTick + 40, () -> {
                List<ServerPlayerEntity> pz = getPlayersInRadius(fCenter, r);
                if (!pz.isEmpty()) {
                    ServerWorld w = server.getOverworld();
                    net.minecraft.entity.LightningEntity lightning = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w, net.minecraft.entity.SpawnReason.TRIGGERED);
                    if (lightning != null) {
                        lightning.refreshPositionAfterTeleport(fCenter.getX() + 0.5, fCenter.getY() + 1, fCenter.getZ() + 0.5);
                        lightning.setCosmetic(true);
                        w.spawnEntity(lightning);
                    }
                    float totalDamage = 16.0f; // 8颗心=16hp
                    float perPlayer = totalDamage / pz.size();
                    for (ServerPlayerEntity p : pz) {
                        p.damage(w, w.getDamageSources().magic(), perPlayer);
                    }
                }
            });
        }

        // 组合触发后将区域方块随机混合两种颜色
        net.minecraft.block.Block block1 = getConcreteForColor(oldColor);
        net.minecraft.block.Block block2 = getConcreteForColor(newColor);
        if (block1 != null && block2 != null) {
            for (BlockPos bp : existingZone.originalBlocks.keySet()) {
                net.minecraft.block.BlockState current = world.getBlockState(bp);
                if (isPaintConcrete(current)) {
                    net.minecraft.block.Block chosen = world.random.nextBoolean() ? block1 : block2;
                    world.setBlockState(bp, chosen.getDefaultState());
                }
            }
        }
    }

    private List<ServerPlayerEntity> getPlayersInRadius(BlockPos center, int radius) {
        List<ServerPlayerEntity> result = new ArrayList<>();
        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (p == null) continue;
            double dx = p.getX() - (center.getX() + 0.5);
            double dz = p.getZ() - (center.getZ() + 0.5);
            if (dx * dx + dz * dz <= radius * radius) {
                result.add(p);
            }
        }
        return result;
    }

    // ALLAND: 颜料弹次数累积 + 绿绿隐身区域检测
    private void tickAlland(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 每15秒(300tick)获得1次颜料弹次数, 上限4
        if (data.getLastPaintChargeGainTick() == 0) data.setLastPaintChargeGainTick(currentTick);
        long chargeElapsed = currentTick - data.getLastPaintChargeGainTick();
        if (chargeElapsed >= 300) {
            if (data.getPaintCharges() < 4) {
                data.setPaintCharges(data.getPaintCharges() + 1);
                sendTip(player, "§e颜料弹 +1！当前: §a" + data.getPaintCharges() + "/4");
            } else {
                sendTip(player, "§e颜料弹已满! §a4/4 §7(已刷新)");
            }
            data.setLastPaintChargeGainTick(currentTick);
        } else {
            int secondsLeft = (int)((300 - chargeElapsed) / 20);
            sendTip(player, "§e颜料弹: §a" + data.getPaintCharges() + "/4 §7(" + secondsLeft + "s)");
        }

        // 检测是否在绿+绿隐身区域内
        boolean inInvisZone = false;
        for (PaintZone zone : paintZones) {
            if (zone.invisZone && zone.ownerUuid.equals(player.getUuid())) {
                double dx = player.getX() - (zone.center.getX() + 0.5);
                double dz = player.getZ() - (zone.center.getZ() + 0.5);
                if (dx * dx + dz * dz <= 25) { // 半径5
                    inInvisZone = true;
                    break;
                }
            }
        }
        if (inInvisZone) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 5, 0, false, false, true));
        }
    }

    // HELI: 揭露过期清理 + 双倍伤害窗口过期清理
    private void tickHeli(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 清理过期揭露
        data.getRevealedPlayerExpiry().entrySet().removeIf(e -> currentTick > e.getValue());
        // 清理过期的双倍伤害起始记录(揭露已过期的也一起清)
        data.getHeliRevealStartTick().entrySet().removeIf(e -> {
            Long expiry = data.getRevealedPlayerExpiry().get(e.getKey());
            return expiry == null || currentTick > expiry;
        });
    }

    // MACHA: 钓鱼倒计时(3秒警告后传送目标到MACHA面前1格)
    private void tickMacha(ServerPlayerEntity player, PlayerData data, long currentTick) {
        if (data.getMachaFishingTarget() == null) return;
        long elapsed = currentTick - data.getMachaFishingStartTick();
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(data.getMachaFishingTarget());
        if (target == null || !target.isAlive()) {
            data.setMachaFishingTarget(null);
            data.setMachaFishingStartTick(0);
            return;
        }
        int countdown = 3 - (int)(elapsed / 20);
        if (countdown > 0) {
            sendTip(target, "§c§l⚠ 你正在被钓！§e" + countdown + "秒§c后将被传送！");
        } else {
            // 传送到MACHA面前1格
            net.minecraft.util.math.Vec3d lookDir = player.getRotationVec(1.0f).normalize();
            double tx = player.getX() + lookDir.x;
            double ty = player.getY();
            double tz = player.getZ() + lookDir.z;
            ServerWorld world = player.getEntityWorld();
            target.teleport(world, tx, ty, tz, java.util.Set.of(), target.getYaw(), target.getPitch(), false);
            broadcastMessage("§6§l[钓鱼] §r§e" + player.getGameProfile().name() + " §a把 §e" + target.getGameProfile().name() + " §a钓到了面前！");
            data.setMachaFishingTarget(null);
            data.setMachaFishingStartTick(0);
        }
    }

    // MACHA: 基头四召唤器 — 召唤4个铁傀儡(15秒后消失, 自动攻击其他玩家)
    public void spawnMachaWardens(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        long currentTick = world.getTime();
        broadcastMessage("§4§l[基头四] §r§e" + player.getGameProfile().name() + " §4召唤了基头四！4只铁傀儡出现！");

        for (int i = 0; i < 4; i++) {
            net.minecraft.entity.passive.IronGolemEntity golem = net.minecraft.entity.EntityType.IRON_GOLEM.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (golem == null) continue;
            double angle = Math.PI * 2 * i / 4;
            double spawnX = player.getX() + Math.cos(angle) * 3;
            double spawnZ = player.getZ() + Math.sin(angle) * 3;
            golem.refreshPositionAndAngles(spawnX, player.getY(), spawnZ, (float)(angle * 180 / Math.PI), 0);
            golem.setPersistent();
            golem.addCommandTag("macha_golem_" + player.getUuid());
            golem.addCommandTag("macha_golem_expire_" + (currentTick + 300)); // 15秒=300tick
            world.spawnEntity(golem);
        }
        sendTip(player, "§4基头四召唤！4只铁傀儡将存活15秒！它们不会攻击你！");
    }

    // MACHA: tick铁傀儡生命周期 + 主动攻击其他玩家(由主tick调用)
    private void tickMachaWardens(long currentTick) {
        ServerWorld world = server.getOverworld();
        List<net.minecraft.entity.Entity> entities = new ArrayList<>();
        world.iterateEntities().forEach(entities::add);
        for (net.minecraft.entity.Entity entity : entities) {
            if (!(entity instanceof net.minecraft.entity.passive.IronGolemEntity golem)) continue;
            String expireTag = null;
            String ownerTag = null;
            for (String tag : golem.getCommandTags()) {
                if (tag.startsWith("macha_golem_expire_")) expireTag = tag;
                else if (tag.startsWith("macha_golem_") && !tag.startsWith("macha_golem_expire_")) ownerTag = tag;
            }
            if (expireTag == null) continue;
            long expireTick = Long.parseLong(expireTag.replace("macha_golem_expire_", ""));
            if (currentTick >= expireTick) {
                golem.discard();
                continue;
            }
            // 主动锁定附近玩家(排除主人)
            if (ownerTag != null) {
                String ownerUuidStr = ownerTag.replace("macha_golem_", "");
                try {
                    UUID ownerUuid = UUID.fromString(ownerUuidStr);
                    // 清除对主人的仇恨
                    if (golem.getTarget() != null && golem.getTarget() instanceof ServerPlayerEntity targetP
                            && targetP.getUuid().equals(ownerUuid)) {
                        golem.setTarget(null);
                    }
                    // 没有目标时找最近的其他玩家
                    if (golem.getTarget() == null || !golem.getTarget().isAlive()) {
                        ServerPlayerEntity closest = null;
                        double closestDist = 30.0 * 30.0; // 30格范围
                        for (ServerPlayerEntity p : world.getPlayers()) {
                            if (p.getUuid().equals(ownerUuid)) continue;
                            PlayerData pd = players.get(p.getUuid());
                            if (pd == null || !pd.isAlive()) continue;
                            if (p.isSpectator()) continue;
                            double dist = p.squaredDistanceTo(golem);
                            if (dist < closestDist) {
                                closestDist = dist;
                                closest = p;
                            }
                        }
                        if (closest != null) {
                            golem.setTarget(closest);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // YOUZHA: 噬元兽猫吸收 + 异次元消化(2分钟) + 消化完成吞噬/自身死亡释放
    private void tickYouzha(ServerPlayerEntity player, PlayerData data, long currentTick) {
        ServerWorld world = player.getEntityWorld();

        // 消化中: 给予YOUZHA强化buff(缓慢II+抗性II+生命恢复II)
        if (!data.getYouzhaTrappedPlayers().isEmpty() && data.getYouzhaTrappedReleaseTick() > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 1, false, false, true));
        }

        // 消化完成 → 吞噬被困玩家(直接死亡)
        if (data.getYouzhaTrappedReleaseTick() > 0 && currentTick >= data.getYouzhaTrappedReleaseTick()) {
            BlockPos trapRoom = data.getYouzhaTrapRoomPos();
            int consumed = 0;
            for (UUID trappedUuid : data.getYouzhaTrappedPlayers()) {
                ServerPlayerEntity trapped = server.getPlayerManager().getPlayer(trappedUuid);
                if (trapped == null) continue;
                PlayerData trappedData = players.get(trappedUuid);
                if (trappedData == null || !trappedData.isAlive()) continue;
                // 消化成功 → 通过outOfWorld伤害触发onDeath(走正常扣命流程)
                trapped.setInvulnerable(false);
                trapped.damage(world, world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
                if (trapped.isAlive()) {
                    // 兜底: 如果damage未能触发, 手动调用kill(会走onDeath)
                    trapped.kill(world);
                }
                consumed++;
            }
            // 清除异次元房间方块
            if (trapRoom != null) {
                for (int x = -2; x <= 2; x++) {
                    for (int y = -1; y <= 3; y++) {
                        for (int z = -2; z <= 2; z++) {
                            world.setBlockState(trapRoom.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
            broadcastMessage("§4§l[噬元兽] §r§c消化完成！§e" + player.getGameProfile().name() + " §c成功吞噬了 §e" + consumed + " §c名玩家！");
            data.getYouzhaTrappedPlayers().clear();
            data.getYouzhaTrappedOriginalPos().clear();
            data.setYouzhaTrappedReleaseTick(0);
            data.setYouzhaTrapRoomPos(null);
            return;
        }

        // 被困玩家锁定在房间内 + 大标题提示(每3秒刷新)
        if (!data.getYouzhaTrappedPlayers().isEmpty() && data.getYouzhaTrapRoomPos() != null) {
            BlockPos trapRoom = data.getYouzhaTrapRoomPos();

            // 每2秒检测异次元房间是否被毒圈淹没, 是则迁移到边界中心附近
            if (currentTick % 40 == 0) {
                net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
                if (!border.contains(trapRoom)) {
                    // 清除旧房间
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -1; y <= 3; y++) {
                            for (int z = -2; z <= 2; z++) {
                                world.setBlockState(trapRoom.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                            }
                        }
                    }
                    // 在边界中心上方Y=310重建
                    int newX = (int) border.getCenterX();
                    int newZ = (int) border.getCenterZ();
                    BlockPos newRoom = new BlockPos(newX, 310, newZ);
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -1; y <= 3; y++) {
                            for (int z = -2; z <= 2; z++) {
                                boolean isWall = (x == -2 || x == 2 || z == -2 || z == 2 || y == -1 || y == 3);
                                BlockPos bp = newRoom.add(x, y, z);
                                if (isWall) {
                                    world.setBlockState(bp, net.minecraft.block.Blocks.REINFORCED_DEEPSLATE.getDefaultState());
                                } else {
                                    world.setBlockState(bp, net.minecraft.block.Blocks.AIR.getDefaultState());
                                }
                            }
                        }
                    }
                    world.setBlockState(newRoom.add(0, 2, 0), net.minecraft.block.Blocks.REDSTONE_LAMP.getDefaultState());
                    data.setYouzhaTrapRoomPos(newRoom);
                    trapRoom = newRoom;
                    // 立即传送被困玩家到新房间
                    for (UUID trappedUuid : data.getYouzhaTrappedPlayers()) {
                        ServerPlayerEntity trapped = server.getPlayerManager().getPlayer(trappedUuid);
                        if (trapped != null) {
                            trapped.teleport(world, newX + 0.5, 310, newZ + 0.5, Set.of(), trapped.getYaw(), trapped.getPitch(), false);
                        }
                    }
                }
            }

            long remaining = (data.getYouzhaTrappedReleaseTick() - currentTick) / 20;
            for (UUID trappedUuid : data.getYouzhaTrappedPlayers()) {
                ServerPlayerEntity trapped = server.getPlayerManager().getPlayer(trappedUuid);
                if (trapped == null) continue;
                // 锁定在房间中心
                double cx = trapRoom.getX() + 0.5;
                double cy = trapRoom.getY();
                double cz = trapRoom.getZ() + 0.5;
                if (trapped.squaredDistanceTo(cx, cy, cz) > 4) {
                    trapped.teleport(world, cx, cy, cz, Set.of(), trapped.getYaw(), trapped.getPitch(), false);
                }
                // 每3秒显示大标题
                if (currentTick % 60 == 0) {
                    trapped.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                            net.minecraft.text.Text.literal("§4§l被消化中")));
                    trapped.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                            net.minecraft.text.Text.literal("§c噬元兽正在消化你！§e" + remaining + "秒后死亡 §7(杀死油炸意面可逃脱)")));
                }
            }
        }
    }

    // YOUZHA死亡时由onPlayerDeath调用: 释放所有被困玩家(不扣血)
    public void releaseAllYouzhaTrapped(PlayerData youzhaData) {
        BlockPos trapRoom = youzhaData.getYouzhaTrapRoomPos();
        ServerWorld world = server.getOverworld();
        int released = 0;
        for (UUID trappedUuid : youzhaData.getYouzhaTrappedPlayers()) {
            ServerPlayerEntity trapped = server.getPlayerManager().getPlayer(trappedUuid);
            if (trapped == null) continue;
            PlayerData trappedData = players.get(trappedUuid);
            if (trappedData == null || !trappedData.isAlive()) continue;
            net.minecraft.util.math.Vec3d origPos = youzhaData.getYouzhaTrappedOriginalPos().get(trappedUuid);
            if (origPos != null) {
                trapped.teleport(world, origPos.x, origPos.y, origPos.z, Set.of(), trapped.getYaw(), trapped.getPitch(), false);
            }
            sendTip(trapped, "§a§l噬元兽死亡, 你从异次元中逃了出来！");
            released++;
        }
        // 清除异次元房间方块
        if (trapRoom != null) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        world.setBlockState(trapRoom.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
        if (released > 0) {
            broadcastMessage("§a§l[噬元兽之死] §r§a噬元兽被杀, §e" + released + " §a名玩家从异次元逃脱！");
        }
        youzhaData.getYouzhaTrappedPlayers().clear();
        youzhaData.getYouzhaTrappedOriginalPos().clear();
        youzhaData.setYouzhaTrappedReleaseTick(0);
        youzhaData.setYouzhaTrapRoomPos(null);
    }

    // YOUZHA: 噬元兽猫tick(每秒检查, 由主tick调用)
    private void tickYouzhaCats(long currentTick) {
        ServerWorld world = server.getOverworld();
        List<net.minecraft.entity.Entity> entities = new ArrayList<>();
        world.iterateEntities().forEach(entities::add);
        for (net.minecraft.entity.Entity entity : entities) {
            if (!(entity instanceof net.minecraft.entity.passive.CatEntity cat)) continue;
            String absorbTag = null;
            String ownerTag = null;
            for (String tag : cat.getCommandTags()) {
                if (tag.startsWith("youzha_cat_absorb_")) absorbTag = tag;
                else if (tag.startsWith("youzha_cat_") && !tag.startsWith("youzha_cat_absorb_")) ownerTag = tag;
            }
            if (absorbTag == null) continue;
            long absorbTick = Long.parseLong(absorbTag.replace("youzha_cat_absorb_", ""));

            if (currentTick >= absorbTick) {
                // 吸入阶段: 找5格内所有玩家(除了YOUZHA本人)
                UUID ownerUuid = null;
                if (ownerTag != null) {
                    try { ownerUuid = UUID.fromString(ownerTag.replace("youzha_cat_", "")); } catch (Exception ignored) {}
                }

                PlayerData ownerData = ownerUuid != null ? players.get(ownerUuid) : null;
                List<ServerPlayerEntity> absorbed = new ArrayList<>();
                for (PlayerData pd : players.values()) {
                    if (!pd.isAlive()) continue;
                    if (pd.getPlayerUuid().equals(ownerUuid)) continue;
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (p == null) continue;
                    if (p.squaredDistanceTo(cat.getX(), cat.getY(), cat.getZ()) <= 25) { // 5格²=25
                        absorbed.add(p);
                    }
                }

                if (!absorbed.isEmpty() && ownerData != null) {
                    // 创建异次元小房间(在Y=310的天空, 偏移避免与SHUBING通道冲突)
                    net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
                    int roomX = (int) border.getCenterX() + 200; // 偏移200格避免冲突
                    int roomY = 310;
                    int roomZ = (int) border.getCenterZ() + 200;
                    BlockPos roomPos = new BlockPos(roomX, roomY, roomZ);

                    // 建造5x4x5不可破坏的深板岩砖房间(内部3x3x3空间)
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -1; y <= 3; y++) {
                            for (int z = -2; z <= 2; z++) {
                                boolean isWall = (x == -2 || x == 2 || z == -2 || z == 2 || y == -1 || y == 3);
                                BlockPos bp = roomPos.add(x, y, z);
                                if (isWall) {
                                    world.setBlockState(bp, net.minecraft.block.Blocks.REINFORCED_DEEPSLATE.getDefaultState());
                                } else {
                                    world.setBlockState(bp, net.minecraft.block.Blocks.AIR.getDefaultState());
                                }
                            }
                        }
                    }
                    // 里面放个红石灯照明
                    world.setBlockState(roomPos.add(0, 2, 0), net.minecraft.block.Blocks.REDSTONE_LAMP.getDefaultState());

                    ownerData.setYouzhaTrapRoomPos(roomPos);
                    ownerData.setYouzhaTrappedReleaseTick(currentTick + 2400); // 2分钟=2400tick (消化期)
                    ownerData.getYouzhaTrappedPlayers().clear();
                    ownerData.getYouzhaTrappedOriginalPos().clear();

                    for (ServerPlayerEntity p : absorbed) {
                        ownerData.getYouzhaTrappedPlayers().add(p.getUuid());
                        ownerData.getYouzhaTrappedOriginalPos().put(p.getUuid(), new net.minecraft.util.math.Vec3d(p.getX(), p.getY(), p.getZ())); // 保存原始位置
                        p.teleport(world, roomX + 0.5, roomY, roomZ + 0.5, Set.of(), p.getYaw(), p.getPitch(), false);
                        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                                net.minecraft.text.Text.literal("§4§l被噬元兽吞噬！")));
                        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                                net.minecraft.text.Text.literal("§c你被困在异次元空间！15秒后释放")));
                        sendTip(p, "§4§l被噬元兽吞噬！§c困在异次元15秒后释放");
                    }
                    broadcastMessage("§6§l[噬元兽] §r" + absorbed.size() + "名玩家被噬元兽吞入异次元！");
                }

                // 移除猫
                cat.discard();
            }
        }
    }

    // JVJV: 大爆炸3秒延迟 + 红莲华10秒延迟+冻结
    private void tickJvjv(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 大爆炸延迟
        if (data.isExplosionUsed() && data.getLastSkillZTime() > 0) {
            long elapsed = currentTick - data.getLastSkillZTime();
            if (elapsed >= 40 && elapsed < 60) { // 2秒后执行（只执行一次）
                data.setLastSkillZTime(0); // 清除防止重复执行
                SkillHandler.executeJvjvExplosion(player, data);
            }
        }
        // 红莲华蓄力期间: 冻结玩家(仅在偏离时传送,避免每tick传送导致不同步)
        if (data.isRedLotusCharging(currentTick)) {
            BlockPos lockPos = data.getRedLotusPos();
            if (lockPos != null) {
                double dx = player.getX() - (lockPos.getX() + 0.5);
                double dy = player.getY() - lockPos.getY();
                double dz = player.getZ() - (lockPos.getZ() + 0.5);
                if (dx * dx + dy * dy + dz * dz > 0.1) {
                    ServerWorld world = player.getEntityWorld();
                    player.teleport(world, lockPos.getX() + 0.5, lockPos.getY(), lockPos.getZ() + 0.5,
                            java.util.Set.of(), player.getYaw(), player.getPitch(), false);
                }
            }
            player.setVelocity(0, 0, 0);
            player.velocityDirty = true;
        }
        // 红莲华延迟执行(死亡时已清除状态,此处双重保险)
        if (data.isRedLotusUsed() && data.getLastSkillXTime() > 0) {
            long elapsed = currentTick - data.getLastSkillXTime();
            if (elapsed >= 200 && elapsed < 220) { // 10秒后执行
                data.setLastSkillXTime(0);
                if (data.isAlive() && !player.isSpectator()) {
                    SkillHandler.executeRedLotus(player, data);
                }
            }
        }
        // 吃人消化tick
        if (data.getJvjvEatenPlayer() != null && data.getJvjvDigestEndTick() > 0) {
            ServerPlayerEntity eaten = server.getPlayerManager().getPlayer(data.getJvjvEatenPlayer());
            BlockPos stomach = data.getJvjvStomachRoomPos();
            // 逃脱检测: 被吞者离开胃袋8格范围=逃脱(传送不失效)
            if (eaten != null && stomach != null) {
                double dist = eaten.getBlockPos().getSquaredDistance(stomach);
                if (dist > 64) { // 8格外=逃脱
                    releaseJvjvEaten(data, eaten, true);
                    return;
                }
            }
            // 被吞者离线或死亡=结束
            if (eaten == null) {
                clearJvjvStomachRoom(data);
                return;
            }
            PlayerData eatenData = players.get(data.getJvjvEatenPlayer());
            if (eatenData == null || !eatenData.isAlive()) {
                clearJvjvStomachRoom(data);
                return;
            }
            // 消化完成 → 秒杀被吞者
            if (currentTick >= data.getJvjvDigestEndTick()) {
                ServerWorld w = (ServerWorld) eaten.getEntityWorld();
                eaten.damage(w, w.getDamageSources().outOfWorld(), Float.MAX_VALUE);
                if (eaten.isAlive()) eaten.kill(w);
                broadcastMessage("§c§l[JVJV] §r§e" + player.getGameProfile().name() + " §c消化了 §e" + eaten.getGameProfile().name() + "§c！");
                data.addKill();
                clearJvjvStomachRoom(data);
                return;
            }
            // 消化中HUD提示(每3秒)
            if (currentTick % 60 == 0) {
                long remaining = (data.getJvjvDigestEndTick() - currentTick) / 20;
                sendTip(player, "§c§l[消化中] §r§e" + eaten.getGameProfile().name() + " §c还需 §e" + remaining + "s §c(杀死JVJV可逃脱)");
                eaten.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                        net.minecraft.text.Text.literal("§4§l被吞噬中")));
                eaten.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                        net.minecraft.text.Text.literal("§c" + remaining + "秒后被消化！§e用珍珠逃脱或杀死JVJV！")));
            }
        }
    }

    // JVJV: 吃人 - 创建胃袋房间并传送目标进入
    public void jvjvEatPlayer(ServerPlayerEntity jvjv, PlayerData jvjvData, ServerPlayerEntity victim, long currentTick) {
        ServerWorld world = (ServerWorld) jvjv.getEntityWorld();
        net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
        // 在边界中心上空Y=305建胃袋(避免与youzha Y=310冲突)
        int roomX = (int) border.getCenterX() + 50; // 偏移避免重叠
        int roomZ = (int) border.getCenterZ() + 50;
        BlockPos roomCenter = new BlockPos(roomX, 305, roomZ);
        // 构建5x5x5密封房间(加固深板岩墙壁+红石灯照明)
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean isWall = (x == -2 || x == 2 || z == -2 || z == 2 || y == -1 || y == 3);
                    BlockPos bp = roomCenter.add(x, y, z);
                    if (isWall) {
                        world.setBlockState(bp, net.minecraft.block.Blocks.REINFORCED_DEEPSLATE.getDefaultState());
                    } else {
                        world.setBlockState(bp, net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
        world.setBlockState(roomCenter.add(0, 2, 0), net.minecraft.block.Blocks.REDSTONE_LAMP.getDefaultState());

        // 保存数据
        jvjvData.setJvjvEatenPlayer(victim.getUuid());
        jvjvData.setJvjvDigestEndTick(currentTick + 1200); // 1分钟消化
        jvjvData.setJvjvStomachRoomPos(roomCenter);
        jvjvData.setJvjvEatenOriginalPos(new net.minecraft.util.math.Vec3d(victim.getX(), victim.getY(), victim.getZ()));

        // 传送被吞者到胃袋
        victim.teleport(world, roomX + 0.5, 305, roomZ + 0.5, Set.of(), victim.getYaw(), victim.getPitch(), false);

        // JVJV饱食度满
        jvjv.getHungerManager().setFoodLevel(20);
        jvjv.getHungerManager().setSaturationLevel(5.0f);

        broadcastMessage("§c§l[JVJV] §r§e" + jvjv.getGameProfile().name() + " §c吞下了 §e" + victim.getGameProfile().name() + "§c！1分钟后消化！");
        world.playSound(null, jvjv.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_BURP,
                net.minecraft.sound.SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    // JVJV: 释放被吞者(逃脱/JVJV死亡)
    public void releaseJvjvEaten(PlayerData jvjvData, ServerPlayerEntity eaten, boolean escaped) {
        if (escaped) {
            // 传送不失效情况: 玩家自己珍珠逃脱, 已经在外面了
            sendTip(eaten, "§a§l你从JVJV的胃袋中逃脱了！");
            broadcastMessage("§a§l[JVJV] §r§e" + eaten.getGameProfile().name() + " §a从胃袋中逃脱！");
        } else {
            // JVJV死亡释放: 传送回原位
            net.minecraft.util.math.Vec3d origPos = jvjvData.getJvjvEatenOriginalPos();
            if (origPos != null) {
                ServerWorld w = (ServerWorld) eaten.getEntityWorld();
                eaten.teleport(w, origPos.x, origPos.y, origPos.z, Set.of(), eaten.getYaw(), eaten.getPitch(), false);
            }
            sendTip(eaten, "§a§lJVJV死亡, 你从胃袋中被释放！");
            broadcastMessage("§a§l[JVJV] §r§cJVJV死亡！§e" + eaten.getGameProfile().name() + " §a从胃袋中被释放！");
        }
        clearJvjvStomachRoom(jvjvData);
    }

    // JVJV: 清理胃袋房间
    private void clearJvjvStomachRoom(PlayerData jvjvData) {
        BlockPos room = jvjvData.getJvjvStomachRoomPos();
        if (room != null) {
            ServerWorld world = server.getOverworld();
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -2; z <= 2; z++) {
                        world.setBlockState(room.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
        jvjvData.setJvjvEatenPlayer(null);
        jvjvData.setJvjvDigestEndTick(0);
        jvjvData.setJvjvStomachRoomPos(null);
        jvjvData.setJvjvEatenOriginalPos(null);
    }

    // POPCORN: 完整逻辑tick — 躯体维护、镭射预警/伤害/粒子、临时血量到期、视角目标检查
    private void tickPopcorn(ServerPlayerEntity player, PlayerData data, long currentTick) {
        ServerWorld world = player.getEntityWorld();

        // 1. 备用躯体维护: 每20tick检查一次
        if (currentTick % 20 == 0) {
            // 自动修复(使躯体"无法被破坏")
            for (BlockPos body : data.getBackupBodies()) {
                if (!world.getBlockState(body).isOf(net.minecraft.block.Blocks.LODESTONE)) {
                    world.setBlockState(body, net.minecraft.block.Blocks.LODESTONE.getDefaultState());
                }
            }
            // 毒圈摧毁圈外躯体
            WorldBorder border = world.getWorldBorder();
            data.getBackupBodies().removeIf(body -> {
                if (!border.contains(body)) {
                    world.setBlockState(body, net.minecraft.block.Blocks.AIR.getDefaultState());
                    player.sendMessage(Text.literal("§c一个备用躯体被毒圈摧毁！"), false);
                    broadcastMessage("§e" + player.getGameProfile().name() + " §7(智械危机) §c一个备用躯体被毒圈摧毁！");
                    return true;
                }
                return false;
            });
        }

        // 2. 镭射冷却完毕广播(首次)
        long laserCooldown = 2400L; // 2分钟
        if (!data.isLaserReadyNotified() && data.getLastLaserTick() > 0
                && currentTick - data.getLastLaserTick() >= laserCooldown) {
            data.setLaserReadyNotified(true);
            broadcastMessage("§c§l[轨道镭射] §r§e" + player.getGameProfile().name() + " §c的轨道镭射已准备就绪！");
        }

        // 3. 镭射3秒预警倒计时 + 预警粒子
        if (data.getLaserPendingTarget() != null) {
            long elapsed = currentTick - data.getLaserPendingStartTick();
            if (elapsed < 60) { // 3秒预警
                if (elapsed % 20 == 0) {
                    int remaining = (int) ((60 - elapsed) / 20);
                    broadcastMessage("§c§l⚠ 轨道镭射将在 §e" + remaining + "§c 秒后降下！");
                }
                // 预警粒子: 红色光柱从天而降(越来越密集)
                BlockPos pendingTarget = data.getLaserPendingTarget();
                double density = 2.0 + (elapsed / 20.0) * 5.0; // 随时间增加密度(3秒更密集)
                for (int i = 0; i < (int) density; i++) {
                    double px = pendingTarget.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 10;
                    double pz = pendingTarget.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 10;
                    double py = pendingTarget.getY() + 20 + world.random.nextDouble() * 30;
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.DUST_PLUME, px, py, pz, 1, 0, -1, 0, 0.5);
                }
                // 预警音效: 每秒一声
                if (elapsed % 20 == 0) {
                    world.playSound(null, pendingTarget, net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, net.minecraft.sound.SoundCategory.HOSTILE, 3.0f, 0.5f);
                }
            } else {
                // 预警结束，开始镭射
                data.setLaserTarget(data.getLaserPendingTarget());
                data.setLaserEndTick(currentTick + 200); // 10秒伤害期
                data.setLaserPendingTarget(null);
                broadcastMessage("§c§l[轨道镭射] §r§c轨道镭射降下！持续10秒！");
                // 降下音效
                BlockPos laserTarget = data.getLaserTarget();
                world.playSound(null, laserTarget, net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, net.minecraft.sound.SoundCategory.HOSTILE, 5.0f, 0.3f);
            }
        }

        // 4. 镭射伤害+粒子: 每秒对半径10格内所有玩家(包含自己)造成6颗心真实伤害
        if (data.getLaserTarget() != null) {
            if (currentTick < data.getLaserEndTick()) {
                BlockPos target = data.getLaserTarget();
                // 粒子效果: 每tick生成光柱+火焰
                for (int i = 0; i < 5; i++) {
                    double angle = world.random.nextDouble() * Math.PI * 2;
                    double r = world.random.nextDouble() * 10;
                    double px = target.getX() + 0.5 + Math.cos(angle) * r;
                    double pz = target.getZ() + 0.5 + Math.sin(angle) * r;
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, px, target.getY() + world.random.nextDouble() * 50, pz, 1, 0, -0.5, 0, 0.2);
                }
                // 中心高密度粒子
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 10, 3, 1, 3, 0.05);
                // 持续音效: 每10tick
                if (currentTick % 10 == 0) {
                    world.playSound(null, target, net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 1.5f);
                }
                // 每秒造成伤害(带攻击者归属,击杀计入POPCORN)
                if (currentTick % 20 == 0) {
                    for (ServerPlayerEntity p : world.getPlayers()) {
                        PlayerData pd = players.get(p.getUuid());
                        if (pd == null || !pd.isAlive()) continue;
                        
                        // 计算水平距离(无视Y轴)
                        double dx = p.getX() - (target.getX() + 0.5);
                        double dz = p.getZ() - (target.getZ() + 0.5);
                        if (dx * dx + dz * dz <= 100.0) { // 半径10格(10^2)
                            p.damage(world, world.getDamageSources().indirectMagic(player, player), 12.0f); // 6心 (12血)
                        }
                    }
                }
            } else {
                data.setLaserTarget(null);
                data.setLaserEndTick(0);
            }
        }

        // 5. 躯体选择模式: 1.5秒后自动确认传送 (随时传送要求)
        if (data.getPopcornBodySelectTick() > 0) {
            if (currentTick - data.getPopcornBodySelectTick() >= 30) { // 1.5秒确认
                data.setPopcornBodySelectTick(0);
                List<BlockPos> bodies = data.getBackupBodies();
                if (!bodies.isEmpty()) {
                    BlockPos target = bodies.get(data.getPopcornBodyTpIndex() % bodies.size());
                    popcornTeleportToBody(player, data, target);
                }
            }
        }

        // 6. 临时+5心到期
        if (data.getPopcornBonusHealthExpiry() > 0 && currentTick >= data.getPopcornBonusHealthExpiry()) {
            data.setPopcornBonusHealthExpiry(0);
            EntityAttributeInstance ha = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (ha != null && ha.getBaseValue() > 20.0) {
                ha.setBaseValue(Math.max(20.0, ha.getBaseValue() - 10.0));
                if (player.getHealth() > player.getMaxHealth()) {
                    player.setHealth(player.getMaxHealth());
                }
            }
        }

        // 7. 旁观中: 躯壳血量监控 + 目标存活检查
        if (data.isPopcornSpectating()) {
            net.minecraft.entity.Entity bodyEntity = popcornBodyEntities.get(player.getUuid());
            if (bodyEntity == null) {
                popcornReturnFromSpectate(player, data);
            } else if (bodyEntity instanceof net.minecraft.entity.LivingEntity le) {
                if (!le.isAlive()) {
                    // 躯壳被杀死 → 返回+玩家死亡
                    popcornReturnFromSpectate(player, data);
                    player.damage(world, world.getDamageSources().magic(), 9999f);
                } else {
                    float currentHealth = le.getHealth();
                    float lastHealth = data.getPopcornBodyLastHealth();
                    if (currentHealth < lastHealth - 0.01f) {
                        // 躯壳受到任何伤害 → 立刻返回
                        popcornReturnFromSpectate(player, data);
                        player.setHealth(Math.max(1.0f, currentHealth));
                        sendTip(player, "§c躯壳被攻击！灵魂强制归体！");
                    } else if (le.hurtTime > 0) {
                        popcornReturnFromSpectate(player, data);
                        player.setHealth(Math.max(1.0f, currentHealth));
                        sendTip(player, "§c躯壳被攻击！灵魂强制归体！");
                    }
                    data.setPopcornBodyLastHealth(currentHealth);
                }
            }

            // 8. 视角目标存活检查
            if (data.isPopcornSpectating() && data.getPopcornCameraTarget() != null) {
                UUID camTarget = data.getPopcornCameraTarget();
                PlayerData camData = players.get(camTarget);
                ServerPlayerEntity camPlayer = server.getPlayerManager().getPlayer(camTarget);
                if (camData == null || !camData.isAlive() || camPlayer == null || fullyHiddenPlayers.contains(camTarget)) {
                    popcornReturnFromSpectate(player, data);
                    sendTip(player, "§e视角目标已消失，灵魂归体！");
                }
            }
        }
    }

    // POPCORN: 执行传送并消耗躯体
    public void popcornTeleportToBody(ServerPlayerEntity player, PlayerData data, BlockPos target) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        // 1. 传送前脉冲: 周围5格所有玩家晕眩5秒
        for (ServerPlayerEntity nearby : world.getPlayers()) {
            if (nearby == player) continue;
            if (nearby.squaredDistanceTo(player) <= 25) { // 5格
                nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, false, true));
                nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 255, false, false, true)); // SLOW 255 禁锢
                nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, false, true));
                sendTip(nearby, "§c§l⚡ 你被智械脉冲晕眩了！");
            }
        }

        // 2. 消耗并移除躯体
        data.getBackupBodies().remove(target);
        world.setBlockState(target, net.minecraft.block.Blocks.AIR.getDefaultState());

        // 3. 执行传送
        player.teleport(world, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5,
                java.util.Set.of(), player.getYaw(), player.getPitch(), false);

        // 4. 如果正在旁观则归体
        if (data.isPopcornSpectating()) {
            int cx = (int) data.getPopcornSavedX() >> 4;
            int cz = (int) data.getPopcornSavedZ() >> 4;
            world.setChunkForced(cx, cz, false);
            net.minecraft.entity.Entity bodyEntity = popcornBodyEntities.remove(player.getUuid());
            if (bodyEntity != null) bodyEntity.discard();
            data.setPopcornSpectating(false);
            player.changeGameMode(GameMode.SURVIVAL);
            player.setCameraEntity(player);
        }

        // 5. 恢复全血并给予临时5心
        applyPopcornBonusHealth(player, data);
        world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        sendTip(player, "§a意识已下载到备用躯体！");
    }

    // POPCORN: 复活/传送后给予全血+临时+5心(30秒) — public供SkillHandler调用
    public void applyPopcornBonusHealthPublic(ServerPlayerEntity player, PlayerData data) {
        applyPopcornBonusHealth(player, data);
    }

    // ...
    // POPCORN: 复活/传送后给予全血+临时+5心(30秒)
    private void applyPopcornBonusHealth(ServerPlayerEntity player, PlayerData data) {
        EntityAttributeInstance ha = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (ha != null) {
            ha.setBaseValue(ha.getBaseValue() + 10.0); // +5心
        }
        player.setHealth(player.getMaxHealth());
        data.setPopcornBonusHealthExpiry(server.getOverworld().getTime() + 600); // 30秒后到期
    }

    // POPCORN: 地图点击触发轨道镭射(5秒预警)
    public void popcornTriggerLaser(ServerPlayerEntity player, PlayerData data, int x, int z) {
        long currentTick = server.getOverworld().getTime();
        long laserCooldown = 2400L; // 2分钟
        if (currentTick - data.getLastLaserTick() < laserCooldown) {
            long remaining = (laserCooldown - (currentTick - data.getLastLaserTick())) / 20;
            sendTip(player, "§c镭射冷却中... §e" + remaining + "s");
            return;
        }
        if (data.getLaserPendingTarget() != null) {
            sendTip(player, "§c镭射已在预警中，请等待！");
            return;
        }
        data.setLastLaserTick(currentTick);
        data.setLaserReadyNotified(false);
        int y = findSafeY(server.getOverworld(), x, z);
        BlockPos target = new BlockPos(x, y, z);
        data.setLaserPendingTarget(target);
        data.setLaserPendingStartTick(currentTick);
        broadcastMessage("§c§l⚠ [轨道镭射] §r§e" + player.getGameProfile().name()
                + " §c对 (" + x + ", " + z + ") 发射轨道镭射！§e3秒后降下！");
    }

    // POPCORN: C键旁观操作(即时进入 / 返回)
    public void popcornSpectateAction(ServerPlayerEntity player, PlayerData data) {
        if (data.isPopcornSpectating()) {
            // 正在旁观 → 返回
            popcornReturnFromSpectate(player, data);
            return;
        }
        // 构建可观察玩家列表
        List<ServerPlayerEntity> visible = getPopcornVisiblePlayers(player);
        if (visible.isEmpty()) {
            sendTip(player, "§c没有可观察的玩家！");
            return;
        }
        // 即时进入旁观第一个目标(无延迟)
        int idx = data.getPopcornSpectateSelectIndex() % visible.size();
        ServerPlayerEntity target = visible.get(idx);
        executePopcornSpectate(player, data, target);
    }

    // POPCORN: 获取可观察的玩家列表
    private List<ServerPlayerEntity> getPopcornVisiblePlayers(ServerPlayerEntity self) {
        List<ServerPlayerEntity> visible = new ArrayList<>();
        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            if (pd.getPlayerUuid().equals(self.getUuid())) continue;
            if (fullyHiddenPlayers.contains(pd.getPlayerUuid())) continue;
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (target != null) visible.add(target);
        }
        return visible;
    }

    // POPCORN: 执行旁观(选择确认后调用)
    private void executePopcornSpectate(ServerPlayerEntity player, PlayerData data, ServerPlayerEntity target) {
        // 保存当前位置
        data.savePopcornPosition(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        data.setPopcornSpectating(true);
        data.setPopcornCameraTarget(target.getUuid());

        // 在原地生成躯壳(僵尸: NoAI+沉默+防火+同步血量)
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.entity.mob.ZombieEntity zombie = new net.minecraft.entity.mob.ZombieEntity(
                net.minecraft.entity.EntityType.ZOMBIE, world);
        zombie.setPosition(player.getX(), player.getY(), player.getZ());
        zombie.setYaw(player.getYaw());
        zombie.setPitch(player.getPitch());
        zombie.setCustomName(Text.literal("§e" + player.getGameProfile().name() + " §7的躯壳"));
        zombie.setCustomNameVisible(true);
        zombie.setAiDisabled(true);
        zombie.setSilent(true);
        zombie.setPersistent();
        zombie.setBaby(false);
        zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false));
        // 同步血量
        EntityAttributeInstance zombieHp = zombie.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (zombieHp != null) zombieHp.setBaseValue(player.getMaxHealth());
        zombie.setHealth(player.getHealth());
        // 复制玩家装备到僵尸
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            zombie.equipStack(slot, player.getEquippedStack(slot).copy());
        }
        world.spawnEntity(zombie);
        popcornBodyEntities.put(player.getUuid(), zombie);
        data.setPopcornBodyLastHealth(zombie.getHealth());

        // 强制加载僵尸所在区块(防止玩家传送走后区块卸载导致僵尸消失)
        int chunkX = (int) player.getX() >> 4;
        int chunkZ = (int) player.getZ() >> 4;
        world.setChunkForced(chunkX, chunkZ, true);

        // 切换旁观者模式并附身目标
        player.changeGameMode(GameMode.SPECTATOR);
        player.teleport(world, target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYaw(), target.getPitch(), false);
        player.setCameraEntity(target);
        List<ServerPlayerEntity> visible = getPopcornVisiblePlayers(player);
        int totalVisible = visible.size();
        PlayerData targetPd = players.get(target.getUuid());
        String roleName = targetPd != null ? targetPd.getRole().getDisplayName() : "???";
        sendTip(player, "§e§l【灵魂出窍】§r §a" + target.getGameProfile().name()
                + " §7[" + roleName + "] §e(1/" + totalVisible + ") §bZ§7/§bX§7切换 §bC§7返回");
        broadcastMessage("§e§l[智械危机] §r§e" + player.getGameProfile().name() + " §7进入了灵魂出窍状态！");
    }

    // POPCORN: 从旁观返回(删除躯壳+传送回原位+切回生存)
    public void popcornReturnFromSpectate(ServerPlayerEntity player, PlayerData data) {
        float healthToSync = player.getHealth();
        ServerWorld world = server.getOverworld();

        // 先取消强制加载躯壳所在区块
        int chunkX = (int) data.getPopcornSavedX() >> 4;
        int chunkZ = (int) data.getPopcornSavedZ() >> 4;
        world.setChunkForced(chunkX, chunkZ, false);

        // 获取躯壳剩余血量并删除
        net.minecraft.entity.Entity bodyEntity = popcornBodyEntities.remove(player.getUuid());
        if (bodyEntity != null) {
            if (bodyEntity instanceof net.minecraft.entity.LivingEntity le && le.isAlive()) {
                healthToSync = le.getHealth();
            }
            bodyEntity.discard();
        }

        data.setPopcornSpectating(false);
        data.setPopcornCameraTarget(null);
        data.setPopcornSpectateSelectTick(0);

        // 切回生存+传送回保存位置
        player.changeGameMode(GameMode.SURVIVAL);
        player.setCameraEntity(player);
        player.teleport(world, data.getPopcornSavedX(), data.getPopcornSavedY(), data.getPopcornSavedZ(),
                Set.of(), data.getPopcornSavedYaw(), data.getPopcornSavedPitch(), false);
        // 同步躯壳剩余血量
        player.setHealth(Math.max(1.0f, healthToSync));
        sendTip(player, "§a灵魂归体！");
    }

    // POPCORN: 旁观中切换观察目标(Z=下一个, X=上一个) — 即时响应无延迟
    public void popcornSwitchSpectateTarget(ServerPlayerEntity player, PlayerData data, int direction) {
        List<ServerPlayerEntity> visible = getPopcornVisiblePlayers(player);
        if (visible.isEmpty()) {
            sendTip(player, "§c没有可观察的玩家！");
            return;
        }
        // 找到当前目标索引
        UUID currentTarget = data.getPopcornCameraTarget();
        int currentIdx = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).getUuid().equals(currentTarget)) {
                currentIdx = i;
                break;
            }
        }
        int nextIdx;
        if (currentIdx == -1) {
            nextIdx = 0;
        } else {
            nextIdx = (currentIdx + direction + visible.size()) % visible.size();
        }
        ServerPlayerEntity newTarget = visible.get(nextIdx);
        data.setPopcornCameraTarget(newTarget.getUuid());
        data.setPopcornSpectateSelectIndex(nextIdx);
        ServerWorld world = player.getEntityWorld();
        player.teleport(world, newTarget.getX(), newTarget.getY(), newTarget.getZ(),
                Set.of(), newTarget.getYaw(), newTarget.getPitch(), false);
        player.setCameraEntity(newTarget);
        PlayerData targetPd = players.get(newTarget.getUuid());
        String roleName = targetPd != null ? targetPd.getRole().getDisplayName() : "???";
        sendTip(player, "§e§l【灵魂出窍】§r §a" + newTarget.getGameProfile().name()
                + " §7[" + roleName + "] §e(" + (nextIdx + 1) + "/" + visible.size() + ") §bZ§7/§bX§7切换 §bC§7返回");
    }

    // SANCHEZ: 狼群管理
    private void tickSanchez(ServerPlayerEntity player, PlayerData data, long currentTick) {
        ServerWorld world = player.getEntityWorld();

        // 头狼死亡3分钟后复活
        if (data.isAlphaWolfDead()) {
            if (currentTick - data.getAlphaWolfDeathTick() >= 3600) { // 3分钟
                data.setAlphaWolfDead(false);
                data.setAlphaWolfHealth(40.0);
                data.setAlphaWolfEntityUuid(null);
                broadcastMessage("§6§l[诸神黄昏] §r§e" + player.getGameProfile().name() + " §a的头狼已复活！");
            }
            return;
        }

        // 确保头狼实体存在
        if (data.getAlphaWolfEntityUuid() == null || getEntityByUuid(world, data.getAlphaWolfEntityUuid()) == null) {
            spawnAlphaWolf(player, data, world);
        }

        // 检查头狼是否存活
        net.minecraft.entity.Entity alphaEntity = data.getAlphaWolfEntityUuid() != null ? getEntityByUuid(world, data.getAlphaWolfEntityUuid()) : null;
        if (alphaEntity instanceof net.minecraft.entity.passive.WolfEntity alphaWolf) {
            // 同步头狼HP到PlayerData
            data.setAlphaWolfHealth(alphaWolf.getHealth());
            if (!alphaWolf.isAlive()) {
                onAlphaWolfDeath(player, data, world, currentTick);
                return;
            }
            // 阻止头狼自然回血(只能通过Z技能恢复)
            if (alphaWolf.getHealth() > (float) data.getAlphaWolfHealth()) {
                alphaWolf.setHealth((float) data.getAlphaWolfHealth());
            }
        }

        // 清理死亡的狼群
        data.getPackWolfUuids().removeIf(uuid -> {
            net.minecraft.entity.Entity e = getEntityByUuid(world, uuid);
            return e == null || !e.isAlive();
        });

        // 狼群自动攻击(每2秒=40tick执行一次)
        if (currentTick % 40 == 0) {
            sanchezWolfAutoAggro(player, data, world);
        }

        // 狩猎模式: 无敌效果
        if (data.getSanchezHuntEndTick() > currentTick) {
            // 给玩家和所有狼抗性255(无敌)+速度2
            if (currentTick % 20 == 0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 255, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 1, false, false, true));
                applySanchezWolfBuff(data, world, 30);
            }
        }
    }

    public void spawnAlphaWolf(ServerPlayerEntity player, PlayerData data, ServerWorld world) {
        net.minecraft.entity.passive.WolfEntity wolf = net.minecraft.entity.EntityType.WOLF.create(world, net.minecraft.entity.SpawnReason.COMMAND);
        if (wolf == null) return;
        wolf.setPosition(player.getX() + 1, player.getY(), player.getZ());
        wolf.setTamed(true, false);
        wolf.setOwner(player);
        // 20心=40HP
        var healthAttr = wolf.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(40.0);
        wolf.setHealth((float) data.getAlphaWolfHealth());
        // 固定3心伤害
        var atkAttr = wolf.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        if (atkAttr != null) atkAttr.setBaseValue(6.0);
        wolf.addCommandTag("sanchez_alpha_" + player.getUuid().toString());
        wolf.setCustomName(Text.literal("§6§l" + player.getGameProfile().name() + "的头狼"));
        wolf.setCustomNameVisible(true);
        wolf.setPersistent();
        world.spawnEntity(wolf);
        data.setAlphaWolfEntityUuid(wolf.getUuid());
    }

    public net.minecraft.entity.passive.WolfEntity spawnPackWolf(ServerPlayerEntity player, PlayerData data, ServerWorld world) {
        net.minecraft.entity.passive.WolfEntity wolf = net.minecraft.entity.EntityType.WOLF.create(world, net.minecraft.entity.SpawnReason.COMMAND);
        if (wolf == null) return null;
        wolf.setPosition(player.getX(), player.getY(), player.getZ());
        wolf.setTamed(true, false);
        wolf.setOwner(player);
        // 1心=2HP
        var healthAttr = wolf.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(2.0);
        wolf.setHealth(2.0f);
        // 固定3心伤害
        var atkAttr = wolf.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
        if (atkAttr != null) atkAttr.setBaseValue(6.0);
        wolf.addCommandTag("sanchez_pack_" + player.getUuid().toString());
        wolf.setPersistent();
        world.spawnEntity(wolf);
        data.getPackWolfUuids().add(wolf.getUuid());
        return wolf;
    }

    private void onAlphaWolfDeath(ServerPlayerEntity player, PlayerData data, ServerWorld world, long currentTick) {
        data.setAlphaWolfDead(true);
        data.setAlphaWolfDeathTick(currentTick);
        data.setAlphaWolfEntityUuid(null);
        // 所有狼群立即死亡
        for (UUID wolfUuid : data.getPackWolfUuids()) {
            net.minecraft.entity.Entity e = getEntityByUuid(world, wolfUuid);
            if (e instanceof net.minecraft.entity.LivingEntity le && le.isAlive()) {
                le.kill(world);
            }
        }
        data.getPackWolfUuids().clear();
        broadcastMessage("§c§l[诸神黄昏] §r§e" + player.getGameProfile().name() + " §c的头狼死亡了！所有狼群已消亡！3分钟后复活。");
    }

    private void sanchezWolfAutoAggro(ServerPlayerEntity player, PlayerData data, ServerWorld world) {
        // 收集所有狼实体
        List<net.minecraft.entity.passive.WolfEntity> wolves = new ArrayList<>();
        if (data.getAlphaWolfEntityUuid() != null) {
            net.minecraft.entity.Entity e = getEntityByUuid(world, data.getAlphaWolfEntityUuid());
            if (e instanceof net.minecraft.entity.passive.WolfEntity w && w.isAlive()) wolves.add(w);
        }
        for (UUID uuid : data.getPackWolfUuids()) {
            net.minecraft.entity.Entity e = getEntityByUuid(world, uuid);
            if (e instanceof net.minecraft.entity.passive.WolfEntity w && w.isAlive()) wolves.add(w);
        }
        if (wolves.isEmpty()) return;

        // 如果有指令目标，优先锁定
        net.minecraft.entity.LivingEntity commandTarget = null;
        if (data.getSanchezAttackTarget() != null) {
            net.minecraft.entity.Entity e = getEntityByUuid(world, data.getSanchezAttackTarget());
            if (e instanceof net.minecraft.entity.LivingEntity le && le.isAlive()) {
                commandTarget = le;
            } else {
                data.setSanchezAttackTarget(null);
            }
        }

        for (net.minecraft.entity.passive.WolfEntity wolf : wolves) {
            if (commandTarget != null) {
                wolf.setTarget(commandTarget);
            } else {
                // 无指令: 自动攻击最近的非狼非玩家主人的生物
                if (wolf.getTarget() == null || !wolf.getTarget().isAlive()) {
                    net.minecraft.entity.LivingEntity nearest = findNearestMobForWolf(wolf, player, data, world, 16.0);
                    if (nearest != null) wolf.setTarget(nearest);
                }
            }
        }
    }

    private net.minecraft.entity.LivingEntity findNearestMobForWolf(
            net.minecraft.entity.passive.WolfEntity wolf, ServerPlayerEntity owner, PlayerData data, ServerWorld world, double range) {
        net.minecraft.entity.LivingEntity nearest = null;
        double nearestDist = range * range;
        for (net.minecraft.entity.Entity e : world.getOtherEntities(wolf, wolf.getBoundingBox().expand(range))) {
            if (!(e instanceof net.minecraft.entity.LivingEntity le)) continue;
            if (!le.isAlive()) continue;
            if (le instanceof ServerPlayerEntity sp && sp.getUuid().equals(owner.getUuid())) continue; // 不攻击主人
            if (le instanceof net.minecraft.entity.passive.WolfEntity w && w.getCommandTags().stream().anyMatch(t -> t.startsWith("sanchez_"))) continue; // 不攻击自家狼
            double dist = wolf.squaredDistanceTo(le);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = le;
            }
        }
        return nearest;
    }

    private void applySanchezWolfBuff(PlayerData data, ServerWorld world, int duration) {
        // 头狼
        if (data.getAlphaWolfEntityUuid() != null) {
            net.minecraft.entity.Entity e = getEntityByUuid(world, data.getAlphaWolfEntityUuid());
            if (e instanceof net.minecraft.entity.LivingEntity le) {
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 255, false, false, true));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false, true));
            }
        }
        // 狼群
        for (UUID uuid : data.getPackWolfUuids()) {
            net.minecraft.entity.Entity e = getEntityByUuid(world, uuid);
            if (e instanceof net.minecraft.entity.LivingEntity le) {
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 255, false, false, true));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false, true));
            }
        }
    }

    private net.minecraft.entity.Entity getEntityByUuid(ServerWorld world, UUID uuid) {
        return world.getEntity(uuid);
    }

    // SANCHEZ: 生物击杀获取charge(由Mixin调用)
    public void onSanchezMobKill(ServerPlayerEntity player, net.minecraft.entity.LivingEntity killed) {
        PlayerData data = players.get(player.getUuid());
        if (data == null || data.getRole() != Role.SANCHEZ) return;
        if (killed instanceof ServerPlayerEntity) return;
        data.addWolfKillCharge();
        sendTip(player, "§6+1 §e召唤次数！当前: §e" + data.getWolfKillCharges());
    }

    // SANCHEZ: 狼击杀生物也给主人加charge
    public void onSanchezWolfKill(UUID ownerUuid) {
        PlayerData data = players.get(ownerUuid);
        if (data == null || data.getRole() != Role.SANCHEZ) return;
        data.addWolfKillCharge();
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) sendTip(owner, "§6+1 §e召唤次数(狼击杀)！当前: §e" + data.getWolfKillCharges());
    }

    // SANCHEZ: 玩家攻击时设置狼群目标(排除自家狼)
    public void onSanchezPlayerAttack(ServerPlayerEntity player, net.minecraft.entity.Entity target) {
        PlayerData data = players.get(player.getUuid());
        if (data == null || data.getRole() != Role.SANCHEZ) return;
        // 不锁定自家狼
        if (target instanceof net.minecraft.entity.passive.WolfEntity wolf) {
            String playerUuidStr = player.getUuid().toString();
            if (wolf.getCommandTags().stream().anyMatch(t -> t.contains(playerUuidStr))) return;
        }
        if (target instanceof net.minecraft.entity.LivingEntity) {
            data.setSanchezAttackTarget(target.getUuid());
        }
    }

    // SHUBING: 画中世界 tick — 由薯饼玩家的tick驱动
    private void tickShubing(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 被动: 每1分钟获得1幅画(上限2)
        if (data.getLastPaintingGainTick() == 0) data.setLastPaintingGainTick(currentTick);
        long paintElapsed = currentTick - data.getLastPaintingGainTick();
        if (paintElapsed >= 1200) { // 1分钟=1200tick
            if (data.getPaintingCount() < 2) {
                data.setPaintingCount(data.getPaintingCount() + 1);
                sendTip(player, "§a获得了1幅画！当前§e" + data.getPaintingCount() + "/2§a幅。");
            } else {
                sendTip(player, "§e画已满! §a2/2 §7(已刷新)");
            }
            data.setLastPaintingGainTick(currentTick);
        } else {
            int secLeft = (int)((1200 - paintElapsed) / 20);
            sendTip(player, "§e画: §a" + data.getPaintingCount() + "/2 §7(" + secLeft + "s)");
        }

        // 每秒检测画是否在毒圈外, 是则自动摧毁
        if (currentTick % 20 == 0) {
            WorldBorder border = server.getOverworld().getWorldBorder();
            ServerWorld world2 = server.getOverworld();
            java.util.List<UUID> toDestroy = new java.util.ArrayList<>();
            for (PlayerData.ShubingPainting painting : data.getPlacedPaintings()) {
                if (painting.pos == null || painting.corridorIndex == -2) continue;
                if (!border.contains(painting.pos)) {
                    toDestroy.add(painting.entityUuid);
                }
            }
            for (UUID uuid : toDestroy) {
                SkillHandler.onShubingPaintingBroken(world2, uuid);
                broadcastMessage("§e§l[画中世界] §c一幅画被毒圈吞噬！");
            }
        }

        // 靠近画方块1格自动进入通道(退出后5秒冷却)
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerData pd = players.get(p.getUuid());
            if (pd == null || !pd.isAlive()) continue;
            if (data.getCorridorPlayers().containsKey(p.getUuid())) continue;
            if (currentTick - pd.getLastCorridorExitTick() < 100) continue; // 5秒=100tick冷却
            for (PlayerData.ShubingPainting painting : data.getPlacedPaintings()) {
                if (painting.pos == null || painting.corridorIndex < 0) continue;
                double dx = p.getX() - (painting.pos.getX() + 0.5);
                double dy = p.getY() - painting.pos.getY();
                double dz = p.getZ() - (painting.pos.getZ() + 0.5);
                if (Math.abs(dx) <= 1.5 && dy >= -0.5 && dy <= 2.0 && Math.abs(dz) <= 1.5) {
                    SkillHandler.onPlayerTouchPaintingBlock(p, painting.pos);
                    break;
                }
            }
        }

        // 处理通道内玩家: 10秒死亡 + 到达端点传出
        ServerWorld world = server.getOverworld();
        for (Map.Entry<UUID, PlayerData.CorridorEntry> entry :
                new java.util.HashMap<>(data.getCorridorPlayers()).entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
            if (p == null) {
                data.getCorridorPlayers().remove(entry.getKey());
                continue;
            }

            PlayerData.CorridorEntry ce = entry.getValue();
            if (ce.corridorIndex >= data.getCorridors().size()) {
                data.getCorridorPlayers().remove(entry.getKey());
                continue;
            }
            PlayerData.ShubingCorridor corridor = data.getCorridors().get(ce.corridorIndex);
            long elapsed = currentTick - ce.entryTick;

            // 画中世界免疫摔落伤害
            p.fallDistance = 0;

            // 画中世界减速: 除薯饼本人以外的玩家在通道内持续减速
            if (!p.getUuid().equals(player.getUuid())) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false, true));
            }

            // 10秒(200tick)死亡
            if (elapsed >= 200) {
                data.getCorridorPlayers().remove(entry.getKey());
                PlayerData exitPd = players.get(entry.getKey());
                if (exitPd != null) exitPd.setLastCorridorExitTick(currentTick);
                p.damage(world, world.getDamageSources().outOfWorld(), 9999.0f);
                broadcastMessage("§c" + p.getGameProfile().name() + " 在画中世界停留太久，死亡了！");
                continue;
            }

            // 检测到达通道端点
            int endReached = SkillHandler.getCorridorEndReached(corridor, p);
            if (corridor.vertical && endReached == 0 && elapsed > 10) {
                int exitEnd = (ce.enteredFromEnd == 0) ? 1 : 0;
                int exitPaintIdx = (exitEnd == 0) ? corridor.painting0Index : corridor.painting1Index;
                boolean sealed = (exitEnd == 0) ? corridor.end0Sealed : corridor.end1Sealed;
                // 出口画被拆→从入口画出来
                BlockPos exitPos = null;
                if (!sealed && exitPaintIdx >= 0 && exitPaintIdx < data.getPlacedPaintings().size()) {
                    PlayerData.ShubingPainting exitPaint = data.getPlacedPaintings().get(exitPaintIdx);
                    if (exitPaint.pos != null) exitPos = exitPaint.pos;
                }
                if (exitPos == null) {
                    // 出口不可用, 从进入端(画2)返回
                    int entryPaintIdx = (ce.enteredFromEnd == 0) ? corridor.painting0Index : corridor.painting1Index;
                    if (entryPaintIdx >= 0 && entryPaintIdx < data.getPlacedPaintings().size()) {
                        PlayerData.ShubingPainting entryPaint = data.getPlacedPaintings().get(entryPaintIdx);
                        if (entryPaint.pos != null) exitPos = entryPaint.pos;
                    }
                }
                if (exitPos != null) {
                    p.teleport(world, exitPos.getX() + 0.5, exitPos.getY() + 1.0,
                            exitPos.getZ() + 0.5, java.util.Set.of(), p.getYaw(), p.getPitch(), false);
                    data.getCorridorPlayers().remove(entry.getKey());
                    PlayerData exitPd1 = players.get(entry.getKey());
                    if (exitPd1 != null) exitPd1.setLastCorridorExitTick(currentTick);
                    p.sendMessage(Text.literal("§a你穿过了画中世界！"), false);
                    continue;
                }
            } else if (!corridor.vertical && endReached >= 0 && endReached != ce.enteredFromEnd) {
                int exitPaintIdx = (endReached == 0) ? corridor.painting0Index : corridor.painting1Index;
                boolean sealed = (endReached == 0) ? corridor.end0Sealed : corridor.end1Sealed;
                if (!sealed && exitPaintIdx >= 0 && exitPaintIdx < data.getPlacedPaintings().size()) {
                    PlayerData.ShubingPainting exitPaint = data.getPlacedPaintings().get(exitPaintIdx);
                    if (exitPaint.pos != null) {
                        p.teleport(world, exitPaint.pos.getX() + 0.5, exitPaint.pos.getY() + 1.0,
                                exitPaint.pos.getZ() + 0.5, java.util.Set.of(), p.getYaw(), p.getPitch(), false);
                        data.getCorridorPlayers().remove(entry.getKey());
                        PlayerData exitPd2 = players.get(entry.getKey());
                        if (exitPd2 != null) exitPd2.setLastCorridorExitTick(currentTick);
                        p.sendMessage(Text.literal("§a你穿过了画中世界！"), false);
                        continue;
                    }
                }
            } else if (!corridor.vertical && endReached >= 0 && endReached == ce.enteredFromEnd) {
                if (elapsed > 20) {
                    int exitPaintIdx = (endReached == 0) ? corridor.painting0Index : corridor.painting1Index;
                    boolean sealed = (endReached == 0) ? corridor.end0Sealed : corridor.end1Sealed;
                    if (!sealed && exitPaintIdx >= 0 && exitPaintIdx < data.getPlacedPaintings().size()) {
                        PlayerData.ShubingPainting exitPaint = data.getPlacedPaintings().get(exitPaintIdx);
                        if (exitPaint.pos != null) {
                            p.teleport(world, exitPaint.pos.getX() + 0.5, exitPaint.pos.getY() + 1.0,
                                    exitPaint.pos.getZ() + 0.5, java.util.Set.of(), p.getYaw(), p.getPitch(), false);
                            data.getCorridorPlayers().remove(entry.getKey());
                            PlayerData exitPd3 = players.get(entry.getKey());
                            if (exitPd3 != null) exitPd3.setLastCorridorExitTick(currentTick);
                            p.sendMessage(Text.literal("§a你从画中世界原路返回！"), false);
                            continue;
                        }
                    }
                }
            }

            // 倒计时提示(每秒一次, 最后5秒)
            int secondsLeft = (int)((200 - elapsed) / 20);
            if (secondsLeft <= 5 && secondsLeft > 0 && elapsed % 20 == 0) {
                sendTip(p, "§c§l⚠ 画中世界: " + secondsLeft + "秒后死亡！");
            }
        }
    }

    // JANE: 赏金猎人 tick — 速度2 + 补箭 + 头号目标追踪 + 暗黑视域 + HUD
    private void tickJane(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 被动: 速度2
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, false, false, true));

        // 确保散弹枪在身上(掉了/被换则补给)
        boolean hasShotgun = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(baby.sv.yepvpfabirc.ModItems.SHOTGUN)) {
                hasShotgun = true;
                break;
            }
        }
        if (!hasShotgun) {
            giveJaneShotgun(player, data);
        }

        // 头号目标追踪: 除Jane外击杀最多的玩家
        UUID topTarget = null;
        int maxKills = 0;
        for (PlayerData pd : players.values()) {
            if (pd.getPlayerUuid().equals(player.getUuid())) continue;
            if (!pd.isAlive()) continue;
            if (pd.getKills() > maxKills) {
                maxKills = pd.getKills();
                topTarget = pd.getPlayerUuid();
            }
        }
        data.setJaneTopTargetUuid(maxKills > 0 ? topTarget : null);

        // 暗黑视域: 失明 + 所有玩家发光
        if (data.isDarkVisionActive()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, true));
            for (PlayerData pd : players.values()) {
                if (pd.getPlayerUuid().equals(player.getUuid())) continue;
                if (!pd.isAlive()) continue;
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                if (target != null) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false, true));
                }
            }
        }

        // HUD提示: 赏金 + 头号目标
        String topName = "无";
        if (data.getJaneTopTargetUuid() != null) {
            PlayerData topData = players.get(data.getJaneTopTargetUuid());
            if (topData != null) topName = topData.getPlayerName() + "(" + topData.getKills() + "杀)";
        }
        String vision = data.isDarkVisionActive() ? "§c暗黑视域" : "§a常态";
        sendTip(player, "§6赏金: §e" + data.getBounty() + " §7| §c头号: §e" + topName + " §7| " + vision);
    }

    // JANE: 生物击杀赏金(由JaneMobKillMixin调用)
    public void onJaneMobKill(ServerPlayerEntity player, net.minecraft.entity.LivingEntity killed) {
        PlayerData data = players.get(player.getUuid());
        if (data == null || data.getRole() != Role.JANE) return;

        // 判断生物类型: 友好+10, 中立+10, 敌对+20
        if (killed instanceof ServerPlayerEntity) return; // 玩家击杀在handlePlayerDeath中处理
        int bountyGain;
        if (killed instanceof net.minecraft.entity.mob.Monster) {
            bountyGain = 20; // 敌对
        } else if (killed instanceof net.minecraft.entity.passive.AnimalEntity) {
            bountyGain = 10; // 友好
        } else {
            bountyGain = 10; // 其他(中立生物等)
        }
        data.addBounty(bountyGain);
        sendTip(player, "§6+§e" + bountyGain + " §6赏金！当前: §e" + data.getBounty());
    }

    public void initializePlayerForRole(ServerPlayerEntity player, PlayerData data) {
        initializePlayerForRole(player, data, true);
    }

    public void initializePlayerForRole(ServerPlayerEntity player, PlayerData data, boolean freshStart) {
        player.changeGameMode(GameMode.SURVIVAL);
        if (freshStart) {
            player.getInventory().clear();
        }
        player.clearStatusEffects();

        EntityAttributeInstance healthAttr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(20.0);
        }

        // freshStart时给基础装备，重生时保留原有物品
        if (freshStart) {
            giveBaseEquipment(player, data.getRole());
        }

        // 各职业额外装备和属性
        switch (data.getRole()) {
            case XLL -> setupXll(player, healthAttr);
            case JIEZU -> setupJiezu(player, healthAttr);
            case DAMAI -> setupDamai(player, healthAttr);
            case DASHA -> setupDasha(player, healthAttr, freshStart);
            case ST -> setupSt(player, healthAttr);
            case BOBBY -> setupBobby(player, healthAttr);
            case RETOUR -> setupRetour(player, healthAttr);
            case ALLAND -> setupAlland(player);
            case JVJV -> setupJvjv(player, healthAttr);
            case SANCHEZ -> setupSanchez(player, data, healthAttr);
            case JANE -> setupJane(player, data, healthAttr, freshStart);
            case LAYI -> setupLayi(player, data, healthAttr, freshStart);
            case MAYPOOR -> setupMaypoor(player, data, healthAttr);
            case YUSUI -> setupYusui(player, data, healthAttr);
            default -> {}
        }

        player.setHealth(player.getMaxHealth());

        if (data.getSpawnPoint() != null) {
            // 始终传送到主世界(玩家可能在大厅维度)
            ServerWorld targetWorld = server != null ? server.getOverworld() : (ServerWorld) player.getEntityWorld();
            player.teleport(targetWorld,
                    data.getSpawnPoint().getX() + 0.5,
                    data.getSpawnPoint().getY(),
                    data.getSpawnPoint().getZ() + 0.5,
                    Set.of(), player.getYaw(), player.getPitch(), false);
        }
    }

    private void setupXll(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(16.0); // 8心
    }

    private void setupJiezu(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(16.0); // 8心
        // 隐身由自定义渲染隐藏处理,不需要移除护甲
    }

    private void setupDamai(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(10.0); // 5心
    }

    private void setupYusui(ServerPlayerEntity player, PlayerData data, EntityAttributeInstance healthAttr) {
        // 基础10心 + 累积的+2心(每淘汰1个男娘)
        if (healthAttr != null) healthAttr.setBaseValue(20.0 + data.getYusuiBonusMaxHealth());
    }

    private void setupDasha(ServerPlayerEntity player, EntityAttributeInstance healthAttr, boolean freshStart) {
        if (healthAttr != null) healthAttr.setBaseValue(16.0); // 8心
        if (!freshStart) return; // 重生保留原有物品, 不重复给
        // 隐身由自定义渲染隐藏处理,不需要移除护甲
        player.getInventory().insertStack(new ItemStack(Items.BUCKET));
        ItemStack trident = new ItemStack(Items.TRIDENT);
        trident.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        var world = player.getEntityWorld();
        var riptide = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(Enchantments.RIPTIDE).orElse(null);
        if (riptide != null) trident.addEnchantment(riptide, 3);
        player.getInventory().insertStack(trident);
        // 深海探索者3靴子
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        var depthStrider = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(Enchantments.DEPTH_STRIDER).orElse(null);
        if (depthStrider != null) boots.addEnchantment(depthStrider, 3);
        player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, boots);
    }

    private void setupSt(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(20.0); // 10心(正常血量)
    }

    // ST看不到其他玩家名字和边框: 用Team的nameTagVisibility=hideForOtherTeams
    private void setupStNametagHiding() {
        if (server == null) return;
        var scoreboard = server.getScoreboard();
        // 清除旧team
        var oldTeam = scoreboard.getTeam("yepvp_visible");
        if (oldTeam != null) scoreboard.removeTeam(oldTeam);
        // 创建team: 只对同team成员显示名牌
        var team = scoreboard.addTeam("yepvp_visible");
        team.setNameTagVisibilityRule(net.minecraft.scoreboard.AbstractTeam.VisibilityRule.HIDE_FOR_OTHER_TEAMS);
        team.setFriendlyFireAllowed(true);
        team.setShowFriendlyInvisibles(false);
        // 所有非ST玩家加入team
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            if (entry.getValue().getRole() == Role.ST) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
            if (p != null) {
                scoreboard.addScoreHolderToTeam(p.getGameProfile().name(), team);
            }
        }
    }

    private void setupBobby(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(30.0); // 15心
    }

    private void setupRetour(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(20.0); // 10心
    }

    private void setupAlland(ServerPlayerEntity player) {
        // 颜料弹由技能系统自动管理, 不再给物品栏染料
    }

    private void setupJvjv(ServerPlayerEntity player, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(16.0); // 8心
    }

    private void setupSanchez(ServerPlayerEntity player, PlayerData data, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(12.0); // 6心
        // 重置狼群状态
        data.resetSanchezWolves();
        data.setAlphaWolfDead(false);
        data.setAlphaWolfHealth(40.0);
        // 头狼在下一个tick生成
    }

    private void setupJane(ServerPlayerEntity player, PlayerData data, EntityAttributeInstance healthAttr, boolean freshStart) {
        if (healthAttr != null) healthAttr.setBaseValue(16.0); // 8心
        if (!freshStart) return; // 重生保留物品
        // 给予散弹枪(开火逻辑在 tryFireShotgun 中处理, 无需弹药)
        giveJaneShotgun(player, data);
    }

    public void giveJaneShotgun(ServerPlayerEntity player, PlayerData data) {
        // 移除旧武器(散弹枪 / 历史遗留的弩)
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(baby.sv.yepvpfabirc.ModItems.SHOTGUN) || s.isOf(Items.CROSSBOW)) {
                player.getInventory().removeStack(i);
            }
        }
        ItemStack shotgun = new ItemStack(baby.sv.yepvpfabirc.ModItems.SHOTGUN);
        shotgun.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        shotgun.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l赏金猎人·散弹枪").styled(s -> s.withItalic(false)));
        // 强制放在快捷栏slot 0, 旧物品移走
        ItemStack oldSlot0 = player.getInventory().getStack(0);
        player.getInventory().setStack(0, shotgun);
        if (!oldSlot0.isEmpty()) {
            player.getInventory().insertStack(oldSlot0);
        }
    }

    // JANE: 散弹枪开火(右键触发) — 一次喷射一簇弹丸(箭), 带射速冷却与音效
    // 返回 true 表示已处理(消耗右键事件)
    public boolean tryFireShotgun(ServerPlayerEntity player, net.minecraft.util.Hand hand) {
        if (!gameActive) return false;
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(baby.sv.yepvpfabirc.ModItems.SHOTGUN)) return false;
        PlayerData data = players.get(player.getUuid());
        if (data == null || data.getRole() != Role.JANE || !data.isAlive()) return true;

        long now = player.getEntityWorld().getTime();
        // 射速冷却: 基础20tick(1秒), 每级泵动 -4tick, 下限6tick
        int cooldownTicks = Math.max(6, 20 - data.getCrossbowQuickCharge() * 4);
        if (now - data.getLastShotgunFireTick() < cooldownTicks) {
            return true; // 冷却中, 消耗事件但不开火
        }
        data.setLastShotgunFireTick(now);

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        int pellets = 5 + data.getCrossbowMultishot() * 2;       // 5 -> 11 颗弹丸
        double damage = 2.0 + data.getCrossbowPower() * 1.0;     // 每颗弹丸伤害(口径越大越疼)
        float divergence = 10.0f;                                // 散布角度

        for (int i = 0; i < pellets; i++) {
            net.minecraft.entity.projectile.ArrowEntity pellet =
                    new net.minecraft.entity.projectile.ArrowEntity(world, player, new ItemStack(Items.ARROW), stack);
            pellet.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 2.6f, divergence);
            pellet.setDamage(damage);
            pellet.setCritical(false);
            // 新建的箭默认 pickupType=DISALLOWED, 不会被拾取造成背包堆积
            world.spawnEntity(pellet);
        }

        // 开火音效
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sound.SoundEvents.ITEM_CROSSBOW_SHOOT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.4f);

        // 物品冷却视觉
        player.getItemCooldownManager().set(stack, cooldownTicks);
        return true;
    }

    // HELI: 地图点击 → 传送自己到最近被揭露的玩家身旁(1分钟CD)
    public void handleHeliMapTeleport(ServerPlayerEntity player, PlayerData data, int clickX, int clickZ) {
        long currentTick = player.getEntityWorld().getTime();
        long HELI_TP_CD = 1200L; // 1分钟
        if (currentTick - data.getLastSkillZTime() < HELI_TP_CD) {
            long remaining = (HELI_TP_CD - (currentTick - data.getLastSkillZTime())) / 20;
            sendTip(player, "§c传送冷却中... §e" + remaining + "s");
            return;
        }
        // 找点击位置最近的被揭露且存活的玩家(30格内)
        ServerPlayerEntity nearest = null;
        double nearestDist = 30 * 30;
        for (java.util.Map.Entry<UUID, Long> entry : data.getRevealedPlayerExpiry().entrySet()) {
            if (entry.getValue() <= currentTick) continue;
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
            if (target == null || !target.isAlive()) continue;
            PlayerData td = players.get(target.getUuid());
            if (td == null || !td.isAlive()) continue;
            double dx = target.getX() - clickX;
            double dz = target.getZ() - clickZ;
            double d = dx * dx + dz * dz;
            if (d < nearestDist) { nearestDist = d; nearest = target; }
        }
        if (nearest == null) {
            sendTip(player, "§c点击位置附近30格内没有被揭露的玩家！");
            return;
        }
        data.setLastSkillZTime(currentTick);
        ServerWorld world = (ServerWorld) nearest.getEntityWorld();
        player.teleport(world, nearest.getX() + 1, nearest.getY(), nearest.getZ(),
                java.util.Set.of(), player.getYaw(), player.getPitch(), false);
        sendTip(player, "§a已传送到 §e" + nearest.getGameProfile().name() + " §a身旁！");
        broadcastMessage("§6§l[揭幕] §r§e" + player.getGameProfile().name() + " §6传送到了 §e" + nearest.getGameProfile().name() + " §6身旁！");
    }

    public void handleJaneShopBuy(ServerPlayerEntity player, PlayerData data, int upgradeType) {
        int cost = 75;
        if (data.getBounty() < cost) {
            sendTip(player, "§c赏金不足！需要 §e" + cost + " §c赏金，当前 §e" + data.getBounty());
            NetworkHandler.sendJaneShopData(player, data);
            return;
        }
        switch (upgradeType) {
            case 0 -> { // 口径强化(每发弹丸伤害)
                if (data.getCrossbowPower() >= 5) {
                    sendTip(player, "§c口径强化已满级！(5级)");
                    NetworkHandler.sendJaneShopData(player, data);
                    return;
                }
                data.setBounty(data.getBounty() - cost);
                data.setCrossbowPower(data.getCrossbowPower() + 1);
                giveJaneShotgun(player, data);
                broadcastMessage("§6§l[赏金] §r§e" + player.getGameProfile().name() + " §6升级了 §e口径强化 " + data.getCrossbowPower() + " §7(-" + cost + "赏金)");
            }
            case 1 -> { // 泵动装填(射速)
                if (data.getCrossbowQuickCharge() >= 3) {
                    sendTip(player, "§c泵动装填已满级！(3级)");
                    NetworkHandler.sendJaneShopData(player, data);
                    return;
                }
                data.setBounty(data.getBounty() - cost);
                data.setCrossbowQuickCharge(data.getCrossbowQuickCharge() + 1);
                giveJaneShotgun(player, data);
                broadcastMessage("§6§l[赏金] §r§e" + player.getGameProfile().name() + " §6升级了 §e泵动装填 " + data.getCrossbowQuickCharge() + " §7(-" + cost + "赏金)");
            }
            case 2 -> { // 散射弹丸(弹丸数量)
                if (data.getCrossbowMultishot() >= 3) {
                    sendTip(player, "§c散射弹丸已满级！(3级)");
                    NetworkHandler.sendJaneShopData(player, data);
                    return;
                }
                data.setBounty(data.getBounty() - cost);
                data.setCrossbowMultishot(data.getCrossbowMultishot() + 1);
                giveJaneShotgun(player, data);
                broadcastMessage("§6§l[赏金] §r§e" + player.getGameProfile().name() + " §6升级了 §e散射弹丸 " + data.getCrossbowMultishot() + " §7(-" + cost + "赏金)");
            }
            default -> { return; }
        }
        // 购买成功后发送更新数据刷新UI
        NetworkHandler.sendJaneShopData(player, data);
    }

    public void onPlayerDeath(ServerPlayerEntity player, ServerPlayerEntity killer) {
        if (!gameActive) return;
        PlayerData victimData = players.get(player.getUuid());
        if (victimData == null) return;

        victimData.addDeath();

        // DAMAI(不死者)死亡特殊处理
        if (victimData.getRole() == Role.DAMAI) {
            if (victimData.isSkeletonForm()) {
                // 骷髅形态下意外死亡(饿死等) → 提前复活并损失命数
                victimData.loseLife();
                endDamaiSkeletonForm(player, victimData);
                broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c骷髅形态下死亡，提前复活！剩余 §e" + victimData.getLives() + " §c命");
            } else if (!victimData.isDamaiWaitingForSkeleton()) {
                // 正常死亡: 不扣命, 放墓碑, 3秒后转化骷髅(命数在骷髅形态结束时才扣)
                // 没命了直接淘汰
                if (victimData.getLives() <= 0) {
                    broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c命数耗尽，已淘汰！");
                } else {
                    BlockPos deathPos = player.getBlockPos();
                    ServerWorld world = server.getOverworld();
                    // 死在毒圈外 → 跳过墓碑/骷髅流程, 正常死亡扣命
                    if (!world.getWorldBorder().contains(deathPos)) {
                        victimData.loseLife();
                        if (victimData.getLives() <= 0) {
                            broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c在毒圈外死亡，命数耗尽！");
                        } else {
                            broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §c在毒圈外死亡，墓碑无法放置！剩余 §e" + victimData.getLives() + " §c命");
                        }
                    } else {
                        long currentTick = world.getTime();
                        victimData.setTombstonePos(deathPos);
                        victimData.setDamaiWaitingForSkeleton(true);
                        victimData.setDamaiDeathTick(currentTick);
                        victimData.setTombstoneProtectedUntilTick(currentTick + 200); // 10秒保护
                        // 放置墓碑方块(磁石)
                        world.setBlockState(deathPos, net.minecraft.block.Blocks.LODESTONE.getDefaultState());
                        // 不在死亡瞬间切旁观, 让respawnPlayer处理(避免死亡瞬间处理导致问题)
                        broadcastMessage("§5" + player.getGameProfile().name() + " §7(不死者) §e被击杀！墓碑已生成，3秒后转化为骷髅形态... §c剩余 §e" + victimData.getLives() + " §c命");
                    }
                }
            }
            // DAMAI死亡不走后续的loseLife/BOBBY等逻辑
            syncAllPlayersHud();
            checkGameEnd();
            return;
        }

        // 双人组(BOBBY/RETOUR)共用命数: 任何一方死亡扣1命并同步
        if (victimData.getRole() == Role.BOBBY || victimData.getRole() == Role.RETOUR) {
            // 灵体中死亡(BOBBY): 结束灵体状态
            if (victimData.getRole() == Role.BOBBY && victimData.getBobbyGhostEndTick() > 0) {
                victimData.setBobbyGhostEndTick(0);
                victimData.setBobbyGhostStartPos(null);
            }
            // 激光中死亡(RETOUR): 关闭激光
            if (victimData.getRole() == Role.RETOUR) {
                victimData.setRetourLaserActive(false);
            }
            victimData.loseLife();
            // 同步命数到队友
            Role partnerRole = (victimData.getRole() == Role.BOBBY) ? Role.RETOUR : Role.BOBBY;
            for (PlayerData pd : players.values()) {
                if (pd.getRole() == partnerRole) {
                    pd.setLives(victimData.getLives());
                    ServerPlayerEntity partner = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (partner != null) {
                        sendTip(partner, "§c§l[双人组] §r§e" + player.getGameProfile().name() + " §c死亡！共用命数: §e" + victimData.getLives());
                    }
                    break;
                }
            }
        } else if (victimData.getRole() == Role.POPCORN && !victimData.getBackupBodies().isEmpty()) {
            // POPCORN被动: 有备用躯体时不扣命, 随机一个备用躯体处复活
            java.util.List<BlockPos> bodies = victimData.getBackupBodies();
            int idx = new java.util.Random().nextInt(bodies.size());
            BlockPos revivePos = bodies.remove(idx);
            server.getOverworld().setBlockState(revivePos, net.minecraft.block.Blocks.AIR.getDefaultState());
            victimData.setPopcornReviving(true);
            victimData.setPopcornRevivePos(revivePos);
            // 旁观状态重置: 取消区块强制加载+删除躯壳+切回生存
            if (victimData.isPopcornSpectating()) {
                ServerWorld ow = server.getOverworld();
                int cx = (int) victimData.getPopcornSavedX() >> 4;
                int cz = (int) victimData.getPopcornSavedZ() >> 4;
                ow.setChunkForced(cx, cz, false);
                net.minecraft.entity.Entity bodyEntity = popcornBodyEntities.remove(player.getUuid());
                if (bodyEntity != null) bodyEntity.discard();
                victimData.setPopcornSpectating(false);
                player.changeGameMode(GameMode.SURVIVAL);
                player.setCameraEntity(player);
            }
            victimData.setPopcornCameraTarget(null);
            victimData.setPopcornSpectateSelectTick(0);
            broadcastMessage("§e" + player.getGameProfile().name() + " §7(智械危机) §a意识已上传！在备用躯体处重生...（剩余 §e" + bodies.size() + " §a个躯体）");
            syncAllPlayersHud();
            checkGameEnd();
            return;
        } else {
            victimData.loseLife();
        }

        // RETOUR(但丁)被淘汰 → 所有罪人失去力量+不能复活
        if (victimData.getRole() == Role.RETOUR && !victimData.isAlive()) {
            for (PlayerData pd : players.values()) {
                if (pd.getRole() == Role.BOBBY) {
                    pd.setApostleStrengthStacks(0);
                    pd.setBobbyCanRevive(false);
                    pd.setRetourDead(true);
                    ServerPlayerEntity bobby = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (bobby != null) {
                        bobby.clearStatusEffects();
                        bobby.sendMessage(Text.literal("§c§l但丁已被淘汰！你失去了所有力量，且不再能被复活！"), false);
                    }
                }
            }
            broadcastMessage("§d" + player.getGameProfile().name() + " §7(但丁) §c§l已被淘汰！罪人失去所有力量且无法再被复活！");
        }

        // ST 死亡时取消蓄力
        if (victimData.getRole() == Role.ST) {
            victimData.setSonicPendingTick(0);
            victimData.setSonicLockPos(null);
        }
        // NIHAO 死亡后重置(回复倒计时+1秒)
        if (victimData.getRole() == Role.NIHAO) victimData.resetNihaoOnDeath();
        // ALLAND 死亡后buff半减
        if (victimData.getRole() == Role.ALLAND) victimData.resetAllandOnDeath();
        // DASHA 死亡后重置水相关状态
        if (victimData.getRole() == Role.DASHA) victimData.setLastOutOfWaterTime(0);
        // JANE 死亡后清空赏金
        if (victimData.getRole() == Role.JANE) {
            int lostBounty = victimData.getBounty();
            victimData.resetJaneOnDeath();
            if (lostBounty > 0) broadcastMessage("§6§l[赏金] §r§e" + player.getGameProfile().name() + " §c死亡损失了 §e" + lostBounty + " §c赏金！");
        }
        // SANCHEZ 死亡后清除所有狼
        if (victimData.getRole() == Role.SANCHEZ) {
            ServerWorld world = server.getOverworld();
            // 删除头狼
            if (victimData.getAlphaWolfEntityUuid() != null) {
                net.minecraft.entity.Entity e = world.getEntity(victimData.getAlphaWolfEntityUuid());
                if (e != null && e.isAlive()) e.discard();
            }
            // 删除狼群
            for (UUID wolfUuid : victimData.getPackWolfUuids()) {
                net.minecraft.entity.Entity e = world.getEntity(wolfUuid);
                if (e != null && e.isAlive()) e.discard();
            }
            victimData.resetSanchezWolves();
        }
        // YOUZHA 死亡 → 释放所有被消化玩家
        if (victimData.getRole() == Role.YOUZHA && !victimData.getYouzhaTrappedPlayers().isEmpty()) {
            releaseAllYouzhaTrapped(victimData);
        }
        // DAMAI 死亡已在上方单独处理(early return), 此处不再需要
        // XLL 死亡后清除正方体(玻璃长道保留)
        if (victimData.getRole() == Role.XLL) {
            if (victimData.isGlassCubeActive() && victimData.getGlassCubeCenter() != null) {
                ServerWorld world = server.getOverworld();
                SkillHandler.removeGlassCube(world, victimData.getGlassCubeCenter());
                victimData.setGlassCubeActive(false);
                victimData.setGlassCubeCenter(null);
            }
        }

        // 击杀者奖励
        if (killer != null) {
            PlayerData killerData = players.get(killer.getUuid());
            if (killerData != null) {
                killerData.addKill();

                // DAMAI击杀: 骷髅形态下击杀+1命+延长1分钟
                if (killerData.getRole() == Role.DAMAI && killerData.isSkeletonForm()) {
                    killerData.addSkeletonKill();
                    killerData.gainLife();
                    killerData.extendSkeletonForm(1200); // +1分钟
                    // 刷新buff持续时间
                    long remaining = (int)(killerData.getSkeletonFormEndTick() - server.getOverworld().getTime());
                    if (remaining > 0) applyDamaiSkeletonBuffs(killer, (int)remaining);
                    broadcastMessage("§5" + killer.getGameProfile().name() + " §e骷髅形态击杀！+1命 +1分钟！(剩余" + killerData.getLives() + "命)");
                }

                // BOBBY击杀(受害者非RETOUR) → 自己和但丁各+1力量
                if (killerData.getRole() == Role.BOBBY && victimData.getRole() != Role.RETOUR) {
                    killerData.addApostleStrengthStack();
                    for (PlayerData pd : players.values()) {
                        if (pd.getRole() == Role.RETOUR && pd.isAlive()) {
                            pd.addApostleStrengthStack();
                        }
                    }
                    broadcastMessage("§a" + killer.getGameProfile().name() + " §7(罪人) §e击杀敌人，罪人和但丁获得 §c+1力量§e！(当前" + killerData.getApostleStrengthStacks() + "/5)");
                }

                // RETOUR击杀(受害者非BOBBY) → 自己和罪人各+1力量
                if (killerData.getRole() == Role.RETOUR && victimData.getRole() != Role.BOBBY) {
                    killerData.addApostleStrengthStack();
                    for (PlayerData pd : players.values()) {
                        if (pd.getRole() == Role.BOBBY) {
                            pd.addApostleStrengthStack();
                        }
                    }
                    broadcastMessage("§d" + killer.getGameProfile().name() + " §7(但丁) §e击杀敌人，罪人和但丁获得 §c+1力量§e！(当前" + killerData.getApostleStrengthStacks() + "/5)");
                }

                // POPCORN击杀获得备用躯体
                if (killerData.getRole() == Role.POPCORN) {
                    killerData.addBackupBodyCharge();
                    broadcastMessage("§e" + killer.getGameProfile().name() + " §a击杀获得备用躯体充能！");
                }

                // JVJV: 普通击杀不+命(只有大爆炸/红莲华才+命, 见executeJvjvExplosion)

                // SANCHEZ击杀获得召唤狼次数
                if (killerData.getRole() == Role.SANCHEZ) {
                    killerData.addWolfKillCharge();
                }

                // JANE击杀玩家+100赏金, 头号目标额外+100
                if (killerData.getRole() == Role.JANE) {
                    int bountyGain = 100;
                    boolean isTopTarget = player.getUuid().equals(killerData.getJaneTopTargetUuid());
                    if (isTopTarget) bountyGain += 100;
                    killerData.addBounty(bountyGain);
                    String msg = "§6§l[赏金] §r§e" + killer.getGameProfile().name() + " §6击杀玩家 §e" + player.getGameProfile().name() + " §6+§e" + bountyGain + " §6赏金！";
                    if (isTopTarget) msg += " §c(头号目标!)";
                    broadcastMessage(msg);
                }

                // 被男娘击杀的玩家也变成男娘（感染机制）— 通过SkillHandler给正确的buff
                if (killerData.isNanniang() && !victimData.isNanniang() && victimData.getRole() != Role.YUSUI) {
                    SkillHandler.onNanniangKillConvert(player, killerData);
                }
                // 鱼碎自己击杀也将对方转化为男娘
                if (killerData.getRole() == Role.YUSUI && !victimData.isNanniang() && victimData.getRole() != Role.YUSUI) {
                    SkillHandler.onNanniangKillConvert(player, killerData);
                }
            }
        }

        // 男娘被淘汰 → 鱼碎永久+2心(累积到PlayerData, 死亡保留)
        if (victimData.isNanniang() && !victimData.isAlive()) {
            for (PlayerData pd : players.values()) {
                if (pd.getRole() == Role.YUSUI && pd.isAlive()) {
                    pd.addYusuiBonusMaxHealth(4.0); // +2心(4hp)累积
                    ServerPlayerEntity yusuiPlayer = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (yusuiPlayer != null) {
                        EntityAttributeInstance ha = yusuiPlayer.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                        if (ha != null) {
                            ha.setBaseValue(ha.getBaseValue() + 4.0);
                            yusuiPlayer.setHealth(yusuiPlayer.getMaxHealth());
                        }
                        broadcastMessage("§d" + victimData.getPlayerName() + " §c(男娘)被淘汰！§d" + pd.getPlayerName() + " §e(鱼碎)获得 §c+2心§e！");
                    }
                    break;
                }
            }
        }

        {
            if (victimData.isAlive()) {
                broadcastMessage("§a" + player.getGameProfile().name() + " §c死亡！剩余 §e" + victimData.getLives() + " §c条命");
                // JVJV: 新命重置大爆炸 + 取消红莲华蓄力 + 释放被吞者
                if (victimData.getRole() == Role.JVJV) {
                    victimData.resetExplosionForNewLife();
                    victimData.setRedLotusActiveTick(0);
                    victimData.setLastSkillXTime(0);
                    victimData.setRedLotusPos(null);
                    // 释放被吞者
                    if (victimData.getJvjvEatenPlayer() != null) {
                        ServerPlayerEntity eaten = server.getPlayerManager().getPlayer(victimData.getJvjvEatenPlayer());
                        if (eaten != null) {
                            releaseJvjvEaten(victimData, eaten, false);
                        } else {
                            clearJvjvStomachRoom(victimData);
                        }
                    }
                }
            } else {
                broadcastMessage("§a" + player.getGameProfile().name() + " §c§l已被淘汰！");
            }
        }

        syncAllPlayersHud();
    }

    // 但丁复活罪人(伤害在SkillHandler中处理)
    public boolean reviveApostle(ServerPlayerEntity retour, double maxDistance, boolean nearRevive) {
        PlayerData retourData = players.get(retour.getUuid());
        if (retourData == null || retourData.getRole() != Role.RETOUR) return false;

        double maxDistSq = maxDistance == Double.MAX_VALUE ? Double.MAX_VALUE : maxDistance * maxDistance;

        for (PlayerData pd : players.values()) {
            if (pd.getRole() != Role.BOBBY || !pd.isWaitingForRevive()) continue;
            ServerPlayerEntity bobby = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (bobby == null) continue;
            BlockPos deathPos = pd.getDeathPos();
            if (deathPos == null) continue;
            double dist = retour.squaredDistanceTo(deathPos.getX() + 0.5, deathPos.getY(), deathPos.getZ() + 0.5);
            if (dist <= maxDistSq) {
                pd.setWaitingForRevive(false);
                bobby.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                initializePlayerForRole(bobby, pd, false);
                // 罪人复活在死亡原地
                bobby.teleport(server.getOverworld(), deathPos.getX() + 0.5, deathPos.getY(), deathPos.getZ() + 0.5, java.util.Set.of(), bobby.getYaw(), bobby.getPitch(), false);
                long reviveTick = server.getOverworld().getTime();
                pd.setRespawnInvincibleUntil(reviveTick + RESPAWN_INVINCIBLE_TICKS);
                bobby.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false, true));
                bobby.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 255, false, false, true));

                // 复活罪人后但丁位置暴露在所有人地图上30秒
                retourData.setRetourRevealedUntilTick(reviveTick + 600); // 30秒
                retour.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0, false, false, true));

                broadcastMessage("§d" + retour.getGameProfile().name() + " §7(但丁) §a复活了罪人 §e" + pd.getPlayerName() + "§a！§c但丁位置暴露30秒！");
                syncAllPlayersHud();
                return true;
            }
        }
        return false;
    }

    public void respawnPlayer(ServerPlayerEntity player) {
        if (!gameActive) return;
        PlayerData data = players.get(player.getUuid());
        if (data == null) return;

        // 使徒/但丁等待复活时: 新实体必须设为SPECTATOR(旧实体的SPECTATOR不会继承到新实体)
        if ((data.getRole() == Role.BOBBY || data.getRole() == Role.RETOUR) && data.isWaitingForRevive()) {
            player.changeGameMode(GameMode.SPECTATOR);
            return;
        }
        // 不死者等待骷髅转化时: 新实体必须设为SPECTATOR，传送到墓碑位置，重置3秒计时
        if (data.getRole() == Role.DAMAI && data.isDamaiWaitingForSkeleton()) {
            player.changeGameMode(GameMode.SPECTATOR);
            BlockPos tomb = data.getTombstonePos();
            if (tomb != null) {
                player.teleport(server.getOverworld(), tomb.getX() + 0.5, tomb.getY() + 1.5, tomb.getZ() + 0.5,
                        Set.of(), player.getYaw(), player.getPitch(), false);
            }
            data.setDamaiDeathTick(server.getOverworld().getTime()); // 重置计时，确保3秒从此刻起算
            return;
        }
        // POPCORN被动复活: 传送至备用躯体位置，恢复全血+临时+5心
        if (data.getRole() == Role.POPCORN && data.isPopcornReviving()) {
            data.setPopcornReviving(false);
            BlockPos revivePos = data.getPopcornRevivePos();
            data.setPopcornRevivePos(null);
            initializePlayerForRole(player, data, false);
            ServerWorld world = server.getOverworld();
            if (revivePos != null) {
                player.teleport(world, revivePos.getX() + 0.5, revivePos.getY() + 1, revivePos.getZ() + 0.5,
                        Set.of(), player.getYaw(), player.getPitch(), false);
                data.setSpawnPoint(revivePos);
            }
            applyPopcornBonusHealth(player, data);
            long currentTick = world.getTime();
            data.setRespawnInvincibleUntil(currentTick + RESPAWN_INVINCIBLE_TICKS);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 255, false, false, true));
            broadcastMessage("§e" + player.getGameProfile().name() + " §7(智械危机) §a从备用躯体复活！");
            syncAllPlayersHud();
            return;
        }
        if (!data.isAlive()) {
            // 已淘汰玩家变观察者
            player.changeGameMode(GameMode.SPECTATOR);
            return;
        }

        // 在玩家当前位置附近复活(区块已加载,避免长时间等待)
        ServerWorld world = server.getOverworld();
        Random rand = new Random();
        int baseX = (int) player.getX();
        int baseZ = (int) player.getZ();
        // 随机偏移30-80格防止原地复活被蹲
        int offsetX = (rand.nextInt(50) + 30) * (rand.nextBoolean() ? 1 : -1);
        int offsetZ = (rand.nextInt(50) + 30) * (rand.nextBoolean() ? 1 : -1);
        int rx = baseX + offsetX;
        int rz = baseZ + offsetZ;
        WorldBorder border = world.getWorldBorder();
        double halfSize = border.getSize() / 2.0;
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        BlockPos safePos;
        if (halfSize < 15.0) {
            // 终圈太小: 直接复活在圈中心
            int cy = findSafeY(world, (int) centerX, (int) centerZ);
            if (cy < 55) cy = (int) MAP_CENTER_Y;
            safePos = new BlockPos((int) centerX, cy, (int) centerZ);
        } else {
            // 在圈内随机选点(留5格余量)
            double safeRange = halfSize - 5.0;
            rx = (int) Math.max(centerX - safeRange, Math.min(centerX + safeRange, rx));
            rz = (int) Math.max(centerZ - safeRange, Math.min(centerZ + safeRange, rz));
            world.getChunk(rx >> 4, rz >> 4);
            BlockPos candidate = findSafeLandSpawn(world, rx, rz, rand);
            // clamp回圈内(findSafeLandSpawn可能跑出圈外)
            int cx = (int) Math.max(centerX - safeRange, Math.min(centerX + safeRange, candidate.getX()));
            int cz = (int) Math.max(centerZ - safeRange, Math.min(centerZ + safeRange, candidate.getZ()));
            int cy = candidate.getY();
            if (cx != candidate.getX() || cz != candidate.getZ()) {
                cy = findSafeY(world, cx, cz);
                if (cy < 55) cy = (int) MAP_CENTER_Y;
            }
            safePos = new BlockPos(cx, cy, cz);
        }
        data.setSpawnPoint(safePos);
        // 立即传送到safePos, 避免重生后短暂出现在死亡位置(可能在圈外)
        player.teleport(world, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5,
                Set.of(), player.getYaw(), player.getPitch(), false);

        initializePlayerForRole(player, data, false);

        // 10秒无敌（无法攻击和被攻击）
        long currentTick = world.getTime();
        data.setRespawnInvincibleUntil(currentTick + RESPAWN_INVINCIBLE_TICKS);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 255, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0, false, false, true));
        sendTip(player, "§e§l复活无敌期! §r§e10秒内无法攻击和被攻击");
        // 标记: 若重生位置在圈外则3秒内自动传送到安全位置(仅一次窗口)
        data.setNeedsBorderSafeTeleportUntilTick(currentTick + 60);
    }

    // 检测所有玩家重生点是否在世界边界内, 不在则重新分配
    private void tickSpawnPointValidation() {
        ServerWorld world = server.getOverworld();
        WorldBorder border = world.getWorldBorder();
        Random rand = new Random();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double halfSize = border.getSize() / 2.0 - 10; // 留10格余量

        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            BlockPos sp = pd.getSpawnPoint();
            if (sp == null) continue;
            if (border.contains(sp)) continue;
            // 重生点在圈外, 重新分配
            int rx = (int) centerX + (rand.nextInt((int) Math.max(1, halfSize * 2)) - (int) halfSize);
            int rz = (int) centerZ + (rand.nextInt((int) Math.max(1, halfSize * 2)) - (int) halfSize);
            // 确保在边界内
            rx = (int) Math.max(centerX - halfSize, Math.min(centerX + halfSize, rx));
            rz = (int) Math.max(centerZ - halfSize, Math.min(centerZ + halfSize, rz));
            world.getChunk(rx >> 4, rz >> 4);
            BlockPos newSpawn = findSafeLandSpawn(world, rx, rz, rand);
            pd.setSpawnPoint(newSpawn);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (player != null) {
                sendTip(player, "§e你的重生点已因缩圈自动更新至圈内！");
            }
        }
    }

    private void checkGameEnd() {
        if (!gameActive || victorySceneActive) return;

        // 存活玩家列表
        List<PlayerData> aliveList = players.values().stream()
                .filter(pd -> pd.isAlive() && !pd.isWaitingForRevive())
                .collect(java.util.stream.Collectors.toList());

        // 鱼碎胜利: 场上所有存活非鱼碎玩家都是男娘时, 鱼碎获胜
        if (aliveList.size() >= 2) {
            PlayerData yusuiAlive = null;
            boolean allOthersNanniang = true;
            for (PlayerData pd : aliveList) {
                if (pd.getRole() == Role.YUSUI) {
                    yusuiAlive = pd;
                } else if (!pd.isNanniang()) {
                    allOthersNanniang = false;
                }
            }
            if (yusuiAlive != null && allOthersNanniang) {
                broadcastMessage("§d§l【鱼碎胜利】§r§d 所有存活玩家都已变成男娘！§e" + yusuiAlive.getPlayerName() + " §d(鱼碎)获胜！");
                triggerVictoryScene(List.of(yusuiAlive));
                return;
            }
        }

        // 双人组胜利: BOBBY + RETOUR 同时存活
        if (aliveList.size() == 2) {
            PlayerData a = aliveList.get(0);
            PlayerData b = aliveList.get(1);
            boolean isDuo = (a.getRole() == Role.BOBBY && b.getRole() == Role.RETOUR)
                    || (a.getRole() == Role.RETOUR && b.getRole() == Role.BOBBY);
            if (isDuo) {
                triggerVictoryScene(aliveList);
                return;
            }
        }

        if (aliveList.size() <= 1) {
            if (!aliveList.isEmpty()) {
                triggerVictoryScene(aliveList);
            } else {
                broadcastMessage("§a§l【游戏结束】§c 没有幸存者！");
                broadcastScoreboard();
                sendGameEndHud(null);
                stopGame();
            }
        }
    }

    /**
     * 触发胜利演礼: 广播+圣杯出现+胜利者许愿台词+结束游戏.
     * 支持单人或双人组(BOBBY+RETOUR)胜利.
     */
    public void triggerVictoryScene(List<PlayerData> winners) {
        if (victorySceneActive || !gameActive) return;
        if (winners == null || winners.isEmpty()) {
            broadcastMessage("§a§l【游戏结束】§c 没有幸存者！");
            broadcastScoreboard();
            sendGameEndHud(null);
            stopGame();
            return;
        }
        victorySceneActive = true;

        long currentTick = server.getOverworld().getTime();

        // 拼接胜利者名称
        StringBuilder nameList = new StringBuilder();
        for (int i = 0; i < winners.size(); i++) {
            if (i > 0) nameList.append(" §a& §e");
            nameList.append(winners.get(i).getPlayerName());
        }
        StringBuilder roleList = new StringBuilder();
        for (int i = 0; i < winners.size(); i++) {
            if (i > 0) roleList.append(" §a+ §e");
            roleList.append(winners.get(i).getRole().getDisplayName());
        }

        final String winnerDisplay = nameList.toString();
        final String winnerRoles = roleList.toString();

        // Stage 0: 胜利广播
        if (winners.size() >= 2) {
            broadcastMessage("§a§l【游戏结束】§e " + winnerDisplay + " §a共同获胜！职业: §e" + winnerRoles);
        } else {
            broadcastMessage("§a§l【游戏结束】§e " + winnerDisplay + " §a获胜！职业: §e" + winnerRoles);
        }

        // Stage 1 (2秒): 圣杯降临
        addDelayedAction(currentTick + 40, () -> {
            broadcastMessage("§6§l✧ 奇迹降临 ✧ §r§e 在 §6§l" + winnerDisplay + " §r§e面前出现了一个 §d§l许愿圣杯§r§e！");
            // 给每个赢家一圈 happy_villager 粒子
            ServerWorld w = server.getOverworld();
            for (PlayerData pd : winners) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                if (p != null) {
                    w.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            p.getX(), p.getY() + 1.5, p.getZ(),
                            40, 1.0, 1.0, 1.0, 0.1);
                    w.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                            p.getX(), p.getY() + 1.5, p.getZ(),
                            20, 0.5, 1.0, 0.5, 0.05);
                }
            }
        });

        // Stage 2 (5秒): 胜利者"说"出愿望 — 以聊天格式广播(看起来像玩家发言)
        addDelayedAction(currentTick + 100, () -> {
            for (PlayerData pd : winners) {
                broadcastMessage("§7<§f" + pd.getPlayerName() + "§7> §f我的愿望是！！！§c§l世界毁灭！");
            }
        });

        // Stage 3 (8秒): 结束游戏
        addDelayedAction(currentTick + 160, () -> {
            broadcastScoreboard();
            sendGameEndHud(winners.get(0));
            stopGame();
        });
    }

    /** 管理员命令: 直接判定某玩家胜利 */
    public boolean triggerVictorySceneForPlayer(PlayerData winner) {
        if (!gameActive || victorySceneActive || winner == null) return false;
        triggerVictoryScene(java.util.Collections.singletonList(winner));
        return true;
    }

    private void sendGameEndHud(PlayerData winner) {
        JsonObject root = new JsonObject();
        root.addProperty("gameActive", false);
        root.addProperty("gameEnded", true);
        root.addProperty("revealed", true);
        root.addProperty("hasGreeting", false);
        if (winner != null) {
            root.addProperty("winnerName", winner.getPlayerName());
            root.addProperty("winnerRole", winner.getRole().getDisplayName());
        } else {
            root.addProperty("winnerName", "");
            root.addProperty("winnerRole", "");
        }

        // 最终记分板
        JsonArray scoreboard = new JsonArray();
        List<PlayerData> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> b.getScore() - a.getScore());
        for (PlayerData pd : sorted) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", pd.getPlayerName());
            entry.addProperty("role", pd.getRole().getDisplayName());
            entry.addProperty("lives", pd.getLives() == Integer.MAX_VALUE ? -1 : pd.getLives());
            entry.addProperty("kills", pd.getKills());
            entry.addProperty("score", pd.getScore());
            entry.addProperty("alive", pd.isAlive());
            scoreboard.add(entry);
        }
        root.add("scoreboard", scoreboard);

        // 空self和skills占位
        JsonObject selfObj = new JsonObject();
        selfObj.addProperty("name", "");
        selfObj.addProperty("role", "");
        selfObj.addProperty("roleName", "");
        selfObj.addProperty("roleDesc", "");
        selfObj.addProperty("lives", 0);
        selfObj.addProperty("kills", 0);
        selfObj.addProperty("deaths", 0);
        selfObj.addProperty("score", 0);
        selfObj.addProperty("alive", false);
        root.add("self", selfObj);
        root.add("skills", new JsonArray());

        String jsonStr = root.toString();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            NetworkHandler.syncHudToPlayer(p, jsonStr);
        }
    }

    private void broadcastScoreboard() {
        List<PlayerData> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> b.getScore() - a.getScore());
        broadcastMessage("§a§l======= 最终战绩 =======");
        int rank = 1;
        for (PlayerData pd : sorted) {
            broadcastMessage("§e#" + rank + " §a" + pd.getPlayerName() + " §7[" + pd.getRole().getDisplayName() + "] §e击杀:" + pd.getKills() + " 死亡:" + pd.getDeaths() + " 得分:" + pd.getScore());
            rank++;
        }
        broadcastMessage("§a§l========================");
    }

    public void broadcastMessage(String message) {
        if (server == null) return;
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.get(uuid);
    }

    public Map<UUID, PlayerData> getAllPlayerData() {
        return Collections.unmodifiableMap(players);
    }

    public boolean isRevealed() {
        return revealed;
    }

    // 管理员手动触发明牌
    public void revealAll() {
        if (!gameActive) return;
        if (revealed) return;
        revealed = true;
        for (PlayerData pd : players.values()) {
            pd.setRevealed(true);
        }
        broadcastMessage("§a§l【明牌阶段】§r§e 管理员已公开所有玩家的职业和位置！");
        // 给所有存活玩家发光
        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (p != null) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, true));
            }
        }
        syncAllPlayersHud();
        sendRevealScreenToAll();
    }

    private void sendRevealScreenToAll() {
        JsonObject json = new JsonObject();
        JsonArray playersArr = new JsonArray();
        for (PlayerData pd : players.values()) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", pd.getPlayerName());
            pObj.addProperty("roleName", pd.getRole().getDisplayName());
            pObj.addProperty("roleDesc", pd.getRole().getDescription());
            pObj.addProperty("alive", pd.isAlive());
            pObj.addProperty("lives", pd.getLives());
            pObj.addProperty("kills", pd.getKills());
            playersArr.add(pObj);
        }
        json.add("players", playersArr);
        String jsonStr = json.toString();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            NetworkHandler.sendRevealScreen(p, jsonStr);
        }
    }

    public void syncAllPlayersHud() {
        if (server != null) {
            baby.sv.yepvpfabirc.network.HudSyncManager.syncAllPlayers(server);
        }
    }

    // 发送左下角提示消息(替代actionbar, 持续3秒)
    public void sendTip(ServerPlayerEntity player, String message) {
        PlayerData data = players.get(player.getUuid());
        if (data != null && server != null) {
            data.setTip(message, server.getOverworld().getTime() + 60); // 3秒
        }
    }

    public Map<String, Role> getPlayerNameToRole() {
        return playerNameToRole;
    }

    // 完全隐身玩家管理
    public Set<UUID> getFullyHiddenPlayers() { return fullyHiddenPlayers; }

    // 同步隐身玩家列表到所有客户端
    private void syncHiddenPlayers() {
        // 重新计算隐身玩家集合
        fullyHiddenPlayers.clear();
        long currentTick = server.getOverworld().getTime();
        long dayTime = server.getOverworld().getTimeOfDay() % 24000;
        boolean isNight = dayTime >= 13000 && dayTime <= 23000;

        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            // JIEZU夜晚完全隐身
            if (pd.getRole() == Role.JIEZU && isNight) {
                fullyHiddenPlayers.add(pd.getPlayerUuid());
            }
            // DASHA水中/雨中完全隐身(显形期除外)
            if (pd.getRole() == Role.DASHA && pd.isInWater() && currentTick >= pd.getDashaVisibleUntilTick()) {
                fullyHiddenPlayers.add(pd.getPlayerUuid());
            }
            // ALLAND绿+绿隐身区域: 区域内的ALLAND完全隐身
            if (pd.getRole() == Role.ALLAND) {
                ServerPlayerEntity ap = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
                if (ap != null) {
                    for (PaintZone zone : paintZones) {
                        if (zone.invisZone && zone.ownerUuid.equals(pd.getPlayerUuid())) {
                            double dx = ap.getX() - (zone.center.getX() + 0.5);
                            double dz = ap.getZ() - (zone.center.getZ() + 0.5);
                            if (dx * dx + dz * dz <= 25) {
                                fullyHiddenPlayers.add(pd.getPlayerUuid());
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 序列化: 4字节数量 + 每个UUID 16字节(高低各8字节)
        byte[] data = new byte[4 + fullyHiddenPlayers.size() * 16];
        int count = fullyHiddenPlayers.size();
        data[0] = (byte)(count >> 24); data[1] = (byte)(count >> 16);
        data[2] = (byte)(count >> 8); data[3] = (byte)(count);
        int offset = 4;
        for (UUID uuid : fullyHiddenPlayers) {
            long msb = uuid.getMostSignificantBits();
            long lsb = uuid.getLeastSignificantBits();
            for (int i = 7; i >= 0; i--) { data[offset++] = (byte)(msb >> (i * 8)); }
            for (int i = 7; i >= 0; i--) { data[offset++] = (byte)(lsb >> (i * 8)); }
        }

        // 发送给所有客户端
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            NetworkHandler.sendHiddenPlayers(p, data);
        }
    }

    // HUD数据 getters
    public long getGameStartTick() { return gameStartTick; }

    public long getGameElapsedTicks() {
        if (!gameActive || server == null) return 0;
        return server.getOverworld().getTime() - gameStartTick;
    }

    public long getNextShrinkTicks() {
        if (!gameActive || server == null) return 0;
        long elapsed = server.getOverworld().getTime() - gameStartTick;
        // 找下一个还没开始的阶段
        for (int i = 0; i < PHASE_START_TICK.length; i++) {
            if (elapsed < PHASE_START_TICK[i]) {
                return PHASE_START_TICK[i] - elapsed;
            }
        }
        return 0;
    }

    public double getCurrentBorderRadius() {
        if (server == null) return MAP_RADIUS;
        WorldBorder border = server.getOverworld().getWorldBorder();
        return border.getSize() / 2.0;
    }

    public double getTargetBorderRadius() {
        if (server == null) return MAP_RADIUS;
        WorldBorder border = server.getOverworld().getWorldBorder();
        // 返回当前边界正在插值的目标大小的一半
        return border.getSize() / 2.0; // 当前实际大小
    }

    public int getPoisonShrinkCount() { return shrinkPhase; }

    public double getDistanceToBorder(ServerPlayerEntity player) {
        if (server == null) return 0;
        WorldBorder border = server.getOverworld().getWorldBorder();
        double halfSize = border.getSize() / 2.0;
        double dx = Math.abs(player.getX() - border.getCenterX());
        double dz = Math.abs(player.getZ() - border.getCenterZ());
        double distX = halfSize - dx;
        double distZ = halfSize - dz;
        return Math.max(0, Math.min(distX, distZ));
    }

    // 观战者切换观察目标
    public void handleSpectatorSwitch(ServerPlayerEntity spectator, int direction) {
        List<ServerPlayerEntity> alivePlayers = new ArrayList<>();
        for (PlayerData pd : players.values()) {
            if (!pd.isAlive()) continue;
            if (pd.getRole() == Role.BOBBY && pd.isWaitingForRevive()) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (p != null) alivePlayers.add(p);
        }
        if (alivePlayers.isEmpty()) return;

        if (direction == 0) {
            // 自由视角：取消观察
            spectator.setCameraEntity(spectator);
            spectator.sendMessage(Text.literal("§7自由视角模式"), true);
            return;
        }

        // 找当前观察的玩家索引
        net.minecraft.entity.Entity currentTarget = spectator.getCameraEntity();
        int currentIdx = -1;
        if (currentTarget instanceof ServerPlayerEntity targetPlayer) {
            for (int i = 0; i < alivePlayers.size(); i++) {
                if (alivePlayers.get(i).getUuid().equals(targetPlayer.getUuid())) {
                    currentIdx = i;
                    break;
                }
            }
        }

        int nextIdx;
        if (currentIdx == -1) {
            nextIdx = 0;
        } else {
            nextIdx = (currentIdx + direction + alivePlayers.size()) % alivePlayers.size();
        }

        ServerPlayerEntity target = alivePlayers.get(nextIdx);
        spectator.setCameraEntity(target);
        PlayerData targetData = players.get(target.getUuid());
        String roleName = (targetData != null && isRevealed()) ? " §7[" + targetData.getRole().getDisplayName() + "]" : "";
        spectator.sendMessage(Text.literal("§a观察: §e" + target.getGameProfile().name() + roleName), true);
    }

    // ==================== DEBUG模式 ====================
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debug) { this.debugMode = debug; }

    public boolean switchPlayerRole(ServerPlayerEntity player, Role newRole) {
        if (!gameActive) return false;
        PlayerData data = players.get(player.getUuid());
        if (data == null) return false;

        // 清理旧职业状态
        Role oldRole = data.getRole();
        if (oldRole == Role.XLL) {
            ServerWorld world = server.getOverworld();
            SkillHandler.removeAllGlassRoads(world, data);
            if (data.isGlassCubeActive() && data.getGlassCubeCenter() != null) {
                SkillHandler.removeGlassCube(world, data.getGlassCubeCenter());
            }
        }
        if (oldRole == Role.DAMAI) {
            if (data.isSkeletonForm() || data.isDamaiWaitingForSkeleton()) {
                endDamaiSkeletonForm(player, data);
            }
        }

        // 创建新PlayerData并保留基础信息
        PlayerData newData = new PlayerData(player.getUuid(), player.getGameProfile().name(), newRole);
        newData.setSpawnPoint(data.getSpawnPoint());
        newData.setLives(data.getLives());
        players.put(player.getUuid(), newData);

        // 特殊命数
        if (newRole == Role.BOBBY) newData.setLives(5);
        else if (newRole == Role.RETOUR) newData.setLives(5);
        else if (newRole == Role.YUSUI) newData.setLives(5);
        else if (newRole == Role.DAMAI) newData.setLives(3);
        else if (newRole == Role.DASHA) newData.setLives(4);
        else if (newRole == Role.POPCORN) newData.setLives(1);
        else if (newRole == Role.JVJV) newData.setLives(2);

        // 重新初始化
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.clearStatusEffects();
        initializePlayerForRole(player, newData);
        player.setHealth(player.getMaxHealth());

        broadcastMessage("§e§l[DEBUG] §r§e" + player.getGameProfile().name() + " §a职业切换: §7" + oldRole.getDisplayName() + " §a→ §e" + newRole.getDisplayName());
        syncAllPlayersHud();
        return true;
    }

    // ==================== 箱子战利品系统 ====================
    public void onChestOpen(ServerPlayerEntity player, BlockPos pos, ServerWorld world) {
        if (!gameActive) return;
        if (openedChests.contains(pos)) return; // 已开过
        // 检查箱子是否空的
        net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof net.minecraft.block.entity.ChestBlockEntity chest)) return;
        boolean isEmpty = true;
        for (int i = 0; i < chest.size(); i++) {
            if (!chest.getStack(i).isEmpty()) { isEmpty = false; break; }
        }
        if (!isEmpty) return;
        openedChests.add(pos);
        generateChestLoot(chest, world);
    }

    private void generateChestLoot(net.minecraft.block.entity.ChestBlockEntity chest, ServerWorld world) {
        Random rand = new Random();
        List<ItemStack> loot = new ArrayList<>();
        var reg = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);

        // 0-5个随机矿石(铜-钻石)
        int oreCount = rand.nextInt(6);
        net.minecraft.item.Item[] ores = {Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND};
        for (int i = 0; i < oreCount; i++) {
            loot.add(new ItemStack(ores[rand.nextInt(ores.length)]));
        }

        // 固定10-15个猪排
        loot.add(new ItemStack(Items.COOKED_PORKCHOP, 10 + rand.nextInt(6)));

        // 1-3组道具
        int groupCount = 1 + rand.nextInt(3);
        for (int i = 0; i < groupCount; i++) {
            loot.add(generateRandomItemGroup(rand, reg, world));
        }

        // 随机放入箱子槽位
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < chest.size(); i++) slots.add(i);
        Collections.shuffle(slots, rand);
        for (int i = 0; i < Math.min(loot.size(), slots.size()); i++) {
            chest.setStack(slots.get(i), loot.get(i));
        }
        chest.markDirty();
    }

    @SuppressWarnings("unchecked")
    private ItemStack generateRandomItemGroup(Random rand, net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> reg, ServerWorld world) {
        int type = rand.nextInt(26);
        return switch (type) {
            case 0 -> new ItemStack(Items.DIRT, 10 + rand.nextInt(21));
            case 1 -> new ItemStack(Items.OBSIDIAN, 1 + rand.nextInt(3));
            case 2 -> new ItemStack(Items.STONE, 10 + rand.nextInt(11));
            case 3 -> new ItemStack(Items.OAK_PLANKS, 10 + rand.nextInt(11));
            case 4 -> new ItemStack(Items.WIND_CHARGE, 1 + rand.nextInt(5));
            case 5 -> new ItemStack(Items.FISHING_ROD);
            case 6 -> new ItemStack(Items.FLINT_AND_STEEL);
            case 7 -> new ItemStack(Items.ENDER_PEARL, 1 + rand.nextInt(3));
            case 8 -> new ItemStack(Items.SWEET_BERRIES, 5 + rand.nextInt(6));
            case 9 -> new ItemStack(Items.CHORUS_FRUIT, 1 + rand.nextInt(3));
            case 10 -> new ItemStack(Items.TNT, 1 + rand.nextInt(3));
            case 11 -> new ItemStack(Items.COOKED_PORKCHOP, 5 + rand.nextInt(6));
            case 12 -> new ItemStack(Items.SHIELD);
            case 13 -> new ItemStack(Items.GOLDEN_APPLE);
            case 14 -> {
                // 随机喷溅型药水
                @SuppressWarnings("unchecked")
                net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion>[] potions = new net.minecraft.registry.entry.RegistryEntry[]{
                    net.minecraft.potion.Potions.STRONG_STRENGTH, net.minecraft.potion.Potions.STRONG_SWIFTNESS,
                    net.minecraft.potion.Potions.STRONG_REGENERATION, net.minecraft.potion.Potions.INVISIBILITY,
                    net.minecraft.potion.Potions.STRONG_HEALING
                };
                ItemStack potion = new ItemStack(Items.SPLASH_POTION);
                potion.set(DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(potions[rand.nextInt(potions.length)]));
                yield potion;
            }
            case 15 -> new ItemStack(Items.ARROW, 5 + rand.nextInt(26));
            case 16 -> new ItemStack(Items.ANVIL);
            case 17 -> new ItemStack(Items.SMITHING_TABLE);
            case 18 -> new ItemStack(Items.NETHERITE_INGOT);
            case 19 -> new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            case 20 -> new ItemStack(Items.WATER_BUCKET);
            case 21 -> new ItemStack(Items.LAVA_BUCKET);
            case 22 -> new ItemStack(Items.COBWEB, 1 + rand.nextInt(3));
            case 23 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + rand.nextInt(10));
            case 24 -> {
                // 锋利1-4附魔书
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                var enchEntry = reg.getOptional(Enchantments.SHARPNESS).orElse(null);
                if (enchEntry != null) book.addEnchantment(enchEntry, 1 + rand.nextInt(4));
                yield book;
            }
            case 25 -> {
                // 力量1-4附魔弓书
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                var power = reg.getOptional(Enchantments.POWER).orElse(null);
                if (power != null) book.addEnchantment(power, 1 + rand.nextInt(4));
                yield book;
            }
            default -> new ItemStack(Items.COOKED_PORKCHOP, 10);
        };
    }

    // ==================== Boss系统 ====================
    private void tickBossSystem(long elapsed, long currentTick) {
        ServerWorld world = server.getOverworld();

        // 铁傀儡Boss: 7分钟预警(3分钟提前), 10/20/30分钟生成
        // 10min = 12000tick, 预警7min = 8400tick
        if (elapsed == 8400) scheduleBossMarker(world, "iron_golem", currentTick);
        if (elapsed == 12000 && !boss10Spawned) { boss10Spawned = true; spawnIronGolemBoss(world, currentTick); }
        // 20min = 24000tick, 预警17min = 20400tick
        if (elapsed == 20400) scheduleBossMarker(world, "iron_golem", currentTick);
        if (elapsed == 24000 && !boss20Spawned) { boss20Spawned = true; spawnIronGolemBoss(world, currentTick); }
        // 30min = 36000tick, 预警27min = 32400tick
        if (elapsed == 32400) scheduleBossMarker(world, "iron_golem", currentTick);
        if (elapsed == 36000 && !boss30Spawned) { boss30Spawned = true; spawnIronGolemBoss(world, currentTick); }
        // 监守者: 30min, 无预警
        if (elapsed == 36000 && !warden30Spawned) { warden30Spawned = true; spawnWardenBoss(world, currentTick); }

        // 清理预警标记(超过3分钟)
        pendingBossMarkers.removeIf(b -> currentTick - b.spawnTick > 3600);

        // 检测Boss死亡(entity==null可能是区块未加载,不算死亡)
        activeBosses.removeIf(boss -> {
            if (boss.entityUuid == null) return true;
            net.minecraft.entity.Entity e = world.getEntity(boss.entityUuid);
            if (e == null) return false; // 区块未加载,不判定死亡
            if (!e.isAlive()) {
                // Boss死亡,取消区块强制加载
                net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(boss.spawnPos);
                world.setChunkForced(cp.x, cp.z, false);
                onBossKilled(boss, world, currentTick);
                return true;
            }
            return false;
        });

        // 每10tick更新Boss名称显示实时血量
        if (currentTick % 10 == 0) {
            for (BossData boss : activeBosses) {
                if (boss.entityUuid == null) continue;
                net.minecraft.entity.Entity e = world.getEntity(boss.entityUuid);
                if (e instanceof net.minecraft.entity.LivingEntity living && e.isAlive()) {
                    int hearts = (int) Math.ceil(living.getHealth() / 2.0);
                    int maxHearts = (int) Math.ceil(living.getMaxHealth() / 2.0);
                    String prefix = boss.type.equals("iron_golem") ? "§c§l铁傀儡Boss" : "§8§l监守者Boss";
                    float pct = living.getHealth() / living.getMaxHealth();
                    String hpColor = pct > 0.5f ? "§a" : (pct > 0.25f ? "§e" : "§c");
                    living.setCustomName(Text.literal(prefix + " " + hpColor + "[" + hearts + "♥/" + maxHearts + "♥]"));
                }
            }
        }
    }

    private BlockPos findBossSpawnPos(ServerWorld world) {
        Random rand = new Random();
        // 优先在随机玩家附近生成(保证区块已加载)
        List<ServerPlayerEntity> alivePlayers = new ArrayList<>();
        for (var entry : players.entrySet()) {
            if (entry.getValue().getLives() > 0) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                if (p != null && !p.isSpectator()) alivePlayers.add(p);
            }
        }
        if (!alivePlayers.isEmpty()) {
            ServerPlayerEntity chosen = alivePlayers.get(rand.nextInt(alivePlayers.size()));
            int baseX = (int) chosen.getX() + 30 + rand.nextInt(40); // 30-70格外
            int baseZ = (int) chosen.getZ() + 30 + rand.nextInt(40);
            if (rand.nextBoolean()) baseX = -baseX + (int)(chosen.getX() * 2);
            if (rand.nextBoolean()) baseZ = -baseZ + (int)(chosen.getZ() * 2);
            int y = findSafeY(world, baseX, baseZ);
            if (y > 55) return new BlockPos(baseX, y, baseZ);
        }
        // 兜底: 世界边界内搜索
        WorldBorder border = world.getWorldBorder();
        double size = border.getSize() / 2.0 * 0.6;
        double cx = border.getCenterX();
        double cz = border.getCenterZ();
        for (int attempt = 0; attempt < 30; attempt++) {
            int x = (int)(cx + rand.nextDouble() * size * 2 - size);
            int z = (int)(cz + rand.nextDouble() * size * 2 - size);
            BlockPos surface = findSafeLandSpawn(world, x, z, rand);
            if (surface != null) return surface;
        }
        return new BlockPos((int)cx, 70, (int)cz);
    }

    private void scheduleBossMarker(ServerWorld world, String type, long currentTick) {
        BlockPos pos = findBossSpawnPos(world);
        pendingBossMarkers.add(new BossData(null, pos, type, currentTick));
        broadcastMessage("§4§l【Boss预警】§r§c 一只强大的 " + (type.equals("iron_golem") ? "§f铁傀儡" : "§8监守者") + " §c将在 §e3分钟 §c后出现在 §e(" + pos.getX() + ", " + pos.getZ() + ")§c！");
    }

    private void spawnIronGolemBoss(ServerWorld world, long currentTick) {
        // 使用最近的预警位置(如果有)
        BlockPos pos = null;
        for (int i = pendingBossMarkers.size() - 1; i >= 0; i--) {
            if (pendingBossMarkers.get(i).type.equals("iron_golem")) {
                pos = pendingBossMarkers.get(i).spawnPos;
                break;
            }
        }
        if (pos == null) pos = findBossSpawnPos(world);

        // 强制加载Boss区块
        net.minecraft.util.math.ChunkPos chunkPos = new net.minecraft.util.math.ChunkPos(pos);
        world.setChunkForced(chunkPos.x, chunkPos.z, true);

        net.minecraft.entity.passive.IronGolemEntity golem = new net.minecraft.entity.passive.IronGolemEntity(net.minecraft.entity.EntityType.IRON_GOLEM, world);
        golem.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        golem.setAiDisabled(true);
        golem.setPersistent();
        golem.setCustomName(Text.literal("§c§l铁傀儡Boss §7[500♥]"));
        golem.setCustomNameVisible(true);
        // 500心 = 1000HP
        var healthAttr = golem.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(1000.0);
        golem.setHealth(1000.0f);
        world.spawnEntity(golem);

        activeBosses.add(new BossData(golem.getUuid(), pos, "iron_golem", currentTick));
        broadcastMessage("§4§l【Boss出现】§r§c§l 铁傀儡Boss §r§c出现在 §e(" + pos.getX() + ", " + pos.getZ() + ")§c！击杀获得 §a+1命 §c+ §e20临时生命值§c！");
    }

    private void spawnWardenBoss(ServerWorld world, long currentTick) {
        BlockPos pos = findBossSpawnPos(world);
        // 强制加载Boss区块
        net.minecraft.util.math.ChunkPos chunkPos = new net.minecraft.util.math.ChunkPos(pos);
        world.setChunkForced(chunkPos.x, chunkPos.z, true);

        net.minecraft.entity.mob.WardenEntity warden = new net.minecraft.entity.mob.WardenEntity(net.minecraft.entity.EntityType.WARDEN, world);
        warden.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        warden.setAiDisabled(true);
        warden.setPersistent();
        warden.setCustomName(Text.literal("§8§l监守者Boss §7[250♥]"));
        warden.setCustomNameVisible(true);
        // 250心 = 500HP
        var healthAttr = warden.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(500.0);
        warden.setHealth(500.0f);
        world.spawnEntity(warden);

        activeBosses.add(new BossData(warden.getUuid(), pos, "warden", currentTick));
        broadcastMessage("§8§l【隐秘Boss】§r§c§l 监守者Boss §r§c在某处出现了！击杀获得 §a+2命 §c+ §e力量II 10分钟§c！");
    }

    private void onBossKilled(BossData boss, ServerWorld world, long currentTick) {
        // 找击杀者: 查最近对boss造成伤害的玩家
        ServerPlayerEntity killer = null;
        double minDist = Double.MAX_VALUE;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            double dist = p.squaredDistanceTo(boss.spawnPos.getX(), boss.spawnPos.getY(), boss.spawnPos.getZ());
            if (dist < minDist) {
                minDist = dist;
                killer = p;
            }
        }
        if (killer == null) return;
        PlayerData killerData = players.get(killer.getUuid());
        if (killerData == null) return;

        if (boss.type.equals("iron_golem")) {
            if (killerData.getLives() != Integer.MAX_VALUE) {
                killerData.setLives(killerData.getLives() + 1);
            }
            // 20颗心临时生命值(吸收效果)
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 6000, 4, false, true, true)); // level4=20hearts, 5min
            broadcastMessage("§a§l" + killer.getGameProfile().name() + " §r§a击杀了 §c§l铁傀儡Boss§r§a！获得 §e+1命 §a+ §e20临时生命值§a！");
        } else if (boss.type.equals("warden")) {
            if (killerData.getLives() != Integer.MAX_VALUE) {
                killerData.setLives(killerData.getLives() + 2);
            }
            // 力量2持续10分钟(12000tick)
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 12000, 1, false, true, true));
            broadcastMessage("§a§l" + killer.getGameProfile().name() + " §r§a击杀了 §8§l监守者Boss§r§a！获得 §e+2命 §a+ §e力量II 10分钟§a！");
        }
    }

    // ============================= LAYI(蜡翼) =============================

    private void setupLayi(ServerPlayerEntity player, PlayerData data, EntityAttributeInstance healthAttr, boolean freshStart) {
        if (healthAttr != null) healthAttr.setBaseValue(20.0); // 10心
        // 重置飞行状态(每命重置)
        data.setLayiFlying(false);
        data.setLayiFlightActivatedThisLife(false);
        data.setLayiArrowBurstRemaining(0);
        if (!freshStart) return; // 重生保留原鞘翅
        // 给无限耐久鞘翅
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        elytra.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        elytra.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l蜡翼").styled(s -> s.withItalic(false)));
        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, elytra);
    }

    // LAYI主动1: 激活飞行 + 生成3个特殊烟花箱子
    public void activateLayiFlight(ServerPlayerEntity player, PlayerData data) {
        if (data.isLayiFlightActivatedThisLife()) {
            sendTip(player, "§c本条命已激活过飞行！");
            return;
        }
        data.setLayiFlightActivatedThisLife(true);
        data.setLayiFlying(true);
        // 发射到空中+强制进入鞘翅状态(为gliding提供空中条件)
        net.minecraft.util.math.Vec3d look = player.getRotationVector();
        player.setVelocity(look.x * 1.0, 1.5, look.z * 1.0); // 强制上冲
        player.velocityDirty = true;
        player.fallDistance = 0;
        ((baby.sv.yepvpfabirc.mixin.EntityAccessor) player).invokeSetFlag(7, true); // 7 = GLIDING_FLAG_INDEX
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        sendTip(player, "§6§l[蜡翼] §r§e飞行激活！你的位置将持续暴露！");
        broadcastMessage("§c§l[蜡翼] §r§c" + player.getGameProfile().name() + " §e启动了飞行！位置已暴露！");

        // 生成2个特殊烟花箱子
        data.getLayiSpecialChestPositions().clear();
        ServerWorld world = server.getOverworld();
        net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
        int centerX = (int) border.getCenterX();
        int centerZ = (int) border.getCenterZ();
        int radius = Math.max(50, (int) (border.getSize() / 2) - 30);
        Random rand = new Random();

        for (int i = 0; i < 2; i++) {
            BlockPos chestPos = findSafeLandSpawn(world,
                    centerX + rand.nextInt(radius) - radius / 2,
                    centerZ + rand.nextInt(radius) - radius / 2,
                    rand);
            if (chestPos == null) continue;
            // 放置箱子
            world.setBlockState(chestPos, net.minecraft.block.Blocks.CHEST.getDefaultState());
            // 填充特殊烟花
            var be = world.getBlockEntity(chestPos);
            if (be instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET, 1);
                firework.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§l追踪导弹烟花").styled(s -> s.withItalic(false)));
                // 添加自定义标记: lore标记为蜡翼导弹
                java.util.List<Text> lore = java.util.List.of(
                        Text.literal("§7使用后将追踪蜡翼并在10秒后爆炸"),
                        Text.literal("§4§lLAYI_MISSILE:" + player.getUuid())
                );
                firework.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
                chest.setStack(13, firework); // 中间格
            }
            data.getLayiSpecialChestPositions().add(chestPos);
        }
        if (!data.getLayiSpecialChestPositions().isEmpty()) {
            broadcastMessage("§e§l[蜡翼] §r§e2个追踪导弹烟花箱已刷新！地图可见！");
        }
    }

    // LAYI主动2: 连射20箭
    public void activateLayiArrowBarrage(ServerPlayerEntity player, PlayerData data) {
        long currentTick = server.getOverworld().getTime();
        if (!data.isLayiFlying()) {
            sendTip(player, "§c必须在飞行状态下才能使用！");
            return;
        }
        if (currentTick - data.getLastLayiArrowBarrageTick() < 200) { // 10s CD
            long remaining = (200 - (currentTick - data.getLastLayiArrowBarrageTick())) / 20;
            sendTip(player, "§c箭雨冷却中: §e" + remaining + "s");
            return;
        }
        data.setLastLayiArrowBarrageTick(currentTick);
        data.setLayiArrowBurstRemaining(20);
        data.setLayiArrowBurstNextTick(currentTick);
        sendTip(player, "§6§l[蜡翼] §r§e箭雨发射！");
    }

    // LAYI tick: 持续飞行 + 箭雨连射 + 离开飞行秒杀 + 位置暴露 + 导弹tick + 50格警报
    private void tickLayi(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 50格内有玩家靠近LAYI时, 给靠近的玩家发警报(蜡翼自身不感知)
        if (currentTick % 40 == 0) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (other == player || other.isSpectator()) continue;
                PlayerData od = players.get(other.getUuid());
                if (od == null || !od.isAlive()) continue;
                double dist = player.squaredDistanceTo(other);
                if (dist <= 2500) { // 50格
                    ServerWorld ow = (ServerWorld) other.getEntityWorld();
                    ow.playSound(null, other.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.5f);
                    sendTip(other, "§6§l[蜡翼警报] §r§e蜡翼在50格内！距离: §c" + (int)Math.sqrt(dist) + "格");
                }
            }
        }

        if (!data.isLayiFlying()) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // 位置暴露
        data.setRevealUntilTick(currentTick + 40);

        // 着陆检测: 真正落地(非水非滑翔) → 秒杀
        // 注意: 不能用 setHealth(0) 再 damage(), 因为 damage 检查 health>0 会跳过 onDeath, 导致命数不扣
        if (player.isOnGround() && !player.isGliding()) {
            data.setLayiFlying(false);
            broadcastMessage("§c§l[蜡翼] §r§c" + player.getGameProfile().name() + " §e着陆，蜡翼融化被摧毁！");
            player.damage(world, world.getDamageSources().outOfWorld(), 9999f);
            if (player.isAlive()) player.kill(world);
            return;
        }

        // 强制保持滑翔状态(受伤/碰撞被打断也立刻恢复)
        if (!player.isGliding()) {
            ((baby.sv.yepvpfabirc.mixin.EntityAccessor) player).invokeSetFlag(7, true); // GLIDING_FLAG
        }

        // 持续firework boost — 模拟vanilla boost: motion += look*0.1 + (look*1.5 - motion)*0.5
        net.minecraft.util.math.Vec3d look = player.getRotationVector();
        net.minecraft.util.math.Vec3d vel = player.getVelocity();
        double boost = 1.5;
        net.minecraft.util.math.Vec3d newVel = vel.add(
                look.x * 0.1 + (look.x * boost - vel.x) * 0.5,
                look.y * 0.1 + (look.y * boost - vel.y) * 0.5,
                look.z * 0.1 + (look.z * boost - vel.z) * 0.5);
        player.setVelocity(newVel);
        player.velocityDirty = true;
        player.fallDistance = 0;
        // 每5tick同步一次velocity给客户端(减少带宽但保证及时)
        if (currentTick % 5 == 0) {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
        }

        // 箭雨连射(每tick发1箭, 共20箭)
        if (data.getLayiArrowBurstRemaining() > 0 && currentTick >= data.getLayiArrowBurstNextTick()) {
            net.minecraft.util.math.Vec3d lookVec = player.getRotationVector();
            Random rand = new Random();
            double spread = 0.08;
            net.minecraft.entity.projectile.ArrowEntity arrow = new net.minecraft.entity.projectile.ArrowEntity(world, player, new ItemStack(Items.ARROW), null);
            // 箭矢从玩家眼部发射, 防止命中自己
            arrow.setPosition(player.getX() + lookVec.x * 1.5,
                    player.getEyeY() + lookVec.y * 1.5,
                    player.getZ() + lookVec.z * 1.5);
            arrow.setVelocity(lookVec.x + rand.nextGaussian() * spread,
                    lookVec.y + rand.nextGaussian() * spread,
                    lookVec.z + rand.nextGaussian() * spread,
                    3.0f, 0);
            arrow.addCommandTag("layi_arrow_" + player.getUuid());
            world.spawnEntity(arrow);
            data.setLayiArrowBurstRemaining(data.getLayiArrowBurstRemaining() - 1);
            data.setLayiArrowBurstNextTick(currentTick + 1); // 每tick1箭
        }
    }

    // 蜡翼导弹tick: 追踪蜡翼(若存活), 10秒后定时爆炸(不依赖蜡翼是否存活/飞行)
    // layiPlayer 可为 null(蜡翼死亡/下线时导弹仍继续倒计时至爆炸)
    private void tickLayiMissiles(ServerPlayerEntity layiPlayer, PlayerData data, long currentTick) {
        ServerWorld world = server.getOverworld();
        Iterator<UUID> it = data.getLayiMissileEntities().iterator();
        while (it.hasNext()) {
            UUID missileUuid = it.next();
            net.minecraft.entity.Entity entity = world.getEntity(missileUuid);
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            long spawnTick = 0;
            for (String tag : entity.getCommandTags()) {
                if (tag.startsWith("layi_missile_tick_")) {
                    spawnTick = Long.parseLong(tag.substring("layi_missile_tick_".length()));
                    break;
                }
            }
            // 严格定时爆炸: 10秒到期才爆, 不因接触/碰撞提前触发
            boolean timeout = spawnTick > 0 && currentTick - spawnTick >= 200;
            if (timeout) {
                // 爆炸半径8格内所有存活玩家必死
                final double EXPLOSION_RADIUS = 8.0;
                double er2 = EXPLOSION_RADIUS * EXPLOSION_RADIUS;
                for (ServerPlayerEntity victim : world.getPlayers()) {
                    if (victim.isSpectator()) continue;
                    PlayerData vd = players.get(victim.getUuid());
                    if (vd == null || !vd.isAlive()) continue;
                    if (victim.squaredDistanceTo(entity) <= er2) {
                        if (layiPlayer != null && victim.getUuid().equals(layiPlayer.getUuid())) {
                            data.setLayiFlying(false);
                        }
                        // 临时清除无敌, 确保命数正常扣除
                        long savedInv = vd.getRespawnInvincibleUntil();
                        vd.setRespawnInvincibleUntil(0);
                        // 不要 setHealth(0), 否则 damage 会跳过 onDeath 导致命数不扣
                        victim.damage(world, world.getDamageSources().genericKill(), 99999f);
                        if (victim.isAlive()) victim.damage(world, world.getDamageSources().outOfWorld(), 99999f);
                        if (victim.isAlive()) victim.kill(world);
                        if (victim.isAlive()) vd.setRespawnInvincibleUntil(savedInv);
                        broadcastMessage("§c§l[追踪导弹] §r§c" + victim.getGameProfile().name() + " §c被导弹炸死！");
                    }
                }
                // 触发烟花原生彩色爆炸 + 自动discard
                if (entity instanceof net.minecraft.entity.projectile.FireworkRocketEntity) {
                    ((baby.sv.yepvpfabirc.mixin.FireworkRocketEntityAccessor) entity).setLife(99999);
                } else {
                    world.createExplosion(null, entity.getX(), entity.getY(), entity.getZ(), 2.0f,
                            net.minecraft.world.World.ExplosionSourceType.NONE);
                    entity.discard();
                }
                it.remove();
                continue;
            }
            // 追踪蜡翼(若存活在线): 直接覆盖烟花的自加速逻辑
            if (layiPlayer != null && layiPlayer.isAlive() && !layiPlayer.isSpectator()) {
                double dx = layiPlayer.getX() - entity.getX();
                double dy = layiPlayer.getY() + 0.5 - entity.getY();
                double dz = layiPlayer.getZ() - entity.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 0.5) {
                    // 实时检测蜡翼速度, 导弹速度 = 蜡翼速度 + 小量(0.3), 最小0.8
                    net.minecraft.util.math.Vec3d layiVel = layiPlayer.getVelocity();
                    double layiSpeed = layiVel.length();
                    double speed = Math.max(0.8, layiSpeed + 0.3);
                    double vx = dx / dist * speed;
                    double vy = dy / dist * speed;
                    double vz = dz / dist * speed;
                    entity.setVelocity(vx, vy, vz);
                    entity.velocityDirty = true;
                    float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
                    float pitch = (float) Math.toDegrees(-Math.atan2(vy, Math.sqrt(vx * vx + vz * vz)));
                    entity.setYaw(yaw);
                    entity.setPitch(pitch);
                }
            } else {
                // 蜡翼已死/下线: 导弹悬停原地等待倒计时爆炸
                entity.setVelocity(0, 0, 0);
                entity.velocityDirty = true;
            }
        }
    }

    // 玩家使用蜡翼导弹烟花时调用(由外部事件触发)
    public void onLayiMissileFireworkUsed(ServerPlayerEntity user, UUID layiPlayerUuid) {
        ServerWorld world = server.getOverworld();
        ServerPlayerEntity layiPlayer = server.getPlayerManager().getPlayer(layiPlayerUuid);
        if (layiPlayer == null) return;
        PlayerData layiData = players.get(layiPlayerUuid);
        if (layiData == null) return;

        long currentTick = world.getTime();

        // 构造带烟花爆炸效果的物品(用于视觉拖尾+爆炸)
        ItemStack fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
        java.util.List<net.minecraft.component.type.FireworkExplosionComponent> explosions = java.util.List.of(
                new net.minecraft.component.type.FireworkExplosionComponent(
                        net.minecraft.component.type.FireworkExplosionComponent.Type.LARGE_BALL,
                        it.unimi.dsi.fastutil.ints.IntList.of(0xFF2222, 0xFF8800),
                        it.unimi.dsi.fastutil.ints.IntList.of(0xFFFFAA),
                        true, true)
        );
        fireworkStack.set(net.minecraft.component.DataComponentTypes.FIREWORKS,
                new net.minecraft.component.type.FireworksComponent((byte) 3, explosions));

        // 真正的烟花火箭实体: 自带粒子拖尾, 有可见模型
        net.minecraft.entity.projectile.FireworkRocketEntity missile =
                new net.minecraft.entity.projectile.FireworkRocketEntity(
                        world, user.getX(), user.getY() + 1.5, user.getZ(), fireworkStack);
        // 拉长寿命防止自动爆炸(由我们手动控制结束)
        ((baby.sv.yepvpfabirc.mixin.FireworkRocketEntityAccessor) missile).setLifeTime(99999);
        // 穿墙飞行: 防止碰撞触发 vanilla explodeAndRemove 导致提前爆炸
        missile.noClip = true;
        // 给一个初始向上速度便于起步可见
        missile.setVelocity(0, 0.5, 0);
        missile.velocityDirty = true;
        missile.addCommandTag("layi_missile_" + layiPlayerUuid);
        missile.addCommandTag("layi_missile_tick_" + currentTick);
        world.spawnEntity(missile);

        layiData.getLayiMissileEntities().add(missile.getUuid());
        broadcastMessage("§c§l[追踪导弹] §r§e" + user.getGameProfile().name() + " §c发射了追踪导弹！§e正在追踪蜡翼！");
        sendTip(layiPlayer, "§c§l警告！追踪导弹已发射！§e10秒后爆炸！");
    }

    // 蜡翼箭矢命中处理(由外部伤害事件调用): 5% maxHP真实伤害
    public void onLayiArrowHit(ServerPlayerEntity victim, ServerPlayerEntity layiPlayer) {
        float maxHp = victim.getMaxHealth();
        float trueDmg = maxHp * 0.05f;
        victim.damage(server.getOverworld(), victim.getDamageSources().magic(), trueDmg);
        sendTip(victim, "§c被蜡翼箭矢命中！-" + String.format("%.1f", trueDmg) + "HP");
    }

    // ============================= MAYPOOR(天锤) =============================

    private void setupMaypoor(ServerPlayerEntity player, PlayerData data, EntityAttributeInstance healthAttr) {
        if (healthAttr != null) healthAttr.setBaseValue(20.0); // 10心
        // 重置技能状态
        data.setMaypoorFlightEndTick(0);
        data.setMaypoorSlamArmed(false);
        data.setMaypoorSlamStartY(0);
        // 取消可能残留的飞行权限
        if (player.getAbilities().allowFlying && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
    }

    // MAYPOOR tick: 飞行计时 + 重锤落地结算
    private void tickMaypoor(ServerPlayerEntity player, PlayerData data, long currentTick) {
        // 飞行计时结束: 收回飞行权限
        if (data.getMaypoorFlightEndTick() > 0 && currentTick >= data.getMaypoorFlightEndTick()) {
            data.setMaypoorFlightEndTick(0);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
            }
            sendTip(player, "§e§l[天锤] §r§7飞行结束");
        }

        // 飞行中: 防止摔落距离累积
        if (data.isMaypoorFlying(currentTick)) {
            player.fallDistance = 0;
        }

        // 重锤激活: 跟踪最高Y(玩家可能跳起后再下落, 取最高位置)
        if (data.isMaypoorSlamArmed()) {
            if (player.getY() > data.getMaypoorSlamStartY()) {
                data.setMaypoorSlamStartY(player.getY());
            }
            // 落地结算
            if (player.isOnGround()) {
                baby.sv.yepvpfabirc.skill.SkillHandler.executeMaypoorSlam(player, data);
            }
        }
    }
}
