package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayerEntity.class)
public abstract class StrengthDamageMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float onDamageModifyAmount(float amount, ServerWorld world, DamageSource source, float originalAmount) {
        if (!GameManager.getInstance().isGameActive()) return amount;

        // 如果攻击者是玩家且有力量效果，把力量加成部分*0.5
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return amount;

        var strengthEffect = attacker.getStatusEffect(StatusEffects.STRENGTH);
        if (strengthEffect == null) return amount;

        int level = strengthEffect.getAmplifier() + 1; // amplifier=0 → 力量1
        // 原版力量加成：每级+3伤害（即+1.5颗心）
        float strengthBonus = level * 3.0f;
        // 将力量加成减半
        return amount - strengthBonus * 0.5f;
    }
}
