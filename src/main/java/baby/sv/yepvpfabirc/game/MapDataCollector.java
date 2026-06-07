package baby.sv.yepvpfabirc.game;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapDataCollector {

    // 缓存: chunkKey(long) → 4x4子区块RGB颜色(int[16])
    private static final ConcurrentHashMap<Long, int[]> chunkColorCache = new ConcurrentHashMap<>();

    // 渐进扫描状态: 遍历整个世界边界区域
    private static int scanCursorIndex = 0;
    private static List<long[]> scanQueue = null; // [chunkX, chunkZ] pairs stored as long[]{cx, cz}
    private static boolean fullScanComplete = false;
    private static final int CHUNKS_PER_TICK = 50; // 每次调用扫描50个区块(避免服务器过载)

    // 每次tick: 渐进扫描未缓存的区块(只采样已加载的, 不强制加载避免磁盘IO和区块生成开销)
    // 动态变化(ALLAND涂色/XLL玻璃/SHUBING画等)通过 invalidateChunk() 精准失效后下轮扫到时重新采样
    public static void tickCollect(ServerWorld world) {
        if (scanQueue == null) {
            buildScanQueue(world);
        }
        if (scanQueue != null && !scanQueue.isEmpty()) {
            int scanned = 0;
            int attempted = 0;
            int maxAttempts = CHUNKS_PER_TICK * 4; // 遇到大量已缓存chunk时最多多扫几次
            while (scanned < CHUNKS_PER_TICK && attempted < maxAttempts && scanCursorIndex < scanQueue.size()) {
                long[] coords = scanQueue.get(scanCursorIndex);
                int cx = (int) coords[0];
                int cz = (int) coords[1];
                long key = ChunkPos.toLong(cx, cz);
                scanCursorIndex++;
                attempted++;
                if (chunkColorCache.containsKey(key)) continue;
                // 只采样已加载的区块, 未加载的下一轮扫描循环再试
                WorldChunk wc = world.getChunkManager().getWorldChunk(cx, cz);
                if (wc == null) continue;
                try {
                    int[] colors = sampleChunkSubColors(world, wc);
                    chunkColorCache.put(key, colors);
                } catch (Exception ignored) {}
                scanned++;
            }
            if (scanCursorIndex >= scanQueue.size()) {
                fullScanComplete = true;
                scanCursorIndex = 0; // 循环重新扫描(处理新加载区块/边界变化)
            }
        }
    }

    // 构建扫描队列: 世界边界内所有区块坐标
    private static void buildScanQueue(ServerWorld world) {
        net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
        int centerX = (int) border.getCenterX();
        int centerZ = (int) border.getCenterZ();
        int radius = (int) (border.getSize() / 2) + 16;
        int minCX = (centerX - radius) >> 4;
        int maxCX = (centerX + radius) >> 4;
        int minCZ = (centerZ - radius) >> 4;
        int maxCZ = (centerZ + radius) >> 4;
        scanQueue = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                scanQueue.add(new long[]{cx, cz});
            }
        }
        scanCursorIndex = 0;
        fullScanComplete = false;
    }

    // 强制刷新指定区块颜色(用于涂色等动态变化)
    public static void invalidateChunk(int chunkX, int chunkZ) {
        chunkColorCache.remove(ChunkPos.toLong(chunkX, chunkZ));
    }

    // 清空缓存(游戏重置时)
    public static void reset() {
        chunkColorCache.clear();
        scanQueue = null;
        scanCursorIndex = 0;
        fullScanComplete = false;
    }

    // 构建地图数据 byte[]
    // 格式: [chunkCount(int)] + 每个chunk: [chunkX(short), chunkZ(short), 16*rgb(48 bytes)]
    //       + [playerCount(int)] + 每个player: [x(int), z(int), nameLen(short), name(bytes), markerColor(int)]
    //       + [paintedChunkCount(int)] + 每个: [chunkX(short), chunkZ(short), paintColor(byte)]
    public static byte[] buildMapData(ServerWorld world, UUID requestingPlayer) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // 世界边界半径 + 中心坐标
            net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
            int borderRadius = (int) (border.getSize() / 2);
            dos.writeInt(borderRadius);
            dos.writeInt((int) border.getCenterX());
            dos.writeInt((int) border.getCenterZ());

            // 区块颜色数据(4x4子区块, 每区块16个RGB)
            dos.writeInt(chunkColorCache.size());
            for (Map.Entry<Long, int[]> entry : chunkColorCache.entrySet()) {
                int cx = ChunkPos.getPackedX(entry.getKey());
                int cz = ChunkPos.getPackedZ(entry.getKey());
                dos.writeShort(cx);
                dos.writeShort(cz);
                int[] subColors = entry.getValue();
                for (int i = 0; i < 16; i++) {
                    int rgb = subColors[i];
                    dos.writeByte((rgb >> 16) & 0xFF);
                    dos.writeByte((rgb >> 8) & 0xFF);
                    dos.writeByte(rgb & 0xFF);
                }
            }

            // 玩家位置标记(根据请求者职业决定是否显示)
            GameManager gm = GameManager.getInstance();
            PlayerData reqData = gm.getPlayerData(requestingPlayer);

            // ST Ciallo期间能看到所有人
            long currentTick = world.getTime();
            boolean stCialloActive = reqData != null && reqData.getRole() == Role.ST
                    && reqData.getCialloSeeAllUntilTick() > currentTick;

            boolean canSeeAllPlayers = reqData != null && (
                    reqData.getRole() == Role.POPCORN ||
                    reqData.getRole() == Role.HELI ||
                    stCialloActive
            );

            // JIEZU夜晚完全隐身(位置无法被探测): 判断当前是否夜晚
            long dayTime = world.getTimeOfDay() % 24000;
            boolean isNight = dayTime >= 13000 && dayTime <= 23000;

            List<PlayerMarker> markers = new ArrayList<>();
            Set<UUID> addedPlayers = new HashSet<>();
            if (canSeeAllPlayers) {
                for (Map.Entry<UUID, PlayerData> e : gm.getAllPlayerData().entrySet()) {
                    PlayerData pd = e.getValue();
                    if (!pd.isAlive()) continue;
                    // JIEZU夜晚不可被探测
                    if (pd.getRole() == Role.JIEZU && isNight) continue;
                    // DASHA水中/雨中不可被探测(且不在显形期内)
                    if (pd.getRole() == Role.DASHA && pd.isInWater()) {
                        long ct = world.getTime();
                        if (ct >= pd.getDashaVisibleUntilTick()) continue;
                    }
                    var player = world.getServer().getPlayerManager().getPlayer(e.getKey());
                    if (player == null) continue;
                    int color = 0xFFFFFF; // 白色默认
                    markers.add(new PlayerMarker((int) player.getX(), (int) player.getZ(), pd.getPlayerName(), color, player.getYaw()));
                    addedPlayers.add(e.getKey());
                }
            }
            // DASHA: 地图持续显示离自己最近的玩家位置
            if (reqData != null && reqData.getRole() == Role.DASHA) {
                var dashaPlayer = world.getServer().getPlayerManager().getPlayer(requestingPlayer);
                if (dashaPlayer != null) {
                    double nearestDist = Double.MAX_VALUE;
                    ServerPlayerEntity nearestPlayer = null;
                    PlayerData nearestPd = null;
                    for (Map.Entry<UUID, PlayerData> e : gm.getAllPlayerData().entrySet()) {
                        if (e.getKey().equals(requestingPlayer)) continue;
                        PlayerData pd = e.getValue();
                        if (!pd.isAlive()) continue;
                        // 不能探测到隐身的JIEZU/DASHA
                        if (pd.getRole() == Role.JIEZU && isNight) continue;
                        if (pd.getRole() == Role.DASHA && pd.isInWater()) continue;
                        var other = world.getServer().getPlayerManager().getPlayer(e.getKey());
                        if (other == null) continue;
                        double dx = dashaPlayer.getX() - other.getX();
                        double dz = dashaPlayer.getZ() - other.getZ();
                        double dist = dx * dx + dz * dz;
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearestPlayer = other;
                            nearestPd = pd;
                        }
                    }
                    if (nearestPlayer != null && !addedPlayers.contains(nearestPlayer.getUuid())) {
                        markers.add(new PlayerMarker((int) nearestPlayer.getX(), (int) nearestPlayer.getZ(), nearestPd.getPlayerName(), 0x00AAFF, nearestPlayer.getYaw())); // 蓝色
                        addedPlayers.add(nearestPlayer.getUuid());
                    }
                }
            }
            // BOBBY和RETOUR地图位置互相共享
            if (reqData != null && (reqData.getRole() == Role.BOBBY || reqData.getRole() == Role.RETOUR)) {
                Role partnerRole = reqData.getRole() == Role.BOBBY ? Role.RETOUR : Role.BOBBY;
                for (Map.Entry<UUID, PlayerData> e : gm.getAllPlayerData().entrySet()) {
                    PlayerData pd = e.getValue();
                    if (pd.getRole() != partnerRole || !pd.isAlive() || addedPlayers.contains(e.getKey())) continue;
                    // 等待复活的罪人也显示(灵体状态)
                    if (pd.getRole() == Role.BOBBY && pd.isWaitingForRevive() && pd.getDeathPos() != null) {
                        markers.add(new PlayerMarker(pd.getDeathPos().getX(), pd.getDeathPos().getZ(), pd.getPlayerName() + "§c(灵体)", 0xAA00AA, 0f));
                        addedPlayers.add(e.getKey());
                        continue;
                    }
                    var player = world.getServer().getPlayerManager().getPlayer(e.getKey());
                    if (player == null) continue;
                    markers.add(new PlayerMarker((int) player.getX(), (int) player.getZ(), pd.getPlayerName(), 0xAA00AA, player.getYaw())); // 紫色
                    addedPlayers.add(e.getKey());
                }
            }
            // RETOUR复活罪人后位置暴露30秒(所有人可见)
            for (Map.Entry<UUID, PlayerData> e : gm.getAllPlayerData().entrySet()) {
                PlayerData pd = e.getValue();
                if (pd.getRole() != Role.RETOUR || !pd.isAlive() || addedPlayers.contains(e.getKey())) continue;
                if (pd.getRetourRevealedUntilTick() > currentTick) {
                    var player = world.getServer().getPlayerManager().getPlayer(e.getKey());
                    if (player == null) continue;
                    markers.add(new PlayerMarker((int) player.getX(), (int) player.getZ(), pd.getPlayerName(), 0xFF8800, player.getYaw())); // 橙色
                    addedPlayers.add(e.getKey());
                }
            }
            // 位置暴露的玩家(所有人可见): revealed=true(明牌阶段) 或 revealUntilTick(HELI揭露/NIHAO等) 或 LAYI飞行中
            for (Map.Entry<UUID, PlayerData> e : gm.getAllPlayerData().entrySet()) {
                PlayerData pd = e.getValue();
                if (!pd.isAlive() || addedPlayers.contains(e.getKey())) continue;
                if (pd.isRevealed() || pd.getRevealUntilTick() > currentTick
                        || (pd.getRole() == Role.LAYI && pd.isLayiFlying())) {
                    var player = world.getServer().getPlayerManager().getPlayer(e.getKey());
                    if (player == null) continue;
                    int color = pd.isRevealed() ? 0xFF6600 : 0xFFFF00; // 橙=永久暴露, 黄=临时暴露
                    markers.add(new PlayerMarker((int) player.getX(), (int) player.getZ(), pd.getPlayerName(), color, player.getYaw()));
                    addedPlayers.add(e.getKey());
                }
            }
            // 自己的位置始终显示
            {
                var selfPlayer = world.getServer().getPlayerManager().getPlayer(requestingPlayer);
                if (selfPlayer != null && !addedPlayers.contains(requestingPlayer)) {
                    markers.add(new PlayerMarker((int) selfPlayer.getX(), (int) selfPlayer.getZ(), selfPlayer.getGameProfile().name(), 0x00FF00, selfPlayer.getYaw()));
                    addedPlayers.add(requestingPlayer);
                }
            }

            dos.writeInt(markers.size());
            for (PlayerMarker m : markers) {
                dos.writeInt(m.x);
                dos.writeInt(m.z);
                byte[] nameBytes = m.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);
                dos.writeInt(m.color);
                dos.writeFloat(m.yaw);
            }

            // ALLAND颜料区域数据(从PaintZone系统读取)
            Map<Long, Byte> paintedChunkColors = new HashMap<>();
            for (GameManager.PaintZone zone : gm.getPaintZones()) {
                for (BlockPos bp : zone.originalBlocks.keySet()) {
                    long ck = ChunkPos.toLong(bp.getX() >> 4, bp.getZ() >> 4);
                    byte paintByte = (byte)(zone.color + 1); // 0=红→1, 1=蓝→2, 2=绿→3, 3=黄→4
                    paintedChunkColors.putIfAbsent(ck, paintByte);
                }
            }
            dos.writeInt(paintedChunkColors.size());
            for (Map.Entry<Long, Byte> e : paintedChunkColors.entrySet()) {
                dos.writeShort(ChunkPos.getPackedX(e.getKey()));
                dos.writeShort(ChunkPos.getPackedZ(e.getKey()));
                dos.writeByte(e.getValue());
            }

            // XLL玻璃迪克位置(已移除,写0保持二进制兼容)
            dos.writeInt(0);

            // JIEZU蛛网位置(所有玩家地图可见)
            List<int[]> webPositions = new ArrayList<>();
            for (PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != Role.JIEZU) continue;
                for (BlockPos wp : pd.getWebPositions()) {
                    webPositions.add(new int[]{wp.getX(), wp.getZ()});
                }
            }
            dos.writeInt(webPositions.size());
            for (int[] wp : webPositions) {
                dos.writeInt(wp[0]);
                dos.writeInt(wp[1]);
            }

            // POPCORN备用躯体位置(所有玩家地图可见)
            List<int[]> backupBodyPositions = new ArrayList<>();
            for (PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != Role.POPCORN) continue;
                for (BlockPos bp : pd.getBackupBodies()) {
                    backupBodyPositions.add(new int[]{bp.getX(), bp.getZ()});
                }
            }
            dos.writeInt(backupBodyPositions.size());
            for (int[] bp : backupBodyPositions) {
                dos.writeInt(bp[0]);
                dos.writeInt(bp[1]);
            }

            // DAMAI墓碑位置(所有玩家地图可见)
            List<int[]> tombstonePositions = new ArrayList<>();
            for (PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != Role.DAMAI) continue;
                BlockPos tp = pd.getTombstonePos();
                if (tp != null && (pd.isDamaiWaitingForSkeleton() || pd.isSkeletonForm())) {
                    tombstonePositions.add(new int[]{tp.getX(), tp.getZ()});
                }
            }
            dos.writeInt(tombstonePositions.size());
            for (int[] tp : tombstonePositions) {
                dos.writeInt(tp[0]);
                dos.writeInt(tp[1]);
            }

            // SHUBING画位置(所有玩家地图可见)
            List<int[]> paintingPositions = new ArrayList<>();
            for (PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != Role.SHUBING) continue;
                for (PlayerData.ShubingPainting sp : pd.getPlacedPaintings()) {
                    if (sp.pos != null && sp.corridorIndex != -2) {
                        paintingPositions.add(new int[]{sp.pos.getX(), sp.pos.getZ()});
                    }
                }
            }
            dos.writeInt(paintingPositions.size());
            for (int[] pp : paintingPositions) {
                dos.writeInt(pp[0]);
                dos.writeInt(pp[1]);
            }

            // Boss位置(预警+存活的Boss)
            List<int[]> bossPositions = new ArrayList<>(); // x, z, type(0=铁傀儡预警,1=铁傀儡存活,2=监守者存活)
            for (GameManager.BossData bd : gm.getPendingBossMarkers()) {
                if (bd.type.equals("iron_golem")) {
                    bossPositions.add(new int[]{bd.spawnPos.getX(), bd.spawnPos.getZ(), 0});
                }
            }
            for (GameManager.BossData bd : gm.getActiveBosses()) {
                net.minecraft.entity.Entity e = world.getEntity(bd.entityUuid);
                if (e != null && e.isAlive()) {
                    int bossType = bd.type.equals("iron_golem") ? 1 : 2;
                    bossPositions.add(new int[]{(int) e.getX(), (int) e.getZ(), bossType});
                }
            }
            dos.writeInt(bossPositions.size());
            for (int[] bp : bossPositions) {
                dos.writeInt(bp[0]);
                dos.writeInt(bp[1]);
                dos.writeByte(bp[2]);
            }

            // LAYI导弹箱位置(所有玩家地图可见)
            List<int[]> layiChestPositions = new ArrayList<>();
            for (PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != Role.LAYI) continue;
                for (BlockPos bp : pd.getLayiSpecialChestPositions()) {
                    layiChestPositions.add(new int[]{bp.getX(), bp.getZ()});
                }
            }
            dos.writeInt(layiChestPositions.size());
            for (int[] lp : layiChestPositions) {
                dos.writeInt(lp[0]);
                dos.writeInt(lp[1]);
            }

            dos.flush();
            // gzip压缩减少传输量
            byte[] raw = baos.toByteArray();
            java.io.ByteArrayOutputStream gzBaos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(gzBaos)) {
                gz.write(raw);
            }
            return gzBaos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    // 采样4x4子区块颜色: 每4x4子区取中心点采样
    // 优先使用 heightmap 快速定位地表Y, 失败时才fallback到线性扫描
    private static int[] sampleChunkSubColors(ServerWorld world, WorldChunk chunk) {
        int[] result = new int[16]; // 4x4 grid
        ChunkPos cp = chunk.getPos();
        int bottomY = world.getBottomY();
        BlockPos.Mutable mp = new BlockPos.Mutable();
        for (int sz = 0; sz < 4; sz++) {
            for (int sx = 0; sx < 4; sx++) {
                int wx = cp.getStartX() + sx * 4 + 2;
                int wz = cp.getStartZ() + sz * 4 + 2;
                int localX = wx & 15;
                int localZ = wz & 15;
                // 优先使用 MOTION_BLOCKING_NO_LEAVES(忽略树叶) 获取地表Y
                int startY;
                try {
                    startY = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, localX, localZ);
                    if (startY < bottomY || startY > 319) startY = 319;
                } catch (Exception e) {
                    startY = 319;
                }
                BlockState foundState = Blocks.STONE.getDefaultState();
                for (int y = startY; y >= bottomY; y--) {
                    BlockState state = chunk.getBlockState(mp.set(localX, y, localZ));
                    if (!state.isAir()) {
                        foundState = state;
                        break;
                    }
                }
                result[sz * 4 + sx] = getBlockColor(foundState.getBlock());
            }
        }
        return result;
    }

    // 方块 → RGB颜色映射
    private static int getBlockColor(Block block) {
        if (block == Blocks.GRASS_BLOCK) return 0x5D9B47;
        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT) return 0x8B6B47;
        if (block == Blocks.STONE || block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE) return 0x7F7F7F;
        if (block == Blocks.DEEPSLATE) return 0x4A4A4A;
        if (block == Blocks.WATER) return 0x3B6BB5;
        if (block == Blocks.SAND || block == Blocks.SANDSTONE) return 0xDBC67B;
        if (block == Blocks.GRAVEL) return 0x8B8680;
        if (block == Blocks.SNOW_BLOCK || block == Blocks.SNOW) return 0xF0F0F0;
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) return 0xA0C8FF;
        if (block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.BIRCH_LOG || block == Blocks.DARK_OAK_LOG) return 0x6B5030;
        if (block == Blocks.OAK_LEAVES || block == Blocks.SPRUCE_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.DARK_OAK_LEAVES) return 0x3A7A24;
        if (block == Blocks.OAK_PLANKS || block == Blocks.SPRUCE_PLANKS || block == Blocks.BIRCH_PLANKS) return 0xA08050;
        if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) return 0x6B6B6B;
        if (block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS) return 0x737373;
        if (block == Blocks.DEEPSLATE_BRICKS) return 0x3D3D3D;
        if (block == Blocks.RED_CONCRETE) return 0xA42C2C;
        if (block == Blocks.BLUE_CONCRETE) return 0x2C2CA4;
        if (block == Blocks.GREEN_CONCRETE) return 0x49A42C;
        if (block == Blocks.IRON_BLOCK) return 0xD8D8D8;
        if (block == Blocks.GOLD_BLOCK) return 0xF0D020;
        if (block == Blocks.DIAMOND_BLOCK) return 0x40E8D0;
        if (block == Blocks.LAVA) return 0xD0500A;
        if (block == Blocks.NETHERRACK) return 0x6B2020;
        if (block == Blocks.CLAY) return 0x9EA4B0;
        if (block == Blocks.PODZOL) return 0x6B5030;
        if (block == Blocks.MYCELIUM) return 0x8B7D8B;
        if (block == Blocks.TERRACOTTA) return 0x9E6246;
        if (block == Blocks.BEDROCK) return 0x353535;
        if (block == Blocks.AMETHYST_BLOCK) return 0x9B59B6; // 紫水晶(SHUBING画)
        // 木质建筑方块(村庄等)
        if (block == Blocks.STRIPPED_OAK_LOG || block == Blocks.STRIPPED_SPRUCE_LOG || block == Blocks.STRIPPED_BIRCH_LOG) return 0xB89058;
        if (block == Blocks.OAK_FENCE || block == Blocks.SPRUCE_FENCE || block == Blocks.BIRCH_FENCE) return 0x9A7040;
        if (block == Blocks.OAK_STAIRS || block == Blocks.SPRUCE_STAIRS || block == Blocks.BIRCH_STAIRS) return 0xA08050;
        if (block == Blocks.OAK_SLAB || block == Blocks.SPRUCE_SLAB || block == Blocks.BIRCH_SLAB) return 0xA08050;
        if (block == Blocks.OAK_DOOR || block == Blocks.SPRUCE_DOOR || block == Blocks.BIRCH_DOOR) return 0x8B6535;
        if (block == Blocks.COBBLESTONE_STAIRS || block == Blocks.STONE_BRICK_STAIRS) return 0x6B6B6B;
        if (block == Blocks.COBBLESTONE_SLAB || block == Blocks.STONE_BRICK_SLAB) return 0x737373;
        if (block == Blocks.COBBLESTONE_WALL || block == Blocks.STONE_BRICK_WALL) return 0x6B6B6B;
        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) return 0xC8E8F0;
        if (block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN) return 0xFFD040;
        if (block == Blocks.CRAFTING_TABLE) return 0x8B6535;
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER) return 0x707070;
        if (block == Blocks.CHEST || block == Blocks.BARREL) return 0x8B6535;
        if (block == Blocks.FARMLAND) return 0x6B4226;
        if (block == Blocks.WHEAT) return 0xD4AA00;
        if (block == Blocks.CARROTS || block == Blocks.POTATOES || block == Blocks.BEETROOTS) return 0x5D9B47;
        if (block == Blocks.HAY_BLOCK) return 0xD4AA00;
        if (block == Blocks.BELL) return 0xF0D020;
        if (block == Blocks.COMPOSTER) return 0x6B5030;
        if (block == Blocks.FLOWER_POT) return 0x8B4513;
        if (block == Blocks.BOOKSHELF) return 0x8B6535;
        if (block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.MANGROVE_LOG) return 0x6B5030;
        if (block == Blocks.JUNGLE_LEAVES || block == Blocks.ACACIA_LEAVES || block == Blocks.MANGROVE_LEAVES || block == Blocks.AZALEA_LEAVES) return 0x3A7A24;
        if (block == Blocks.JUNGLE_PLANKS || block == Blocks.ACACIA_PLANKS || block == Blocks.MANGROVE_PLANKS || block == Blocks.DARK_OAK_PLANKS) return 0xA08050;
        if (block == Blocks.BAMBOO || block == Blocks.BAMBOO_BLOCK) return 0x7AA02A;
        if (block == Blocks.MOSS_BLOCK) return 0x4D7A30;
        if (block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS) return 0x5A4A3A;
        if (block == Blocks.SWEET_BERRY_BUSH) return 0x3A6A24;
        if (block == Blocks.SUNFLOWER || block == Blocks.DANDELION) return 0xFFD040;
        if (block == Blocks.POPPY || block == Blocks.ROSE_BUSH) return 0xCC2020;
        if (block == Blocks.CORNFLOWER || block == Blocks.BLUE_ORCHID) return 0x4080CC;
        if (block == Blocks.LILY_PAD) return 0x208020;
        if (block == Blocks.CACTUS) return 0x287028;
        if (block == Blocks.DEAD_BUSH) return 0x8B7355;
        if (block == Blocks.SUGAR_CANE) return 0x80C040;
        if (block == Blocks.MELON) return 0x40A020;
        if (block == Blocks.PUMPKIN) return 0xD08020;
        if (block == Blocks.BROWN_MUSHROOM_BLOCK || block == Blocks.RED_MUSHROOM_BLOCK) return 0x8B4513;
        if (block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL) return 0x8B8680;
        if (block == Blocks.WHITE_WOOL || block == Blocks.WHITE_CARPET) return 0xE8E8E8;
        if (block == Blocks.BLACK_WOOL || block == Blocks.BLACK_CARPET) return 0x1A1A1A;
        if (block == Blocks.BRICKS || block == Blocks.BRICK_STAIRS || block == Blocks.BRICK_SLAB) return 0x9B5B4A;
        if (block == Blocks.SMOOTH_STONE || block == Blocks.SMOOTH_STONE_SLAB) return 0x9A9A9A;
        if (block == Blocks.TUFF || block == Blocks.TUFF_BRICKS) return 0x6A6A60;
        if (block == Blocks.CALCITE) return 0xDDDDD0;
        if (block == Blocks.DRIPSTONE_BLOCK || block == Blocks.POINTED_DRIPSTONE) return 0x8B7355;
        if (block == Blocks.COPPER_BLOCK) return 0xC07040;
        // 默认灰色
        return 0x606060;
    }

    private record PlayerMarker(int x, int z, String name, int color, float yaw) {}
}
