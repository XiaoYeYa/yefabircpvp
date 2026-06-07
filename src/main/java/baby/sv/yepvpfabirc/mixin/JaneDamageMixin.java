package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class JaneDamageMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float janeBonusDamage(float amount, ServerWorld world, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 只对非玩家实体翻倍
        if (self instanceof ServerPlayerEntity) return amount;

        // 检查攻击者是否是JANE
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return amount;

        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return amount;

        PlayerData data = gm.getPlayerData(attacker.getUuid());
        if (data == null || data.getRole() != Role.JANE) return amount;

        return amount * 2.0f; // 翻倍
    }
}
