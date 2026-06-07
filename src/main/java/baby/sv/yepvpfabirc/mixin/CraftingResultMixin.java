package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"), cancellable = true)
    private void onPreventChestCraft(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!GameManager.getInstance().isGameActive()) return;
        if (stack.isOf(Items.CHEST) || stack.isOf(Items.TRAPPED_CHEST)) {
            stack.setCount(0);
            ci.cancel();
            player.sendMessage(net.minecraft.text.Text.literal("§c游戏中禁止合成箱子！"), true);
        }
    }
}
