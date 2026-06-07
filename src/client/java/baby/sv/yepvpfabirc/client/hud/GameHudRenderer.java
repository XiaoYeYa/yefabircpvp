package baby.sv.yepvpfabirc.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

public class GameHudRenderer implements HudRenderCallback {

    // Green theme colors (ARGB)
    private static final int BG_COLOR = 0xCC1A2E1A;          // Dark green background
    private static final int BG_HEADER = 0xDD0D3B0D;         // Darker green header
    private static final int BORDER_COLOR = 0xFF2ECC71;       // Bright green border
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF2ECC71;
    private static final int TEXT_LIGHT_GREEN = 0xFF82E0AA;
    private static final int TEXT_YELLOW = 0xFFF1C40F;
    private static final int TEXT_RED = 0xFFE74C3C;
    private static final int TEXT_GRAY = 0xFFBBBBBB;
    private static final int ACCENT_GREEN = 0xFF27AE60;
    private static final int SKILL_BG = 0xCC0A3D0A;
    private static final int SKILL_BORDER = 0xFF2ECC71;
    private static final int HEART_RED = 0xFFE74C3C;
    private static final int WARNING_BG = 0xCCCC3300;
    private static final int WARNING_BORDER = 0xFFFF6600;
    private static final int SIDEBAR_BG = 0xCC1A1A2E;        // Dark blue-ish sidebar
    private static final int SIDEBAR_HEADER = 0xDD0D0D3B;
    private static final int SIDEBAR_BORDER = 0xFF3498DB;     // Blue border
    private static final int TEXT_CYAN = 0xFF00D4FF;
    private static final int TEXT_ORANGE = 0xFFFF8C00;

    // 跑马灯状态
    private long marqueeOffset = 0;
    private long lastMarqueeTick = 0;

    public static void register() {
        HudRenderCallback.EVENT.register(new GameHudRenderer());
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        // 服务端未安装本mod时不会发送HUD数据, 不渲染任何UI
        if (!HudDataCache.hasEverReceived()) return;

        TextRenderer tr = client.textRenderer;
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // 游戏结束画面
        if (HudDataCache.isGameEnded()) {
            drawGameEndScreen(context, tr, screenW, screenH);
            return;
        }

        // 游戏进行中
        if (HudDataCache.isGameActive()) {
            drawScoreboard(context, tr, screenW);
            drawRolePanel(context, tr, screenH);
            drawSkillBar(context, tr, screenW, screenH);
            drawBorderSidebar(context, tr, screenW, screenH);

            // 左上角小地图(在死亡覆盖之前先画, 死亡覆盖会覆盖一半)
            MinimapRenderer.render(context, client, tr);

            if (HudDataCache.hasGreeting()) {
                drawGreetingWarning(context, tr, screenW, screenH);
            }

            if (!HudDataCache.isSelfAlive()) {
                drawDeathOverlay(context, tr, screenW, screenH);
            }

            drawTipMessage(context, tr, screenW, screenH);
            return;
        }

        // 游戏未开始 - 等待画面
        drawWaitingScreen(context, tr, screenW, screenH);
    }

