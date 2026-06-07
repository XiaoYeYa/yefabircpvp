package baby.sv.yepvpfabirc.client.screen;

import baby.sv.yepvpfabirc.network.NetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class JaneShopScreen extends Screen {

    private int bounty;
    private int power;
    private int quickCharge;
    private int multishot;

    private static final int BG_COLOR = 0xDD1A1A2E;
    private static final int HEADER_BG = 0xDD3B2D00;
    private static final int BORDER_COLOR = 0xFFD4A017;
    private static final int CARD_BG = 0xCC222244;
    private static final int CARD_HOVER = 0xCC333366;
    private static final int CARD_MAXED = 0xCC444444;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_RED = 0xFFFF5555;
    private static final int TEXT_GRAY = 0xFF999999;
    private static final int TEXT_YELLOW = 0xFFFFFF55;

    private static final int PANEL_W = 340;
    private static final int CARD_W = 100;
    private static final int CARD_H = 120;
    private static final int CARD_GAP = 10;
    private static final int COST = 75;

    public JaneShopScreen(int bounty, int power, int quickCharge, int multishot) {
        super(Text.literal("赏金猎人商店"));
        this.bounty = bounty;
        this.power = power;
        this.quickCharge = quickCharge;
        this.multishot = multishot;
    }

    public void updateData(int bounty, int power, int quickCharge, int multishot) {
        this.bounty = bounty;
        this.power = power;
        this.quickCharge = quickCharge;
        this.multishot = multishot;
        clearAndInit();
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int cardsY = (this.height - CARD_H) / 2 + 10;
        int startX = px + (PANEL_W - 3 * CARD_W - 2 * CARD_GAP) / 2;

        // 力量升级按钮
        addUpgradeButton(startX, cardsY + CARD_H + 6, CARD_W, 0, power, 5);
        // 快速装填升级按钮
        addUpgradeButton(startX + CARD_W + CARD_GAP, cardsY + CARD_H + 6, CARD_W, 1, quickCharge, 3);
        // 多重射击升级按钮
        addUpgradeButton(startX + 2 * (CARD_W + CARD_GAP), cardsY + CARD_H + 6, CARD_W, 2, multishot, 1);

        // 关闭按钮
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§c关闭"), btn -> close())
                .dimensions(this.width / 2 - 40, cardsY + CARD_H + 36, 80, 20)
                .build());
    }

    private void addUpgradeButton(int x, int y, int w, int type, int currentLevel, int maxLevel) {
        boolean maxed = currentLevel >= maxLevel;
        boolean canAfford = bounty >= COST;
        String label = maxed ? "§7已满级" : (canAfford ? "§a升级 §e-" + COST : "§c赏金不足");
        this.addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
            if (!maxed && canAfford) {
                ClientPlayNetworking.send(new NetworkHandler.JaneShopBuyPayload(type));
            }
        }).dimensions(x, y, w, 18).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 半透明背景
        ctx.fill(0, 0, this.width, this.height, 0xBB000000);

        int px = (this.width - PANEL_W) / 2;
        int cardsY = (this.height - CARD_H) / 2 + 10;
        int panelTop = cardsY - 50;
        int panelBottom = cardsY + CARD_H + 64;

        // 面板背景+边框
        ctx.fill(px, panelTop, px + PANEL_W, panelBottom, BG_COLOR);
        drawBorder(ctx, px, panelTop, PANEL_W, panelBottom - panelTop, BORDER_COLOR);

        // 标题栏
        ctx.fill(px + 1, panelTop + 1, px + PANEL_W - 1, panelTop + 28, HEADER_BG);
        String title = "⚔ 赏金猎人商店 ⚔";
        int titleW = this.textRenderer.getWidth(title);
        ctx.drawText(this.textRenderer, title, px + (PANEL_W - titleW) / 2, panelTop + 9, TEXT_GOLD, true);

        // 赏金显示
        String bountyText = "§6赏金: §e" + bounty;
        int bountyW = this.textRenderer.getWidth(bountyText);
        ctx.drawText(this.textRenderer, bountyText, px + (PANEL_W - bountyW) / 2, panelTop + 33, TEXT_GOLD, true);

        // 三张升级卡片
        int startX = px + (PANEL_W - 3 * CARD_W - 2 * CARD_GAP) / 2;

        drawUpgradeCard(ctx, startX, cardsY, CARD_W, CARD_H, mouseX, mouseY,
                "§b⚡ 力量", "Power", power, 5,
                "弩箭伤害增加", "每级+0.5心");

        drawUpgradeCard(ctx, startX + CARD_W + CARD_GAP, cardsY, CARD_W, CARD_H, mouseX, mouseY,
                "§a⚡ 快速装填", "Quick Charge", quickCharge, 3,
                "装填时间缩短", "每级-0.25秒");

        drawUpgradeCard(ctx, startX + 2 * (CARD_W + CARD_GAP), cardsY, CARD_W, CARD_H, mouseX, mouseY,
                "§d⚡ 多重射击", "Multishot", multishot, 1,
                "同时发射3支箭", "最大1级");

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawUpgradeCard(DrawContext ctx, int x, int y, int w, int h,
                                  int mouseX, int mouseY,
                                  String name, String engName, int level, int maxLevel,
                                  String desc1, String desc2) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        boolean maxed = level >= maxLevel;

        int bg = maxed ? CARD_MAXED : (hovered ? CARD_HOVER : CARD_BG);
        ctx.fill(x, y, x + w, y + h, bg);

        int borderCol = maxed ? TEXT_GRAY : BORDER_COLOR;
        drawBorder(ctx, x, y, w, h, borderCol);

        // 名称
        int nameW = this.textRenderer.getWidth(name);
        ctx.drawText(this.textRenderer, name, x + (w - nameW) / 2, y + 8, TEXT_WHITE, true);

        // 英文名
        int engW = this.textRenderer.getWidth(engName);
        ctx.drawText(this.textRenderer, engName, x + (w - engW) / 2, y + 22, TEXT_GRAY, false);

        // 等级条
        int barX = x + 10;
        int barY = y + 38;
        int barW = w - 20;
        int barH = 8;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        if (maxLevel > 0) {
            int filledW = (int)((float) level / maxLevel * barW);
            int barColor = maxed ? 0xFF55FF55 : 0xFFFFAA00;
            ctx.fill(barX, barY, barX + filledW, barY + barH, barColor);
        }

        // 等级文字
        String levelText = maxed ? "§a满级 " + level + "/" + maxLevel : "§e" + level + "/" + maxLevel;
        int lvlW = this.textRenderer.getWidth(levelText);
        ctx.drawText(this.textRenderer, levelText, x + (w - lvlW) / 2, y + 52, TEXT_WHITE, true);

        // 描述
        int d1W = this.textRenderer.getWidth(desc1);
        ctx.drawText(this.textRenderer, desc1, x + (w - d1W) / 2, y + 70, TEXT_GRAY, false);
        int d2W = this.textRenderer.getWidth(desc2);
        ctx.drawText(this.textRenderer, desc2, x + (w - d2W) / 2, y + 82, TEXT_GRAY, false);

        // 费用
        if (!maxed) {
            String costText = "§6" + COST + " 赏金";
            int cW = this.textRenderer.getWidth(costText);
            int costColor = bounty >= COST ? TEXT_GREEN : TEXT_RED;
            ctx.drawText(this.textRenderer, costText, x + (w - cW) / 2, y + 100, costColor, true);
        } else {
            String maxText = "§7已满级";
            int mW = this.textRenderer.getWidth(maxText);
            ctx.drawText(this.textRenderer, maxText, x + (w - mW) / 2, y + 100, TEXT_GRAY, true);
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
