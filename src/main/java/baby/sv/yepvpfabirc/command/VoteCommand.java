package baby.sv.yepvpfabirc.command;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.Role;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class VoteCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /vote <mode> - 发起投票
        dispatcher.register(CommandManager.literal("vote")
                .then(CommandManager.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("id");
                            builder.suggest("select");
                            builder.suggest("random");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String mode = StringArgumentType.getString(ctx, "mode");
                            GameManager.getInstance().getLobbyManager().initiateVote(
                                    player, mode, ctx.getSource().getServer());
                            return 1;
                        })
                )
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§7用法: §e/vote <id|select|random>"), false);
                    return 1;
                })
        );

        // /voteyes - 投同意票
        dispatcher.register(CommandManager.literal("voteyes")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    GameManager.getInstance().getLobbyManager().castVote(player, true);
                    return 1;
                })
        );

        // /voteno - 投反对票
        dispatcher.register(CommandManager.literal("voteno")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    GameManager.getInstance().getLobbyManager().castVote(player, false);
                    return 1;
                })
        );

        // /selectrole <roleid> - 自选模式选择职业
        dispatcher.register(CommandManager.literal("selectrole")
                .then(CommandManager.argument("role", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Role role : Role.values()) {
                                builder.suggest(role.getPlayerId());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String roleId = StringArgumentType.getString(ctx, "role");
                            GameManager.getInstance().getLobbyManager().handleRoleSelection(
                                    player, roleId, ctx.getSource().getServer());
                            return 1;
                        })
                )
        );
    }
}