    private void drawScoreboard(DrawContext context, TextRenderer tr, int screenW) {
        List<HudDataCache.ScoreEntry> sb = HudDataCache.getScoreboard();
        if (sb.isEmpty()) return;

        int playerCount = sb.size();
        int headerH = 14;
        int entryH = 11;
        int padX = 6;
        int padY = 3;

        // 动态计算每列宽度：名字最大宽度 + 间隔 + 职业最大宽度 + 间隔 + 生命宽度
        int maxNameW = 0;
        int maxRoleW = 0;
        for (HudDataCache.ScoreEntry e : sb) {
            String name = e.name();
            if (name.length() > 8) name = name.substring(0, 8) + "..";
            maxNameW = Math.max(maxNameW, tr.getWidth(name));
            String role = e.role();
            if (role.length() > 4) role = role.substring(0, 4) + "..";
            maxRoleW = Math.max(maxRoleW, tr.getWidth("[" + role + "]"));
        }
        int livesW = tr.getWidth("\u2764\u221e") + 4;
        int gapBetween = 4;
        int colW = maxNameW + gapBetween + maxRoleW + gapBetween + livesW;

        // 决定列数和每列行数
        int perCol;
        if (playerCount <= 4) perCol = playerCount;
        else if (playerCount <= 8) perCol = 4;
        else perCol = 5;

        int cols = (playerCount + perCol - 1) / perCol;
        int rowsInTallestCol = Math.min(playerCount, perCol);
        int colGap = 8;
        int totalW = cols * colW + (cols - 1) * colGap + padX * 2;
        int totalH = headerH + padY + rowsInTallestCol * entryH + padY;

        int x = (screenW - totalW) / 2;
        int y = 2;

        drawRoundedRect(context, x, y, totalW, totalH, 3, BG_COLOR);
        drawRoundedBorder(context, x, y, totalW, totalH, 3, BORDER_COLOR);

        // Header
        drawRoundedRect(context, x, y, totalW, headerH, 3, BG_HEADER);
        String title = "\u2694 \u611a\u4eba\u8282\u5927\u4e71\u6597 \u2694";
        int titleW = tr.getWidth(title);
        context.drawText(tr, title, x + (totalW - titleW) / 2, y + 3, TEXT_GREEN, true);

        // 渲染玩家条目
        for (int i = 0; i < sb.size(); i++) {
            int col = i / perCol;
            int row = i % perCol;
            int ex = x + padX + col * (colW + colGap);
            int ey = y + headerH + padY + row * entryH;

            HudDataCache.ScoreEntry e = sb.get(i);
            int textColor = e.alive() ? TEXT_WHITE : TEXT_GRAY;

            // 名字
            String name = e.name();
            if (name.length() > 8) name = name.substring(0, 8) + "..";
            context.drawText(tr, name, ex, ey, textColor, false);

            // 职业（紧跟名字最大宽度后）
            String role = e.role();
            if (role.length() > 4) role = role.substring(0, 4) + "..";
            String roleStr = "[" + role + "]";
            context.drawText(tr, roleStr, ex + maxNameW + gapBetween, ey, TEXT_LIGHT_GREEN, false);

            // 生命（右对齐）
            String livesStr = e.lives() == -1 ? "\u221e" : String.valueOf(e.lives());
            String heartStr = "\u2764" + livesStr;
            int heartW = tr.getWidth(heartStr);
            context.drawText(tr, heartStr, ex + colW - heartW, ey, e.alive() ? HEART_RED : TEXT_GRAY, false);
        }
    }

    private void drawRolePanel(DrawContext context, TextRenderer tr, int screenH) {
        int panelW = 140;
        int panelH = 70;
        int x = 4;
        int y = screenH / 2 - panelH / 2;

        drawRoundedRect(context, x, y, panelW, panelH, 4, BG_COLOR);
        drawRoundedBorder(context, x, y, panelW, panelH, 4, BORDER_COLOR);

        // Role header
        drawRoundedRect(context, x, y, panelW, 14, 4, BG_HEADER);
        String roleTitle = "\u2726 " + HudDataCache.getSelfRoleName();
        context.drawText(tr, roleTitle, x + 6, y + 3, TEXT_GREEN, true);

        // Lives
        int ly = y + 18;
        String livesLabel = "\u2764 \u751f\u547d: ";
        context.drawText(tr, livesLabel, x + 6, ly, TEXT_LIGHT_GREEN, false);
        String livesVal;
        if (HudDataCache.getSelfLives() == -1) {
            livesVal = "\u221e";
        } else {
            livesVal = "";
            for (int i = 0; i < HudDataCache.getSelfLives(); i++) livesVal += "\u2764 ";
        }
        context.drawText(tr, livesVal, x + 6 + tr.getWidth(livesLabel), ly, HEART_RED, false);

        // Kills
        int ky = ly + 12;
        context.drawText(tr, "\u2620 \u51fb\u6740: " + HudDataCache.getSelfKills(), x + 6, ky, TEXT_LIGHT_GREEN, false);
        context.drawText(tr, "  \u6b7b\u4ea1: " + HudDataCache.getSelfDeaths(), x + 70, ky, TEXT_GRAY, false);

        // Score
        int sy = ky + 12;
        context.drawText(tr, "\u2605 \u5f97\u5206: " + HudDataCache.getSelfScore(), x + 6, sy, TEXT_YELLOW, false);

        // Revealed status
        int ry = sy + 12;
        if (HudDataCache.isRevealed()) {
            context.drawText(tr, "\u26a0 \u5df2\u660e\u724c", x + 6, ry, TEXT_RED, true);
        } else {
            context.drawText(tr, "\u2714 \u672a\u660e\u724c", x + 6, ry, TEXT_GREEN, false);
        }
    }

