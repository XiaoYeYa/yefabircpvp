package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(FireworkRocketItem.class)
public class LayiFireworkMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onFireworkUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) return;
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        ItemStack stack = player.getStackInHand(hand);
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return;

        List<Text> lines = lore.lines();
        for (Text line : lines) {
            String raw = line.getString();
            int idx = raw.indexOf("LAYI_MISSILE:");
            if (idx >= 0) {
                String uuidStr = raw.substring(idx + "LAYI_MISSILE:".length()).trim();
                // 去除可能残留的§格式码
                uuidStr = uuidStr.replaceAll("§[0-9a-fk-or]", "");
                try {
                    UUID layiUuid = UUID.fromString(uuidStr);
                    // 消耗烟花
                    stack.decrement(1);
                    // 触发导弹发射
                    gm.onLayiMissileFireworkUsed(player, layiUuid);
                    cir.setReturnValue(ActionResult.SUCCESS);
                } catch (Exception ignored) {}
                return;
            }
        }
    }
}
