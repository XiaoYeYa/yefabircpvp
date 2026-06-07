package baby.sv.yepvpfabirc.mixin.client;

import baby.sv.yepvpfabirc.client.hud.HiddenPlayersCache;
import baby.sv.yepvpfabirc.client.hud.HudDataCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

// LivingEntityRenderer重写了EntityRenderer.render(), 必须在此注入才能拦截玩家渲染
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderCancelMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!HudDataCache.isGameActive()) return;
        UUID uuid = HiddenPlayersCache.getStateUuid(state);
        if (uuid == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        // 自己不隐藏(本地玩家看得到自己)
        if (mc.player != null && mc.player.getUuid().equals(uuid)) return;
        // 旁观者可以看到所有隐身玩家
        if (mc.player != null && mc.player.isSpectator()) return;
        // 检查服务端同步的完全隐身列表
        if (HiddenPlayersCache.isHidden(uuid)) {
            ci.cancel();
        }
    }
}