    private void drawSkillBar(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        List<HudDataCache.SkillInfo> skills = HudDataCache.getSkills();
        if (skills.isEmpty()) return;

        int skillBoxW = 70;
        int skillBoxH = 28;
        int gap = 6;
        int totalSkillsW = skills.size() * skillBoxW + (skills.size() - 1) * gap;
        int startX = (screenW - totalSkillsW) / 2;
        int startY = screenH - 80;

        for (int i = 0; i < skills.size(); i++) {
            HudDataCache.SkillInfo skill = skills.get(i);
            int sx = startX + i * (skillBoxW + gap);

            drawRoundedRect(context, sx, startY, skillBoxW, skillBoxH, 3, SKILL_BG);
            drawRoundedBorder(context, sx, startY, skillBoxW, skillBoxH, 3, SKILL_BORDER);

            // Key badge
            String keyBadge = "[" + skill.key() + "]";
            context.drawText(tr, keyBadge, sx + 4, startY + 4, TEXT_GREEN, true);

            // Skill name
            context.drawText(tr, skill.name(), sx + 4 + tr.getWidth(keyBadge) + 2, startY + 4, TEXT_WHITE, false);

            // Short desc (truncated)
            String desc = skill.desc();
            if (desc.length() > 10) desc = desc.substring(0, 10) + "..";
            context.drawText(tr, desc, sx + 4, startY + 16, TEXT_GRAY, false);
        }
    }

    private void drawGreetingWarning(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        int boxW = 240;
        int boxH = 36;
        int x = (screenW - boxW) / 2;
        int y = screenH / 2 - 60;

        drawRoundedRect(context, x, y, boxW, boxH, 4, WARNING_BG);
        drawRoundedBorder(context, x, y, boxW, boxH, 4, WARNING_BORDER);

        String line1 = "\u26a0 \u4e00\u540d\u73a9\u5bb6\u5411\u4f60\u95ee\u597d\uff01";
        String line2 = "\u8bf7\u8f93\u5165 /helloback hello hello hello \u56de\u590d\uff01";
        int w1 = tr.getWidth(line1);
        int w2 = tr.getWidth(line2);
        context.drawText(tr, line1, x + (boxW - w1) / 2, y + 5, TEXT_YELLOW, true);
        context.drawText(tr, line2, x + (boxW - w2) / 2, y + 19, TEXT_RED, true);
    }

    private void drawBorderSidebar(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        int panelW = 130;
        int panelH = 96;
        int x = screenW - panelW - 4;
        int y = screenH / 2 - panelH / 2;

        drawRoundedRect(context, x, y, panelW, panelH, 4, SIDEBAR_BG);
        drawRoundedBorder(context, x, y, panelW, panelH, 4, SIDEBAR_BORDER);

        // Header
        drawRoundedRect(context, x, y, panelW, 14, 4, SIDEBAR_HEADER);
        String header = "\u2609 \u6e38\u620f\u4fe1\u606f";
        context.drawText(tr, header, x + 6, y + 3, TEXT_CYAN, true);

        int ly = y + 18;

        // \u6e38\u620f\u65f6\u95f4
        int sec = HudDataCache.getGameTimeSec();
        int min = sec / 60;
        int remSec = sec % 60;
        String timeStr = String.format("%d:%02d", min, remSec);
        context.drawText(tr, "\u23f0 \u65f6\u95f4: " + timeStr, x + 6, ly, TEXT_WHITE, false);
        ly += 12;

        // \u5f53\u524d\u534a\u5f84
        context.drawText(tr, "\u25ce \u534a\u5f84: " + HudDataCache.getCurrentBorderRadius(), x + 6, ly, TEXT_LIGHT_GREEN, false);
        ly += 12;

        // \u4e0b\u6b21\u7f29\u5708\u5012\u8ba1\u65f6
        int nextShrink = HudDataCache.getNextShrinkSec();
        int shrinkColor = nextShrink <= 30 ? TEXT_RED : (nextShrink <= 60 ? TEXT_ORANGE : TEXT_YELLOW);
        context.drawText(tr, "\u26a0 \u7f29\u5708: " + nextShrink + "s", x + 6, ly, shrinkColor, false);
        ly += 12;

        // \u76ee\u6807\u534a\u5f84
        context.drawText(tr, "\u2192 \u76ee\u6807: " + HudDataCache.getTargetBorderRadius(), x + 6, ly, TEXT_GRAY, false);
        ly += 12;

        // \u8ddd\u79bb\u8fb9\u754c
        int dist = HudDataCache.getDistToBorder();
        int distColor = dist <= 50 ? TEXT_RED : (dist <= 150 ? TEXT_ORANGE : TEXT_GREEN);
        context.drawText(tr, "\u21d4 \u8ddd\u5708: " + dist + "\u683c", x + 6, ly, distColor, false);
    }

