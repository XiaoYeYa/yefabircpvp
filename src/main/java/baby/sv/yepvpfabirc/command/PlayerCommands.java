package baby.sv.yepvpfabirc.command;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PlayerCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /Ciallo - 柚子厨技能
        dispatcher.register(CommandManager.literal("Ciallo")
                .executes(ctx -> {
                    ServerPlayerEntity source = ctx.getSource().getPlayerOrThrow();
                    if (!GameManager.getInstance().isGameActive()) {
                        ctx.getSource().sendError(Text.literal("§c游戏未开始！"));
                        return 0;
                    }
                    SkillHandler.handleCialloCommand(source);
                    return 1;
                })
        );

        // /debugmode - 切换调试模式(OP only)
        dispatcher.register(CommandManager.literal("debugmode")
                .requires(src -> {
                    try {
                        ServerPlayerEntity p = src.getPlayerOrThrow();
                        return src.getServer().getPlayerManager().getOpList().get(new net.minecraft.server.PlayerConfigEntry(p.getGameProfile())) != null;
                    } catch (Exception e) {
                        return true; // console
                    }
                })
                .executes(ctx -> {
                    GameManager gm = GameManager.getInstance();
                    gm.setDebugMode(!gm.isDebugMode());
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            gm.isDebugMode() ? "§a§l[调试模式] §r§a已开启！可使用 /switchrole <职业id>" : "§c§l[调试模式] §r§c已关闭"
                    ), true);
                    return 1;
                })
        );

        // /switchrole <roleid> - 切换职业(调试模式下)
        dispatcher.register(CommandManager.literal("switchrole")
                .then(CommandManager.argument("role", StringArgumentType.word())
                        .executes(ctx -> {
                            GameManager gm = GameManager.getInstance();
                            if (!gm.isDebugMode()) {
                                ctx.getSource().sendError(Text.literal("§c调试模式未开启！请先使用 /debugmode"));
                                return 0;
                            }
                            if (!gm.isGameActive()) {
                                ctx.getSource().sendError(Text.literal("§c游戏未开始！"));
                                return 0;
                            }
                            ServerPlayerEntity source = ctx.getSource().getPlayerOrThrow();
                            String roleId = StringArgumentType.getString(ctx, "role");
                            Role role = Role.fromId(roleId);
                            if (role == null) {
                                StringBuilder sb = new StringBuilder("§c未知职业ID: " + roleId + "\n§e可用职业: ");
                                for (Role r : Role.values()) {
                                    sb.append(r.getPlayerId()).append("(").append(r.getDisplayName()).append(") ");
                                }
                                ctx.getSource().sendError(Text.literal(sb.toString()));
                                return 0;
                            }
                            if (gm.switchPlayerRole(source, role)) {
                                return 1;
                            } else {
                                ctx.getSource().sendError(Text.literal("§c切换失败！你可能不在游戏中"));
                                return 0;
                            }
                        })
                )
        );

        // /hello <player> - NIHAO问好
        dispatcher.register(CommandManager.literal("hello")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity source = ctx.getSource().getPlayerOrThrow();
                            if (!GameManager.getInstance().isGameActive()) {
                                ctx.getSource().sendError(Text.literal("§c游戏未开始！"));
                                return 0;
                            }
                            PlayerData data = GameManager.getInstance().getPlayerData(source.getUuid());
                            if (data == null || data.getRole() != Role.NIHAO) {
                                ctx.getSource().sendError(Text.literal("§c你不是Nihao角色！"));
                                return 0;
                            }
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            SkillHandler.handleHelloCommand(source, target);
                            return 1;
                        })
                )
        );

        // /helloback <code> - 回复NIHAO问好
        dispatcher.register(CommandManager.literal("helloback")
                .then(CommandManager.argument("code", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            ServerPlayerEntity source = ctx.getSource().getPlayerOrThrow();
                            if (!GameManager.getInstance().isGameActive()) {
                                ctx.getSource().sendError(Text.literal("§c游戏未开始！"));
                                return 0;
                            }
                            int code = IntegerArgumentType.getInteger(ctx, "code");
                            SkillHandler.handleHelloBackCommand(source, code);
                            return 1;
                        })
                )
        );

        // /listroles - 列出所有职业ID
        dispatcher.register(CommandManager.literal("listroles")
                .executes(ctx -> {
                    StringBuilder sb = new StringBuilder("§e§l职业列表:\n");
                    for (Role r : Role.values()) {
                        sb.append("§a").append(r.getPlayerId()).append(" §7- §e").append(r.getDisplayName()).append("\n");
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                    return 1;
                })
        );
    }
}
