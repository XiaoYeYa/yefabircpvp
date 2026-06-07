package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class GlassBreakMixin {

    @Inject(method = "onBreak", at = @At("HEAD"))
    private void onBlockBreak(World world, BlockPos pos, BlockState state, net.minecraft.entity.player.PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        if (world.isClient()) return;
        if (!GameManager.getInstance().isGameActive()) return;

        // Shubing 水平画(紫水晶方块)被破坏检测
        if (state.isOf(Blocks.AMETHYST_BLOCK)) {
            SkillHandler.onShubingPaintingBlockBroken((ServerWorld) world, pos);
            // 不return，让方块正常破坏
        }

        // DAMAI 墓碑(磁石)保护
        if (state.isOf(Blocks.LODESTONE)) {
            GameManager gm = GameManager.getInstance();
            for (PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != baby.sv.yepvpfabirc.game.Role.DAMAI) continue;
                if (pd.getTombstonePos() == null || !pd.getTombstonePos().equals(pos)) continue;
                long currentTick = ((ServerWorld) world).getTime();
                if (currentTick < pd.getTombstoneProtectedUntilTick()) {
                    // 10秒内不可破坏
                    cir.setReturnValue(state);
                    world.setBlockState(pos, state);
                    if (player instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(net.minecraft.text.Text.literal("§c墓碑正在保护中，暂时无法破坏！"), true);
                    }
                    return;
                }
                // 保护期过后可以破坏(tickDamai会检测到墓碑消失并触发提前复活)
                break;
            }
        }

        // XLL 玻璃建筑不可破坏(正方体/玻璃长道)
        if (state.isOf(Blocks.GLASS) || state.isOf(Blocks.GLOWSTONE)) {
            if (SkillHandler.isXllProtectedBlock(pos)) {
                cir.setReturnValue(state);
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(net.minecraft.text.Text.literal("§c这是XLL的玻璃建筑，无法破坏！"), true);
                    // 强制恢复方块并同步给客户端(防止客户端破坏动画导致延迟)
                    ((ServerWorld) world).setBlockState(pos, state, Block.NOTIFY_ALL | Block.FORCE_STATE);
                }
                return;
            }
        }

        // JIEZU 特殊蛛网被破坏: 从webPositions移除，不掉落物品(直接设为空气)
        if (state.isOf(Blocks.COBWEB)) {
            GameManager gm = GameManager.getInstance();
            for (baby.sv.yepvpfabirc.game.PlayerData pd : gm.getAllPlayerData().values()) {
                if (pd.getRole() != baby.sv.yepvpfabirc.game.Role.JIEZU) continue;
                if (pd.getWebPositions().remove(pos)) {
                    // 取消正常掉落,直接设为空气
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    cir.setReturnValue(state);
                    if (player instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(net.minecraft.text.Text.literal("§8你破坏了节足动物的特殊蛛网！"), true);
                    }
                    // 通知JIEZU
                    ServerPlayerEntity jiezu = ((ServerWorld) world).getServer().getPlayerManager().getPlayer(pd.getPlayerUuid());
                    if (jiezu != null) {
                        jiezu.sendMessage(net.minecraft.text.Text.literal("§c§l你的蛛网被破坏了！剩余: " + pd.getWebPositions().size() + "/3"), false);
                    }
                    return;
                }
            }
        }
    }
}
