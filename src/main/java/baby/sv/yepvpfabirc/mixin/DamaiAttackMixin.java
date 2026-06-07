package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import baby.sv.yepvpfabirc.skill.SkillHandler;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class DamaiAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Entity target, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        PlayerData selfData = gm.getPlayerData(self.getUuid());
        if (selfData == null) return;

        // SANCHEZ攻击时通知狼群锁定目标(对任何实体生效)
        if (selfData.getRole() == Role.SANCHEZ) {
            gm.onSanchezPlayerAttack(self, target);
        }

        if (!(target instanceof ServerPlayerEntity targetPlayer)) return;
        PlayerData targetData = gm.getPlayerData(targetPlayer.getUuid());

        // 复活无敌期间无法攻击也无法被攻击
        long currentTick = self.getEntityWorld().getTime();
        if (selfData.isInvincible(currentTick)) {
            self.sendMessage(net.minecraft.text.Text.literal("§c复活无敌期间无法攻击！"), true);
            ci.cancel();
            return;
        }
        if (targetData != null && targetData.isInvincible(currentTick)) {
            self.sendMessage(net.minecraft.text.Text.literal("§c对方处于复活无敌期！"), true);
            ci.cancel();
            return;
        }

        // JVJV红莲华蓄力期间无法攻击
        if (selfData.getRole() == Role.JVJV && selfData.isRedLotusCharging(currentTick)) {
            self.sendMessage(net.minecraft.text.Text.literal("§c红莲华蓄力中，无法操作！"), true);
            ci.cancel();
            return;
        }

        // ST声波蓄力期间无法攻击
        if (selfData.getRole() == Role.ST && selfData.getSonicPendingTick() > 0) {
            self.sendMessage(net.minecraft.text.Text.literal("§c声波蓄力中，无法攻击！"), true);
            ci.cancel();
            return;
        }

        // Heli攻击揭露对方位置
        if (selfData.getRole() == Role.HELI) {
            SkillHandler.heliRevealOnAttack(self, targetPlayer);
        }

        // DASHA攻击后显形2秒
        if (selfData.getRole() == Role.DASHA) {
            selfData.setDashaVisibleUntilTick(currentTick + 40); // 2秒
        }
    }
}
