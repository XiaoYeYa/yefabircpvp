package baby.sv.yepvpfabirc.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.render.entity.LivingEntityRenderer;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEquipmentRenderMixin {
    // 隐身渲染取消已移至EntityRenderCancelMixin(基于EntityRenderDispatcher)
    // 此mixin保留为空以兼容mixin配置
}