    private void drawDeathOverlay(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        // 观战提示条(下移到小地图下方, 避开小地图区域 ~165px)
        int oy = 165;
        drawRoundedRect(context, 4, oy, 150, 24, 3, 0xAA000000);
        drawRoundedBorder(context, 4, oy, 150, 24, 3, TEXT_RED);
        context.drawText(tr, "\u2620 \u89c2\u6218\u6a21\u5f0f", 10, oy + 3, TEXT_RED, true);
        context.drawText(tr, "Z\u4e0a X\u4e0b V\u81ea\u7531\u89c6\u89d2", 10, oy + 13, TEXT_GRAY, false);

        // 右上角玩家信息竖条面板
        drawSpectatorStatsPanel(context, tr, screenW, screenH);
    }

    private void drawSpectatorStatsPanel(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        List<HudDataCache.ScoreEntry> sb = HudDataCache.getScoreboard();
        if (sb.isEmpty()) return;

        int padX = 5;
        int padY = 3;
        int rowH = 22;
        int headerH = 16;
        int panelW = 130;
        int panelH = headerH + padY + sb.size() * rowH + padY;

        int x = screenW - panelW - 4;
        int y = 4;

        drawRoundedRect(context, x, y, panelW, panelH, 4, 0xDD0A0A1E);
        drawRoundedBorder(context, x, y, panelW, panelH, 4, SIDEBAR_BORDER);

        // \u8868\u5934
        drawRoundedRect(context, x, y, panelW, headerH, 4, 0xDD0D0D3B);
        context.drawText(tr, "\u2694 \u73a9\u5bb6\u4fe1\u606f", x + padX, y + 4, TEXT_CYAN, true);

        // \u6bcf\u4e2a\u73a9\u5bb6\u4e00\u884c\uff1a\u540d\u5b57 + \u72b6\u6001\u56fe\u6807\uff0c\u4e0b\u4e00\u884c\u663e\u793a\u51fb\u6740/\u5f97\u5206/\u547d
        for (int i = 0; i < sb.size(); i++) {
            HudDataCache.ScoreEntry e = sb.get(i);
            int ry = y + headerH + padY + i * rowH;

            // \u5947\u5076\u884c\u80cc\u666f
            if (i % 2 == 0) {
                context.fill(x + 2, ry, x + panelW - 2, ry + rowH, 0x18FFFFFF);
            }

            int tc = e.alive() ? TEXT_WHITE : TEXT_GRAY;
            String statusIcon = e.alive() ? "\u25cf" : "\u25cb";
            int statusColor = e.alive() ? TEXT_GREEN : TEXT_RED;

            // \u7b2c\u4e00\u884c\uff1a\u72b6\u6001\u56fe\u6807 + \u540d\u5b57
            String name = e.name();
            if (name.length() > 8) name = name.substring(0, 8) + "..";
            context.drawText(tr, statusIcon, x + padX, ry + 2, statusColor, false);
            context.drawText(tr, name, x + padX + 10, ry + 2, tc, false);

            // \u7b2c\u4e8c\u884c\uff1a\u51fb\u6740/\u5f97\u5206/\u547d
            String livesStr = e.lives() == -1 ? "\u221e" : String.valueOf(e.lives());
            String statsLine = "\u2620" + e.kills() + " \u2605" + e.score() + " \u2764" + livesStr;
            context.drawText(tr, statsLine, x + padX + 10, ry + 12, TEXT_GRAY, false);
        }
    }

