package baby.sv.yepvpfabirc.mixin;

// PaintingInteractMixin - 画实体右键交互由BlockInteractMixin和PaintingAttackMixin处理
// 此类保留为空以避免Mixin注册报错
import org.spongepowered.asm.mixin.Mixin;

@Mixin(net.minecraft.server.network.ServerPlayerInteractionManager.class)
public abstract class PaintingInteractMixin {
}
