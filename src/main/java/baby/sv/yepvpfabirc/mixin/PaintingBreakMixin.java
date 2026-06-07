package baby.sv.yepvpfabirc.mixin;

// 不再使用PaintingEntity,画统一用AMETHYST_BLOCK标记方块
// 破坏检测由GlassBreakMixin处理
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.decoration.painting.PaintingEntity;

@Mixin(PaintingEntity.class)
public abstract class PaintingBreakMixin {
}
