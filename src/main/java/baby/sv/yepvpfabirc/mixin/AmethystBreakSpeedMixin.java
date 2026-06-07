package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public abstract class AmethystBreakSpeedMixin {

    @Inject(method = "calcBlockBreakingDelta", at = @At("RETURN"), cancellable = true)
    private void boostAmethystBreaking(BlockState state, PlayerEntity player, BlockView world, BlockPos pos,
                                        CallbackInfoReturnable<Float> cir) {
        if (!GameManager.getInstance().isGameActive()) return;
        if (state.isOf(Blocks.AMETHYST_BLOCK)) {
            // 紫水晶方块挖掘速度提高5倍(让画更容易被破坏)
            cir.setReturnValue(cir.getReturnValue() * 5.0f);
        }
    }
}
