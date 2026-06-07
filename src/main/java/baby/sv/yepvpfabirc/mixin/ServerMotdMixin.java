package baby.sv.yepvpfabirc.mixin;

import baby.sv.yepvpfabirc.game.GameManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Random;

@Mixin(MinecraftServer.class)
public class ServerMotdMixin {

    private static final Random MOTD_RANDOM = new Random();

    private static final List<String> MOTD_LINES = List.of(
            "§e每个人都有自己的故事...",
            "§b今晚的月色真美",
            "§d你好，欢迎来到大乱斗！",
            "§a活下来的人才有资格说话",
            "§c不要相信任何人",
            "§6问好记得回复，不然会死的哦~",
            "§e谁是最后的赢家？",
            "§b鱼碎的后宫在哪里？",
            "§d大麦永远不会死！（大概）",
            "§a节足动物在暗处注视着你...",
            "§c大鲨在水里等你",
            "§6Ciallo～(∠・ω< )⌒☆",
            "§e画中世界的门已经打开",
            "§b赏金猎人正在寻找目标",
            "§d爆米花的镭射即将就绪",
            "§a油炸意面请你吃猫罐头",
            "§c蜡翼飞过天际",
            "§6诸神黄昏，狼群出击！",
            "§e智械危机：灵魂出窍中...",
            "§b但丁与罪人的羁绊",
            "§d大爆炸倒计时 3... 2... 1...",
            "§a抹茶巴菲钓到了什么？",
            "§c喷涂战士正在涂色中...",
            "§6立体艺术：一个正方体困住了你",
            "§e柚子厨的声波正在蓄力..."
    );

    @Inject(method = "createMetadata", at = @At("RETURN"), cancellable = true)
    private void yepvp_modifyMotd(CallbackInfoReturnable<ServerMetadata> cir) {
        ServerMetadata original = cir.getReturnValue();
        if (original == null) return;

        GameManager gm = GameManager.getInstance();
        boolean isOpen = gm.getLobbyManager().isServerOpen();
        long adminRemain = gm.getLobbyManager().getAdminOpenRemainingMs();
        boolean isGameActive = gm.isGameActive();

        // 第一行: 服务器名 + 开放状态
        String line1;
        if (isGameActive) {
            line1 = "§b§l夜喵喵愚人节大乱斗 §8| §c§l游戏进行中...";
        } else if (adminRemain > 0) {
            long mins = adminRemain / 60000;
            line1 = "§b§l夜喵喵愚人节大乱斗 §8| §a§l临时开放中 §e" + mins + "分钟";
        } else if (isOpen) {
            line1 = "§b§l夜喵喵愚人节大乱斗 §8| §a§l✦ 正在开放 ✦";
        } else {
            String nextOpen = gm.getLobbyManager().getNextOpenDay();
            line1 = "§b§l夜喵喵愚人节大乱斗 §8| §c§l✘ 未开放 §7下次: §e" + nextOpen;
        }

        // 第二行: 随机语录
        String line2 = MOTD_LINES.get(MOTD_RANDOM.nextInt(MOTD_LINES.size()));

        Text motd = Text.literal(line1 + "\n" + line2);

        cir.setReturnValue(new ServerMetadata(
                motd,
                original.players(),
                original.version(),
                original.favicon(),
                original.secureChatEnforced()
        ));
    }
}
