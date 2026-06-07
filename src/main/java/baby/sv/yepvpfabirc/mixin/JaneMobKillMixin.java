package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class JaneMobKillMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onMobDeath(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        // 被杀的是玩家则不处理
        if (self instanceof ServerPlayerEntity) return;

        Entity attacker = damageSource.getAttacker();
        if (attacker == null) return;

        // 玩家直接击杀
        if (attacker instanceof ServerPlayerEntity player) {
            PlayerData data = gm.getPlayerData(player.getUuid());
            if (data == null) return;
            if (data.getRole() == Role.JANE) {
                gm.onJaneMobKill(player, self);
            } else if (data.getRole() == Role.SANCHEZ) {
                gm.onSanchezMobKill(player, self);
            }
            return;
        }

        // 狼击杀: 检查是否是SANCHEZ的狼(头狼或狼群), 从tag提取owner UUID
        if (attacker instanceof net.minecraft.entity.passive.WolfEntity wolf) {
            for (String tag : wolf.getCommandTags()) {
                String prefix = null;
                if (tag.startsWith("sanchez_alpha_")) prefix = "sanchez_alpha_";
                else if (tag.startsWith("sanchez_pack_")) prefix = "sanchez_pack_";
                if (prefix != null) {
                    try {
                        java.util.UUID ownerUuid = java.util.UUID.fromString(tag.substring(prefix.length()));
                        gm.onSanchezWolfKill(ownerUuid);
                    } catch (IllegalArgumentException ignored) {}
                    break;
                }
            }
        }
    }
}
