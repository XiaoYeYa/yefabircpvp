package baby.sv.yepvpfabirc.mixin;

// 不再使用PaintingEntity, 画统一用AMETHYST_BLOCK
// 交互由BlockInteractMixin处理, 破坏由GlassBreakMixin处理
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerEntity.class)
public abstract class PaintingAttackMixin {
}
