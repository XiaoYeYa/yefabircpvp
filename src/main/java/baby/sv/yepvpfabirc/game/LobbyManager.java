package baby.sv.yepvpfabirc.game;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;

public class LobbyManager {

    private static final RegistryKey<World> LOBBY_DIMENSION_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("yepvpfabirc", "lobby"));

    // 大厅出生点
    private static final BlockPos LOBBY_SPAWN = new BlockPos(0, 65, 0);
    // 跑酷起点(大厅东侧外)
    private static final BlockPos PARKOUR_START = new BlockPos(20, 65, 0);
    private static final int MIN_PLAYERS_TO_START = 7;

    private boolean lobbyBuilt = false;

    // ===== 投票系统 =====
    private boolean voteActive = false;
    private long voteStartTick = 0;
    private UUID voteInitiator = null;
    // 0=对应ID, 1=自选, 2=全随机(ban双人组)
    private int voteMode = -1;
    private final Set<UUID> voteYes = new HashSet<>();
    private final Set<UUID> voteNo = new HashSet<>();
    private static final long VOTE_DURATION_TICKS = 600; // 30秒投票时间
    // 双人组职业(随机模式排除)
    private static final Set<Role> DUO_ROLES = Set.of(Role.BOBBY, Role.RETOUR);

    // ===== 自选模式系统 =====
    private boolean roleSelectActive = false;
    private long roleSelectStartTick = 0;
    private static final long ROLE_SELECT_DURATION_TICKS = 1200; // 60秒选择时间
    private final Map<UUID, Role> roleSelections = new HashMap<>(); // 玩家已选的职业
    private final Set<Role> takenRoles = new HashSet<>(); // 已被选走的职业

    // ===== 开放日期系统 =====
    // 每周 六/日 开放 (DayOfWeek: SATURDAY=6, SUNDAY=7)
    private static final Set<DayOfWeek> OPEN_DAYS = Set.of(
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    private long adminOpenUntilMs = 0; // 管理员临时开放到期时间(System.currentTimeMillis)

    // ===== 准备系统 =====
    private final Set<UUID> readyPlayers = new HashSet<>();

    // ===== 侧边栏 =====
    private static final String SIDEBAR_OBJECTIVE_NAME = "yepvp_lobby";
    private ScoreboardObjective sidebarObjective = null;

    // ==================== 维度访问 ====================

    public static RegistryKey<World> getLobbyDimensionKey() {
        return LOBBY_DIMENSION_KEY;
    }

    public static ServerWorld getLobbyWorld(MinecraftServer server) {
        return server.getWorld(LOBBY_DIMENSION_KEY);
    }

    // ==================== 大厅传送 ====================

    public void teleportToLobby(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        ServerWorld lobbyWorld = getLobbyWorld(server);
        if (lobbyWorld == null) {
            System.err.println("[YePVP] Lobby dimension not found! Falling back to overworld.");
            // 回退: 如果大厅维度不存在，传送到主世界出生点
            ServerWorld overworld = server.getOverworld();
            player.teleport(overworld, 0.5, 100, 0.5, Set.of(), 0, 0, false);
            return;
        }

        // 确保大厅已建造(每次检查地板方块是否存在)
        boolean floorExists = !lobbyWorld.getBlockState(LOBBY_SPAWN.down()).isAir();
        System.out.println("[YePVP] teleportToLobby: lobbyBuilt=" + lobbyBuilt + ", floorExists=" + floorExists);
        if (!lobbyBuilt || !floorExists) {
            buildLobby(lobbyWorld);
            lobbyBuilt = true;
        }

        // 传送到大厅
        player.teleport(lobbyWorld, LOBBY_SPAWN.getX() + 0.5, LOBBY_SPAWN.getY(),
                LOBBY_SPAWN.getZ() + 0.5, Set.of(), 0, 0, false);

        // 设置冒险模式
        player.changeGameMode(GameMode.ADVENTURE);

        // 清空背包
        player.getInventory().clear();

        // 给予大厅物品
        giveLobbyItems(player);

        // 重置准备状态
        readyPlayers.remove(player.getUuid());

        // 重置最大生命值为20
        var healthAttr = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(20.0);

        // 满血
        player.setHealth(player.getMaxHealth());

        // 清除所有效果后给饱腹
        player.clearStatusEffects();
        applyLobbyEffects(player);

        player.sendMessage(Text.literal("§b§l【大厅】§r§a 欢迎来到大厅！"), false);
        player.sendMessage(Text.literal("§7手持 §e钟 §7右键打开投票菜单 | 手持 §a绿宝石 §7右键准备"), false);

        // 非开放日醒目提示
        if (!isServerOpen()) {
            player.sendMessage(Text.literal(""), false);
            player.sendMessage(Text.literal("§c§l╔══════════════════════════════╗"), false);
            player.sendMessage(Text.literal("§c§l║  §e§l⚠ 今天不是开放日！§c§l              ║"), false);
            player.sendMessage(Text.literal("§c§l║  §7开放时间: §a周六 周日§c§l            ║"), false);
            player.sendMessage(Text.literal("§c§l║  §7下次开放: §e" + getNextOpenDay() + "§c§l  ║"), false);
            player.sendMessage(Text.literal("§c§l╚══════════════════════════════╝"), false);
            player.sendMessage(Text.literal(""), false);
        }
    }

    public void teleportAllToLobby(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            teleportToLobby(player);
        }
    }

    // ==================== 大厅效果 (每秒刷新) ====================

    public void applyLobbyEffects(ServerPlayerEntity player) {
        // 持续饱腹 + 生命恢复
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 600, 0, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 2, false, false, false));
    }

    public boolean isInLobby(ServerPlayerEntity player) {
        return player.getEntityWorld().getRegistryKey() == LOBBY_DIMENSION_KEY;
    }

    // ==================== 开放日期系统 ====================

    public boolean isServerOpen() {
        // 管理员临时开放
        if (System.currentTimeMillis() < adminOpenUntilMs) return true;
        // 按UTC+8判断星期
        DayOfWeek today = LocalDate.now(ZoneId.of("Asia/Shanghai")).getDayOfWeek();
        return OPEN_DAYS.contains(today);
    }

    public void adminOpen(int minutes) {
        adminOpenUntilMs = System.currentTimeMillis() + (long) minutes * 60 * 1000;
    }

    public long getAdminOpenRemainingMs() {
        long remain = adminOpenUntilMs - System.currentTimeMillis();
        return Math.max(0, remain);
    }

    public String getNextOpenDay() {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (int i = 1; i <= 7; i++) {
            LocalDate check = now.plusDays(i);
            if (OPEN_DAYS.contains(check.getDayOfWeek())) {
                String[] dayNames = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
                return dayNames[check.getDayOfWeek().getValue()] + " (" + check + ")";
            }
        }
        return "未知";
    }

    // ==================== 投票系统 ====================

    /**
     * 玩家发起投票: /vote id | /vote select | /vote random
     */
    public void initiateVote(ServerPlayerEntity player, String mode, MinecraftServer server) {
        if (GameManager.getInstance().isGameActive()) {
            player.sendMessage(Text.literal("§c游戏正在进行中！"), false);
            return;
        }
        if (GameManager.getInstance().isMapResetInProgress()) {
            player.sendMessage(Text.literal("§c地图正在重置中，请稍后！"), false);
            return;
        }
        // 非开放日禁止投票(管理员除外)
        boolean isOp2 = server.getPlayerManager().getOpList().get(new net.minecraft.server.PlayerConfigEntry(player.getGameProfile())) != null;
        if (!isServerOpen() && !isOp2) {
            player.sendMessage(Text.literal("§c§l今天不是开放日！§r§c下次开放: §e" + getNextOpenDay()), false);
            return;
        }
        int onlineCount = server.getPlayerManager().getPlayerList().size();
        boolean isOp = server.getPlayerManager().getOpList().get(new net.minecraft.server.PlayerConfigEntry(player.getGameProfile())) != null;
        if (!isOp && onlineCount < MIN_PLAYERS_TO_START) {
            player.sendMessage(Text.literal("§c至少需要 §e" + MIN_PLAYERS_TO_START + " §c人才能开局！当前在线: §e" + onlineCount), false);
            return;
        }
        if (voteActive) {
            player.sendMessage(Text.literal("§c已有投票正在进行中！"), false);
            return;
        }

        int modeId;
        String modeName;
        switch (mode.toLowerCase()) {
            case "id" -> { modeId = 0; modeName = "对应ID模式"; }
            case "select" -> { modeId = 1; modeName = "自选模式"; }
            case "random" -> { modeId = 2; modeName = "全随机模式"; }
            default -> {
                player.sendMessage(Text.literal("§c未知模式！可用: §eid§c/§eselect§c/§erandom"), false);
                return;
            }
        }

        voteActive = true;
        voteStartTick = server.getOverworld().getTime();
        voteInitiator = player.getUuid();
        voteMode = modeId;
        voteYes.clear();
        voteNo.clear();
        voteYes.add(player.getUuid()); // 发起者自动同意

        // 广播投票信息
        Text yesButton = Text.literal("§a§l[同意]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/voteyes"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("§a点击同意"))));
        Text noButton = Text.literal("§c§l[反对]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/voteno"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("§c点击反对"))));

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(""), false);
            p.sendMessage(Text.literal("§6§l═══════════════════════════"), false);
            p.sendMessage(Text.literal("§e§l  " + player.getGameProfile().name() + " §r§e发起了投票开局！"), false);
            p.sendMessage(Text.literal("§7  模式: §b§l" + modeName), false);
            p.sendMessage(Text.literal("§7  30秒内投票，过半数同意即开局"), false);
            p.sendMessage(Text.literal(""), false);
            p.sendMessage(Text.empty().append(Text.literal("  ")).append(yesButton).append(Text.literal("  ")).append(noButton), false);
            p.sendMessage(Text.literal("§6§l═══════════════════════════"), false);
        }
    }

    public void castVote(ServerPlayerEntity player, boolean yes) {
        if (!voteActive) {
            player.sendMessage(Text.literal("§c当前没有投票！"), false);
            return;
        }
        UUID uuid = player.getUuid();
        if (voteYes.contains(uuid) || voteNo.contains(uuid)) {
            player.sendMessage(Text.literal("§c你已经投过票了！"), false);
            return;
        }
        if (yes) {
            voteYes.add(uuid);
            broadcastAll(player.getEntityWorld().getServer(), "§a" + player.getGameProfile().name() + " 投了 §a§l同意 §r§7(" + voteYes.size() + "同意/" + voteNo.size() + "反对)");
        } else {
            voteNo.add(uuid);
            broadcastAll(player.getEntityWorld().getServer(), "§c" + player.getGameProfile().name() + " 投了 §c§l反对 §r§7(" + voteYes.size() + "同意/" + voteNo.size() + "反对)");
        }
    }

    private void broadcastAll(MinecraftServer server, String msg) {
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(msg), false);
        }
    }

    // ==================== 大厅 Tick ====================

    public void tick(MinecraftServer server) {
        long time = server.getOverworld().getTime();

        // 确保大厅维度中方块存在（维度存档被删后需要重建）
        // 每60秒验证一次地板是否存在
        if (!lobbyBuilt || (time % 1200 == 0)) {
            ServerWorld lobbyWorld = getLobbyWorld(server);
            if (lobbyWorld != null) {
                boolean floorOk = !lobbyWorld.getBlockState(LOBBY_SPAWN.down()).isAir();
                if (!floorOk) {
                    System.out.println("[YePVP] Lobby floor missing! Rebuilding...");
                    lobbyBuilt = false;
                    buildLobby(lobbyWorld);
                    lobbyBuilt = true;
                } else if (!lobbyBuilt) {
                    lobbyBuilt = true;
                }
            }
        }

        // 清理已离线玩家的准备状态
        Set<UUID> onlineUuids = new HashSet<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            onlineUuids.add(p.getUuid());
        }
        readyPlayers.retainAll(onlineUuids);

        // 每秒更新侧边栏 + ActionBar
        if (time % 20 == 0) {
            updateSidebar(server);
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (isInLobby(player)) {
                boolean isOp = server.getPlayerManager().getOpList().get(new net.minecraft.server.PlayerConfigEntry(player.getGameProfile())) != null;
                // 每10秒刷新效果
                if (time % 200 == 0) {
                    if (!isOp && player.interactionManager.getGameMode() != GameMode.ADVENTURE) {
                        player.changeGameMode(GameMode.ADVENTURE);
                    }
                    applyLobbyEffects(player);
                    // 检查是否缺少大厅物品，缺少则补给
                    if (player.getInventory().getStack(0).isEmpty() || !player.getInventory().getStack(0).isOf(Items.CLOCK)) {
                        giveLobbyItems(player);
                    }
                }
                // 每秒发送ActionBar显示在线人数
                if (time % 20 == 0) {
                    int online = server.getPlayerManager().getPlayerList().size();
                    int ready = readyPlayers.size();
                    String readyColor = ready >= MIN_PLAYERS_TO_START ? "§a" : "§e";
                    String status = voteActive ? " §6| §c投票进行中..." :
                            roleSelectActive ? " §6| §b自选中 §e" + Math.max(0, (int)((ROLE_SELECT_DURATION_TICKS - (time - roleSelectStartTick)) / 20)) + "s" : "";
                    String openStatus = isServerOpen() ? "" : " §c§l| ⚠ 非开放日";
                    long adminRemain = getAdminOpenRemainingMs();
                    if (adminRemain > 0) {
                        long mins = adminRemain / 60000;
                        openStatus = " §a| 临时开放 §e" + mins + "min";
                    }
                    player.sendMessage(Text.literal(
                            "§b§l在线: " + readyColor + online + "§7/" + MIN_PLAYERS_TO_START +
                            " §a§l准备: §e" + ready + "§7/" + online + status + openStatus), true);
                }
                // 跑酷: 掉入虚空传送回大厅
                if (player.getY() < 50) {
                    player.teleport((ServerWorld) player.getEntityWorld(),
                            LOBBY_SPAWN.getX() + 0.5, LOBBY_SPAWN.getY(),
                            LOBBY_SPAWN.getZ() + 0.5, Set.of(), 0, 0, false);
                    player.setHealth(player.getMaxHealth());
                }
            } else {
                // 不在大厅的玩家: 延迟1秒后传送(确保实体完全初始化)
                if (player.age > 20) {
                    teleportToLobby(player);
                }
            }
        }

        // 自选模式计时
        if (roleSelectActive) {
            long selectElapsed = time - roleSelectStartTick;
            // 每15秒提醒
            if (selectElapsed % 300 == 0 && selectElapsed > 0 && selectElapsed < ROLE_SELECT_DURATION_TICKS) {
                int remaining = (int) ((ROLE_SELECT_DURATION_TICKS - selectElapsed) / 20);
                int selected = roleSelections.size();
                int total = server.getPlayerManager().getPlayerList().size();
                broadcastAll(server, "§e§l【自选】§r§7 剩余 §e" + remaining + "秒 §7已选: §a" + selected + "§7/" + total);
            }
            // 最后10秒每秒提醒
            if (selectElapsed >= ROLE_SELECT_DURATION_TICKS - 200 && selectElapsed < ROLE_SELECT_DURATION_TICKS && selectElapsed % 20 == 0) {
                int remaining = (int) ((ROLE_SELECT_DURATION_TICKS - selectElapsed) / 20);
                if (remaining <= 10 && remaining > 0) {
                    broadcastAll(server, "§c§l【自选】§r§c 还有 §e§l" + remaining + " §r§c秒！未选择将随机分配！");
                }
            }
            // 超时
            if (selectElapsed >= ROLE_SELECT_DURATION_TICKS) {
                broadcastAll(server, "§c§l【自选】§r§c 选择时间到！未选择的玩家将随机分配职业。");
                finishRoleSelection(server);
            }
        }

        // 投票计时
        if (voteActive) {
            long elapsed = time - voteStartTick;
            int onlineCount = server.getPlayerManager().getPlayerList().size();
            int needed = onlineCount / 2 + 1; // 过半数

            // 提前通过: 同意数≥过半
            if (voteYes.size() >= needed) {
                voteActive = false;
                broadcastAll(server, "§a§l【投票通过】§r§a 同意: " + voteYes.size() + " 反对: " + voteNo.size());
                executeVoteResult(server);
                return;
            }
            // 提前失败: 反对数过多，同意已无法过半
            if (voteNo.size() > onlineCount - needed) {
                voteActive = false;
                broadcastAll(server, "§c§l【投票未通过】§r§c 同意: " + voteYes.size() + " 反对: " + voteNo.size());
                return;
            }
            // 超时
            if (elapsed >= VOTE_DURATION_TICKS) {
                voteActive = false;
                if (voteYes.size() >= needed) {
                    broadcastAll(server, "§a§l【投票通过】§r§a 同意: " + voteYes.size() + " 反对: " + voteNo.size());
                    executeVoteResult(server);
                } else {
                    broadcastAll(server, "§c§l【投票未通过】§r§c 同意: " + voteYes.size() + " 反对: " + voteNo.size() + " (需要" + needed + "票同意)");
                }
            }
            // 每10秒提醒
            else if (elapsed % 200 == 0 && elapsed > 0) {
                int remaining = (int) ((VOTE_DURATION_TICKS - elapsed) / 20);
                broadcastAll(server, "§7投票剩余 §e" + remaining + "秒 §7(同意:" + voteYes.size() + " 反对:" + voteNo.size() + " 需要:" + needed + ")");
            }
        }
    }

    private void executeVoteResult(MinecraftServer server) {
        GameManager gm = GameManager.getInstance();
        switch (voteMode) {
            case 0 -> {
                // 对应ID模式: 使用预设的 PLAYER_ROLE_MAP
                broadcastAll(server, "§b§l【开局】§r§e 对应ID模式！职业根据玩家ID自动分配。");
                gm.startGame(false, false);
            }
            case 1 -> {
                // 自选模式: 进入60秒选择阶段
                startRoleSelection(server);
            }
            case 2 -> {
                // 全随机模式: ban掉双人组(BOBBY+RETOUR)
                broadcastAll(server, "§b§l【开局】§r§e 全随机模式！(已排除双人组职业)");
                gm.startGameRandomNoDuo();
            }
        }
    }

    // ==================== 自选模式 ====================

    private void startRoleSelection(MinecraftServer server) {
        roleSelectActive = true;
        roleSelectStartTick = server.getOverworld().getTime();
        roleSelections.clear();
        takenRoles.clear();

        broadcastAll(server, "");
        broadcastAll(server, "§b§l╔═══════════════════════════════╗");
        broadcastAll(server, "§b§l║   §e§l⚡ 自选职业模式 ⚡§b§l            ║");
        broadcastAll(server, "§b§l║   §7点击下方职业进行选择§b§l          ║");
        broadcastAll(server, "§b§l║   §7每个职业只能被1人选择§b§l         ║");
        broadcastAll(server, "§b§l║   §c§l60秒§7后未选择将随机分配§b§l       ║");
        broadcastAll(server, "§b§l╚═══════════════════════════════╝");
        broadcastAll(server, "");

        // 给每个玩家发送可点击的职业列表
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendRoleSelectionMenu(player);
        }
    }

    private void sendRoleSelectionMenu(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§6§l══════ 可选职业 ══════"), false);

        // 每行显示2个职业按钮
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length; i += 2) {
            Text line = Text.empty();
            for (int j = i; j < Math.min(i + 2, roles.length); j++) {
                Role role = roles[j];
                boolean taken = takenRoles.contains(role);
                boolean selectedByMe = roleSelections.get(player.getUuid()) == role;

                Text btn;
                if (selectedByMe) {
                    btn = Text.literal("§a§l[✓ " + role.getDisplayName() + "]")
                            .setStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Text.literal("§a§l你已选择此职业\n§7" + role.getDescription()))));
                } else if (taken) {
                    btn = Text.literal("§8§m[" + role.getDisplayName() + "]§r§c(已选)")
                            .setStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Text.literal("§c此职业已被其他玩家选择"))));
                } else {
                    btn = Text.literal("§e§l[" + role.getDisplayName() + "]")
                            .setStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent.RunCommand("/selectrole " + role.getPlayerId()))
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Text.literal("§e点击选择\n§7" + role.getDescription()))));
                }
                if (j > i) line = Text.empty().append(line).append(Text.literal("  "));
                line = Text.empty().append(line).append(btn);
            }
            player.sendMessage(line, false);
        }
        player.sendMessage(Text.literal("§6§l═══════════════════════"), false);
    }

    public void handleRoleSelection(ServerPlayerEntity player, String roleId, MinecraftServer server) {
        if (!roleSelectActive) {
            player.sendMessage(Text.literal("§c当前不在自选阶段！"), false);
            return;
        }

        Role role = Role.fromId(roleId);
        if (role == null) {
            player.sendMessage(Text.literal("§c未知职业ID: " + roleId), false);
            return;
        }

        UUID uuid = player.getUuid();

        // 如果玩家之前选过，释放旧职业
        Role oldRole = roleSelections.get(uuid);
        if (oldRole == role) {
            player.sendMessage(Text.literal("§e你已经选了 §b" + role.getDisplayName() + " §e！"), false);
            return;
        }

        // 检查是否已被他人选走
        if (takenRoles.contains(role) && oldRole != role) {
            player.sendMessage(Text.literal("§c§l" + role.getDisplayName() + " §r§c已被其他玩家选择！"), false);
            // 刷新菜单让玩家看到最新状态
            sendRoleSelectionMenu(player);
            return;
        }

        // 释放旧选择
        if (oldRole != null) {
            takenRoles.remove(oldRole);
        }

        // 记录新选择
        roleSelections.put(uuid, role);
        takenRoles.add(role);

        player.sendMessage(Text.literal("§a§l✓ 你选择了 §b§l" + role.getDisplayName() + "§a§l！"), false);
        broadcastAll(server, "§e" + player.getGameProfile().name() + " §7选择了 §b" + role.getDisplayName() +
                " §7(" + roleSelections.size() + "/" + server.getPlayerManager().getPlayerList().size() + ")");

        // 刷新所有玩家的菜单
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            sendRoleSelectionMenu(p);
        }

        // 检查是否所有人都选完了
        if (roleSelections.size() >= server.getPlayerManager().getPlayerList().size()) {
            finishRoleSelection(server);
        }
    }

    private void finishRoleSelection(MinecraftServer server) {
        roleSelectActive = false;
        GameManager gm = GameManager.getInstance();

        // 将已选职业写入分配表
        for (var entry : roleSelections.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                gm.assignRole(player.getGameProfile().name(), entry.getValue());
            }
        }

        // 未选择的玩家随机分配
        List<Role> availableRoles = new ArrayList<>();
        for (Role r : Role.values()) {
            if (!takenRoles.contains(r)) {
                availableRoles.add(r);
            }
        }
        Collections.shuffle(availableRoles);
        int idx = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!roleSelections.containsKey(player.getUuid())) {
                Role randomRole = availableRoles.get(idx % availableRoles.size());
                gm.assignRole(player.getGameProfile().name(), randomRole);
                player.sendMessage(Text.literal("§e§l你未选择职业，已随机分配: §b§l" + randomRole.getDisplayName()), false);
                idx++;
            }
        }

        roleSelections.clear();
        takenRoles.clear();

        broadcastAll(server, "§a§l【自选完成】§r§e 所有职业已分配，游戏即将开始！");
        gm.startGame(false, false);
    }

    // ==================== 地图重置 ====================

    public boolean resetMainWorldMap(MinecraftServer server) {
        Path presetDir = server.getRunDirectory().resolve("config").resolve("yepvp_preset_map");
        if (!Files.exists(presetDir) || !Files.isDirectory(presetDir)) {
            System.err.println("[YePVP] Preset map directory not found: " + presetDir);
            return false;
        }

        // 获取主世界
        ServerWorld overworld = server.getOverworld();

        // 保存并卸载所有区块
        overworld.save(null, true, false);

        // 获取主世界存档目录
        Path sessionDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
        Path regionDir = sessionDir.resolve("region");
        Path poiDir = sessionDir.resolve("poi");
        Path entitiesDir = sessionDir.resolve("entities");

        try {
            // 删除现有的 region/poi/entities 文件
            deleteDirectoryContents(regionDir);
            deleteDirectoryContents(poiDir);
            deleteDirectoryContents(entitiesDir);

            // 从预设目录复制
            Path presetRegion = presetDir.resolve("region");
            Path presetPoi = presetDir.resolve("poi");
            Path presetEntities = presetDir.resolve("entities");

            if (Files.exists(presetRegion)) {
                copyDirectory(presetRegion, regionDir);
            }
            if (Files.exists(presetPoi)) {
                copyDirectory(presetPoi, poiDir);
            }
            if (Files.exists(presetEntities)) {
                copyDirectory(presetEntities, entitiesDir);
            }

            System.out.println("[YePVP] Main world map reset successfully from preset.");
            return true;
        } catch (IOException e) {
            System.err.println("[YePVP] Failed to reset map: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void deleteDirectoryContents(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyDirectory(Path src, Path dst) throws IOException {
        Files.createDirectories(dst);
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = dst.resolve(src.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ==================== 大厅建筑生成 ====================

    private static final int SET_FLAGS = net.minecraft.block.Block.NOTIFY_ALL | net.minecraft.block.Block.FORCE_STATE;

    private void buildLobby(ServerWorld world) {
        System.out.println("[YePVP] buildLobby() called! World: " + world.getRegistryKey().getValue());

        // 强制加载大厅+跑酷区域涉及的所有区块
        int minCX = (-25) >> 4;
        int maxCX = 75 >> 4;
        int minCZ = (-30) >> 4;
        int maxCZ = 30 >> 4;
        for (int chunkX = minCX; chunkX <= maxCX; chunkX++) {
            for (int chunkZ = minCZ; chunkZ <= maxCZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ);
            }
        }
        System.out.println("[YePVP] Chunks loaded. Checking floor at " + LOBBY_SPAWN.down());

        // 检查大厅是否已经存在 (避免重复建造)
        if (!world.getBlockState(LOBBY_SPAWN.down()).isAir()) {
            System.out.println("[YePVP] Lobby already exists, skipping build.");
            return;
        }
        System.out.println("[YePVP] Building lobby structure...");

        int cx = LOBBY_SPAWN.getX();
        int cy = LOBBY_SPAWN.getY();
        int cz = LOBBY_SPAWN.getZ();

        BlockState quartzBlock = Blocks.QUARTZ_BLOCK.getDefaultState();
        BlockState quartzPillar = Blocks.QUARTZ_PILLAR.getDefaultState();
        BlockState smoothQuartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
        BlockState glowstone = Blocks.GLOWSTONE.getDefaultState();
        BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState barrier = Blocks.BARRIER.getDefaultState();
        BlockState blackConcrete = Blocks.BLACK_CONCRETE.getDefaultState();
        BlockState whiteConcrete = Blocks.WHITE_CONCRETE.getDefaultState();
        BlockState cyanConcrete = Blocks.CYAN_CONCRETE.getDefaultState();
        BlockState purpleGlass = Blocks.PURPLE_STAINED_GLASS.getDefaultState();
        BlockState cyanGlass = Blocks.CYAN_STAINED_GLASS.getDefaultState();

        int halfW = 20; // 大厅半宽(41x41)
        int height = 10; // 大厅高度

        // ===== 地板: 棋盘格图案 =====
        for (int x = cx - halfW; x <= cx + halfW; x++) {
            for (int z = cz - halfW; z <= cz + halfW; z++) {
                BlockState floor;
                int dx = Math.abs(x - cx);
                int dz = Math.abs(z - cz);
                // 中心圆形区域(半径5): 青色混凝土
                if (dx * dx + dz * dz <= 25) {
                    floor = cyanConcrete;
                }
                // 边缘装饰带
                else if (dx == halfW || dz == halfW) {
                    floor = blackConcrete;
                }
                // 棋盘格
                else {
                    floor = ((x + z) % 2 == 0) ? smoothQuartz : whiteConcrete;
                }
                world.setBlockState(new BlockPos(x, cy - 1, z), floor, SET_FLAGS);
                world.setBlockState(new BlockPos(x, cy - 2, z), barrier, SET_FLAGS);
            }
        }

        // ===== 墙壁: 石英块 + 玻璃窗 =====
        for (int y = cy; y < cy + height; y++) {
            for (int x = cx - halfW; x <= cx + halfW; x++) {
                // 北墙/南墙
                for (int wallZ : new int[]{cz - halfW, cz + halfW}) {
                    if (y >= cy + 2 && y <= cy + height - 3 && Math.abs(x - cx) <= 8 && Math.abs(x - cx) > 1) {
                        world.setBlockState(new BlockPos(x, y, wallZ), (y % 2 == 0) ? cyanGlass : purpleGlass, SET_FLAGS);
                    } else {
                        world.setBlockState(new BlockPos(x, y, wallZ), quartzBlock, SET_FLAGS);
                    }
                }
            }
            for (int z = cz - halfW; z <= cz + halfW; z++) {
                // 东墙/西墙
                for (int wallX : new int[]{cx - halfW, cx + halfW}) {
                    if (y >= cy + 2 && y <= cy + height - 3 && Math.abs(z - cz) <= 8 && Math.abs(z - cz) > 1) {
                        world.setBlockState(new BlockPos(wallX, y, z), (y % 2 == 0) ? purpleGlass : cyanGlass, SET_FLAGS);
                    } else {
                        world.setBlockState(new BlockPos(wallX, y, z), quartzBlock, SET_FLAGS);
                    }
                }
            }
        }

        // ===== 天花板: 石英块 + 海晶灯照明 =====
        for (int x = cx - halfW; x <= cx + halfW; x++) {
            for (int z = cz - halfW; z <= cz + halfW; z++) {
                int dx = Math.abs(x - cx);
                int dz = Math.abs(z - cz);
                if (dx % 4 == 0 && dz % 4 == 0) {
                    world.setBlockState(new BlockPos(x, cy + height, z), seaLantern, SET_FLAGS);
                } else {
                    world.setBlockState(new BlockPos(x, cy + height, z), quartzBlock, SET_FLAGS);
                }
            }
        }

        // ===== 四角柱子: 石英柱 + 海晶灯顶 =====
        int[][] corners = {{-halfW + 2, -halfW + 2}, {-halfW + 2, halfW - 2},
                          {halfW - 2, -halfW + 2}, {halfW - 2, halfW - 2}};
        for (int[] c : corners) {
            for (int y = cy; y < cy + height; y++) {
                world.setBlockState(new BlockPos(cx + c[0], y, cz + c[1]), quartzPillar, SET_FLAGS);
            }
            world.setBlockState(new BlockPos(cx + c[0], cy + height - 1, cz + c[1]), seaLantern, SET_FLAGS);
        }

        // ===== 中心装饰: 信标底座 =====
        world.setBlockState(new BlockPos(cx, cy, cz), Blocks.BEACON.getDefaultState(), SET_FLAGS);
        // 3x3铁块底座
        for (int bx = -1; bx <= 1; bx++) {
            for (int bz = -1; bz <= 1; bz++) {
                world.setBlockState(new BlockPos(cx + bx, cy - 1, cz + bz), Blocks.IRON_BLOCK.getDefaultState(), SET_FLAGS);
            }
        }

        // ===== 屏障围栏 (大厅外围防止逃出, 但跑酷方向不封) =====
        for (int y = cy - 2; y <= cy + height + 1; y++) {
            for (int x = cx - halfW - 1; x <= cx + halfW + 1; x++) {
                world.setBlockState(new BlockPos(x, y, cz - halfW - 1), barrier, SET_FLAGS);
                world.setBlockState(new BlockPos(x, y, cz + halfW + 1), barrier, SET_FLAGS);
            }
            for (int z = cz - halfW - 1; z <= cz + halfW + 1; z++) {
                world.setBlockState(new BlockPos(cx - halfW - 1, y, z), barrier, SET_FLAGS);
                // 东墙留出跑酷入口(z在-2到2之间, y在65-67)
                if (z >= cz - 2 && z <= cz + 2 && y >= cy && y <= cy + 2) {
                    // 不放屏障,留出入口
                } else {
                    world.setBlockState(new BlockPos(cx + halfW + 1, y, z), barrier, SET_FLAGS);
                }
            }
        }
        // 天花板上方屏障
        for (int x = cx - halfW - 1; x <= cx + halfW + 1; x++) {
            for (int z = cz - halfW - 1; z <= cz + halfW + 1; z++) {
                world.setBlockState(new BlockPos(x, cy + height + 1, z), barrier, SET_FLAGS);
            }
        }

        // ===== 跑酷区域 (大厅东侧外部) =====
        buildParkour(world, cx + halfW + 2, cy, cz);

        System.out.println("[YePVP] Lobby structure built at " + LOBBY_SPAWN);
    }

    // ==================== 跑酷区域生成 ====================

    private void buildParkour(ServerWorld world, int startX, int startY, int startZ) {
        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();
        BlockState mossyStone = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        BlockState prismarineBricks = Blocks.PRISMARINE_BRICKS.getDefaultState();
        BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState barrier = Blocks.BARRIER.getDefaultState();

        Random rand = new Random(42); // 固定种子确保每次一致

        // 平台序列: {dx, dy, dz, sizeX, sizeZ}
        int[][] platforms = {
            {0, 0, 0, 3, 3},       // 起点平台
            {4, 1, 2, 2, 2},       // 阶梯向上
            {7, 2, -1, 2, 2},
            {11, 3, 1, 1, 3},      // 窄桥
            {14, 4, 0, 2, 2},
            {17, 3, -3, 2, 2},     // 下降
            {20, 4, -1, 1, 1},     // 单格跳
            {23, 5, 1, 1, 1},
            {26, 6, -1, 1, 1},
            {29, 7, 0, 2, 2},
            {32, 8, 2, 1, 3},      // 长窄桥
            {35, 9, 0, 2, 2},
            {38, 8, -2, 1, 1},     // 下降单格
            {41, 9, 0, 1, 1},
            {44, 10, 1, 2, 2},
            {47, 11, -1, 3, 3},    // 终点大平台
        };

        for (int[] plat : platforms) {
            int px = startX + plat[0];
            int py = startY + plat[1];
            int pz = startZ + plat[2];
            int sx = plat[3];
            int sz = plat[4];

            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    BlockState block = rand.nextInt(3) == 0 ? mossyStone : stone;
                    world.setBlockState(new BlockPos(px + dx, py - 1, pz + dz), block, SET_FLAGS);
                }
            }
        }

        // 终点奖励: 海晶灯
        int endX = startX + 47;
        int endY = startY + 11;
        world.setBlockState(new BlockPos(endX + 1, endY, startZ), seaLantern, SET_FLAGS);

        // 跑酷区域底部屏障网(防止无限掉落)
        for (int x = startX - 2; x <= startX + 52; x++) {
            for (int z = startZ - 6; z <= startZ + 6; z++) {
                world.setBlockState(new BlockPos(x, startY - 5, z), barrier, SET_FLAGS);
            }
        }
        // 跑酷区域侧面屏障墙
        for (int y = startY - 5; y <= startY + 15; y++) {
            for (int x = startX - 2; x <= startX + 52; x++) {
                world.setBlockState(new BlockPos(x, y, startZ - 7), barrier, SET_FLAGS);
                world.setBlockState(new BlockPos(x, y, startZ + 7), barrier, SET_FLAGS);
            }
            world.setBlockState(new BlockPos(startX + 53, y, startZ), barrier, SET_FLAGS);
        }
        // 跑酷区域顶部屏障
        for (int x = startX - 2; x <= startX + 52; x++) {
            for (int z = startZ - 6; z <= startZ + 6; z++) {
                world.setBlockState(new BlockPos(x, startY + 16, z), barrier, SET_FLAGS);
            }
        }

        // 起点告示(用发光方块标记)
        world.setBlockState(new BlockPos(startX, startY, startZ + 2), Blocks.AMETHYST_BLOCK.getDefaultState(), SET_FLAGS);
    }

    // ==================== 大厅物品 ====================

    private static final String LOBBY_ITEM_TAG = "yepvp_lobby";

    private void giveLobbyItems(ServerPlayerEntity player) {
        // 槽位0: 投票菜单(钟)
        ItemStack voteClock = new ItemStack(Items.CLOCK);
        voteClock.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l投票开局 §7(右键)"));
        voteClock.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7右键打开投票菜单"),
                Text.literal("§7选择模式发起投票")
        )));
        player.getInventory().setStack(0, voteClock);

        // 槽位4: 准备(绿宝石)
        ItemStack readyItem = new ItemStack(Items.EMERALD);
        readyItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l准备 §7(右键)"));
        readyItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7右键切换准备状态"),
                Text.literal("§7准备好后等待开局")
        )));
        player.getInventory().setStack(4, readyItem);

        // 槽位8: 跑酷传送(紫水晶碎片)
        ItemStack parkourItem = new ItemStack(Items.AMETHYST_SHARD);
        parkourItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§l跑酷 §7(右键)"));
        parkourItem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7右键传送到跑酷起点")
        )));
        player.getInventory().setStack(8, parkourItem);
    }

    /**
     * 处理大厅物品右键使用，返回true表示消耗了该事件
     */
    public boolean handleLobbyItemUse(ServerPlayerEntity player) {
        if (!isInLobby(player)) return false;

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) return false;

        // 钟: 投票菜单
        if (held.isOf(Items.CLOCK)) {
            showVoteMenu(player);
            return true;
        }
        // 绿宝石/绿色染料: 准备
        if (held.isOf(Items.EMERALD) || held.isOf(Items.LIME_DYE)) {
            toggleReady(player);
            return true;
        }
        // 紫水晶碎片: 跑酷
        if (held.isOf(Items.AMETHYST_SHARD)) {
            player.teleport((ServerWorld) player.getEntityWorld(),
                    PARKOUR_START.getX() + 0.5, PARKOUR_START.getY() + 1,
                    PARKOUR_START.getZ() + 0.5, Set.of(), 0, 0, false);
            player.sendMessage(Text.literal("§d§l【跑酷】§r§a 传送到跑酷起点！"), false);
            return true;
        }

        return false;
    }

    private void showVoteMenu(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        int online = server.getPlayerManager().getPlayerList().size();
        boolean canStart = online >= MIN_PLAYERS_TO_START;
        String countColor = canStart ? "§a" : "§c";

        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§6§l═══════ 投票开局 ═══════"), false);
        player.sendMessage(Text.literal("§7在线人数: " + countColor + online + "§7/" + MIN_PLAYERS_TO_START +
                (canStart ? " §a✓ 可以开局" : " §c✗ 人数不足")), false);
        player.sendMessage(Text.literal(""), false);

        // 三个可点击选项
        Text idBtn = Text.literal("§e§l[对应ID模式]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/vote id"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("§e根据玩家ID分配对应职业"))));
        Text selectBtn = Text.literal("§b§l[自选模式]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/vote select"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("§b使用已分配的职业"))));
        Text randomBtn = Text.literal("§d§l[全随机模式]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/vote random"))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("§d随机分配职业(排除双人组)"))));

        player.sendMessage(Text.empty().append(Text.literal("  ")).append(idBtn), false);
        player.sendMessage(Text.empty().append(Text.literal("  ")).append(selectBtn), false);
        player.sendMessage(Text.empty().append(Text.literal("  ")).append(randomBtn), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§6§l═══════════════════════"), false);
    }

    // ==================== 准备系统 ====================

    public void toggleReady(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (readyPlayers.contains(uuid)) {
            readyPlayers.remove(uuid);
            player.sendMessage(Text.literal("§c§l【准备】§r§c 已取消准备"), false);
            // 更新手持物品外观
            ItemStack item = new ItemStack(Items.EMERALD);
            item.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l准备 §7(右键)"));
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7右键切换准备状态"),
                    Text.literal("§c当前状态: 未准备")
            )));
            player.getInventory().setStack(4, item);
        } else {
            readyPlayers.add(uuid);
            player.sendMessage(Text.literal("§a§l【准备】§r§a 已准备！等待其他玩家..."), false);
            // 更新手持物品外观
            ItemStack item = new ItemStack(Items.LIME_DYE);
            item.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l已准备 ✓ §7(右键取消)"));
            item.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§7右键切换准备状态"),
                    Text.literal("§a当前状态: 已准备")
            )));
            player.getInventory().setStack(4, item);

            // 广播准备消息
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server != null) {
                int online = server.getPlayerManager().getPlayerList().size();
                broadcastAll(server, "§a" + player.getGameProfile().name() + " §7已准备 §e(" + readyPlayers.size() + "/" + online + ")");
            }
        }
    }

    public Set<UUID> getReadyPlayers() {
        return readyPlayers;
    }

    // ==================== 侧边栏(Scoreboard) ====================

    private void updateSidebar(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        // 创建/获取侧边栏Objective
        if (sidebarObjective == null) {
            sidebarObjective = scoreboard.getNullableObjective(SIDEBAR_OBJECTIVE_NAME);
            if (sidebarObjective == null) {
                sidebarObjective = scoreboard.addObjective(
                        SIDEBAR_OBJECTIVE_NAME,
                        ScoreboardCriterion.DUMMY,
                        Text.literal("§b§lYePVP §7大乱斗"),
                        ScoreboardCriterion.RenderType.INTEGER,
                        true,
                        BlankNumberFormat.INSTANCE
                );
            }
            scoreboard.setObjectiveSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR, sidebarObjective);
        }

        // 清除旧分数
        for (String entry : new ArrayList<>(scoreboard.getKnownScoreHolders().stream()
                .map(holder -> holder.getNameForScoreboard()).toList())) {
            if (scoreboard.getScore(net.minecraft.scoreboard.ScoreHolder.fromName(entry), sidebarObjective) != null) {
                scoreboard.removeScore(net.minecraft.scoreboard.ScoreHolder.fromName(entry), sidebarObjective);
            }
        }

        int online = server.getPlayerManager().getPlayerList().size();
        int ready = readyPlayers.size();
        boolean mapResetting = GameManager.getInstance().isMapResetInProgress();
        String readyColor = ready >= MIN_PLAYERS_TO_START ? "§a" : "§e";

        // 分数越高越靠上
        setScore(scoreboard, "§6§l════════════", 10);
        setScore(scoreboard, "§f在线: " + (online >= MIN_PLAYERS_TO_START ? "§a" : "§c") + online + "§7/" + MIN_PLAYERS_TO_START, 9);
        setScore(scoreboard, "§f准备: " + readyColor + ready + "§7/" + online, 8);
        setScore(scoreboard, " ", 7);
        if (mapResetting) {
            setScore(scoreboard, "§c⚡ 地图重置中...", 6);
        } else if (roleSelectActive) {
            int selRemaining = (int) ((ROLE_SELECT_DURATION_TICKS - (server.getOverworld().getTime() - roleSelectStartTick)) / 20);
            if (selRemaining < 0) selRemaining = 0;
            setScore(scoreboard, "§b§l自选职业中!", 6);
            setScore(scoreboard, "§a已选: §e" + roleSelections.size() + "§7/" + online, 5);
            setScore(scoreboard, "§7剩余: §f" + selRemaining + "秒", 4);
        } else if (voteActive) {
            int remaining = (int) ((VOTE_DURATION_TICKS - (server.getOverworld().getTime() - voteStartTick)) / 20);
            if (remaining < 0) remaining = 0;
            String modeName = switch (voteMode) {
                case 0 -> "对应ID";
                case 1 -> "自选";
                case 2 -> "全随机";
                default -> "未知";
            };
            setScore(scoreboard, "§e投票: §b" + modeName, 6);
            setScore(scoreboard, "§a同意:" + voteYes.size() + " §c反对:" + voteNo.size(), 5);
            setScore(scoreboard, "§7剩余: §f" + remaining + "秒", 4);
        } else {
            setScore(scoreboard, "§a等待开局...", 6);
        }
        setScore(scoreboard, "  ", 3);

        // 显示准备状态列表
        int idx = 2;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (idx < 0) break;
            String name = p.getGameProfile().name();
            if (name.length() > 10) name = name.substring(0, 10);
            String mark = readyPlayers.contains(p.getUuid()) ? "§a✓ " : "§7○ ";
            setScore(scoreboard, mark + "§f" + name, idx--);
        }
    }

    private void setScore(Scoreboard scoreboard, String name, int score) {
        scoreboard.getOrCreateScore(net.minecraft.scoreboard.ScoreHolder.fromName(name), sidebarObjective).setScore(score);
    }

    public void removeSidebar(MinecraftServer server) {
        if (server == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        if (sidebarObjective != null) {
            scoreboard.removeObjective(sidebarObjective);
            sidebarObjective = null;
        }
    }

    // ==================== 获取双人组职业集合 ====================
    public static Set<Role> getDuoRoles() {
        return DUO_ROLES;
    }
}
