package baby.sv.yepvpfabirc.command;

import baby.sv.yepvpfabirc.config.AprilFoolsConfig;
import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.Role;
import baby.sv.yepvpfabirc.network.HudSyncManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class AprilFoolsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("af")
                .requires(source -> {
                    try {
                        ServerPlayerEntity p = source.getPlayerOrThrow();
                        return source.getServer().getPlayerManager().getOpList().get(new net.minecraft.server.PlayerConfigEntry(p.getGameProfile())) != null;
                    } catch (Exception e) {
                        return true; // console
                    }
                })
                .then(CommandManager.literal("start")
                        .executes(ctx -> {
                            GameManager.getInstance().startGame();
                            HudSyncManager.syncAllPlayers(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("§a愚人节大乱斗已开始！"), true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("random")
                        .executes(ctx -> {
                            GameManager.getInstance().startGame(true, false);
                            HudSyncManager.syncAllPlayers(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("§6随机职业模式已开始！"), true);
                            return 1;
                        })
                        .then(CommandManager.literal("fire")
                                .executes(ctx -> {
                                    GameManager.getInstance().startGame(true, true);
                                    HudSyncManager.syncAllPlayers(ctx.getSource().getServer());
                                    ctx.getSource().sendFeedback(() -> Text.literal("§c随机职业+无限火力模式已开始！"), true);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("fire")
                        .executes(ctx -> {
                            GameManager.getInstance().startGame(false, true);
                            HudSyncManager.syncAllPlayers(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("§c无限火力模式已开始！"), true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("stop")
                        .executes(ctx -> {
                            GameManager.getInstance().stopGame();
                            ctx.getSource().sendFeedback(() -> Text.literal("§c愚人节大乱斗已停止！"), true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("mapedit")
                        .executes(ctx -> {
                            ServerPlayerEntity player;
                            try {
                                player = ctx.getSource().getPlayerOrThrow();
                            } catch (Exception e) {
                                ctx.getSource().sendError(Text.literal("§c该命令只能由玩家执行。"));
                                return 0;
                            }
                            Text result = GameManager.getInstance().toggleMapEdit(player);
                            ctx.getSource().sendFeedback(() -> result, false);
                            return 1;
                        })
                )
                .then(CommandManager.literal("assign")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("role", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Role role : Role.values()) {
                                                builder.suggest(role.getPlayerId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                            String roleId = StringArgumentType.getString(ctx, "role");
                                            Role role = Role.fromId(roleId);
                                            if (role == null) {
                                                ctx.getSource().sendError(Text.literal("§c未知职业: " + roleId));
                                                return 0;
                                            }
                                            GameManager.getInstance().assignRole(player.getGameProfile().name(), role);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a已将 " + player.getGameProfile().name() + " 分配为 " + role.getDisplayName()), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("spawn")
                        .then(CommandManager.argument("playerName", StringArgumentType.word())
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            String playerName = StringArgumentType.getString(ctx, "playerName");
                                            BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                            GameManager.getInstance().setSpawnPoint(playerName, pos);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a已设置 " + playerName + " 的出生点为 " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("arena")
                        .then(CommandManager.argument("center", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(10, 200))
                                        .executes(ctx -> {
                                            BlockPos center = BlockPosArgumentType.getBlockPos(ctx, "center");
                                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                            GameManager.getInstance().setArena(center, radius);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a已设置场地中心 " + center.getX() + ", " + center.getY() + ", " + center.getZ() + " 半径 " + radius), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            AprilFoolsConfig config = new AprilFoolsConfig();
                            config.load();
                            config.applyToGameManager();
                            ctx.getSource().sendFeedback(() -> Text.literal("§a配置已重新加载！"), true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("save")
                        .executes(ctx -> {
                            AprilFoolsConfig config = new AprilFoolsConfig();
                            config.save();
                            ctx.getSource().sendFeedback(() -> Text.literal("§a配置已保存！"), true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("reveal")
                        .executes(ctx -> {
                            GameManager gm = GameManager.getInstance();
                            if (!gm.isGameActive()) {
                                ctx.getSource().sendError(Text.literal("§c游戏未开始！"));
                                return 0;
                            }
                            if (gm.isRevealed()) {
                                ctx.getSource().sendError(Text.literal("§c已经是明牌状态了！"));
                                return 0;
                            }
                            gm.revealAll();
                            ctx.getSource().sendFeedback(() -> Text.literal("§a已触发明牌！"), true);
                            return 1;
                        })
                )
                .then(CommandManager.literal("rolespawn")
                        .then(CommandManager.argument("role", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Role role : Role.values()) {
                                        builder.suggest(role.getPlayerId());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            String roleId = StringArgumentType.getString(ctx, "role");
                                            Role role = Role.fromId(roleId);
                                            if (role == null) {
                                                ctx.getSource().sendError(Text.literal("§c未知职业: " + roleId));
                                                return 0;
                                            }
                                            BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                            GameManager.getInstance().setRoleSpawnPoint(role, pos);
                                            ctx.getSource().sendFeedback(() -> Text.literal("§a已设置 " + role.getDisplayName() + " 的出生点为 " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("open")
                        .then(CommandManager.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(ctx -> {
                                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                    GameManager.getInstance().getLobbyManager().adminOpen(minutes);
                                    ctx.getSource().sendFeedback(() -> Text.literal("§a§l【临时开放】§r§a 服务器已临时开放 §e" + minutes + " §a分钟！"), true);
                                    // 广播给所有玩家
                                    for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                                        p.sendMessage(Text.literal("§a§l【系统】§r§e 管理员已临时开放服务器 §a" + minutes + " §e分钟！"), false);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("list")
                        .executes(ctx -> {
                            GameManager gm = GameManager.getInstance();
                            ctx.getSource().sendFeedback(() -> Text.literal("§a§l=== 职业分配列表 ==="), false);
                            for (var entry : gm.getPlayerNameToRole().entrySet()) {
                                ctx.getSource().sendFeedback(() -> Text.literal("§e" + entry.getKey() + " §7-> §a" + entry.getValue().getDisplayName()), false);
                            }
                            return 1;
                        })
                )
                .then(CommandManager.literal("win")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                    GameManager gm = GameManager.getInstance();
                                    if (!gm.isGameActive()) {
                                        ctx.getSource().sendError(Text.literal("§c游戏未开始！"));
                                        return 0;
                                    }
                                    baby.sv.yepvpfabirc.game.PlayerData pd = gm.getPlayerData(player.getUuid());
                                    if (pd == null) {
                                        ctx.getSource().sendError(Text.literal("§c该玩家未参与本局游戏！"));
                                        return 0;
                                    }
                                    boolean ok = gm.triggerVictorySceneForPlayer(pd);
                                    if (!ok) {
                                        ctx.getSource().sendError(Text.literal("§c无法触发胜利(游戏未激活或已在演礼中)"));
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal("§a已判定 §e" + player.getGameProfile().name() + " §a胜利！"), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}
