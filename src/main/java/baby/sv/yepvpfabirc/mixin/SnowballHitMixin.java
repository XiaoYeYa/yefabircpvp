package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(SnowballEntity.class)
public abstract class SnowballHitMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void onSnowballHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        SnowballEntity snowball = (SnowballEntity) (Object) this;
        Entity owner = snowball.getOwner();
        Entity target = entityHitResult.getEntity();

        if (!(owner instanceof ServerPlayerEntity thrower)) return;
        if (!(target instanceof ServerPlayerEntity victim)) return;

        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        PlayerData throwerData = gm.getPlayerData(thrower.getUuid());
        if (throwerData == null) return;

        // JIEZU: 蜘蛛茧晕眩5秒(完全定身+无法攻击)
        if (throwerData.getRole() == Role.JIEZU) {
            PlayerData victimData = gm.getPlayerData(victim.getUuid());
            if (victimData != null) {
                long currentTick = victim.getEntityWorld().getTime();
                victimData.setStunUntilTick(currentTick + 100); // 5秒定身
            }
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 255, false, false, true));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0, false, false, true));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 255, false, false, true));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 255, false, false, true));
            thrower.sendMessage(net.minecraft.text.Text.literal("§a蜘蛛茧命中 " + victim.getGameProfile().name() + "！对方晕眩5秒！"), true);
            victim.sendMessage(net.minecraft.text.Text.literal("§c§l你被蜘蛛茧命中！无法移动和攻击5秒！"), true);
        }

        // YUSUI: 男娘雪球命中
        if (throwerData.getRole() == Role.YUSUI) {
            baby.sv.yepvpfabirc.skill.SkillHandler.onYusuiSnowballHit(thrower, victim);
        }
    }

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onSnowballCollision(HitResult hitResult, CallbackInfo ci) {
        SnowballEntity snowball = (SnowballEntity) (Object) this;
        if (snowball.getEntityWorld().isClient()) return;

        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        // 检查是否为ALLAND颜料弹
        int[] colorData = gm.getPaintProjectileColor(snowball.getId());
        UUID ownerUuid = gm.getPaintProjectileOwner(snowball.getId());
        if (colorData == null || ownerUuid == null) return;

        int color = colorData[0];

        // 获取命中位置
        BlockPos hitPos;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            hitPos = ((BlockHitResult) hitResult).getBlockPos();
        } else {
            hitPos = snowball.getBlockPos();
        }

        gm.onPaintProjectileHit(hitPos, color, ownerUuid);
    }
}
