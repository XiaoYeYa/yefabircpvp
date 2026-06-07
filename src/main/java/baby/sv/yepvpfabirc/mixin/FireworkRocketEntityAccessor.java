package baby.sv.yepvpfabirc.mixin;

import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    @Accessor("lifeTime")
    void setLifeTime(int lifeTime);

    @Accessor("life")
    int getLife();

    @Accessor("life")
    void setLife(int life);
}
