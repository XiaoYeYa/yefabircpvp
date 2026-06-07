package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class BaneOfArthropodsMixin {

    @org.spongepowered.asm.mixin.Unique
    private boolean yepvp_inDamageHandler = false;

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (yepvp_inDamageHandler) return;
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        PlayerData selfData = gm.getPlayerData(self.getUuid());
        if (selfData == null) return;

        // 复活无敌期间免疫所有伤害
        long currentTick = world.getTime();
        if (selfData.isInvincible(currentTick)) {
            cir.setReturnValue(false);
            return;
        }

        // 男娘+鱼碎互伤免疫: 男娘不能伤害男娘, 男娘不能伤害鱼碎, 鱼碎不能伤害男娘
        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            PlayerData attackerData = gm.getPlayerData(attacker.getUuid());
            if (attackerData != null) {
                boolean attackerIsFemboy = attackerData.isNanniang() || attackerData.getRole() == Role.YUSUI;
                boolean victimIsFemboy = selfData.isNanniang() || selfData.getRole() == Role.YUSUI;
                if (attackerIsFemboy && victimIsFemboy) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        // SANCHEZ: 头狼代承伤害 — 将伤害转移到头狼实体
        if (selfData.getRole() == Role.SANCHEZ && !selfData.isAlphaWolfDead() && selfData.getAlphaWolfEntityUuid() != null) {
            net.minecraft.entity.Entity alphaEntity = world.getEntity(selfData.getAlphaWolfEntityUuid());
            if (alphaEntity instanceof net.minecraft.entity.LivingEntity alphaWolf && alphaWolf.isAlive()) {
                // 伤害转移到头狼
                alphaWolf.damage(world, source, amount);
                // 同步头狼血量到PlayerData
                selfData.setAlphaWolfHealth(alphaWolf.getHealth());
                cir.setReturnValue(false); // 玩家不受伤
                return;
            }
        }

        // HELI揭露双倍伤害: 被揭露前15秒内受到的所有伤害翻倍
        for (PlayerData heliData : gm.getAllPlayerData().values()) {
            if (heliData.getRole() != Role.HELI) continue;
            if (heliData.isInHeliDoubleDamageWindow(self.getUuid(), currentTick)) {
                // 额外施加等量伤害(翻倍效果): 直接扣血绕过护甲
                float bonusDamage = amount;
                float newHp = Math.max(self.getHealth() - bonusDamage, 0);
                self.setHealth(newHp);
                if (newHp <= 0) {
                    yepvp_inDamageHandler = true;
                    try {
                        self.kill(world);
                    } finally {
                        yepvp_inDamageHandler = false;
                    }
                }
                break;
            }
        }

        // LAYI: 飞行中受伤不打断飞行
        if (selfData.getRole() == Role.LAYI && selfData.isLayiFlying() && self.isGliding()) {
            // 让伤害正常结算, 但之后立刻恢复飞行状态(通过标记, 在tick中恢复)
            // MC默认受伤会取消鞘翅飞行, 我们在伤害结算后立刻恢复
        }

        // MAYPOOR: 免疫摔落伤害(被动)
        if (selfData.getRole() == Role.MAYPOOR && source == world.getDamageSources().fall()) {
            cir.setReturnValue(false);
            return;
        }

        // 蜡翼箭矢命中: 5% maxHP真伤
        if (source.getSource() instanceof net.minecraft.entity.projectile.ArrowEntity arrow) {
            for (String tag : arrow.getCommandTags()) {
                if (tag.startsWith("layi_arrow_")) {
                    String layiUuidStr = tag.substring("layi_arrow_".length());
                    try {
                        java.util.UUID layiUuid = java.util.UUID.fromString(layiUuidStr);
                        ServerPlayerEntity layiPlayer = world.getServer().getPlayerManager().getPlayer(layiUuid);
                        if (layiPlayer != null && self != layiPlayer) {
                            float trueDmg = self.getMaxHealth() * 0.05f;
                            float newHealth = Math.max(self.getHealth() - trueDmg, 0);
                            self.setHealth(newHealth);
                            if (newHealth <= 0) {
                                yepvp_inDamageHandler = true;
                                try { self.kill(world); } finally { yepvp_inDamageHandler = false; }
                            }
                            arrow.discard();
                            cir.setReturnValue(false); // 取消原始伤害, 我们已手动扣血
                            return;
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }

        // 以下是节足专属逻辑
        if (selfData.getRole() != Role.JIEZU) return;

        // 免疫掉落伤害
        if (source == world.getDamageSources().fall()) {
            cir.setReturnValue(false);
            return;
        }

        // 节肢杀手秒杀
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) return;

        ItemStack weapon = attacker.getMainHandStack();
        var bane = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(Enchantments.BANE_OF_ARTHROPODS).orElse(null);
        if (bane != null) {
            int level = weapon.getEnchantments().getLevel(bane);
            if (level > 0) {
                self.kill(world);
                gm.broadcastMessage("§a" + self.getGameProfile().name() + " §c被节肢杀手秒杀了！");
                cir.setReturnValue(true);
            }
        }
    }
}
