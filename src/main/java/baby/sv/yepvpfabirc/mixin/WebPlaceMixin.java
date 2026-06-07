package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class WebPlaceMixin {

    @Inject(method = "onPlaced", at = @At("HEAD"))
    private void onBlockPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (world.isClient()) return;
        if (!GameManager.getInstance().isGameActive()) return;
        if (!state.isOf(Blocks.COBWEB)) return;
        if (!(placer instanceof ServerPlayerEntity player)) return;

        PlayerData data = GameManager.getInstance().getPlayerData(player.getUuid());
        if (data == null || data.getRole() != Role.JIEZU) return;

        if (data.getWebPositions().size() >= 3) {
            // 已放置3个，移除最旧的
            BlockPos oldest = data.getWebPositions().remove(0);
            world.setBlockState(oldest, Blocks.AIR.getDefaultState());
            player.sendMessage(net.minecraft.text.Text.literal("§c最旧的蛛网被替换！"), true);
        }
        data.getWebPositions().add(pos);
        player.sendMessage(net.minecraft.text.Text.literal("§a放置了特殊蛛网！(" + data.getWebPositions().size() + "/3) 位置: " + pos.getX() + "," + pos.getY() + "," + pos.getZ()), false);
        GameManager.getInstance().broadcastMessage("§8§l[节足动物] §r§e" + player.getGameProfile().name() + " §8放置了特殊蛛网！");
    }
}
