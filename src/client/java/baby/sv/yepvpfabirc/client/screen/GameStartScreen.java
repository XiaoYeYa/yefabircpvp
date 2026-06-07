package baby.sv.yepvpfabirc.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class GameStartScreen extends Screen {

    private final String roleName;
    private final String roleDesc;
    private final int lives;
    private final String playerName;
    private final List<String> rules;

    // Colors
    private static final int BG_COLOR = 0xDD1A1A2E;
    private static final int HEADER_BG = 0xDD0D0D3B;
    private static final int BORDER_COLOR = 0xFF2ECC71;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF2ECC71;
    private static final int TEXT_YELLOW = 0xFFF1C40F;
    private static final int TEXT_GRAY = 0xFFBBBBBB;

    public GameStartScreen(String jsonData) {
        super(Text.literal("游戏开始"));
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        this.roleName = json.get("roleName").getAsString();
        this.roleDesc = json.get("roleDesc").getAsString();
        this.lives = json.get("lives").getAsInt();
        this.playerName = json.get("playerName").getAsString();
        this.rules = new ArrayList<>();
        JsonArray rulesArr = json.getAsJsonArray("rules");
        for (int i = 0; i < rulesArr.size(); i++) {
            rules.add(rulesArr.get(i).getAsString());
        }
    }

    @Override
    protected void init() {
        int buttonW = 120;
        int buttonH = 20;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a确认开始"), btn -> close())
                .dimensions(this.width / 2 - buttonW / 2, this.height - 40, buttonW, buttonH)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark overlay
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int panelW = 320;
        int panelH = 240;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2 - 10;

        // Panel background
        context.fill(px, py, px + panelW, py + panelH, BG_COLOR);
        // Border
        context.fill(px, py, px + panelW, py + 1, BORDER_COLOR);
        context.fill(px, py + panelH - 1, px + panelW, py + panelH, BORDER_COLOR);
        context.fill(px, py, px + 1, py + panelH, BORDER_COLOR);
        context.fill(px + panelW - 1, py, px + panelW, py + panelH, BORDER_COLOR);

        // Header
        context.fill(px + 1, py + 1, px + panelW - 1, py + 26, HEADER_BG);
        String title = "⚔ 夜喵喵愚人节大乱斗 ⚔";
        int titleW = this.textRenderer.getWidth(title);
        context.drawText(this.textRenderer, title, px + (panelW - titleW) / 2, py + 8, TEXT_GREEN, true);

        // Player name and role
        int y = py + 34;
        context.drawText(this.textRenderer, "§e玩家: §f" + playerName, px + 12, y, TEXT_WHITE, true);
        y += 16;
        context.drawText(this.textRenderer, "§a职业: §e" + roleName, px + 12, y, TEXT_GREEN, true);
        y += 14;
        context.drawText(this.textRenderer, "§7" + roleDesc, px + 12, y, TEXT_GRAY, false);
        y += 14;

        String heartsStr = "§c生命: ";
        for (int i = 0; i < Math.min(lives, 10); i++) heartsStr += "❤";
        context.drawText(this.textRenderer, heartsStr, px + 12, y, 0xFFE74C3C, true);
        y += 20;

        // Divider
        context.fill(px + 10, y, px + panelW - 10, y + 1, 0x55FFFFFF);
        y += 8;

        // Rules
        context.drawText(this.textRenderer, "§e§l游戏规则:", px + 12, y, TEXT_YELLOW, true);
        y += 14;
        for (String rule : rules) {
            context.drawText(this.textRenderer, "  • " + rule, px + 12, y, TEXT_WHITE, false);
            y += 12;
        }

        super.render(context, mouseX, mouseY, delta);
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