    // ==================== \u6e38\u620f\u7ed3\u675f\u753b\u9762 ====================
    private void drawGameEndScreen(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        // \u534a\u900f\u660e\u9ed1\u8272\u80cc\u666f
        context.fill(0, 0, screenW, screenH, 0x88000000);

        // \u4e2d\u592e\u80dc\u5229\u6846
        int boxW = 260;
        int boxH = 40;
        int bx = (screenW - boxW) / 2;
        int by = screenH / 4;
        drawRoundedRect(context, bx, by, boxW, boxH, 5, 0xDD1A2E1A);
        drawRoundedBorder(context, bx, by, boxW, boxH, 5, TEXT_GREEN);

        String winnerName = HudDataCache.getWinnerName();
        if (winnerName != null && !winnerName.isEmpty()) {
            String line1 = "\u2694 \u6e38\u620f\u7ed3\u675f \u2694";
            String line2 = "\u679c\u51a0: " + winnerName + " [" + HudDataCache.getWinnerRole() + "]";
            int w1 = tr.getWidth(line1);
            int w2 = tr.getWidth(line2);
            context.drawText(tr, line1, bx + (boxW - w1) / 2, by + 6, TEXT_GREEN, true);
            context.drawText(tr, line2, bx + (boxW - w2) / 2, by + 22, TEXT_YELLOW, true);
        } else {
            String line1 = "\u2694 \u6e38\u620f\u7ed3\u675f \u2694";
            String line2 = "\u6ca1\u6709\u5e78\u5b58\u8005\uff01";
            int w1 = tr.getWidth(line1);
            int w2 = tr.getWidth(line2);
            context.drawText(tr, line1, bx + (boxW - w1) / 2, by + 6, TEXT_GREEN, true);
            context.drawText(tr, line2, bx + (boxW - w2) / 2, by + 22, TEXT_RED, true);
        }

        // \u4e0b\u65b9\u6700\u7ec8\u6392\u884c\u699c
        List<HudDataCache.ScoreEntry> sb = HudDataCache.getScoreboard();
        if (!sb.isEmpty()) {
            int tableW = 280;
            int rowH = 14;
            int headerH = 16;
            int padX = 8;
            int padY = 4;
            int tableH = headerH + padY + sb.size() * rowH + padY;
            int tx = (screenW - tableW) / 2;
            int ty = by + boxH + 16;

            drawRoundedRect(context, tx, ty, tableW, tableH, 4, 0xDD0A0A1E);
            drawRoundedBorder(context, tx, ty, tableW, tableH, 4, SIDEBAR_BORDER);

            drawRoundedRect(context, tx, ty, tableW, headerH, 4, 0xDD0D0D3B);
            context.drawText(tr, "#", tx + padX, ty + 4, TEXT_CYAN, true);
            context.drawText(tr, "\u73a9\u5bb6", tx + padX + 18, ty + 4, TEXT_CYAN, true);
            context.drawText(tr, "\u804c\u4e1a", tx + padX + 88, ty + 4, TEXT_CYAN, true);
            context.drawText(tr, "\u51fb\u6740", tx + padX + 160, ty + 4, TEXT_CYAN, true);
            context.drawText(tr, "\u5f97\u5206", tx + padX + 200, ty + 4, TEXT_CYAN, true);
            context.drawText(tr, "\u547d", tx + padX + 240, ty + 4, TEXT_CYAN, true);

            for (int i = 0; i < sb.size(); i++) {
                HudDataCache.ScoreEntry e = sb.get(i);
                int ry = ty + headerH + padY + i * rowH;
                int tc = e.alive() ? TEXT_WHITE : TEXT_GRAY;

                if (i % 2 == 0) {
                    context.fill(tx + 2, ry - 1, tx + tableW - 2, ry + rowH - 1, 0x22FFFFFF);
                }

                context.drawText(tr, String.valueOf(i + 1), tx + padX, ry, TEXT_GRAY, false);

                String name = e.name();
                if (name.length() > 8) name = name.substring(0, 8) + "..";
                context.drawText(tr, name, tx + padX + 18, ry, tc, false);

                String role = e.role();
                if (role.length() > 5) role = role.substring(0, 5) + "..";
                context.drawText(tr, role, tx + padX + 88, ry, TEXT_LIGHT_GREEN, false);

                context.drawText(tr, String.valueOf(e.kills()), tx + padX + 164, ry, TEXT_YELLOW, false);
                context.drawText(tr, String.valueOf(e.score()), tx + padX + 204, ry, TEXT_ORANGE, false);

                String livesStr = e.lives() == -1 ? "\u221e" : String.valueOf(e.lives());
                context.drawText(tr, livesStr, tx + padX + 244, ry, HEART_RED, false);
            }
        }
    }

