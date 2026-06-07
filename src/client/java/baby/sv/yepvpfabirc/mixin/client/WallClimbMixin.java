package baby.sv.yepvpfabirc.mixin.client;

import baby.sv.yepvpfabirc.client.hud.HudDataCache;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class WallClimbMixin {

    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
    private void onIsClimbing(CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!HudDataCache.isGameActive()) return;
        if (!"jiezu".equals(HudDataCache.getSelfRoleId())) return;

        if (self.horizontalCollision && !self.isOnGround()) {
            cir.setReturnValue(true);
        }
    }
}
