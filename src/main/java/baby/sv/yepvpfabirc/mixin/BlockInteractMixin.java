package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class BlockInteractMixin {

    // 拦截玩家右键方块 - 如果是紫水晶方块(水平画)则进入画中世界
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(ServerPlayerEntity player, World world, net.minecraft.item.ItemStack stack,
                                  Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient()) return;
        if (!GameManager.getInstance().isGameActive()) return;

        // 基头四召唤器: 右键使用回响碎片
        if (stack.isOf(net.minecraft.item.Items.ECHO_SHARD)) {
            net.minecraft.text.Text customName = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
            if (customName != null && customName.getString().contains("基头四召唤器")) {
                baby.sv.yepvpfabirc.game.PlayerData data = GameManager.getInstance().getPlayerData(player.getUuid());
                if (data != null && data.isAlive()) {
                    stack.decrement(1);
                    GameManager.getInstance().spawnMachaWardens(player);
                    cir.setReturnValue(ActionResult.SUCCESS);
                    return;
                }
            }
        }

        BlockPos pos = hitResult.getBlockPos();
        if (world.getBlockState(pos).isOf(Blocks.AMETHYST_BLOCK)) {
            SkillHandler.onPlayerTouchPaintingBlock(player, pos);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        // 箱子战利品生成: 打开空箱子时自动填充
        if (world.getBlockState(pos).isOf(Blocks.CHEST) || world.getBlockState(pos).isOf(Blocks.TRAPPED_CHEST)) {
            GameManager.getInstance().onChestOpen(player, pos, (net.minecraft.server.world.ServerWorld) world);
        }
    }
}