    // ==================== \u7b49\u5f85\u753b\u9762 ====================
    private void drawWaitingScreen(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        // \u5c4f\u5e55\u4e2d\u592e\u7b49\u5f85\u6846
        int boxW = 220;
        int boxH = 50;
        int x = (screenW - boxW) / 2;
        int y = screenH / 3;

        drawRoundedRect(context, x, y, boxW, boxH, 5, 0xCC1A2E1A);
        drawRoundedBorder(context, x, y, boxW, boxH, 5, BORDER_COLOR);

        String line1 = "\u2694 \u611a\u4eba\u8282\u5927\u4e71\u6597 \u2694";
        int w1 = tr.getWidth(line1);
        context.drawText(tr, line1, x + (boxW - w1) / 2, y + 8, TEXT_GREEN, true);

        // \u8df3\u52a8\u7684\u7b49\u5f85\u6587\u5b57
        long dots = (System.currentTimeMillis() / 500) % 4;
        String dotStr = ".".repeat((int) dots);
        String line2 = "\u7b49\u5f85\u6e38\u620f\u5f00\u59cb" + dotStr;
        int w2 = tr.getWidth(line2);
        context.drawText(tr, line2, x + (boxW - w2) / 2, y + 24, TEXT_GRAY, false);

        String line3 = "\u7ba1\u7406\u5458\u4f7f\u7528 /af start \u5f00\u59cb";
        int w3 = tr.getWidth(line3);
        context.drawText(tr, line3, x + (boxW - w3) / 2, y + 36, TEXT_YELLOW, false);
    }

    // ==================== 左下角提示消息 ====================
    private void drawTipMessage(DrawContext context, TextRenderer tr, int screenW, int screenH) {
        String tip = HudDataCache.getTipMessage();
        if (tip == null || tip.isEmpty()) return;

        // 3秒淡出
        long elapsed = System.currentTimeMillis() - HudDataCache.getTipReceiveTime();
        if (elapsed > 3000) return;

        // 计算透明度(最后1秒淡出)
        float alpha = elapsed > 2000 ? 1.0f - (elapsed - 2000) / 1000.0f : 1.0f;
        int alphaInt = (int)(alpha * 255) & 0xFF;
        if (alphaInt < 10) return;

        int textW = tr.getWidth(tip);
        int boxW = textW + 12;
        int boxH = 16;
        int x = 4;
        int y = screenH - 40; // 左下角, 在技能栏上方

        int bgAlpha = (int)(alpha * 0xCC) << 24;
        int borderAlpha = (alphaInt << 24) | (TEXT_YELLOW & 0x00FFFFFF);
        int textAlpha = (alphaInt << 24) | 0x00FFFFFF;

        drawRoundedRect(context, x, y, boxW, boxH, 3, bgAlpha | 0x001A1A0A);
        drawRoundedBorder(context, x, y, boxW, boxH, 3, borderAlpha);
        context.drawText(tr, tip, x + 6, y + 4, textAlpha, true);
    }

    // ==================== Drawing Helpers ====================

    private void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        // Main body
        ctx.fill(x + r, y, x + w - r, y + h, color);
        // Left/right strips
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        // Corner fills (simple square corners for performance, looks close enough at small radius)
        ctx.fill(x, y, x + r, y + r, color);
        ctx.fill(x + w - r, y, x + w, y + r, color);
        ctx.fill(x, y + h - r, x + r, y + h, color);
        ctx.fill(x + w - r, y + h - r, x + w, y + h, color);
    }

    private void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        // Top edge
        ctx.fill(x + r, y, x + w - r, y + 1, color);
        // Bottom edge
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
        // Left edge
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        // Right edge
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        // Corner pixels (simplified rounded appearance)
        ctx.fill(x + 1, y + 1, x + r, y + 2, color);
        ctx.fill(x + 1, y + 1, x + 2, y + r, color);
        ctx.fill(x + w - r, y + 1, x + w - 1, y + 2, color);
        ctx.fill(x + w - 2, y + 1, x + w - 1, y + r, color);
        ctx.fill(x + 1, y + h - 2, x + r, y + h - 1, color);
        ctx.fill(x + 1, y + h - r, x + 2, y + h - 1, color);
        ctx.fill(x + w - r, y + h - 2, x + w - 1, y + h - 1, color);
        ctx.fill(x + w - 2, y + h - r, x + w - 1, y + h - 1, color);
    }
}
