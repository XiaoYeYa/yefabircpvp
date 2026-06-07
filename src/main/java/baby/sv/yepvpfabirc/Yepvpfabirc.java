package baby.sv.yepvpfabirc;

import baby.sv.yepvpfabirc.command.AprilFoolsCommand;
import baby.sv.yepvpfabirc.command.PlayerCommands;
import baby.sv.yepvpfabirc.command.VoteCommand;
import baby.sv.yepvpfabirc.config.AprilFoolsConfig;
import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import baby.sv.yepvpfabirc.network.HudSyncManager;
import baby.sv.yepvpfabirc.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import java.util.Set;

public class Yepvpfabirc implements ModInitializer {

    private int hudSyncTimer = 0;

    private static final Set<Block> PROTECTED_ORES = Set.of(
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE
    );
    private static final Set<Block> PROTECTED_ORES_2 = Set.of(
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.ANCIENT_DEBRIS
    );
    private static final Set<Block> PROTECTED_MINERAL_BLOCKS = Set.of(
        Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK,
        Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK,
        Blocks.COPPER_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.COAL_BLOCK,
        Blocks.RAW_IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_COPPER_BLOCK
    );

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModItems.register();
        NetworkHandler.registerServer();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AprilFoolsCommand.register(dispatcher);
            PlayerCommands.register(dispatcher);
            VoteCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GameManager.getInstance().setServer(server);
            AprilFoolsConfig config = new AprilFoolsConfig();
            config.load();
            config.applyToGameManager();
            System.out.println("[YePvP] 愚人节大乱斗模组已加载！使用 /af start 开始游戏");
        });

        // 方块破坏保护
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!GameManager.getInstance().isGameActive()) return true;
            // 噬元兽异次元空间: 被困玩家不可破坏任何方块
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                PlayerData pd = GameManager.getInstance().getPlayerData(sp.getUuid());
                if (pd != null) {
                    for (PlayerData ownerData : GameManager.getInstance().getAllPlayerData().values()) {
                        if (ownerData.getRole() == Role.YOUZHA && ownerData.getYouzhaTrappedPlayers().contains(sp.getUuid())) {
                            return false;
                        }
                    }
                }
            }
            // XLL玻璃建筑不可破坏
            if (state.isOf(Blocks.GLASS) || state.isOf(Blocks.GLOWSTONE)) {
                if (SkillHandler.isXllProtectedBlock(pos)) {
                    player.sendMessage(Text.literal("§c这是XLL的玻璃建筑，无法破坏！"), true);
                    return false;
                }
            }
            // 禁止破坏箱子
            Block block = state.getBlock();
            if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)) {
                player.sendMessage(Text.literal("§c箱子不可破坏！"), true);
                return false;
            }
            // 禁止挖掘矿物原矿和矿物块
            if (PROTECTED_ORES.contains(block) || PROTECTED_ORES_2.contains(block) || PROTECTED_MINERAL_BLOCKS.contains(block)) {
                player.sendMessage(Text.literal("§c矿物和矿物块不可挖掘！"), true);
                return false;
            }
            return true;
        });

        // 大厅物品右键拦截
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
                if (!GameManager.getInstance().isGameActive()) {
                    boolean handled = GameManager.getInstance().getLobbyManager().handleLobbyItemUse(sp);
                    if (handled) {
                        return ActionResult.SUCCESS;
                    }
                } else {
                    // JANE: 右键散弹枪开火
                    if (GameManager.getInstance().tryFireShotgun(sp, hand)) {
                        return ActionResult.SUCCESS;
                    }
                }
            }
            return ActionResult.PASS;
        });

        // 玩家加入时发送版本检查
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                NetworkHandler.sendVersionCheck(handler.getPlayer());
            });
        });

        // 玩家退出时清理地图编辑状态(避免重连后卡在地图)
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GameManager.getInstance().exitMapEditOnDisconnect(handler.getPlayer().getUuid());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GameManager gm = GameManager.getInstance();
            gm.tick();

            // 大厅系统tick(游戏未进行时维护大厅玩家状态)
            if (!gm.isGameActive()) {
                gm.getLobbyManager().tick(server);
            }

            // 版本检查超时踢出
            NetworkHandler.tickVersionChecks(server);

            if (gm.isGameActive()) {
                hudSyncTimer++;
                if (hudSyncTimer >= 20) {
                    hudSyncTimer = 0;
                    HudSyncManager.syncAllPlayers(server);
                }
            }
        });
    }
}
