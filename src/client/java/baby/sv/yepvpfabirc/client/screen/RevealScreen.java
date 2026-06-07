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

public class RevealScreen extends Screen {

    private final List<PlayerEntry> playerEntries;

    private static final int BG_COLOR = 0xDD1A1A2E;
    private static final int HEADER_BG = 0xDD3B0D0D;
    private static final int BORDER_COLOR = 0xFFE74C3C;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF2ECC71;
    private static final int TEXT_YELLOW = 0xFFF1C40F;
    private static final int TEXT_RED = 0xFFE74C3C;
    private static final int TEXT_GRAY = 0xFF999999;
    private static final int ROW_ALT = 0x22FFFFFF;

    public RevealScreen(String jsonData) {
        super(Text.literal("明牌"));
        this.playerEntries = new ArrayList<>();
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        JsonArray playersArr = json.getAsJsonArray("players");
        for (int i = 0; i < playersArr.size(); i++) {
            JsonObject p = playersArr.get(i).getAsJsonObject();
            playerEntries.add(new PlayerEntry(
                    p.get("name").getAsString(),
                    p.get("roleName").getAsString(),
                    p.get("roleDesc").getAsString(),
                    p.get("alive").getAsBoolean(),
                    p.get("lives").getAsInt(),
                    p.get("kills").getAsInt()
            ));
        }
    }

    @Override
    protected void init() {
        int buttonW = 120;
        int buttonH = 20;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a确认"), btn -> close())
                .dimensions(this.width / 2 - buttonW / 2, this.height - 35, buttonW, buttonH)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xBB000000);

        int panelW = 360;
        int rowH = 28;
        int headerH = 30;
        int panelH = headerH + playerEntries.size() * rowH + 16;
        int maxH = this.height - 80;
        if (panelH > maxH) panelH = maxH;

        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2 - 15;

        // Panel
        context.fill(px, py, px + panelW, py + panelH, BG_COLOR);
        context.fill(px, py, px + panelW, py + 1, BORDER_COLOR);
        context.fill(px, py + panelH - 1, px + panelW, py + panelH, BORDER_COLOR);
        context.fill(px, py, px + 1, py + panelH, BORDER_COLOR);
        context.fill(px + panelW - 1, py, px + panelW, py + panelH, BORDER_COLOR);

        // Header
        context.fill(px + 1, py + 1, px + panelW - 1, py + headerH, HEADER_BG);
        String title = "⚠ 明牌阶段 - 所有职业已公开 ⚠";
        int titleW = this.textRenderer.getWidth(title);
        context.drawText(this.textRenderer, title, px + (panelW - titleW) / 2, py + 10, TEXT_RED, true);

        // Player list
        int y = py + headerH + 4;
        for (int i = 0; i < playerEntries.size(); i++) {
            PlayerEntry e = playerEntries.get(i);
            if (y + rowH > py + panelH - 4) break;

            // Alternating row bg
            if (i % 2 == 0) {
                context.fill(px + 2, y, px + panelW - 2, y + rowH, ROW_ALT);
            }

            int textColor = e.alive ? TEXT_WHITE : TEXT_GRAY;

            // Name
            context.drawText(this.textRenderer, e.name, px + 10, y + 4, textColor, true);

            // Role
            context.drawText(this.textRenderer, "§e" + e.roleName, px + 100, y + 4, TEXT_YELLOW, true);

            // Status
            String status = e.alive ? "§a存活 ❤" + e.lives : "§c已淘汰";
            context.drawText(this.textRenderer, status, px + 240, y + 4, e.alive ? TEXT_GREEN : TEXT_RED, true);

            // Kills
            context.drawText(this.textRenderer, "§7击杀:" + e.kills, px + 310, y + 4, TEXT_GRAY, false);

            // Desc (second line)
            String desc = e.roleDesc;
            if (desc.length() > 45) desc = desc.substring(0, 45) + "..";
            context.drawText(this.textRenderer, "§7" + desc, px + 14, y + 16, TEXT_GRAY, false);

            y += rowH;
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

    private record PlayerEntry(String name, String roleName, String roleDesc, boolean alive, int lives, int kills) {}
}
