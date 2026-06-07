package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SanchezWolfProtectMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onSanchezWolfDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof WolfEntity wolf)) return;

        // 只处理带sanchez标签的狼
        boolean isSanchezWolf = wolf.getCommandTags().stream().anyMatch(t -> t.startsWith("sanchez_"));
        if (!isSanchezWolf) return;

        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        // 获取伤害来源实体
        net.minecraft.entity.Entity attacker = source.getAttacker();
        if (attacker == null) return;

        // 1. SANCHEZ玩家不能伤害自己的狼
        if (attacker instanceof ServerPlayerEntity player) {
            PlayerData data = gm.getPlayerData(player.getUuid());
            if (data != null && data.getRole() == Role.SANCHEZ) {
                // 检查这只狼是否属于该玩家(通过commandTag)
                String playerUuidStr = player.getUuid().toString();
                if (wolf.getCommandTags().stream().anyMatch(t -> t.contains(playerUuidStr))) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        // 2. SANCHEZ的狼不能伤害同主人的友方狼
        if (attacker instanceof WolfEntity attackerWolf) {
            boolean attackerIsSanchez = attackerWolf.getCommandTags().stream().anyMatch(t -> t.startsWith("sanchez_"));
            if (attackerIsSanchez) {
                // 提取两只狼的主人UUID(从tag中解析)
                String selfOwner = extractOwnerFromTags(wolf);
                String attackerOwner = extractOwnerFromTags(attackerWolf);
                if (selfOwner != null && selfOwner.equals(attackerOwner)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

    // SANCHEZ狼攻击非狼实体: 使用正常物理伤害(狼的ATTACK_DAMAGE属性已设为6.0=3心)
    // 不再拦截, 让狼自然攻击(这样击杀检测也能正常工作)

    @org.spongepowered.asm.mixin.Unique
    private static String extractOwnerFromTags(WolfEntity wolf) {
        for (String tag : wolf.getCommandTags()) {
            if (tag.startsWith("sanchez_alpha_")) return tag.substring("sanchez_alpha_".length());
            if (tag.startsWith("sanchez_pack_")) return tag.substring("sanchez_pack_".length());
        }
        return null;
    }
}
