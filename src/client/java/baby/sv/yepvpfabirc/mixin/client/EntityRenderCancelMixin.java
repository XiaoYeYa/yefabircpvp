package baby.sv.yepvpfabirc.mixin.client;

import baby.sv.yepvpfabirc.client.hud.HiddenPlayersCache;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// getAndUpdateRenderState定义在EntityRenderer且未被子类重写,在此捕获Entity UUID
@Mixin(EntityRenderer.class)
public abstract class EntityRenderCancelMixin {

    @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
    private void onGetAndUpdateRenderState(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
        if (entity instanceof PlayerEntity) {
            HiddenPlayersCache.putStateUuid(cir.getReturnValue(), entity.getUuid());
        }
    }
}
