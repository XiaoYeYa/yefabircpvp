package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WolfEntity.class)
public abstract class SanchezWolfFeedMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void onInteractSanchezWolf(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        WolfEntity self = (WolfEntity) (Object) this;

        // 只拦截SANCHEZ的狼
        boolean isSanchezWolf = self.getCommandTags().stream().anyMatch(t -> t.startsWith("sanchez_"));
        if (!isSanchezWolf) return;

        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        // 禁止SANCHEZ玩家喂自己的狼(阻止恢复生命)
        if (player instanceof ServerPlayerEntity sp) {
            PlayerData data = gm.getPlayerData(sp.getUuid());
            if (data != null && data.getRole() == Role.SANCHEZ) {
                String playerUuidStr = sp.getUuid().toString();
                if (self.getCommandTags().stream().anyMatch(t -> t.contains(playerUuidStr))) {
                    cir.setReturnValue(ActionResult.FAIL);
                }
            }
        }
    }
}
