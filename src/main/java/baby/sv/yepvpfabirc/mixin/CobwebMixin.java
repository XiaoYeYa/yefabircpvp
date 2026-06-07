package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class CobwebMixin {

    // JIEZU在蛛网中不减速
    @Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
    private void onSlowMovement(BlockState state, net.minecraft.util.math.Vec3d multiplier, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!GameManager.getInstance().isGameActive()) return;
        PlayerData data = GameManager.getInstance().getPlayerData(player.getUuid());
        if (data != null && data.getRole() == Role.JIEZU) {
            ci.cancel(); // JIEZU不受蛛网减速
        }
    }
}
