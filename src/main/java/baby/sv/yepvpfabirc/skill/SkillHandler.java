package baby.sv.yepvpfabirc.skill;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class SkillHandler {

    // 左下角提示(替代actionbar)
    private static void tip(ServerPlayerEntity player, String msg) {
        GameManager.getInstance().sendTip(player, msg);
    }

    // Cooldowns in ticks (20 ticks = 1 second)
    private static final long XLL_CUBE_COOLDOWN = 3600;     // 3分钟
    private static final long XLL_ROAD_COOLDOWN = 2400;     // 2分钟
    private static final long NIHAO_COOLDOWN = 2400;         // 2分钟
    private static final long ST_CIALLO_COOLDOWN = 2400;     // 2分钟
    private static final long ST_SONIC_COOLDOWN = 2400;      // 2分钟
    private static final long RETOUR_REVIVE_COOLDOWN = 600;  // 30秒
    private static final long MACHA_COOLDOWN = 600;          // 30秒
    private static final long DAMAI_TP_COOLDOWN = 600;       // 30秒
    private static final long DASHA_RAIN_COOLDOWN = 6000;    // 5分钟
    private static final long HELI_TP_COOLDOWN = 1200;       // 1分钟
    private static final long HELI_EYE_COOLDOWN = 3600;      // 3分钟
    private static final long JIEZU_NIGHT_COOLDOWN = 6000;   // 5分钟
    private static final long JIEZU_WEB_TP_COOLDOWN = 2400;  // 2分钟
    // ALLAND: 颜料弹由charge系统管理, 无独立CD
    private static final long YOUZHA_ULTIMATE_COOLDOWN = 2400; // 2分钟
    private static final long YOUZHA_EAT_COOLDOWN = 600;       // 30秒
    private static final long POPCORN_LASER_COOLDOWN = 2400; // 2分钟
    private static final long SANCHEZ_HEAL_COOLDOWN = 600;   // 30秒
    private static final long SANCHEZ_HUNT_COOLDOWN = 2400;  // 2分钟
    // MAYPOOR
    private static final long MAYPOOR_FLIGHT_COOLDOWN = 600;  // 30秒
    private static final long MAYPOOR_FLIGHT_DURATION = 200;  // 10秒
    private static final long MAYPOOR_SLAM_COOLDOWN = 200;    // 10秒

    public static void handleSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        switch (data.getRole()) {
            case XLL -> handleXllSkill(player, data, skillSlot);
            case JIEZU -> handleJiezuSkill(player, data, skillSlot);
            case DAMAI -> handleDamaiSkill(player, data, skillSlot);
            case DASHA -> handleDashaSkill(player, data, skillSlot);
            case JVJV -> handleJvjvSkill(player, data, skillSlot);
            case ST -> handleStSkill(player, data, skillSlot);
            case BOBBY -> handleBobbySkill(player, data, skillSlot);
            case RETOUR -> handleRetourSkill(player, data, skillSlot);
            case ALLAND -> handleAllandSkill(player, data, skillSlot);
            case MACHA -> handleMachaSkill(player, data, skillSlot);
            case HELI -> handleHeliSkill(player, data, skillSlot);
            case POPCORN -> handlePopcornSkill(player, data, skillSlot);
            case SANCHEZ -> handleSanchezSkill(player, data, skillSlot);
            case SHUBING -> handleShubingSkill(player, data, skillSlot);
            case NIHAO -> handleNihaoSkill(player, data, skillSlot);
            case JANE -> handleJaneSkill(player, data, skillSlot);
            case YOUZHA -> handleYouzhaSkill(player, data, skillSlot);
            case LAYI -> handleLayiSkill(player, data, skillSlot);
            case MAYPOOR -> handleMaypoorSkill(player, data, skillSlot);
            default -> {}
        }
    }

    // ==================== XLL: 立体艺术 ====================
    // Z: 生成边长10无法破坏玻璃正方体（5分钟冷却，8秒持续，内部失明+10%maxHP真伤/秒）
    // X: 向前生成3x50永久玻璃长道（2分钟冷却，只限东西南北方向）
    private static void handleXllSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();

        if (skillSlot == 0) {
            // Z键: 玻璃正方体
            if (currentTick - data.getLastSkillZTime() < XLL_CUBE_COOLDOWN) {
                long remaining = (XLL_CUBE_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
                tip(player, "§c玻璃正方体冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillZTime(currentTick);

            BlockPos center = player.getBlockPos();
            ServerWorld world = player.getEntityWorld();
            int half = 5; // 边长10，半径5

            for (int x = -half; x <= half; x++) {
                for (int y = -half; y <= half; y++) {
                    for (int z = -half; z <= half; z++) {
                        if (Math.abs(x) == half || Math.abs(y) == half || Math.abs(z) == half) {
                            BlockPos bp = center.add(x, y, z);
                            world.setBlockState(bp, Blocks.GLASS.getDefaultState());
                        }
                    }
                }
            }

            data.setGlassCubeActive(true);
            data.setGlassCubeCenter(center);
            data.setGlassCubeEndTick(currentTick + 160); // 8秒

            GameManager.getInstance().broadcastMessage("§b§l[立体艺术] §r§e" + player.getGameProfile().name() + " §b生成了玻璃正方体！范围内玩家将受到失明和伤害！");

        } else if (skillSlot == 1) {
            // X键: 玻璃长道
            if (currentTick - data.getLastSkillXTime() < XLL_ROAD_COOLDOWN) {
                long remaining = (XLL_ROAD_COOLDOWN - (currentTick - data.getLastSkillXTime())) / 20;
                tip(player, "§c玻璃长道冷却中... §e" + remaining + "s");
                return;
            }

            // 只允许东西南北方向(不能斜向或垂直)
            float yaw = player.getYaw() % 360;
            if (yaw < 0) yaw += 360;
            int dx = 0, dz = 0;
            // 南=0, 西=90, 北=180, 东=270
            if (yaw >= 315 || yaw < 45) { dz = 1; } // 南
            else if (yaw >= 45 && yaw < 135) { dx = -1; } // 西
            else if (yaw >= 135 && yaw < 225) { dz = -1; } // 北
            else { dx = 1; } // 东

            data.setLastSkillXTime(currentTick);

            ServerWorld world = player.getEntityWorld();
            BlockPos start = player.getBlockPos();
            int roadLength = 50;
            int roadWidth = 3; // 3格宽(中心+两侧1)

            // 确定宽度方向的偏移
            int wx = (dz != 0) ? 1 : 0; // 前进Z方向→宽度沿X
            int wz = (dx != 0) ? 1 : 0; // 前进X方向→宽度沿Z

            int placed = 0;
            for (int i = 0; i < roadLength; i++) {
                for (int w = -1; w <= 1; w++) {
                    BlockPos roadPos = start.add(dx * i + wx * w, 0, dz * i + wz * w);
                    world.setBlockState(roadPos, Blocks.GLASS.getDefaultState());
                    data.getGlassRoadBlocks().add(roadPos);
                    placed++;

                    // 清空上方2格(保护特殊方块)
                    for (int dy = 1; dy <= 2; dy++) {
                        BlockPos above = roadPos.up(dy);
                        if (!isXllRoadProtectedBlock(world.getBlockState(above))) {
                            world.setBlockState(above, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }

            String dirName = (dz > 0) ? "南" : (dz < 0) ? "北" : (dx > 0) ? "东" : "西";
            GameManager.getInstance().broadcastMessage("§b§l[立体艺术] §r§e" + player.getGameProfile().name() + " §b向" + dirName + "展开了50格玻璃长道！");
            tip(player, "§b玻璃长道已生成！方向: §e" + dirName + " §b共" + placed + "格");
        }
    }

    // 玻璃长道上方清除时不清空的方块(只保留基岩)
    private static boolean isXllRoadProtectedBlock(net.minecraft.block.BlockState state) {
        if (state.isAir()) return true;
        if (state.getBlock() == Blocks.BEDROCK) return true;
        return false;
    }

    // 检测某个位置是否属于XLL的任何不可破坏建筑(正方体/玻璃长道)
    public static boolean isXllProtectedBlock(BlockPos pos) {
        GameManager gm = GameManager.getInstance();
        for (PlayerData pd : gm.getAllPlayerData().values()) {
            if (pd.getRole() != Role.XLL) continue;
            // 检查活跃的正方体
            if (pd.isGlassCubeActive() && pd.getGlassCubeCenter() != null) {
                BlockPos center = pd.getGlassCubeCenter();
                int dx = Math.abs(pos.getX() - center.getX());
                int dy = Math.abs(pos.getY() - center.getY());
                int dz = Math.abs(pos.getZ() - center.getZ());
                if (dx <= 5 && dy <= 5 && dz <= 5 && (dx == 5 || dy == 5 || dz == 5)) {
                    return true;
                }
            }
            // 检查玻璃长道
            if (pd.getGlassRoadBlocks().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    // 检查某个位置是否属于某个XLL的玻璃(正方体或长道)
    public static boolean isXllGlassBlock(BlockPos pos) {
        return isXllProtectedBlock(pos);
    }

    // 找到某个玻璃长道方块的所有者
    public static PlayerData findGlassRoadOwner(BlockPos pos) {
        GameManager gm = GameManager.getInstance();
        for (PlayerData pd : gm.getAllPlayerData().values()) {
            if (pd.getRole() != Role.XLL) continue;
            if (pd.getGlassRoadBlocks().contains(pos)) return pd;
        }
        return null;
    }

    // 清除XLL玻璃正方体
    public static void removeGlassCube(ServerWorld world, BlockPos center) {
        int half = 5;
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    if (Math.abs(x) == half || Math.abs(y) == half || Math.abs(z) == half) {
                        BlockPos bp = center.add(x, y, z);
                        if (world.getBlockState(bp).getBlock() == Blocks.GLASS) {
                            world.setBlockState(bp, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    // 清除XLL所有玻璃长道
    public static void removeAllGlassRoads(ServerWorld world, PlayerData ownerData) {
        for (BlockPos pos : new HashSet<>(ownerData.getGlassRoadBlocks())) {
            if (world.getBlockState(pos).getBlock() == Blocks.GLASS) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
        }
        ownerData.getGlassRoadBlocks().clear();
    }

    // ==================== JIEZU: 节足动物 ====================
    // Z: 强行切换至夜晚+所有人失明30秒+30秒后回白天(5分钟CD), X: 传送至蛛网(2分钟CD,循环选择)
    private static void handleJiezuSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();
        if (skillSlot == 0) { // Z: 强制夜晚
            if (currentTick - data.getLastSkillZTime() < JIEZU_NIGHT_COOLDOWN) {
                long remaining = (JIEZU_NIGHT_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
                tip(player, "§c技能冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillZTime(currentTick);
            ServerWorld world = player.getEntityWorld();
            world.setTimeOfDay(18000); // 午夜
            // 所有人失明30秒(JIEZU自己除外)
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.getUuid().equals(player.getUuid())) continue;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 600, 0, false, false, true));
            }
            // 30秒后回白天
            data.setJiezuNightEndTick(currentTick + 600);
            GameManager.getInstance().broadcastMessage("§8§l[节足动物] §r§e" + player.getGameProfile().name() + " §8强制切换到了夜晚！所有人失明30秒！30秒后恢复白天。");
        } else if (skillSlot == 1) { // X: 切换选中的蛛网(1/2/3循环)
            if (data.getWebPositions().isEmpty()) {
                tip(player, "§c没有放置过蛛网！");
                return;
            }
            int nextIdx = (data.getJiezuWebTpIndex() + 1) % data.getWebPositions().size();
            data.setJiezuWebTpIndex(nextIdx);
            BlockPos target = data.getWebPositions().get(nextIdx);
            tip(player, "§e已选中蛛网#" + (nextIdx + 1) + " §7(" + target.getX() + "," + target.getY() + "," + target.getZ() + ") §e按V键传送");
        } else if (skillSlot == 2) { // V: 传送到选中的蛛网
            if (data.getWebPositions().isEmpty()) {
                tip(player, "§c没有放置过蛛网！");
                return;
            }
            if (currentTick - data.getLastSkillXTime() < JIEZU_WEB_TP_COOLDOWN) {
                long remaining = (JIEZU_WEB_TP_COOLDOWN - (currentTick - data.getLastSkillXTime())) / 20;
                tip(player, "§c传送冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillXTime(currentTick);
            int idx = data.getJiezuWebTpIndex() % data.getWebPositions().size();
            BlockPos target = data.getWebPositions().get(idx);
            ServerWorld world = player.getEntityWorld();
            // 在蛛网周围8方向找安全位置(2格空气+下方实心), 避免卡墙
            int[][] offsets = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
            BlockPos safePos = null;
            for (int[] off : offsets) {
                BlockPos check = target.add(off[0], 0, off[1]);
                if (world.getBlockState(check).isAir()
                        && world.getBlockState(check.up()).isAir()
                        && !world.getBlockState(check.down()).isAir()) {
                    safePos = check;
                    break;
                }
            }
            // 兜底: 垂直向上找空气
            if (safePos == null) {
                for (int dy = 0; dy <= 5; dy++) {
                    BlockPos check = target.add(0, dy, 0);
                    if (world.getBlockState(check).isAir() && world.getBlockState(check.up()).isAir()) {
                        safePos = check;
                        break;
                    }
                }
            }
            if (safePos == null) safePos = target.up(); // 最终兜底
            player.teleport(world, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch(), false);
            tip(player, "§a已传送至蛛网#" + (idx + 1) + "！(" + target.getX() + "," + target.getY() + "," + target.getZ() + ")");
        }
    }

    // ==================== YUSUI: 男娘后宫 ====================
    // 雪球命中时调用（由SnowballHitMixin触发）
    // 只在场上没有男娘时才能转化
    public static void onYusuiSnowballHit(ServerPlayerEntity yusui, ServerPlayerEntity target) {
        GameManager gm = GameManager.getInstance();
        PlayerData data = gm.getPlayerData(yusui.getUuid());
        if (data == null || data.getRole() != Role.YUSUI) return;
        PlayerData targetData = gm.getPlayerData(target.getUuid());
        if (targetData == null) return;
        if (targetData.getRole() == Role.YUSUI) return; // 不能让鱼碎自己变男娘
        if (targetData.isNanniang()) return;

        // 只在场上没有存活的男娘时才能用雪球转化
        for (PlayerData pd : gm.getAllPlayerData().values()) {
            if (pd.isNanniang() && pd.isAlive()) {
                tip(yusui, "§c场上已有男娘，雪球无法转化新目标！");
                return;
            }
        }

        makeNanniang(yusui, target, data, targetData);
    }

    // 被男娘击杀的玩家也变男娘(在GameManager.onPlayerDeath中调用)
    public static void onNanniangKillConvert(ServerPlayerEntity victim, PlayerData killerData) {
        GameManager gm = GameManager.getInstance();
        PlayerData victimData = gm.getPlayerData(victim.getUuid());
        if (victimData == null || victimData.isNanniang() || victimData.getRole() == Role.YUSUI) return;

        UUID masterUuid = killerData.getNanniangMaster();
        PlayerData masterData = gm.getPlayerData(masterUuid);
        if (masterData == null) return;

        ServerPlayerEntity masterPlayer = victim.getEntityWorld().getServer().getPlayerManager().getPlayer(masterUuid);
        makeNanniang(masterPlayer, victim, masterData, victimData);
    }

    private static void makeNanniang(ServerPlayerEntity master, ServerPlayerEntity target,
                                     PlayerData masterData, PlayerData targetData) {
        masterData.getNanniangTargets().add(target.getUuid());
        targetData.setNanniang(true);
        targetData.setNanniangMaster(masterData.getPlayerUuid());

        GameManager.getInstance().broadcastMessage("§d" + target.getGameProfile().name() + " 成为了男娘，喵~");
        target.sendMessage(Text.literal("§e你被变成了男娘！"), false);
    }

    // ==================== NIHAO: 问好 ====================
    // 无主动技能键, 通过/hello指令触发
    private static void handleNihaoSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        // NIHAO的技能通过/hello命令触发, 不使用按键
        tip(player, "§e使用 §a/hello <玩家名> §e来问好！");
    }

    // /hello 命令处理(由PlayerCommands调用)
    public static void handleHelloCommand(ServerPlayerEntity player, ServerPlayerEntity target) {
        GameManager gm = GameManager.getInstance();
        PlayerData data = gm.getPlayerData(player.getUuid());
        if (data == null || data.getRole() != Role.NIHAO) {
            player.sendMessage(Text.literal("§c你不是Nihao角色！"), false);
            return;
        }

        long currentTick = player.getEntityWorld().getTime();
        if (currentTick - data.getLastSkillZTime() < NIHAO_COOLDOWN) {
            long remaining = (NIHAO_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
            tip(player, "§c问好冷却中... §e" + remaining + "s");
            return;
        }
        if (data.getHelloTargetUuid() != null) {
            tip(player, "§c你已经在问好中！等待对方回复...");
            return;
        }

        PlayerData targetData = gm.getPlayerData(target.getUuid());
        if (targetData == null || !targetData.isAlive()) {
            tip(player, "§c目标不在游戏中或已死亡！");
            return;
        }

        data.setLastSkillZTime(currentTick);
        int code = 100000 + new java.util.Random().nextInt(900000); // 6位随机数
        data.setHelloTargetUuid(target.getUuid());
        data.setHelloCode(code);
        data.setHelloExpiryTick(currentTick + data.getHelloTimerSeconds() * 20L);

        gm.broadcastMessage("§e§l[问好] §r§e" + player.getGameProfile().name() + " §a向 §e" + target.getGameProfile().name() + " §a发起了问好！");

        // 目标: 正中间大标题 + 副标题显示验证码 + 警报音效
        int timer = data.getHelloTimerSeconds();
        // Title 时序: fadeIn=10tick, stay=timer秒, fadeOut=20tick
        target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, timer * 20, 20));
        target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                Text.literal("§c§l⚠ 你被问好了！")));
        target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                Text.literal("§e输入 §a/helloback " + code + " §e否则 §c§l" + timer + "秒后秒杀！")));
        // 警报音效(带诅咒氛围)+铃声
        net.minecraft.server.world.ServerWorld tWorld = (net.minecraft.server.world.ServerWorld) target.getEntityWorld();
        tWorld.playSound(null, target.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                net.minecraft.sound.SoundCategory.MASTER, 1.0f, 1.0f);
        tWorld.playSound(null, target.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                net.minecraft.sound.SoundCategory.MASTER, 1.0f, 0.8f);
        // 聊天消息(fallback, 玩家可以复制)
        target.sendMessage(Text.literal("§c§l⚠ 你被问好了！§r§e" + timer + "秒内输入 §a/helloback " + code + " §e否则被秒杀！"), false);

        // NIHAO本人: 轻提示
        net.minecraft.server.world.ServerWorld pWorld = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        pWorld.playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(),
                net.minecraft.sound.SoundCategory.MASTER, 0.8f, 1.2f);
        tip(player, "§a已向 " + target.getGameProfile().name() + " 发起问好！验证码: §e" + code + " §a倒计时: §e" + timer + "秒");
    }

    // /helloback 命令处理(由PlayerCommands调用)
    public static void handleHelloBackCommand(ServerPlayerEntity responder, int code) {
        GameManager gm = GameManager.getInstance();
        UUID responderUuid = responder.getUuid();

        for (PlayerData nihaoData : gm.getAllPlayerData().values()) {
            if (nihaoData.getRole() != Role.NIHAO) continue;

            // 检查全体问好
            if (!nihaoData.getBroadcastHelloCodes().isEmpty()) {
                Integer broadcastCode = nihaoData.getBroadcastHelloCodes().get(responderUuid);
                if (broadcastCode != null && !nihaoData.getBroadcastHelloResponded().contains(responderUuid)) {
                    if (broadcastCode == code) {
                        nihaoData.getBroadcastHelloResponded().add(responderUuid);
                        responder.sendMessage(Text.literal("§a§l✓ 成功回复全体问好！"), false);
                        gm.broadcastMessage("§a§l[问好] §r§e" + responder.getGameProfile().name() + " §a成功回复了全体问好！");
                        // 清除Title
                        responder.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(0, 0, 0));
                        responder.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.empty()));
                        return;
                    } else {
                        responder.sendMessage(Text.literal("§c验证码错误！"), false);
                        return;
                    }
                }
            }

            // 检查单体问好
            if (nihaoData.getHelloTargetUuid() != null
                    && nihaoData.getHelloTargetUuid().equals(responderUuid)) {
                if (nihaoData.getHelloCode() == code) {
                    // 回复正确: NIHAO位置暴露30秒
                    long currentTick = responder.getEntityWorld().getTime();
                    nihaoData.setNihaoRevealUntilTick(currentTick + 600);
                    nihaoData.setRevealUntilTick(currentTick + 600);
                    ServerPlayerEntity nihaoPlayer = responder.getEntityWorld().getServer().getPlayerManager().getPlayer(nihaoData.getPlayerUuid());
                    if (nihaoPlayer != null) {
                        nihaoPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0, false, false, true));
                    }
                    nihaoData.setHelloTargetUuid(null);
                    nihaoData.setHelloCode(0);
                    nihaoData.setHelloExpiryTick(0);
                    // 清除Title
                    responder.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(0, 0, 0));
                    responder.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.empty()));
                    gm.broadcastMessage("§a§l[问好] §r§e" + responder.getGameProfile().name() + " §a成功回复了问好！§e" + nihaoData.getPlayerName() + " §a的位置暴露30秒！");
                    return;
                } else {
                    responder.sendMessage(Text.literal("§c验证码错误！"), false);
                    return;
                }
            }
        }
        responder.sendMessage(Text.literal("§c没有人在向你问好！"), false);
    }

    // ==================== DAMAI: 不死者 ====================
    // Z: 传送回墓碑(30秒冷却)
    private static void handleDamaiSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        if (skillSlot != 0) return;
        if (!data.isSkeletonForm()) {
            tip(player, "§c你不在骷髅形态！");
            return;
        }
        BlockPos tombstone = data.getTombstonePos();
        if (tombstone == null) {
            tip(player, "§c没有墓碑位置！");
            return;
        }
        long currentTick = player.getEntityWorld().getTime();
        if (currentTick - data.getLastSkillZTime() < DAMAI_TP_COOLDOWN) {
            long remaining = (DAMAI_TP_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
            tip(player, "§c传送冷却中... §e" + remaining + "s");
            return;
        }
        data.setLastSkillZTime(currentTick);
        ServerWorld world = player.getEntityWorld();
        player.teleport(world, tombstone.getX() + 0.5, tombstone.getY() + 1, tombstone.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch(), false);
        tip(player, "§a已传送回墓碑位置！");
    }

    // ==================== DASHA: 鲨鱼人 ====================
    // Z: 变天为雨(5分钟冷却)
    private static void handleDashaSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        if (skillSlot != 0) return;
        long currentTick = player.getEntityWorld().getTime();
        if (currentTick - data.getLastSkillZTime() < DASHA_RAIN_COOLDOWN) {
            long remaining = (DASHA_RAIN_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
            tip(player, "§c技能冷却中... §e" + remaining + "s");
            return;
        }
        data.setLastSkillZTime(currentTick);
        ServerWorld world = player.getEntityWorld();
        world.setWeather(0, 1200, true, false); // 雨天1分钟
        GameManager.getInstance().broadcastMessage("§b§l我的世界下雨了...");
    }

    // ==================== JVJV: 大爆炸 ====================
    // Z: 大爆炸, X: 红莲华, C: 进食回血, V: 吃人(5格内最近, 1minCD, 消化1min)
    private static void handleJvjvSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        if (skillSlot == 3) { // V: 吃人
            long currentTick = player.getEntityWorld().getTime();
            if (data.getJvjvEatenPlayer() != null) {
                tip(player, "§c正在消化中，无法再次吃人！");
                return;
            }
            if (currentTick - data.getLastJvjvEatTick() < 1200) { // 1分钟CD
                int remaining = (int) ((1200 - (currentTick - data.getLastJvjvEatTick())) / 20);
                tip(player, "§c吃人冷却中... §e" + remaining + "s");
                return;
            }
            // 红莲华蓄力期间无法操作
            if (data.isRedLotusCharging(currentTick)) {
                tip(player, "§c红莲华蓄力中，无法操作！");
                return;
            }
            // 寻找5格内最近的存活玩家
            ServerWorld world = player.getEntityWorld();
            ServerPlayerEntity nearest = null;
            double minDist = 25; // 5格squared
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (other == player || other.isSpectator()) continue;
                PlayerData od = GameManager.getInstance().getPlayerData(other.getUuid());
                if (od == null || !od.isAlive()) continue;
                double d = player.squaredDistanceTo(other);
                if (d < minDist) {
                    minDist = d;
                    nearest = other;
                }
            }
            if (nearest == null) {
                tip(player, "§c5格内没有可吃的玩家！");
                return;
            }
            data.setLastJvjvEatTick(currentTick);
            GameManager.getInstance().jvjvEatPlayer(player, data, nearest, currentTick);
            return;
        }
        if (skillSlot == 2) { // C: 进食回血
            long currentTick = player.getEntityWorld().getTime();
            if (currentTick - data.getLastJvjvHealTick() < 20) { // 1秒CD
                return;
            }
            int foodLevel = player.getHungerManager().getFoodLevel();
            if (foodLevel < 5) {
                tip(player, "§c饱食度不足！需要5点饱食度(当前" + foodLevel + ")");
                return;
            }
            data.setLastJvjvHealTick(currentTick);
            player.getHungerManager().setFoodLevel(foodLevel - 5);
            player.heal(10.0f); // 回复10HP=5心
            ServerWorld world = player.getEntityWorld();
            world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_BURP,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            tip(player, "§a进食回血！回复5心(剩余饱食度" + player.getHungerManager().getFoodLevel() + ")");
            return;
        }
        if (skillSlot == 0) { // Z: 大爆炸
            if (data.isExplosionUsed()) {
                tip(player, "§c本条命已使用大爆炸！");
                return;
            }
            // 红莲华蓄力期间无法操作
            long currentTick = player.getEntityWorld().getTime();
            if (data.isRedLotusCharging(currentTick)) {
                tip(player, "§c红莲华蓄力中，无法操作！");
                return;
            }
            data.setExplosionUsed(true);
            data.setLastSkillZTime(currentTick);
            // 全体玩家播放自定义爆炸前音效
            ServerWorld world = player.getEntityWorld();
            for (ServerPlayerEntity p : world.getPlayers()) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(),
                        baby.sv.yepvpfabirc.ModSounds.EXPLOSION, net.minecraft.sound.SoundCategory.MASTER,
                        1.0f, 1.0f);
            }
            GameManager.getInstance().broadcastMessage("§c§l[大爆炸] §r§e" + player.getGameProfile().name() + " §c发动了大爆炸！3秒后爆炸！");
        } else if (skillSlot == 1) { // X: 红莲华
            if (!data.canRedLotus()) {
                tip(player, "§c未解锁红莲华！需要累计大爆炸炸死5人！(当前" + data.getJvjvTotalExplosionKills() + "/5)");
                return;
            }
            if (data.isRedLotusUsed()) {
                tip(player, "§c红莲华已使用！");
                return;
            }
            long currentTick = player.getEntityWorld().getTime();
            data.setRedLotusUsed(true);
            data.setRedLotusActiveTick(currentTick);
            data.setRedLotusPos(player.getBlockPos());
            data.setLastSkillXTime(currentTick);
            // 高亮显示(发光效果10秒)
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0, false, false, true));
            // 全体播放红莲华音乐
            ServerWorld world = player.getEntityWorld();
            for (ServerPlayerEntity p : world.getPlayers()) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(),
                        baby.sv.yepvpfabirc.ModSounds.GURENGE, net.minecraft.sound.SoundCategory.MASTER,
                        1.0f, 1.0f);
            }
            GameManager.getInstance().broadcastMessage("§c§l§k!!§r §4§l红莲华 §c§l§k!! §r§e" + player.getGameProfile().name() + " §c发动了红莲华！10秒后100格内所有玩家将被秒杀！");
        }
    }

    // JVJV大爆炸延迟执行（在GameManager tick中调用）
    public static void executeJvjvExplosion(ServerPlayerEntity player, PlayerData data) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());

        world.createExplosion(null, pos.x, pos.y, pos.z, 6.0f, World.ExplosionSourceType.NONE);

        int killCount = 0;
        for (ServerPlayerEntity target : new ArrayList<>(world.getPlayers())) {
            if (target == player) continue;
            PlayerData td = GameManager.getInstance().getPlayerData(target.getUuid());
            if (td == null || !td.isAlive()) continue;
            if (target.squaredDistanceTo(pos) <= 64) { // 8格
                // 绕过复活无敌: 临时清除
                long savedInvincibleUntil = td.getRespawnInvincibleUntil();
                td.setRespawnInvincibleUntil(0);
                // 穿透下界合金套/抗性: 多重保险击杀
                // 注意: 不能用 setHealth(0) 再 damage(), 因为 damage 检查 health>0 会跳过 onDeath, 导致命数不扣
                target.damage(world, world.getDamageSources().genericKill(), 99999f);
                if (target.isAlive()) target.damage(world, world.getDamageSources().outOfWorld(), 99999f);
                if (target.isAlive()) target.kill(world);
                if (target.isAlive()) td.setRespawnInvincibleUntil(savedInvincibleUntil); // 兜底
                killCount++;
                data.addKill(); // 计入左侧击杀数
            }
        }
        data.setLastExplosionKills(killCount);
        data.addJvjvTotalExplosionKills(killCount); // 累计击杀

        // 每炸死1人+1命(在自身死亡前累加, 避免被loseLife抵消)
        if (killCount > 0) {
            for (int i = 0; i < killCount; i++) {
                data.gainLife();
            }
        }

        GameManager.getInstance().broadcastMessage("§c§l[大爆炸] §r§e" + player.getGameProfile().name() + " §c炸死了 §e" + killCount + " §c名玩家！(累计" + data.getJvjvTotalExplosionKills() + "人)" + (killCount > 0 ? " §a§l+" + killCount + "命§r§c!" : ""));

        // 自身死亡(在bonus life加完之后, 确保loseLife不会抵消)
        player.kill(world);
        // 累计炸死5人解锁红莲华
        if (data.getJvjvTotalExplosionKills() >= 5 && !data.canRedLotus()) {
            data.setCanRedLotus(true);
            GameManager.getInstance().broadcastMessage("§4§l" + player.getGameProfile().name() + " 累计炸死5人，解锁了红莲华！");
        }
    }

    // JVJV红莲华延迟执行
    public static void executeRedLotus(ServerPlayerEntity player, PlayerData data) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());

        for (ServerPlayerEntity target : new ArrayList<>(world.getPlayers())) {
            if (target == player) continue;
            PlayerData td = GameManager.getInstance().getPlayerData(target.getUuid());
            if (td == null || !td.isAlive()) continue;
            if (target.squaredDistanceTo(pos) <= 10000) { // 100格
                target.kill(world);
                data.addKill(); // 计入左侧击杀数
            }
        }
        data.setRedLotusActiveTick(0); // 清除冻结状态
        GameManager.getInstance().broadcastMessage("§4§l[红莲华] §r§c100格内所有玩家被秒杀！§e" + player.getGameProfile().name() + " §c存活！");
    }

    // ==================== ST: 柚子厨 ====================
    // Z: Ciallo暴露自己+看到所有人30秒(2分钟CD), V: 自瞄声波(选择ciallo暴露目标, 4秒自动攻击, 2分钟CD)
    private static void handleStSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();
        if (skillSlot == 0) { // Z: Ciallo
            handleCialloCommand(player);
        } else if (skillSlot == 2) { // V: 自瞄声波
            // 已在自瞄中 → 再按V循环切换目标
            if (data.getSonicAutoAimTarget() != null) {
                selectNextAutoAimTarget(player, data);
                return;
            }
            // 冷却检查
            if (currentTick - data.getLastSonicTick() < ST_SONIC_COOLDOWN) {
                long remaining = (ST_SONIC_COOLDOWN - (currentTick - data.getLastSonicTick())) / 20;
                tip(player, "§c声波冷却中... §e" + remaining + "s");
                return;
            }
            // 必须在Ciallo暴露期间才能使用
            if (data.getCialloSeeAllUntilTick() <= currentTick) {
                tip(player, "§c需要先使用Ciallo暴露目标！(Z键)");
                return;
            }
            // 选择第一个目标并开始自瞄
            if (!selectNextAutoAimTarget(player, data)) {
                tip(player, "§c没有可锁定的目标！");
                return;
            }
            data.setLastSonicTick(currentTick);
            data.setSonicReadyNotified(false);
            GameManager.getInstance().broadcastMessage("§d§l[Ciallo自瞄] §r§e" + player.getGameProfile().name() + " §c锁定了目标！4秒自动攻击开始！");
        }
    }

    // 循环选择下一个ciallo暴露的目标
    private static boolean selectNextAutoAimTarget(ServerPlayerEntity player, PlayerData data) {
        GameManager gm = GameManager.getInstance();
        net.minecraft.server.MinecraftServer srv = gm.getServer();
        java.util.List<UUID> candidates = new java.util.ArrayList<>();
        for (PlayerData pd : gm.getAllPlayerData().values()) {
            if (!pd.isAlive() || pd.getPlayerUuid().equals(player.getUuid())) continue;
            ServerPlayerEntity target = srv.getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (target == null) continue;
            candidates.add(pd.getPlayerUuid());
        }
        if (candidates.isEmpty()) return false;

        int idx = data.getSonicSelectIndex() % candidates.size();
        UUID targetUuid = candidates.get(idx);
        data.setSonicSelectIndex(idx + 1);
        data.setSonicAutoAimTarget(targetUuid);
        data.setSonicAutoAimStartTick(player.getEntityWorld().getTime());

        ServerPlayerEntity target = srv.getPlayerManager().getPlayer(targetUuid);
        String targetName = target != null ? target.getGameProfile().name() : "???";
        double dist = target != null ? Math.sqrt(player.squaredDistanceTo(target)) : 0;
        tip(player, "§d§l[自瞄锁定] §e" + targetName + " §7(距离" + (int)dist + "格) §a再按V切换目标");
        return true;
    }

    // /Ciallo命令处理(ST专属主动1)
    public static void handleCialloCommand(ServerPlayerEntity player) {
        PlayerData data = GameManager.getInstance().getPlayerData(player.getUuid());
        if (data == null || data.getRole() != Role.ST) {
            player.sendMessage(Text.literal("§c你不是柚子厨角色！"), false);
            return;
        }

        long currentTick = player.getEntityWorld().getTime();
        if (currentTick - data.getLastCialloTick() < ST_CIALLO_COOLDOWN) {
            long remaining = (ST_CIALLO_COOLDOWN - (currentTick - data.getLastCialloTick())) / 20;
            tip(player, "§cCiallo冷却中... §e" + remaining + "s");
            return;
        }

        data.setLastCialloTick(currentTick);
        // 暴露自己30秒
        data.setCialloRevealUntilTick(currentTick + 600);
        data.setRevealUntilTick(currentTick + 600);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 600, 0, false, false, true));
        // 看到所有人30秒
        data.setCialloSeeAllUntilTick(currentTick + 600);

        // 全体玩家播放Ciallo自定义音效
        ServerWorld world = player.getEntityWorld();
        for (ServerPlayerEntity p : world.getPlayers()) {
            world.playSound(null, p.getX(), p.getY(), p.getZ(),
                    baby.sv.yepvpfabirc.ModSounds.CIALLO, net.minecraft.sound.SoundCategory.MASTER,
                    1.0f, 1.0f);
        }

        GameManager.getInstance().broadcastMessage("§d§lCiallo～(∠・ω< )⌒☆ §r§e" + player.getGameProfile().name() + " §a发送了Ciallo！位置暴露30秒！");
        tip(player, "§aCiallo！你的位置暴露30秒，同时可以看到所有人位置30秒！");
    }

    // ST自瞄声波: 4秒蓄力结束后一次性结算(由GameManager.tickSt调用)
    // 伤害公式: 0-100格线性提升(0->20心), 100-200格线性衰减(20->10心), 200格以上保底10心
    public static void executeSonicFinalDamage(ServerPlayerEntity player, PlayerData data, ServerPlayerEntity target) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        double distance = Math.sqrt(player.squaredDistanceTo(target));

        double totalDamageHearts;
        if (distance <= 100.0) {
            // 0-100格: 0 -> 20心
            totalDamageHearts = distance * (20.0 / 100.0);
        } else if (distance <= 200.0) {
            // 100-200格: 20 -> 10心
            totalDamageHearts = 20.0 - (distance - 100.0) * (10.0 / 100.0);
        } else {
            // 200格以上: 保底10心
            totalDamageHearts = 10.0;
        }
        
        float damage = (float) (totalDamageHearts * 2.0); // 心→HP

        // 结算: 播放声波爆响+Ciallo音效
        world.playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                net.minecraft.sound.SoundCategory.PLAYERS, 2.0f, 1.0f);
        for (ServerPlayerEntity p : world.getPlayers()) {
            world.playSound(null, p.getX(), p.getY(), p.getZ(),
                    baby.sv.yepvpfabirc.ModSounds.CIALLO, net.minecraft.sound.SoundCategory.MASTER, 1.0f, 1.0f);
        }

        // 一次性结算伤害(穿透抗性: 用magic)
        target.damage(world, world.getDamageSources().magic(), damage);

        // 击退约10格
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        if (dist2d < 0.1) { dx = 1; dz = 0; dist2d = 1; }
        double knockScale = 2.5 / dist2d;
        target.setVelocity(dx * knockScale, 0.4, dz * knockScale);
        target.velocityDirty = true;
        target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(target));

        tip(target, "§c§l被Ciallo声波结算命中！§r§c承受了 §e" + String.format("%.1f", totalDamageHearts) + "心 §7(距离" + (int)distance + "格)");
        tip(player, "§d§l[声波结算] §e" + target.getGameProfile().name() + " §d承受 §e" + String.format("%.1f", totalDamageHearts) + "心 §7(距离" + (int)distance + "格)");
    }

    // ==================== BOBBY: 罪人(主动) ====================
    // Z: 灵体(旁观者模式30s, 2minCD)
    private static void handleBobbySkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        if (skillSlot != 0) return;
        long currentTick = player.getEntityWorld().getTime();
        // 灵体中不可再次使用
        if (data.getBobbyGhostEndTick() > 0) {
            tip(player, "§c已在灵体状态中！");
            return;
        }
        if (currentTick - data.getLastBobbyGhostTick() < 2400) { // 2分钟CD
            int remaining = (int) ((2400 - (currentTick - data.getLastBobbyGhostTick())) / 20);
            tip(player, "§c灵体冷却中... §e" + remaining + "s");
            return;
        }
        data.setLastBobbyGhostTick(currentTick);
        GameManager.getInstance().startBobbyGhost(player, data, currentTick);
    }

    // ==================== RETOUR: 但丁 ====================
    // Z: 与罪人(Bobby)交换位置(1minCD)
    // V: 切换激光(无CD, 准星方向30格内1心/秒真伤)
    private static void handleRetourSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        if (skillSlot == 0) { // Z键
            GameManager.getInstance().retourSwapWithBobby(player, data);
        } else if (skillSlot == 2) { // V键
            boolean active = !data.isRetourLaserActive();
            data.setRetourLaserActive(active);
            if (active) {
                tip(player, "§c§l激光已开启！§r§c准星对准敌人30格内造成1心/秒真伤");
                GameManager.getInstance().broadcastMessage("§c§l[激光] §r§e" + player.getGameProfile().name() + " §c开启了激光！");
            } else {
                tip(player, "§7激光已关闭");
            }
        }
    }

    // ==================== HELI: 揭开帷幕 ====================
    // Z: 传送到被揭露的玩家身旁(1分钟CD, 循环选择)
    // X: 全视之眼(3分钟CD, 揭露所有玩家1分钟)
    private static void handleHeliSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();
        if (skillSlot == 0) {
            // Z: 传送到被揭露的玩家
            if (currentTick - data.getLastSkillZTime() < HELI_TP_COOLDOWN) {
                long remaining = (HELI_TP_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
                tip(player, "§c技能冷却中... §e" + remaining + "s");
                return;
            }
            // 收集当前仍在揭露中的玩家
            List<UUID> activeRevealed = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : data.getRevealedPlayerExpiry().entrySet()) {
                if (entry.getValue() > currentTick) activeRevealed.add(entry.getKey());
            }
            if (activeRevealed.isEmpty()) {
                tip(player, "§c当前没有被揭露的玩家可传送！");
                return;
            }
            int idx = data.getHeliTpIndex() % activeRevealed.size();
            UUID targetUuid = activeRevealed.get(idx);
            data.setHeliTpIndex(idx + 1);
            ServerPlayerEntity target = player.getEntityWorld().getServer().getPlayerManager().getPlayer(targetUuid);
            if (target == null || !target.isAlive()) {
                tip(player, "§c目标不在线或已死亡！");
                return;
            }
            data.setLastSkillZTime(currentTick);
            ServerWorld world = (ServerWorld) target.getEntityWorld();
            player.teleport(world, target.getX() + 1, target.getY(), target.getZ(), Set.of(), player.getYaw(), player.getPitch(), false);
            tip(player, "§a已传送到 §e" + target.getGameProfile().name() + " §a身旁！");
            GameManager.getInstance().broadcastMessage("§6§l[揭幕] §r§e" + player.getGameProfile().name() + " §6传送到了 §e" + target.getGameProfile().name() + " §6身旁！");
        } else if (skillSlot == 1) {
            // X: 全视之眼 — 揭露所有玩家1分钟
            if (currentTick - data.getLastSkillXTime() < HELI_EYE_COOLDOWN) {
                long remaining = (HELI_EYE_COOLDOWN - (currentTick - data.getLastSkillXTime())) / 20;
                tip(player, "§c技能冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillXTime(currentTick);
            ServerWorld world = player.getEntityWorld();
            long dayTime = world.getTimeOfDay() % 24000;
            boolean isNight = dayTime >= 13000 && dayTime <= 23000;
            int revealedCount = 0;
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.getUuid().equals(player.getUuid())) continue;
                PlayerData pd = GameManager.getInstance().getPlayerData(p.getUuid());
                if (pd == null || !pd.isAlive()) continue;
                if (isFullyInvisible(pd, isNight, currentTick)) continue;
                heliApplyReveal(data, pd, p.getUuid(), currentTick);
                revealedCount++;
            }
            GameManager.getInstance().broadcastMessage("§6§l[全视之眼] §r§e" + player.getGameProfile().name() + " §6开启了全视之眼！揭露了 §e" + revealedCount + " §6名玩家！");
        }
    }

    // 判断玩家是否处于"完全隐身"状态(揭露对其位置暴露无效)
    public static boolean isFullyInvisible(PlayerData pd, boolean isNight, long currentTick) {
        // JIEZU夜晚完全隐身
        if (pd.getRole() == Role.JIEZU && isNight) return true;
        // DASHA水中完全隐身(显形期除外)
        if (pd.getRole() == Role.DASHA && pd.isInWater() && currentTick >= pd.getDashaVisibleUntilTick()) return true;
        return false;
    }

    // 对目标施加揭露(1分钟位置暴露 + 前15秒双倍伤害)
    private static void heliApplyReveal(PlayerData heliData, PlayerData targetData, UUID targetUuid, long currentTick) {
        heliData.getRevealedPlayers().add(targetUuid);
        heliData.getRevealedPlayerExpiry().put(targetUuid, currentTick + 1200); // 1分钟
        heliData.getHeliRevealStartTick().put(targetUuid, currentTick); // 记录开始时间(15s双倍伤害)
        targetData.setRevealUntilTick(currentTick + 1200);
    }

    // Heli攻击揭露（从DamaiAttackMixin调用）
    // 揭露持续1分钟, 持续期间不能再次揭露同一玩家
    public static void heliRevealOnAttack(ServerPlayerEntity heli, ServerPlayerEntity target) {
        PlayerData heliData = GameManager.getInstance().getPlayerData(heli.getUuid());
        if (heliData == null || heliData.getRole() != Role.HELI) return;
        long currentTick = heli.getEntityWorld().getTime();

        // 该玩家仍在揭露中 → 不能重复揭露
        Long expiry = heliData.getRevealedPlayerExpiry().get(target.getUuid());
        if (expiry != null && expiry > currentTick) return;

        // 检查目标是否完全隐身
        PlayerData targetData = GameManager.getInstance().getPlayerData(target.getUuid());
        if (targetData != null) {
            long dayTime = heli.getEntityWorld().getTimeOfDay() % 24000;
            boolean isNight = dayTime >= 13000 && dayTime <= 23000;
            if (isFullyInvisible(targetData, isNight, currentTick)) {
                tip(heli, "§c对方处于完全隐身状态，无法揭露！");
                return;
            }
        }

        heliApplyReveal(heliData, targetData, target.getUuid(), currentTick);
        GameManager.getInstance().broadcastMessage("§6§l[揭幕] §r§e" + heli.getGameProfile().name() + " §6揭露了 §e" + target.getGameProfile().name() + " §6的位置！§c15秒内受到的伤害翻倍！");
    }

    // ==================== MACHA: 钓鱼 ====================
    // Z: 15秒冷却, 随机战利品(不钓人)
    // X: 钓人(每10次出钩获得1次钓人机会, 3秒警告)
    // 10%合金武器(不重复) 10%合金装备(不重复) 5%随机传送 35%随机道具 5%基头四召唤器 33%鱼 1%+命 1%暴毙
    private static void handleMachaSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();

        if (skillSlot == 1) { // X: 钓人
            if (data.getMachaFishPlayerCharges() <= 0) {
                tip(player, "§c没有钓人次数！(每10次出钩获得1次, 当前第" + data.getMachaFishCount() + "次)");
                return;
            }
            if (data.getMachaFishingTarget() != null) {
                tip(player, "§c正在钓人中...");
                return;
            }
            data.useMachaFishPlayerCharge();
            fishPlayer(player, data, currentTick);
            return;
        }

        if (skillSlot != 0) return; // Z: 钓鱼
        if (currentTick - data.getLastSkillZTime() < MACHA_COOLDOWN) {
            long remaining = (MACHA_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
            tip(player, "§c技能冷却中... §e" + remaining + "s");
            return;
        }
        if (data.getMachaFishingTarget() != null) {
            tip(player, "§c正在钓鱼中...");
            return;
        }

        data.setLastSkillZTime(currentTick);
        data.incrementMachaFishCount();
        ServerWorld world = player.getEntityWorld();
        Random rand = new Random();

        // 每10次出钩获得1次钓人机会
        if (data.getMachaFishCount() % 10 == 0) {
            data.addMachaFishPlayerCharge();
            tip(player, "§a§l第" + data.getMachaFishCount() + "次出钩！获得钓人机会！(按X使用, 当前" + data.getMachaFishPlayerCharges() + "次)");
        }

        int roll = rand.nextInt(100);
        if (roll < 1) {
            // 1% 暴毙
            player.kill(world);
            GameManager.getInstance().broadcastMessage("§c§l[钓鱼] §r§e" + player.getGameProfile().name() + " §c钓到了死亡！暴毙！");
        } else if (roll < 2) {
            // 1% +1命
            data.gainLife();
            GameManager.getInstance().broadcastMessage("§a§l[钓鱼] §r§e" + player.getGameProfile().name() + " §a钓到了额外一条命！");
            tip(player, "§a§l钓到了1条命！当前命数: " + data.getLives());
        } else if (roll < 12) {
            // 10% 下界合金武器(不重复)
            machaGiveUniqueWeapon(player, data, rand);
        } else if (roll < 22) {
            // 10% 下界合金装备(不重复)
            machaGiveUniqueArmor(player, data, rand);
        } else if (roll < 27) {
            // 5% 自己随机传送
            net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
            double radius = border.getSize() / 2.0 * 0.8;
            double cx = border.getCenterX();
            double cz = border.getCenterZ();
            int rx = (int) (cx + (rand.nextDouble() - 0.5) * 2 * radius);
            int rz = (int) (cz + (rand.nextDouble() - 0.5) * 2 * radius);
            world.getChunk(rx >> 4, rz >> 4);
            BlockPos safePos = GameManager.getInstance().findSafeLandSpawnPublic(world, rx, rz, rand);
            player.teleport(world, safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch(), false);
            tip(player, "§e钓到了...传送！你被随机传送了！");
        } else if (roll < 62) {
            // 35% 随机道具
            machaGiveRandomItem(player, world, rand);
        } else if (roll < 67) {
            // 5% 基头四召唤器
            machaGiveWardenSummoner(player);
        } else {
            // 33% 鱼
            ItemStack[] fish = {new ItemStack(Items.COD), new ItemStack(Items.SALMON),
                    new ItemStack(Items.TROPICAL_FISH), new ItemStack(Items.PUFFERFISH)};
            player.getInventory().insertStack(fish[rand.nextInt(fish.length)]);
            tip(player, "§7钓到了鱼...");
        }
    }

    // 给不重复的下界合金武器
    private static void machaGiveUniqueWeapon(ServerPlayerEntity player, PlayerData data, Random rand) {
        String[] allWeapons = {"netherite_sword", "netherite_axe"};
        List<String> available = new ArrayList<>();
        for (String w : allWeapons) {
            if (!data.getMachaObtainedWeapons().contains(w)) available.add(w);
        }
        if (available.isEmpty()) {
            // 全部获得过, 给鱼代替
            tip(player, "§7已获得所有下界合金武器，钓到了鱼...");
            player.getInventory().insertStack(new ItemStack(Items.COD));
            return;
        }
        String chosen = available.get(rand.nextInt(available.size()));
        data.getMachaObtainedWeapons().add(chosen);
        ItemStack item = switch (chosen) {
            case "netherite_sword" -> new ItemStack(Items.NETHERITE_SWORD);
            case "netherite_axe" -> new ItemStack(Items.NETHERITE_AXE);
            default -> new ItemStack(Items.NETHERITE_SWORD);
        };
        player.getInventory().insertStack(item);
        tip(player, "§d§l钓到了下界合金武器！" + item.getName().getString());
    }

    // 给不重复的下界合金装备
    private static void machaGiveUniqueArmor(ServerPlayerEntity player, PlayerData data, Random rand) {
        String[] allArmor = {"netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"};
        List<String> available = new ArrayList<>();
        for (String a : allArmor) {
            if (!data.getMachaObtainedArmor().contains(a)) available.add(a);
        }
        if (available.isEmpty()) {
            tip(player, "§7已获得所有下界合金装备，钓到了鱼...");
            player.getInventory().insertStack(new ItemStack(Items.COD));
            return;
        }
        String chosen = available.get(rand.nextInt(available.size()));
        data.getMachaObtainedArmor().add(chosen);
        ItemStack item = switch (chosen) {
            case "netherite_helmet" -> new ItemStack(Items.NETHERITE_HELMET);
            case "netherite_chestplate" -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case "netherite_leggings" -> new ItemStack(Items.NETHERITE_LEGGINGS);
            case "netherite_boots" -> new ItemStack(Items.NETHERITE_BOOTS);
            default -> new ItemStack(Items.NETHERITE_HELMET);
        };
        player.getInventory().insertStack(item);
        tip(player, "§d§l钓到了下界合金装备！" + item.getName().getString());
    }

    // 基头四召唤器: 给物品(使用时由GameManager处理)
    private static void machaGiveWardenSummoner(ServerPlayerEntity player) {
        ItemStack item = new ItemStack(Items.ECHO_SHARD); // 回响碎片作为召唤器物品
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                net.minecraft.text.Text.literal("§4§l基头四召唤器 §7(右键使用)"));
        player.getInventory().insertStack(item);
        tip(player, "§4§l钓到了基头四召唤器！§7右键使用召唤4个监守者(15秒)！");
        GameManager.getInstance().broadcastMessage("§4§l[钓鱼] §r§e" + player.getGameProfile().name() + " §4钓到了基头四召唤器！");
    }

    private static void machaGiveRandomItem(ServerPlayerEntity player, ServerWorld world, Random rand) {
        var registry = world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        int pick = rand.nextInt(13);
        ItemStack item;
        switch (pick) {
            case 0 -> {
                item = new ItemStack(Items.SPLASH_POTION);
                net.minecraft.registry.entry.RegistryEntry<net.minecraft.potion.Potion>[] potionEntries = new net.minecraft.registry.entry.RegistryEntry[]{
                    net.minecraft.potion.Potions.STRONG_STRENGTH, net.minecraft.potion.Potions.STRONG_SWIFTNESS,
                    net.minecraft.potion.Potions.STRONG_REGENERATION, net.minecraft.potion.Potions.LONG_INVISIBILITY,
                    net.minecraft.potion.Potions.STRONG_HEALING
                };
                var chosen = potionEntries[rand.nextInt(potionEntries.length)];
                item.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
                    new net.minecraft.component.type.PotionContentsComponent(chosen));
            }
            case 1 -> item = new ItemStack(Items.ELYTRA);
            case 2 -> item = new ItemStack(Items.FIREWORK_ROCKET, 10);
            case 3 -> item = new ItemStack(Items.ENDER_PEARL, 5);
            case 4 -> item = new ItemStack(Items.TNT, 2);
            case 5 -> {
                item = new ItemStack(Items.MACE);
                var density = registry.getOptional(net.minecraft.enchantment.Enchantments.DENSITY).orElse(null);
                if (density != null) item.addEnchantment(density, 5);
            }
            case 6 -> item = new ItemStack(Items.WIND_CHARGE, 10);
            case 7 -> item = new ItemStack(Items.SHIELD);
            case 8 -> item = new ItemStack(Items.BOW);
            case 9 -> item = new ItemStack(Items.CROSSBOW);
            case 10 -> item = new ItemStack(Items.ARROW, 64);
            case 11 -> item = new ItemStack(Items.LAVA_BUCKET, 3);
            case 12 -> item = new ItemStack(Items.COBWEB, 10);
            default -> item = new ItemStack(Items.COD);
        }
        player.getInventory().insertStack(item);
        tip(player, "§a钓到了 §e" + item.getName().getString() + (item.getCount() > 1 ? " x" + item.getCount() : "") + "§a！");
    }

    private static void fishPlayer(ServerPlayerEntity player, PlayerData data, long currentTick) {
        GameManager gm = GameManager.getInstance();
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (PlayerData pd : gm.getAllPlayerData().values()) {
            if (!pd.isAlive() || pd.getPlayerUuid().equals(player.getUuid())) continue;
            ServerPlayerEntity target = gm.getServer().getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (target != null) candidates.add(target);
        }
        if (candidates.isEmpty()) {
            tip(player, "§c没有可以钓的玩家！");
            return;
        }
        ServerPlayerEntity target = candidates.get(new Random().nextInt(candidates.size()));
        data.setMachaFishingTarget(target.getUuid());
        data.setMachaFishingStartTick(currentTick);
        gm.broadcastMessage("§6§l[钓鱼] §r§e" + player.getGameProfile().name() + " §a正在钓 §e" + target.getGameProfile().name() + "§a！§c3秒后传送！");
        gm.sendTip(target, "§c§l⚠ 你被钓了！3秒后将被传送到 " + player.getGameProfile().name() + " 面前！");
    }

    // ==================== ALLAND: 喷射战士 ====================
    // Z=红(slot0), X=蓝(slot1), V=黄(slot2), C=绿(slot3) 颜料弹发射
    private static void handleAllandSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        // skillSlot: 0=Z, 1=X, 2=V, 3=C → color: 0=红, 1=蓝, 2=绿, 3=黄
        int color = switch (skillSlot) {
            case 0 -> 0; // Z → 红
            case 1 -> 1; // X → 蓝
            case 2 -> 3; // V → 黄
            case 3 -> 2; // C → 绿
            default -> -1;
        };
        if (color < 0) return;

        if (data.getPaintCharges() <= 0) {
            tip(player, "§c没有颜料弹次数！(每15秒+1, 上限4)");
            return;
        }

        String colorName = switch (color) {
            case 0 -> "§c红";
            case 1 -> "§9蓝";
            case 2 -> "§a绿";
            case 3 -> "§e黄";
            default -> "?";
        };

        data.usePaintCharge();

        // 发射颜料弹(雪球, 无重力直线飞行)
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        ItemStack snowballItem = new ItemStack(net.minecraft.item.Items.SNOWBALL);
        net.minecraft.entity.projectile.thrown.SnowballEntity snowball =
            new net.minecraft.entity.projectile.thrown.SnowballEntity(world, player, snowballItem);
        snowball.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 1.5f, 0.0f);
        snowball.setNoGravity(true);
        world.spawnEntity(snowball);

        // 注册颜料弹(用color而非skillSlot)
        GameManager.getInstance().registerPaintProjectile(snowball.getId(), color, player.getUuid());

        tip(player, "§e发射" + colorName + "§e颜料弹！剩余: §a" + data.getPaintCharges() + "/4");
    }

    // ==================== POPCORN: 智械危机 ====================
    // Z: 放置备用躯体(充能制, 开局2次+击杀+1)
    // X: 传送到备用躯体(循环选择, 脉冲晕眩5格5秒, 满血+5临时心)
    // C(slot3): 灵魂出窍(即时进入旁观/返回)
    // V(slot2): 轨道镭射(对自身位置释放,旁观时对附身目标位置释放)
    private static void handlePopcornSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        GameManager gm = GameManager.getInstance();
        // 旁观模式中: Z=下一个目标, X=上一个目标
        if (data.isPopcornSpectating() && (skillSlot == 0 || skillSlot == 1)) {
            int direction = (skillSlot == 0) ? 1 : -1;
            gm.popcornSwitchSpectateTarget(player, data, direction);
            return;
        }
        // 旁观模式中V键: 对附身目标位置释放镭射
        if (data.isPopcornSpectating() && skillSlot == 2) {
            java.util.UUID camTarget = data.getPopcornCameraTarget();
            if (camTarget != null) {
                net.minecraft.server.network.ServerPlayerEntity targetPlayer =
                        gm.getServer().getPlayerManager().getPlayer(camTarget);
                if (targetPlayer != null) {
                    gm.popcornTriggerLaser(player, data, (int) targetPlayer.getX(), (int) targetPlayer.getZ());
                    return;
                }
            }
            tip(player, "§c没有附身目标！");
            return;
        }
        if (skillSlot == 0) { // Z: 放置备用躯体
            if (data.isPopcornSpectating()) {
                tip(player, "§c旁观模式中无法放置躯体！");
                return;
            }
            if (data.getBackupBodyCharges() <= 0) {
                tip(player, "§c没有备用躯体充能！击杀玩家可获得。");
                return;
            }
            data.setBackupBodyCharges(data.getBackupBodyCharges() - 1);
            BlockPos pos = player.getBlockPos();
            data.getBackupBodies().add(pos);
            player.getEntityWorld().setBlockState(pos, Blocks.LODESTONE.getDefaultState());
            tip(player, "§a已放置备用躯体！当前 §e" + data.getBackupBodies().size() + " §a个，剩余充能 §e" + data.getBackupBodyCharges());
            gm.broadcastMessage("§e§l[智械危机] §r§e" + player.getGameProfile().name() + " §a放置了备用躯体！（所有人地图可见）");
        } else if (skillSlot == 1) { // X: 选择并传送到备用躯体(将其消耗)
            if (data.isPopcornSpectating()) {
                tip(player, "§c旁观模式中无法传送！先返回自身！");
                return;
            }
            if (data.getBackupBodies().isEmpty()) {
                tip(player, "§c没有备用躯体可传送！");
                return;
            }
            List<BlockPos> bodies = data.getBackupBodies();
            long currentTick = player.getEntityWorld().getTime();

            // 如果已经进入选择模式
            if (data.getPopcornBodySelectTick() > 0) {
                // 如果距离上次按X时间很短(0.75秒内), 视为切换下一个
                if (currentTick - data.getLastSkillXTime() < 15) {
                    int idx = (data.getPopcornBodyTpIndex() + 1) % bodies.size();
                    data.setPopcornBodyTpIndex(idx);
                    data.setLastSkillXTime(currentTick);
                    BlockPos target = bodies.get(idx);
                    int dist = (int) Math.sqrt(player.squaredDistanceTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5));
                    tip(player, "§e选择躯体 §a" + (idx + 1) + "/" + bodies.size()
                            + " §7(" + target.getX() + "," + target.getY() + "," + target.getZ() + ") §e距离§a" + dist
                            + "格 §7(再次按X切换, 等待1.5秒或按V确认传送)");
                } else {
                    // 确认传送
                    BlockPos target = bodies.get(data.getPopcornBodyTpIndex() % bodies.size());
                    gm.popcornTeleportToBody(player, data, target);
                    data.setPopcornBodySelectTick(0);
                }
            } else {
                // 进入选择模式
                data.setPopcornBodyTpIndex(0);
                data.setPopcornBodySelectTick(currentTick);
                data.setLastSkillXTime(currentTick);
                BlockPos target = bodies.get(0);
                int dist = (int) Math.sqrt(player.squaredDistanceTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5));
                tip(player, "§e开启选择模式 §a1/" + bodies.size()
                        + " §7(" + target.getX() + "," + target.getY() + "," + target.getZ() + ") §e距离§a" + dist
                        + "格 §7(再次按X切换, 等待1.5秒或按V确认传送)");
            }
        } else if (skillSlot == 2) { // V: 轨道镭射(对准心所指位置释放)
            // 如果在X选择模式下按V, 视为确认传送
            if (data.getPopcornBodySelectTick() > 0 && !data.getBackupBodies().isEmpty()) {
                BlockPos target = data.getBackupBodies().get(data.getPopcornBodyTpIndex() % data.getBackupBodies().size());
                gm.popcornTeleportToBody(player, data, target);
                data.setPopcornBodySelectTick(0);
                return;
            }
            // 从视线发射raycast, 命中则取命中点, 未命中则沿视线投射256格取落点
            net.minecraft.util.hit.HitResult hit = player.raycast(256.0, 1.0f, false);
            int tx, tz;
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                net.minecraft.util.math.Vec3d hp = hit.getPos();
                tx = (int) hp.x;
                tz = (int) hp.z;
            } else {
                net.minecraft.util.math.Vec3d eye = player.getEyePos();
                net.minecraft.util.math.Vec3d dir = player.getRotationVector();
                net.minecraft.util.math.Vec3d end = eye.add(dir.multiply(256.0));
                tx = (int) end.x;
                tz = (int) end.z;
            }
            gm.popcornTriggerLaser(player, data, tx, tz);
        } else if (skillSlot == 3) { // C: 灵魂出窍(即时进入/返回)
            gm.popcornSpectateAction(player, data);
        }
    }

    // ==================== SANCHEZ: 诸神黄昏 ====================
    // Z: 恢复头狼全血(30秒冷却,唯一治愈手段), X: 召唤狼(消耗1心+1charge), V: 狩猎模式(2分钟冷却)
    private static void handleSanchezSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();
        GameManager gm = GameManager.getInstance();

        if (skillSlot == 0) { // Z: 恢复头狼
            if (data.isAlphaWolfDead()) {
                long remain = (3600 - (currentTick - data.getAlphaWolfDeathTick())) / 20;
                tip(player, "§c头狼已死亡，等待复活！§e" + Math.max(0, remain) + "s");
                return;
            }
            if (currentTick - data.getLastSkillZTime() < SANCHEZ_HEAL_COOLDOWN) {
                long remaining = (SANCHEZ_HEAL_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
                tip(player, "§c冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillZTime(currentTick);
            data.setAlphaWolfHealth(40.0);
            // 同步到实体
            ServerWorld world = player.getEntityWorld();
            if (data.getAlphaWolfEntityUuid() != null) {
                net.minecraft.entity.Entity e = world.getEntity(data.getAlphaWolfEntityUuid());
                if (e instanceof net.minecraft.entity.passive.WolfEntity wolf) {
                    wolf.setHealth(40.0f);
                }
            }
            tip(player, "§a头狼已恢复全血！(20心)");
        } else if (skillSlot == 1) { // X: 召唤狼
            if (data.isAlphaWolfDead()) {
                tip(player, "§c头狼已死亡，无法召唤狼群！");
                return;
            }
            if (data.getWolfKillCharges() <= 0) {
                tip(player, "§c没有召唤次数！击杀生物可获得。");
                return;
            }
            if (player.getHealth() <= 2.0f) {
                tip(player, "§c血量不足1心，无法召唤！");
                return;
            }
            data.useWolfKillCharge();
            ServerWorld world = player.getEntityWorld();
            // 自己受1心伤害
            player.damage(world, world.getDamageSources().magic(), 2.0f);
            // 召唤真实狼实体(1心HP, 3心伤害)
            gm.spawnPackWolf(player, data, world);
            int wolfCount = data.getPackWolfUuids().size();
            tip(player, "§a已召唤狼！当前狼群: §e" + wolfCount + "只 §7(剩余次数: " + data.getWolfKillCharges() + ")");
        } else if (skillSlot == 2) { // V: 狩猎模式
            if (currentTick - data.getLastSkillVTime() < SANCHEZ_HUNT_COOLDOWN) {
                long remaining = (SANCHEZ_HUNT_COOLDOWN - (currentTick - data.getLastSkillVTime())) / 20;
                tip(player, "§c冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillVTime(currentTick);
            data.setSanchezHuntEndTick(currentTick + 200); // 10秒
            // 立即给效果
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, false, false, true));
            gm.broadcastMessage("§6§l[狩猎] §r§e" + player.getGameProfile().name() + " §6开启了狩猎模式！全军无敌10秒！");
        } else if (skillSlot == 3) { // C: 召集狼群(传送所有狼到自己位置, 30秒CD)
            if (currentTick - data.getLastSkillCTime() < 600) { // 30秒=600tick
                long remaining = (600 - (currentTick - data.getLastSkillCTime())) / 20;
                tip(player, "§c召集冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillCTime(currentTick);
            ServerWorld world = player.getEntityWorld();
            int teleported = 0;
            // 传送头狼
            if (data.getAlphaWolfEntityUuid() != null) {
                net.minecraft.entity.Entity e = world.getEntity(data.getAlphaWolfEntityUuid());
                if (e != null && e.isAlive()) {
                    e.teleport(world, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 0, 0, false);
                    teleported++;
                }
            }
            // 传送狼群
            for (UUID wolfId : data.getPackWolfUuids()) {
                net.minecraft.entity.Entity e = world.getEntity(wolfId);
                if (e != null && e.isAlive()) {
                    e.teleport(world, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 0, 0, false);
                    teleported++;
                }
            }
            tip(player, "§a已召集 §e" + teleported + " §a只狼到身边！");
        }
    }

    // ==================== SHUBING: 画中世界 ====================
    // 通道在天空中生成, 每条通道偏移避免重叠
    // 基础坐标 (0, 300, corridorIndex*10), 在世界边界(半径1000)内
    public static final int CORRIDOR_BASE_Y = 300;
    public static final int CORRIDOR_LEN = 150;
    private static int nextCorridorZOffset = 0; // 每条通道Z偏移10格避免重叠

    // Z: 放置画, X: 切换垂直/水平模式, V: 画中世界传送随机玩家(2min CD)
    private static void handleShubingSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        // X键: 切换画的放置模式
        if (skillSlot == 1) {
            data.togglePaintingMode();
            String mode = data.isPaintingModeHorizontal() ? "§b水平模式§r(水平通道)" : "§d垂直模式§r(垂直通道)";
            player.sendMessage(Text.literal("§e§l[画模式切换] §r当前: " + mode), false);
            return;
        }
        // V键: 画中世界传送随机玩家到身旁(2min CD, 必须在画中世界内)
        if (skillSlot == 2) {
            handleShubingSummon(player, data);
            return;
        }
        if (skillSlot != 0) return;
        if (data.getPaintingCount() <= 0) {
            tip(player, "§c没有画可以放置！每1分钟获得1幅。");
            return;
        }
        // 限制场上最多2幅画
        if (data.getActivePaintingCount() >= 2) {
            tip(player, "§c场上已有2幅画！拆除旧画后才能放置新的。");
            return;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // 跟随准心放置: raycast玩家视线方向, 最远5格
        net.minecraft.util.hit.HitResult hitResult = player.raycast(5.0, 0, false);
        BlockPos pos;
        if (hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            net.minecraft.util.hit.BlockHitResult blockHit = (net.minecraft.util.hit.BlockHitResult) hitResult;
            // 放在命中方块的相邻面(外侧)
            pos = blockHit.getBlockPos().offset(blockHit.getSide());
        } else {
            // 没看到方块: 放在视线前方3格位置
            net.minecraft.util.math.Vec3d eyePos = player.getEyePos();
            net.minecraft.util.math.Vec3d lookDir = player.getRotationVector();
            pos = BlockPos.ofFloored(eyePos.add(lookDir.multiply(3.0)));
        }

        // 检查是否在世界边界内
        net.minecraft.world.border.WorldBorder border = world.getWorldBorder();
        if (!border.contains(pos)) {
            tip(player, "§c无法在毒圈外放置画！");
            return;
        }

        // 使用当前模式(X键切换): 水平=水平通道, 垂直=垂直通道
        boolean horizontal = data.isPaintingModeHorizontal();
        String modeStr = horizontal ? "§b水平" : "§d垂直";
        player.sendMessage(Text.literal("§e[画] §r当前模式: " + modeStr + "§r | §7按X键切换模式"), false);

        // 统一用紫水晶方块作为画的标记
        world.setBlockState(pos, Blocks.AMETHYST_BLOCK.getDefaultState());
        UUID paintingEntityUuid = UUID.nameUUIDFromBytes(("shubing_" + pos.toShortString()).getBytes());

        data.usePainting();
        PlayerData.ShubingPainting painting = new PlayerData.ShubingPainting(pos, paintingEntityUuid, horizontal);
        data.getPlacedPaintings().add(painting);
        int paintIdx = data.getPlacedPaintings().size() - 1;

        // 生成悬浮文字
        String ownerName = player.getGameProfile().name();
        Text hologramText = buildHologramText(painting, data, ownerName);
        painting.hologramTag = spawnHologramTag(world, pos, hologramText);

        // 广播画位置
        String dir = horizontal ? "水平" : "垂直";
        GameManager.getInstance().broadcastMessage(
                "§e§l[画中世界] §r§e" + ownerName +
                " §a放置了一幅" + dir + "画于 §e(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");

        // 检查是否有未配对的画可以配对
        int unpairedIdx = -1;
        for (int i = 0; i < data.getPlacedPaintings().size() - 1; i++) {
            if (data.getPlacedPaintings().get(i).corridorIndex == -1) {
                unpairedIdx = i;
                break;
            }
        }

        if (unpairedIdx >= 0) {
            // 配对! 第二幅画的方向决定通道走向
            // 垂直放(挂墙上)→垂直通道(攀爬), 水平放(地面)→水平通道(行走)
            boolean corridorVertical = !horizontal;

            // 生成通道
            int corridorIdx = data.getCorridors().size();
            int zOffset = nextCorridorZOffset;
            nextCorridorZOffset += 10;
            // 通道生成在世界边界中心上空, 避免被毒圈吞噬
            net.minecraft.world.border.WorldBorder wb = world.getWorldBorder();
            int cx = (int) wb.getCenterX();
            int cz = (int) wb.getCenterZ();
            BlockPos origin = new BlockPos(cx, CORRIDOR_BASE_Y, cz + zOffset);

            PlayerData.ShubingCorridor corridor = new PlayerData.ShubingCorridor(
                    origin, corridorVertical, unpairedIdx, paintIdx);
            data.getCorridors().add(corridor);

            // 更新两幅画的配对信息
            PlayerData.ShubingPainting firstPainting = data.getPlacedPaintings().get(unpairedIdx);
            firstPainting.corridorIndex = corridorIdx;
            firstPainting.corridorEnd = 0;
            painting.corridorIndex = corridorIdx;
            painting.corridorEnd = 1;

            // 在天空中建造通道
            buildCorridor(world, origin, corridorVertical);

            // 更新两端悬浮文字(显示链接目标)
            updateHologramByTag(world, firstPainting.hologramTag,
                    buildHologramText(firstPainting, data, ownerName));
            updateHologramByTag(world, painting.hologramTag,
                    buildHologramText(painting, data, ownerName));

            player.sendMessage(Text.literal("§a§l两幅画已连接！画中世界通道已生成！" +
                    (corridorVertical ? "(垂直通道)" : "(水平通道)")), true);
            GameManager.getInstance().broadcastMessage(
                    "§e§l[画中世界] §r§a" + ownerName +
                    " 的两幅画已连接！触碰画即可进入通道！");
        } else {
            tip(player, "§a画已放置！放下第二幅画来连接通道。");
        }
    }

    // 建造通道: 水平通道沿X轴, 垂直通道沿Y轴
    // 墙壁用石砖, 每3格嵌入海晶灯照明, 两端用紫水晶方块
    private static void buildCorridor(ServerWorld world, BlockPos origin, boolean vertical) {
        if (vertical) {
            // 垂直通道: 5x5外壳, 3x3空气内部, Y方向延伸50格, 无梯子(自由落体)
            // 先清空整个区域(包括顶底2层额外封口), 确保完全密闭
            for (int y = -2; y <= CORRIDOR_LEN + 1; y++) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos bp = origin.add(x, y, z);
                        boolean isInner = (x >= -1 && x <= 1 && z >= -1 && z <= 1);
                        boolean inCorridorRange = (y >= 0 && y < CORRIDOR_LEN);
                        if (inCorridorRange && isInner) {
                            // 内部3x3: 空气
                            world.setBlockState(bp, Blocks.AIR.getDefaultState());
                        } else if (inCorridorRange) {
                            // 外壳: 石砖墙 + 每3格海晶灯
                            if (y % 3 == 0 && x == 2 && z == 0) {
                                world.setBlockState(bp, Blocks.SEA_LANTERN.getDefaultState());
                            } else {
                                world.setBlockState(bp, Blocks.STONE_BRICKS.getDefaultState());
                            }
                        } else {
                            // 顶底封口层(y<0或y>=CORRIDOR_LEN): 全部石砖封死
                            world.setBlockState(bp, Blocks.STONE_BRICKS.getDefaultState());
                        }
                    }
                }
            }
            // 顶底封口标记层: 紫水晶方块(覆盖完整5x5, 在最外层)
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.setBlockState(origin.add(x, -2, z), Blocks.AMETHYST_BLOCK.getDefaultState());
                    world.setBlockState(origin.add(x, CORRIDOR_LEN + 1, z), Blocks.AMETHYST_BLOCK.getDefaultState());
                }
            }
        } else {
            // 水平通道: 3宽(Z) x 4高(Y=0地板,1-2空气,3天花板) x 50长(X)
            for (int x = 0; x < CORRIDOR_LEN; x++) {
                for (int y = 0; y <= 3; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos bp = origin.add(x, y, z);
                        boolean isFloor = (y == 0);
                        boolean isCeiling = (y == 3);
                        boolean isSideWall = (z == -1 || z == 1);
                        if (isFloor) {
                            world.setBlockState(bp, Blocks.DEEPSLATE_BRICKS.getDefaultState());
                        } else if (isCeiling) {
                            if (x % 3 == 0 && z == 0) {
                                world.setBlockState(bp, Blocks.SEA_LANTERN.getDefaultState());
                            } else {
                                world.setBlockState(bp, Blocks.STONE_BRICKS.getDefaultState());
                            }
                        } else if (isSideWall) {
                            world.setBlockState(bp, Blocks.STONE_BRICKS.getDefaultState());
                        } else {
                            world.setBlockState(bp, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
            // 两端封口: 紫水晶方块
            for (int y = 0; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    world.setBlockState(origin.add(-1, y, z), Blocks.AMETHYST_BLOCK.getDefaultState());
                    world.setBlockState(origin.add(CORRIDOR_LEN, y, z), Blocks.AMETHYST_BLOCK.getDefaultState());
                }
            }
        }
    }

    // ===== 悬浮文字(TextDisplayEntity, 反射设置FIXED+正反两面) =====
    private static long hologramCounter = 0;
    @SuppressWarnings("unchecked")
    private static net.minecraft.entity.data.TrackedData<Text> TEXT_TRACKED = null;
    @SuppressWarnings("unchecked")
    private static net.minecraft.entity.data.TrackedData<Byte> BILLBOARD_TRACKED = null;
    @SuppressWarnings("unchecked")
    private static net.minecraft.entity.data.TrackedData<Integer> BACKGROUND_TRACKED = null;

    private static void initTrackedFields() {
        if (TEXT_TRACKED != null) return;
        try {
            for (java.lang.reflect.Field f : DisplayEntity.TextDisplayEntity.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && f.getType() == net.minecraft.entity.data.TrackedData.class) {
                    Object val = f.get(null);
                    if (val != null) {
                        // 通过测试set来区分TEXT vs BACKGROUND等字段
                        String fname = f.getName();
                        if (TEXT_TRACKED == null && fname.toLowerCase().contains("text")) {
                            TEXT_TRACKED = (net.minecraft.entity.data.TrackedData<Text>) val;
                        } else if (BACKGROUND_TRACKED == null && fname.toLowerCase().contains("background")) {
                            BACKGROUND_TRACKED = (net.minecraft.entity.data.TrackedData<Integer>) val;
                        }
                    }
                }
            }
            // TEXT字段可能不叫text,按声明顺序:第一个是TEXT,第二个是LINE_WIDTH,第三个是BACKGROUND,...
            if (TEXT_TRACKED == null) {
                java.lang.reflect.Field[] fields = DisplayEntity.TextDisplayEntity.class.getDeclaredFields();
                List<java.lang.reflect.Field> statics = new ArrayList<>();
                for (java.lang.reflect.Field f : fields) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                            && f.getType() == net.minecraft.entity.data.TrackedData.class) {
                        f.setAccessible(true);
                        statics.add(f);
                    }
                }
                if (statics.size() >= 1) TEXT_TRACKED = (net.minecraft.entity.data.TrackedData<Text>) statics.get(0).get(null);
                if (statics.size() >= 3) BACKGROUND_TRACKED = (net.minecraft.entity.data.TrackedData<Integer>) statics.get(2).get(null);
            }
            // BILLBOARD在DisplayEntity父类 — 通过泛型类型TrackedData<Byte>定位
            for (java.lang.reflect.Field f : DisplayEntity.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() != net.minecraft.entity.data.TrackedData.class) continue;
                java.lang.reflect.Type gt = f.getGenericType();
                if (gt instanceof java.lang.reflect.ParameterizedType pt) {
                    java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                    if (args.length == 1 && args[0] == Byte.class) {
                        f.setAccessible(true);
                        BILLBOARD_TRACKED = (net.minecraft.entity.data.TrackedData<Byte>) f.get(null);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static DisplayEntity.TextDisplayEntity createTextDisplay(ServerWorld world, double x, double y, double z, float yaw, Text text) {
        initTrackedFields();
        DisplayEntity.TextDisplayEntity display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        display.setPosition(x, y, z);
        display.setYaw(yaw);
        try {
            if (TEXT_TRACKED != null) display.getDataTracker().set(TEXT_TRACKED, text);
            if (BILLBOARD_TRACKED != null) display.getDataTracker().set(BILLBOARD_TRACKED, (byte) 0); // 0=FIXED
            if (BACKGROUND_TRACKED != null) display.getDataTracker().set(BACKGROUND_TRACKED, 0x40000000);
        } catch (Exception ignored) {}
        return display;
    }

    private static String spawnHologramTag(ServerWorld world, BlockPos paintingPos, Text text) {
        String tag = "shubing_holo_" + (hologramCounter++);
        double x = paintingPos.getX() + 0.5;
        double y = paintingPos.getY() + 1.5;
        double z = paintingPos.getZ() + 0.5;
        // 正面(yaw=0)
        DisplayEntity.TextDisplayEntity front = createTextDisplay(world, x, y, z, 0f, text);
        front.addCommandTag(tag);
        world.spawnEntity(front);
        // 背面(yaw=180)
        DisplayEntity.TextDisplayEntity back = createTextDisplay(world, x, y, z, 180f, text);
        back.addCommandTag(tag);
        world.spawnEntity(back);
        return tag;
    }

    private static void updateHologramByTag(ServerWorld world, String tag, Text text) {
        if (tag == null) return;
        initTrackedFields();
        for (var entity : world.iterateEntities()) {
            if (entity.getCommandTags().contains(tag) && entity instanceof DisplayEntity.TextDisplayEntity display) {
                if (TEXT_TRACKED != null) {
                    try { display.getDataTracker().set(TEXT_TRACKED, text); } catch (Exception ignored) {}
                }
            }
        }
    }

    private static void removeHologramByTag(ServerWorld world, String tag) {
        if (tag == null) return;
        List<net.minecraft.entity.Entity> toRemove = new ArrayList<>();
        for (var entity : world.iterateEntities()) {
            if (entity.getCommandTags().contains(tag)) toRemove.add(entity);
        }
        toRemove.forEach(net.minecraft.entity.Entity::discard);
    }

    // 构建MC Text组件(带颜色)
    private static Text buildHologramText(PlayerData.ShubingPainting painting, PlayerData data, String ownerName) {
        String mode = painting.horizontal ? "水平画" : "垂直画";
        net.minecraft.util.Formatting modeColor = painting.horizontal ? net.minecraft.util.Formatting.AQUA : net.minecraft.util.Formatting.LIGHT_PURPLE;
        if (painting.corridorIndex < 0) {
            return Text.literal(ownerName + "的画 ").formatted(net.minecraft.util.Formatting.GOLD)
                    .append(Text.literal("[" + mode + "]").formatted(modeColor))
                    .append(Text.literal(" 未配对").formatted(net.minecraft.util.Formatting.GRAY));
        }
        PlayerData.ShubingCorridor corridor = data.getCorridors().get(painting.corridorIndex);
        int otherEnd = (painting.corridorEnd == 0) ? corridor.painting1Index : corridor.painting0Index;
        if (otherEnd >= 0 && otherEnd < data.getPlacedPaintings().size()) {
            BlockPos otherPos = data.getPlacedPaintings().get(otherEnd).pos;
            String type = corridor.vertical ? "垂直通道" : "水平通道";
            net.minecraft.util.Formatting typeColor = corridor.vertical ? net.minecraft.util.Formatting.LIGHT_PURPLE : net.minecraft.util.Formatting.AQUA;
            return Text.literal(ownerName + "的画 ").formatted(net.minecraft.util.Formatting.GOLD)
                    .append(Text.literal("[" + mode + "] ").formatted(modeColor))
                    .append(Text.literal(type + " -> ").formatted(typeColor))
                    .append(Text.literal("(" + otherPos.getX() + ", " + otherPos.getY() + ", " + otherPos.getZ() + ")").formatted(net.minecraft.util.Formatting.YELLOW));
        }
        return Text.literal(ownerName + "的画 ").formatted(net.minecraft.util.Formatting.GOLD)
                .append(Text.literal("[" + mode + "]").formatted(modeColor));
    }

    // 获取通道某端的传送坐标
    // end: 0=入口端, 1=出口端
    public static double[] getCorridorTeleportPos(PlayerData.ShubingCorridor corridor, int end) {
        BlockPos o = corridor.origin;
        if (corridor.vertical) {
            // 垂直通道: end0(画1)=底部, end1(画2)=顶部(从顶部落下)
            if (end == 0) {
                return new double[]{o.getX() + 0.5, o.getY() + 1.0, o.getZ() + 0.5};
            } else {
                int ty = o.getY() + CORRIDOR_LEN - 2;
                return new double[]{o.getX() + 0.5, ty, o.getZ() + 0.5};
            }
        } else {
            // 水平: end0=x+1, end1=x+LEN-2
            int tx = (end == 0) ? o.getX() + 1 : o.getX() + CORRIDOR_LEN - 2;
            return new double[]{tx + 0.5, o.getY() + 1.0, o.getZ() + 0.5};
        }
    }

    // 判断玩家在通道中到达了哪一端 (0=入口端, 1=出口端, -1=中间)
    public static int getCorridorEndReached(PlayerData.ShubingCorridor corridor, ServerPlayerEntity player) {
        BlockPos o = corridor.origin;
        if (corridor.vertical) {
            // 垂直通道: 底部=end0, 顶部=end1
            double relY = player.getY() - o.getY();
            if (relY <= 1.0) return 0; // 到达底部
            if (relY >= CORRIDOR_LEN - 2.0) return 1; // 到达顶部
        } else {
            double relX = player.getX() - o.getX();
            if (relX <= 1.0) return 0;
            if (relX >= CORRIDOR_LEN - 2.0) return 1;
        }
        return -1;
    }

    // 玩家触碰画 → 进入通道 (由Mixin调用)
    public static void onPlayerTouchPainting(ServerPlayerEntity player, UUID paintingEntityUuid) {
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;
        PlayerData pd = gm.getPlayerData(player.getUuid());
        if (pd == null || !pd.isAlive()) return;

        // 找到这幅画属于哪个SHUBING玩家
        for (PlayerData shubingData : gm.getAllPlayerData().values()) {
            if (shubingData.getRole() != Role.SHUBING) continue;
            int paintIdx = shubingData.findPaintingByEntityUuid(paintingEntityUuid);
            if (paintIdx < 0) continue;

            PlayerData.ShubingPainting painting = shubingData.getPlacedPaintings().get(paintIdx);
            if (painting.corridorIndex < 0) {
                tip(player, "§e这幅画还没有连接通道。");
                return;
            }

            // 已经在某个通道内，不能再进
            if (shubingData.getCorridorPlayers().containsKey(player.getUuid())) {
                return;
            }

            PlayerData.ShubingCorridor corridor = shubingData.getCorridors().get(painting.corridorIndex);
            // 如果这端被封了，不能进入
            if (painting.corridorEnd == 0 && corridor.end0Sealed) {
                tip(player, "§c这一端的出口已被封闭！");
                return;
            }
            if (painting.corridorEnd == 1 && corridor.end1Sealed) {
                tip(player, "§c这一端的出口已被封闭！");
                return;
            }

            // 垂直通道: 画1(end0)进入→直接弹回, 只有画2(end1)可以从顶部落下
            if (corridor.vertical && painting.corridorEnd == 0) {
                tip(player, "§c这一端无法进入垂直通道！请从另一幅画进入！");
                return;
            }

            // 传送进通道对应端
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            double[] tp = getCorridorTeleportPos(corridor, painting.corridorEnd);
            player.teleport(world, tp[0], tp[1], tp[2], java.util.Set.of(), 0, 0, false);
            player.fallDistance = 0; // 进入时重置摔落距离

            long currentTick = world.getTime();
            shubingData.getCorridorPlayers().put(player.getUuid(),
                    new PlayerData.CorridorEntry(painting.corridorIndex, currentTick, painting.corridorEnd));

            // 20秒摔落伤害免疫
            pd.setCorridorFallImmuneUntilTick(currentTick + 400);

            player.sendMessage(Text.literal("§e§l你进入了画中世界！§c§l10秒内必须到达另一端！"), false);
            gm.broadcastMessage("§e" + player.getGameProfile().name() + " §a进入了画中世界通道！");
            return;
        }
    }

    // 玩家触碰标记方块(水平画) → 同onPlayerTouchPainting
    public static void onPlayerTouchPaintingBlock(ServerPlayerEntity player, BlockPos pos) {
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        for (PlayerData shubingData : gm.getAllPlayerData().values()) {
            if (shubingData.getRole() != Role.SHUBING) continue;
            int paintIdx = shubingData.findPaintingByPos(pos);
            if (paintIdx < 0) continue;

            PlayerData.ShubingPainting painting = shubingData.getPlacedPaintings().get(paintIdx);
            // 用entityUuid调用统一入口
            onPlayerTouchPainting(player, painting.entityUuid);
            return;
        }
    }

    // 画被破坏 (PaintingEntity死亡 或 标记方块被拆)
    public static void onShubingPaintingBroken(ServerWorld world, UUID paintingEntityUuid) {
        GameManager gm = GameManager.getInstance();
        for (PlayerData shubingData : gm.getAllPlayerData().values()) {
            if (shubingData.getRole() != Role.SHUBING) continue;
            int paintIdx = shubingData.findPaintingByEntityUuid(paintingEntityUuid);
            if (paintIdx < 0) continue;

            PlayerData.ShubingPainting painting = shubingData.getPlacedPaintings().get(paintIdx);

            // 如果画有所属通道，封闭对应端
            if (painting.corridorIndex >= 0) {
                PlayerData.ShubingCorridor corridor = shubingData.getCorridors().get(painting.corridorIndex);
                if (painting.corridorEnd == 0) {
                    corridor.end0Sealed = true;
                    sealCorridorEnd(world, corridor, 0);
                } else {
                    corridor.end1Sealed = true;
                    sealCorridorEnd(world, corridor, 1);
                }

                // 检查是否两端都封了→摧毁通道
                if (corridor.end0Sealed && corridor.end1Sealed) {
                    destroyCorridor(world, shubingData, painting.corridorIndex);
                }

                gm.broadcastMessage("§e§l[画中世界] §c一幅画被拆除！对应出口已关闭！");
            }

            // 移除悬浮文字
            removeHologramByTag(world, painting.hologramTag);
            painting.hologramTag = null;

            // 移除画数据(注意: 不能改变list索引因为corridor引用了索引)
            // 标记为已删除(pos=null)
            painting.pos = null;
            painting.corridorIndex = -2; // -2=已删除
            return;
        }
    }

    // 按位置触发画破坏(水平画用)
    public static void onShubingPaintingBlockBroken(ServerWorld world, BlockPos pos) {
        GameManager gm = GameManager.getInstance();
        for (PlayerData shubingData : gm.getAllPlayerData().values()) {
            if (shubingData.getRole() != Role.SHUBING) continue;
            int paintIdx = shubingData.findPaintingByPos(pos);
            if (paintIdx < 0) continue;

            PlayerData.ShubingPainting painting = shubingData.getPlacedPaintings().get(paintIdx);
            onShubingPaintingBroken(world, painting.entityUuid);
            return;
        }
    }

    // 封闭通道某一端
    private static void sealCorridorEnd(ServerWorld world, PlayerData.ShubingCorridor corridor, int end) {
        BlockPos o = corridor.origin;
        if (corridor.vertical) {
            int sealY = (end == 0) ? 0 : CORRIDOR_LEN - 1;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.setBlockState(o.add(x, sealY, z), Blocks.STONE_BRICKS.getDefaultState());
                }
            }
        } else {
            int sealX = (end == 0) ? 0 : CORRIDOR_LEN - 1;
            for (int y = 0; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    world.setBlockState(o.add(sealX, y, z), Blocks.STONE_BRICKS.getDefaultState());
                }
            }
        }
    }

    // 摧毁整条通道, 踢出所有玩家
    public static void destroyCorridor(ServerWorld world, PlayerData shubingData, int corridorIdx) {
        PlayerData.ShubingCorridor corridor = shubingData.getCorridors().get(corridorIdx);
        BlockPos o = corridor.origin;

        // 踢出通道内玩家
        for (Map.Entry<UUID, PlayerData.CorridorEntry> entry :
                new HashMap<>(shubingData.getCorridorPlayers()).entrySet()) {
            if (entry.getValue().corridorIndex == corridorIdx) {
                ServerPlayerEntity p = GameManager.getInstance().getServer().getPlayerManager().getPlayer(entry.getKey());
                if (p != null) {
                    p.teleport(world, 0.5, 100, 0.5, java.util.Set.of(), 0, 0, false);
                    tip(p, "§c画中世界崩塌！你被传回了现实！");
                }
                shubingData.getCorridorPlayers().remove(entry.getKey());
            }
        }

        // 清除方块
        if (corridor.vertical) {
            for (int y = -1; y <= CORRIDOR_LEN; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        world.setBlockState(o.add(x, y, z), Blocks.AIR.getDefaultState());
                    }
                }
            }
        } else {
            for (int x = -1; x <= CORRIDOR_LEN; x++) {
                for (int y = 0; y <= 3; y++) {
                    for (int z = -1; z <= 1; z++) {
                        world.setBlockState(o.add(x, y, z), Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    // V键: 画中世界传送随机玩家到身旁(2min CD, 必须在画中世界内)
    private static void handleShubingSummon(ServerPlayerEntity player, PlayerData data) {
        GameManager gm = GameManager.getInstance();
        long currentTick = player.getEntityWorld().getTime();

        // 检查CD(2分钟=2400tick)
        if (data.getLastShubingSummonTick() > 0 && currentTick - data.getLastShubingSummonTick() < 2400) {
            int secLeft = (int)((2400 - (currentTick - data.getLastShubingSummonTick())) / 20);
            tip(player, "§c画中世界传送冷却中! §e" + secLeft + "秒");
            return;
        }

        // 必须在画中世界内(即在某个通道内)
        boolean inCorridor = false;
        for (PlayerData shubingData : gm.getAllPlayerData().values()) {
            if (shubingData.getRole() != Role.SHUBING) continue;
            if (shubingData.getCorridorPlayers().containsKey(player.getUuid())) {
                inCorridor = true;
                break;
            }
        }
        if (!inCorridor) {
            tip(player, "§c必须在画中世界内才能使用此技能！");
            return;
        }

        // 随机选一个存活的非SHUBING玩家
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (PlayerData pd : gm.getAllPlayerData().values()) {
            if (!pd.isAlive()) continue;
            if (pd.getPlayerUuid().equals(player.getUuid())) continue;
            ServerPlayerEntity target = gm.getServer().getPlayerManager().getPlayer(pd.getPlayerUuid());
            if (target != null && !target.isSpectator()) {
                // 不传送已在通道内的玩家
                boolean alreadyInCorridor = false;
                for (PlayerData sd : gm.getAllPlayerData().values()) {
                    if (sd.getRole() != Role.SHUBING) continue;
                    if (sd.getCorridorPlayers().containsKey(target.getUuid())) {
                        alreadyInCorridor = true;
                        break;
                    }
                }
                if (!alreadyInCorridor) candidates.add(target);
            }
        }

        if (candidates.isEmpty()) {
            tip(player, "§c没有可传送的玩家！");
            return;
        }

        ServerPlayerEntity target = candidates.get(new java.util.Random().nextInt(candidates.size()));
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        target.teleport(world, player.getX() + 1.0, player.getY(), player.getZ(),
                java.util.Set.of(), target.getYaw(), target.getPitch(), false);

        data.setLastShubingSummonTick(currentTick);

        // 将目标也注册为通道内玩家(找到SHUBING自己所在的通道)
        for (PlayerData shubingData : gm.getAllPlayerData().values()) {
            if (shubingData.getRole() != Role.SHUBING) continue;
            PlayerData.CorridorEntry myEntry = shubingData.getCorridorPlayers().get(player.getUuid());
            if (myEntry != null) {
                shubingData.getCorridorPlayers().put(target.getUuid(),
                        new PlayerData.CorridorEntry(myEntry.corridorIndex, currentTick, myEntry.enteredFromEnd));
                break;
            }
        }

        target.sendMessage(Text.literal("§c§l你被拉入了画中世界！§e10秒内到达出口否则死亡！"), false);
        gm.broadcastMessage("§e§l[画中世界] §r§c" + target.getGameProfile().name() + " §e被传送进了画中世界！");
        tip(player, "§a已将 §e" + target.getGameProfile().name() + " §a传送到画中世界！");
    }

    // ==================== JANE: 赏金猎人 ====================
    // Z: 切换暗黑视域(失明+看到所有人边框)
    // X: 打开商店UI(客户端Screen)
    private static void handleJaneSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        GameManager gm = GameManager.getInstance();

        if (skillSlot == 0) { // Z: 切换暗黑视域
            data.setDarkVisionActive(!data.isDarkVisionActive());
            if (data.isDarkVisionActive()) {
                tip(player, "§c§l暗黑视域 §a已开启！§7(失明+看到所有人边框)");
                gm.broadcastMessage("§6§l[赏金] §r§e" + player.getGameProfile().name() + " §7切换到了暗黑视域！");
            } else {
                player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
                tip(player, "§a常态视域 §a已开启！");
            }
        } else if (skillSlot == 1) { // X: 打开商店UI
            baby.sv.yepvpfabirc.network.NetworkHandler.sendJaneShopData(player, data);
        }
    }

    // ==================== YOUZHA: 油炸意面 ====================
    // Z: 噬元兽(2minCD) — 指针处召唤猫, 1秒吸人, 异次元15秒, 出来扣血扣上限
    // X: 大吃特吃(30sCD) — 消耗5饱食度, 随机buff
    private static void handleYouzhaSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();

        if (skillSlot == 0) { // Z: 噬元兽
            if (currentTick - data.getLastYouzhaUltimateTick() < YOUZHA_ULTIMATE_COOLDOWN) {
                long remaining = (YOUZHA_ULTIMATE_COOLDOWN - (currentTick - data.getLastYouzhaUltimateTick())) / 20;
                tip(player, "§c噬元兽冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastYouzhaUltimateTick(currentTick);

            // Raycast找指针位置(最大50格)
            net.minecraft.util.hit.HitResult hit = player.raycast(50.0, 1.0f, false);
            net.minecraft.util.math.Vec3d spawnPos;
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                BlockPos bp = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
                spawnPos = new net.minecraft.util.math.Vec3d(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5);
            } else {
                spawnPos = hit.getPos();
            }

            ServerWorld world = player.getEntityWorld();
            // 召唤猫
            net.minecraft.entity.passive.CatEntity cat = net.minecraft.entity.EntityType.CAT.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (cat == null) return;
            cat.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0, 0);
            cat.setNoGravity(false);
            cat.setAiDisabled(true);
            cat.setInvulnerable(true);
            cat.addCommandTag("youzha_cat_" + player.getUuid());
            cat.addCommandTag("youzha_cat_absorb_" + (currentTick + 20)); // 1秒后吸人
            world.spawnEntity(cat);

            GameManager.getInstance().broadcastMessage("§6§l[噬元兽] §r§e" + player.getGameProfile().name() + " §6召唤了噬元兽！1秒后吞噬周围的人！");
            tip(player, "§6噬元兽已召唤！1秒后吞噬5格内的人！");

        } else if (skillSlot == 1) { // X: 大吃特吃(无CD, 只需饱食度)
            // 检查饱食度
            int foodLevel = player.getHungerManager().getFoodLevel();
            if (foodLevel < 5) {
                tip(player, "§c饱食度不足！需要5点饱食度(当前" + foodLevel + ")");
                return;
            }
            player.getHungerManager().setFoodLevel(foodLevel - 5);

            // 随机buff(10秒)
            Random rand = new Random();
            int pick = rand.nextInt(6);
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect;
            String effectName;
            int amplifier = 0;
            switch (pick) {
                case 0 -> { effect = StatusEffects.SPEED; effectName = "速度II"; amplifier = 1; }
                case 1 -> { effect = StatusEffects.JUMP_BOOST; effectName = "跳跃提升II"; amplifier = 1; }
                case 2 -> { effect = StatusEffects.REGENERATION; effectName = "生命恢复II"; amplifier = 1; }
                case 3 -> { effect = StatusEffects.STRENGTH; effectName = "力量I"; amplifier = 0; }
                case 4 -> { effect = StatusEffects.RESISTANCE; effectName = "抗性I"; amplifier = 0; }
                default -> { effect = StatusEffects.HASTE; effectName = "急迫II"; amplifier = 1; }
            }
            player.addStatusEffect(new StatusEffectInstance(effect, 200, amplifier, false, true, true)); // 10秒
            tip(player, "§a大吃特吃！消耗5饱食度，获得 §e" + effectName + " §a10秒！");
        }
    }

    // ==================== LAYI: 蜡翼 ====================
    // Z: 激活飞行(每命1次) + 刷3个导弹箱
    // X: 连射20箭(30sCD, 飞行中)
    private static void handleLayiSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        GameManager gm = GameManager.getInstance();
        if (skillSlot == 0) {
            gm.activateLayiFlight(player, data);
        } else if (skillSlot == 1) {
            gm.activateLayiArrowBarrage(player, data);
        }
    }

    // ==================== MAYPOOR: 天锤 ====================
    // 被动: 免疫摔落伤害(在BaneOfArthropodsMixin中处理)
    // Z: 30sCD 获得创造模式飞行10秒
    // X: 10sCD 下落中激活,落地时制造爆炸(基础0格半径/0伤害, 每下落1格 半径+0.5/伤害+1)
    private static void handleMaypoorSkill(ServerPlayerEntity player, PlayerData data, int skillSlot) {
        long currentTick = player.getEntityWorld().getTime();

        if (skillSlot == 0) {
            // Z: 创造飞行
            if (currentTick - data.getLastSkillZTime() < MAYPOOR_FLIGHT_COOLDOWN) {
                long remaining = (MAYPOOR_FLIGHT_COOLDOWN - (currentTick - data.getLastSkillZTime())) / 20;
                tip(player, "§c飞行冷却中... §e" + remaining + "s");
                return;
            }
            data.setLastSkillZTime(currentTick);
            data.setMaypoorFlightEndTick(currentTick + MAYPOOR_FLIGHT_DURATION);

            // 启用创造模式飞行
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();

            ServerWorld world = player.getEntityWorld();
            world.playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ITEM_ELYTRA_FLYING,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            tip(player, "§6§l[天锤] §r§e翱翔！飞行10秒");
            GameManager.getInstance().broadcastMessage("§6§l[天锤] §r§e" + player.getGameProfile().name() + " §6启动了飞行(10秒)！");

        } else if (skillSlot == 1) {
            // X: 重锤激活(下落时使用)
            if (data.isMaypoorSlamArmed()) {
                tip(player, "§c重锤已激活！等待落地结算");
                return;
            }
            if (currentTick - data.getLastSkillXTime() < MAYPOOR_SLAM_COOLDOWN) {
                long remaining = (MAYPOOR_SLAM_COOLDOWN - (currentTick - data.getLastSkillXTime())) / 20;
                tip(player, "§c重锤冷却中... §e" + remaining + "s");
                return;
            }
            // 必须正在下落: 不在地面 + 下落中(velocityY < 0)
            if (player.isOnGround()) {
                tip(player, "§c必须在下落时使用！");
                return;
            }
            // 飞行中不允许使用(避免与创造飞行冲突)
            if (data.isMaypoorFlying(currentTick) || player.getAbilities().flying) {
                tip(player, "§c飞行中无法使用重锤！");
                return;
            }
            if (player.getVelocity().y >= 0) {
                tip(player, "§c必须在向下坠落时才能使用！");
                return;
            }

            data.setLastSkillXTime(currentTick);
            data.setMaypoorSlamArmed(true);
            data.setMaypoorSlamStartY(player.getY());

            ServerWorld world = player.getEntityWorld();
            world.playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(),
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 0.6f);
            tip(player, "§6§l[天锤] §r§c重锤激活！落地时爆炸，下落越高威力越大！");
        }
    }

    // 落地结算: 计算下落距离并制造爆炸(由GameManager.tickMaypoor调用)
    public static void executeMaypoorSlam(ServerPlayerEntity player, PlayerData data) {
        ServerWorld world = player.getEntityWorld();
        double fallDist = Math.max(0, data.getMaypoorSlamStartY() - player.getY());
        // 基础: 0伤害 0半径; 每下落1格: 伤害+1, 半径+0.5
        float damage = (float) fallDist;
        float radius = (float) (fallDist * 0.5);
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());

        // 仅在有意义时才执行(下落>0)
        if (radius > 0) {
            // 视觉/音效爆炸 (不破坏方块, 不造成原生伤害, 我们手动结算)
            world.createExplosion(player, pos.x, pos.y, pos.z, radius,
                    net.minecraft.world.World.ExplosionSourceType.NONE);

            // 手动对范围内玩家施加伤害(不打到自己)
            double radSq = radius * radius;
            for (ServerPlayerEntity target : new ArrayList<>(world.getPlayers())) {
                if (target == player || target.isSpectator()) continue;
                PlayerData td = GameManager.getInstance().getPlayerData(target.getUuid());
                if (td == null || !td.isAlive()) continue;
                double d2 = target.squaredDistanceTo(pos);
                if (d2 <= radSq) {
                    target.damage(world, world.getDamageSources().playerAttack(player), damage);
                }
            }
        }

        GameManager.getInstance().broadcastMessage("§6§l[天锤] §r§e" + player.getGameProfile().name()
                + " §6重锤落地！下落 §c" + String.format("%.1f", fallDist) + " §6格，伤害 §c"
                + String.format("%.1f", damage) + "§6, 半径 §c" + String.format("%.1f", radius) + "§6格");

        data.setMaypoorSlamArmed(false);
        data.setMaypoorSlamStartY(0);
    }

    // ==================== Utility ====================
    public static ServerPlayerEntity findNearestPlayer(ServerPlayerEntity source, double maxDistance) {
        ServerPlayerEntity nearest = null;
        double nearestDist = maxDistance * maxDistance;
        for (ServerPlayerEntity other : source.getEntityWorld().getPlayers()) {
            if (other == source) continue;
            PlayerData otherData = GameManager.getInstance().getPlayerData(other.getUuid());
            if (otherData == null || !otherData.isAlive()) continue;
            double dist = other.squaredDistanceTo(source);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        return nearest;
    }
}
