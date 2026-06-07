package baby.sv.yepvpfabirc.client.screen;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import baby.sv.yepvpfabirc.client.hud.HudDataCache;
import baby.sv.yepvpfabirc.client.hud.MapDataCache;
import baby.sv.yepvpfabirc.client.network.ClientNetworkHandler;

import java.util.List;
import java.util.Map;

/**
 * 世界地图全屏界面 — 仿 Xaero's World Map 风格
 * 特性: 鼠标拖拽平移, 滚轮缩放(以鼠标为中心), 网格线, 坐标轴刻度,
 *       精致玩家标记, 信息面板, 深色主题
 */
public class WorldMapScreen extends Screen {

    private static final Identifier MAP_TEXTURE_ID = Identifier.of("yepvpfabirc", "worldmap_dynamic");

    // 配色常量
    private static final int BG_COLOR        = 0xFF0D0D1A;
    private static final int PANEL_BG        = 0xE0111122;
    private static final int PANEL_BORDER    = 0xFF2A2A44;
    private static final int MAP_BORDER      = 0xFF3A3A5E;
    private static final int MAP_INNER_BG    = 0xFF141428;
    private static final int GRID_COLOR      = 0x18FFFFFF;
    private static final int GRID_REGION     = 0x30FFFFFF;
    private static final int AXIS_TEXT       = 0xFF888899;
    private static final int BORDER_COLOR    = 0xCCFF2222;
    private static final int TEXT_WHITE      = 0xFFEEEEFF;
    private static final int TEXT_YELLOW     = 0xFFFFDD44;
    private static final int TEXT_GRAY       = 0xFF888899;
    private static final int TEXT_CYAN       = 0xFF44DDDD;
    private static final int MARKER_SELF     = 0xFF44FF44;
    private static final int CROSSHAIR_COLOR = 0x40FFFFFF;

    // 地图视口
    private float mapCenterX = 0;
    private float mapCenterZ = 0;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    // 缓存地图几何
    private int mapLeft, mapTop, mapW, mapH;
    private float worldMinX, worldMinZ, worldViewW, worldViewH;

    // 鼠标拖拽(GLFW轮询)
    private boolean dragging = false;
    private double lastMouseX, lastMouseY;
    private boolean lastLeftPressed = false;
    private double lastScrollY = 0;

    // POPCORN右键镭射防抖
    private boolean rightClickHeld = false;

    // 纹理缓存
    private NativeImageBackedTexture mapTexture;
    private boolean textureRegistered = false;
    private long lastTextureVersion = -1;
    private int texSize;
    private int texMaxChunk;
    private int texCenterChunkX; // 世界边界中心对应的区块坐标
    private int texCenterChunkZ;

    public WorldMapScreen() {
        super(Text.literal("世界地图"));
    }

    @Override
    protected void init() {
        // 初始化时以玩家当前位置为中心(如果有数据)
        List<MapDataCache.PlayerMarker> markers = MapDataCache.getPlayerMarkers();
        if (!markers.isEmpty()) {
            // 第一个marker通常是自己
            mapCenterX = markers.get(0).x();
            mapCenterZ = markers.get(0).z();
        }
    }

    // ==================== 纹理管理 ====================

    private void rebuildTextureIfNeeded() {
        long ver = MapDataCache.getVersion();
        if (ver == lastTextureVersion && mapTexture != null) return;

        int borderRadius = MapDataCache.getBorderRadius();
        if (borderRadius <= 0) return;
        int maxChunk = borderRadius / 16 + 4;
        int scale = 4; // 每区块占4x4像素(对应4x4子区块)
        int size = maxChunk * 2 * scale;
        if (size < 16) size = 16;

        // 世界边界中心对应的区块坐标
        int centerCX = MapDataCache.getBorderCenterX() >> 4;
        int centerCZ = MapDataCache.getBorderCenterZ() >> 4;

        if (mapTexture == null || texSize != size) {
            destroyTexture();
            mapTexture = new NativeImageBackedTexture("yepvp_worldmap", size, size, false);
            texSize = size;
            texMaxChunk = maxChunk;
        }
        texCenterChunkX = centerCX;
        texCenterChunkZ = centerCZ;

        NativeImage img = mapTexture.getImage();
        if (img == null) return;

        img.fillRect(0, 0, size, size, MAP_INNER_BG);

        Map<Long, int[]> chunkColors = MapDataCache.getChunkColors();
        for (Map.Entry<Long, int[]> entry : chunkColors.entrySet()) {
            long key = entry.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            int[] subColors = entry.getValue();
            int baseX = (cx - centerCX + texMaxChunk) * scale;
            int baseZ = (cz - centerCZ + texMaxChunk) * scale;
            for (int sz = 0; sz < 4; sz++) {
                for (int sx = 0; sx < 4; sx++) {
                    int px = baseX + sx;
                    int pz = baseZ + sz;
                    if (px >= 0 && px < size && pz >= 0 && pz < size) {
                        img.setColorArgb(px, pz, 0xFF000000 | subColors[sz * 4 + sx]);
                    }
                }
            }
        }

        List<MapDataCache.PaintedChunk> paintedChunks = MapDataCache.getPaintedChunks();
        for (MapDataCache.PaintedChunk pc : paintedChunks) {
            int baseX = (pc.cx() - centerCX + texMaxChunk) * scale;
            int baseZ = (pc.cz() - centerCZ + texMaxChunk) * scale;
            int paintArgb;
            switch (pc.paintColor()) {
                case 1 -> paintArgb = 0xFFFF3333;
                case 2 -> paintArgb = 0xFF3333FF;
                case 3 -> paintArgb = 0xFF33FF33;
                default -> { continue; }
            }
            for (int sz = 0; sz < scale; sz++) {
                for (int sx = 0; sx < scale; sx++) {
                    int px = baseX + sx;
                    int pz = baseZ + sz;
                    if (px < 0 || px >= size || pz < 0 || pz >= size) continue;
                    int existing = img.getColorArgb(px, pz);
                    int er = (existing >> 16) & 0xFF, eg = (existing >> 8) & 0xFF, eb = existing & 0xFF;
                    int pr = (paintArgb >> 16) & 0xFF, pg = (paintArgb >> 8) & 0xFF, pb = paintArgb & 0xFF;
                    int blended = 0xFF000000 | (((er + pr) / 2) << 16) | (((eg + pg) / 2) << 8) | ((eb + pb) / 2);
                    img.setColorArgb(px, pz, blended);
                }
            }
        }

        mapTexture.upload();
        lastTextureVersion = ver;

        if (!textureRegistered && this.client != null) {
            this.client.getTextureManager().registerTexture(MAP_TEXTURE_ID, mapTexture);
            textureRegistered = true;
        }
    }

    private void destroyTexture() {
        if (textureRegistered && this.client != null) {
            this.client.getTextureManager().destroyTexture(MAP_TEXTURE_ID);
            textureRegistered = false;
        }
        if (mapTexture != null) {
            mapTexture.close();
            mapTexture = null;
        }
    }

    @Override
    public void removed() {
        super.removed();
        destroyTexture();
    }

    // ==================== 主渲染 ====================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        handleKeyboardInput();

        // 平滑缩放插值
        zoom += (targetZoom - zoom) * 0.15f;

        // 全屏背景
        ctx.fill(0, 0, this.width, this.height, BG_COLOR);

        int borderRadius = MapDataCache.getBorderRadius();

        // 地图区域: 留出左侧信息面板和边距
        int panelW = 160;
        int margin = 8;
        mapLeft = panelW + margin * 2;
        mapTop = margin + 12;
        mapW = this.width - mapLeft - margin;
        mapH = this.height - mapTop - margin - 20;
        if (mapW < 100) mapW = 100;
        if (mapH < 100) mapH = 100;

        // 世界视口
        float aspect = (float) mapW / mapH;
        float viewH = (borderRadius * 2.0f) / zoom;
        float viewW = viewH * aspect;
        worldMinX = mapCenterX - viewW / 2;
        worldMinZ = mapCenterZ - viewH / 2;
        worldViewW = viewW;
        worldViewH = viewH;

        // ===== 地图外框 =====
        ctx.fill(mapLeft - 2, mapTop - 2, mapLeft + mapW + 2, mapTop + mapH + 2, MAP_BORDER);
        ctx.fill(mapLeft - 1, mapTop - 1, mapLeft + mapW + 1, mapTop + mapH + 1, MAP_INNER_BG);

        // ===== 地形纹理(裁剪防平铺) =====
        rebuildTextureIfNeeded();
        if (textureRegistered && texSize > 0) {
            // 计算纹理UV(浮点, 单位=像素), 需要减去世界边界中心偏移
            int scale = 4;
            float centerOffX = texCenterChunkX;
            float centerOffZ = texCenterChunkZ;
            float uStart = (worldMinX / 16.0f - centerOffX + texMaxChunk) * scale;
            float vStart = (worldMinZ / 16.0f - centerOffZ + texMaxChunk) * scale;
            float uEnd = ((worldMinX + worldViewW) / 16.0f - centerOffX + texMaxChunk) * scale;
            float vEnd = ((worldMinZ + worldViewH) / 16.0f - centerOffZ + texMaxChunk) * scale;

            // 裁剪到纹理边界[0, texSize], 同时调整屏幕坐标
            float clampedUStart = Math.max(uStart, 0);
            float clampedVStart = Math.max(vStart, 0);
            float clampedUEnd = Math.min(uEnd, texSize);
            float clampedVEnd = Math.min(vEnd, texSize);

            if (clampedUEnd > clampedUStart && clampedVEnd > clampedVStart) {
                float uRange = uEnd - uStart;
                float vRange = vEnd - vStart;

                // 对应的屏幕像素范围
                int drawX1 = mapLeft + (int) ((clampedUStart - uStart) / uRange * mapW);
                int drawY1 = mapTop + (int) ((clampedVStart - vStart) / vRange * mapH);
                int drawX2 = mapLeft + (int) ((clampedUEnd - uStart) / uRange * mapW);
                int drawY2 = mapTop + (int) ((clampedVEnd - vStart) / vRange * mapH);
                int drawW = drawX2 - drawX1;
                int drawH = drawY2 - drawY1;

                if (drawW > 0 && drawH > 0) {
                    float regionW = clampedUEnd - clampedUStart;
                    float regionH = clampedVEnd - clampedVStart;
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, MAP_TEXTURE_ID,
                            drawX1, drawY1,
                            clampedUStart, clampedVStart,
                            drawW, drawH,
                            (int) Math.ceil(regionW), (int) Math.ceil(regionH),
                            texSize, texSize);
                }
            }
        }

        // ===== 网格线(区块16格 + 区域512格) =====
        drawGrid(ctx);

        // ===== 中心十字准线 =====
        drawCrosshair(ctx);

        // ===== 特殊标记(迪克/蛛网/备用躯体) =====
        drawSpecialMarkers(ctx);

        // ===== 玩家标记 =====
        drawPlayerMarkers(ctx);

        // ===== 坐标轴刻度 =====
        drawAxisLabels(ctx);

        // ===== 左侧信息面板 =====
        drawInfoPanel(ctx, mouseX, mouseY);

        // ===== 底部状态栏 =====
        drawBottomBar(ctx, mouseX, mouseY);

        // POPCORN: 右键地图触发轨道镭射
        handlePopcornRightClick();

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ==================== 网格线 ====================

    private void drawGrid(DrawContext ctx) {
        // 根据缩放决定网格密度
        float blocksPerPixel = worldViewW / mapW;
        int gridSpacing;
        if (blocksPerPixel < 1) gridSpacing = 16;       // 缩放很大: 每区块画线
        else if (blocksPerPixel < 4) gridSpacing = 64;
        else if (blocksPerPixel < 16) gridSpacing = 256;
        else gridSpacing = 512;                          // 缩放很小: 区域级

        int startX = ((int) worldMinX / gridSpacing) * gridSpacing;
        int startZ = ((int) worldMinZ / gridSpacing) * gridSpacing;

        for (int wx = startX; wx <= worldMinX + worldViewW; wx += gridSpacing) {
            int sx = worldToScreenX(wx);
            if (sx >= mapLeft && sx <= mapLeft + mapW) {
                boolean isRegion = (wx % 512 == 0);
                ctx.fill(sx, mapTop, sx + 1, mapTop + mapH, isRegion ? GRID_REGION : GRID_COLOR);
            }
        }
        for (int wz = startZ; wz <= worldMinZ + worldViewH; wz += gridSpacing) {
            int sy = worldToScreenZ(wz);
            if (sy >= mapTop && sy <= mapTop + mapH) {
                boolean isRegion = (wz % 512 == 0);
                ctx.fill(mapLeft, sy, mapLeft + mapW, sy + 1, isRegion ? GRID_REGION : GRID_COLOR);
            }
        }
    }

    // ==================== 中心十字 ====================

    private void drawCrosshair(DrawContext ctx) {
        int cx = worldToScreenX(mapCenterX);
        int cy = worldToScreenZ(mapCenterZ);
        if (cx >= mapLeft && cx <= mapLeft + mapW && cy >= mapTop && cy <= mapTop + mapH) {
            int len = 8;
            ctx.fill(cx - len, cy, cx + len + 1, cy + 1, CROSSHAIR_COLOR);
            ctx.fill(cx, cy - len, cx + 1, cy + len + 1, CROSSHAIR_COLOR);
        }
    }

    // ==================== 特殊标记 ====================

    private void drawSpecialMarkers(DrawContext ctx) {
        // 迪克
        for (int[] dp : MapDataCache.getDickPositions()) {
            int sx = worldToScreenX(dp[0]);
            int sy = worldToScreenZ(dp[1]);
            if (!inMapBounds(sx, sy)) continue;
            drawDiamondMarker(ctx, sx, sy, 0xFF00CCDD, 5);
            drawCenteredText(ctx, "♦", sx, sy - 12, 0xFF00CCDD);
        }
        // 蛛网
        for (int[] wp : MapDataCache.getWebPositions()) {
            int sx = worldToScreenX(wp[0]);
            int sy = worldToScreenZ(wp[1]);
            if (!inMapBounds(sx, sy)) continue;
            drawDiamondMarker(ctx, sx, sy, 0xFF999999, 4);
            drawCenteredText(ctx, "⛃", sx, sy - 12, 0xFF999999);
        }
        // 备用躯体(POPCORN)
        for (int[] bp : MapDataCache.getBackupBodyPositions()) {
            int sx = worldToScreenX(bp[0]);
            int sy = worldToScreenZ(bp[1]);
            if (!inMapBounds(sx, sy)) continue;
            drawDiamondMarker(ctx, sx, sy, 0xFFFF8800, 4);
            drawCenteredText(ctx, "⚡", sx, sy - 12, 0xFFFF8800);
        }
        // 墓碑(DAMAI)
        for (int[] tp : MapDataCache.getTombstonePositions()) {
            int sx = worldToScreenX(tp[0]);
            int sy = worldToScreenZ(tp[1]);
            if (!inMapBounds(sx, sy)) continue;
            drawDiamondMarker(ctx, sx, sy, 0xFF8B008B, 5);
            drawCenteredText(ctx, "†", sx, sy - 12, 0xFF8B008B);
        }
        // 画(SHUBING)
        for (int[] pp : MapDataCache.getPaintingPositions()) {
            int sx = worldToScreenX(pp[0]);
            int sy = worldToScreenZ(pp[1]);
            if (!inMapBounds(sx, sy)) continue;
            drawDiamondMarker(ctx, sx, sy, 0xFF9B59B6, 5);
            drawCenteredText(ctx, "🖼", sx, sy - 12, 0xFF9B59B6);
        }
        // Boss标记
        for (int[] bp : MapDataCache.getBossPositions()) {
            int sx = worldToScreenX(bp[0]);
            int sy = worldToScreenZ(bp[1]);
            if (!inMapBounds(sx, sy)) continue;
            int bossType = bp[2];
            if (bossType == 0) {
                // 铁傀儡预警: 闪烁黄色
                int alpha = (int)(180 + 75 * Math.sin(System.currentTimeMillis() / 200.0));
                int color = (alpha << 24) | 0xFFD700;
                drawDiamondMarker(ctx, sx, sy, color, 7);
                drawCenteredText(ctx, "⚠Boss", sx, sy - 14, 0xFFFFD700);
            } else if (bossType == 1) {
                // 铁傀儡存活: 红色大标记
                drawGlowDiamond(ctx, sx, sy, 0xFFFF4444, 7);
                drawCenteredText(ctx, "§c铁傀儡", sx, sy - 14, 0xFFFF4444);
            }
            // bossType==2 监守者: 不显示位置
        }
        // 蜡翼导弹箱
        for (int[] lp : MapDataCache.getLayiChestPositions()) {
            int sx = worldToScreenX(lp[0]);
            int sy = worldToScreenZ(lp[1]);
            if (!inMapBounds(sx, sy)) continue;
            int alpha = (int)(180 + 75 * Math.sin(System.currentTimeMillis() / 300.0));
            int color = (alpha << 24) | 0xFF2222;
            drawDiamondMarker(ctx, sx, sy, color, 5);
            drawCenteredText(ctx, "§c🚀", sx, sy - 12, 0xFFFF2222);
        }
    }

    // ==================== 玩家标记 ====================

    private void drawPlayerMarkers(DrawContext ctx) {
        List<MapDataCache.PlayerMarker> markers = MapDataCache.getPlayerMarkers();
        boolean isSelf = true;
        for (MapDataCache.PlayerMarker marker : markers) {
            int sx = worldToScreenX(marker.x());
            int sy = worldToScreenZ(marker.z());
            if (!inMapBounds(sx, sy)) { isSelf = false; continue; }

            int color = 0xFF000000 | marker.color();
            int size = isSelf ? 7 : 5;

            if (isSelf) {
                // 自己: 较大菱形 + 发光环
                drawGlowDiamond(ctx, sx, sy, MARKER_SELF, size);
            } else {
                drawDiamondMarker(ctx, sx, sy, color, size);
            }

            // 朝向指示三角(根据yaw)
            drawHeadingArrow(ctx, sx, sy, marker.yaw(), size + 4, isSelf ? MARKER_SELF : color);

            // 名字(上方)
            drawCenteredText(ctx, marker.name(), sx, sy - size - 12, isSelf ? MARKER_SELF : color);

            // 坐标(名字上方, 灰色)
            String coordStr = marker.x() + ", " + marker.z();
            drawCenteredText(ctx, coordStr, sx, sy - size - 22, TEXT_GRAY);

            isSelf = false;
        }
    }

    // 根据MC玩家yaw绘制三角朝向箭头
    // yaw=0=南(+Z), yaw=90=西(-X), yaw=180=北(-Z), yaw=-90/270=东(+X)
    private void drawHeadingArrow(DrawContext ctx, int cx, int cy, float yawDeg, int length, int color) {
        // MC约定: yaw=0面向+Z(南/屏幕下), yaw=90面向-X(西/屏幕左), yaw=-90面向+X(东/屏幕右)
        double rad = Math.toRadians(yawDeg);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        // 起点=marker中心, 终点=length像素外
        double tipX = cx + dx * length;
        double tipY = cy + dz * length;
        // 用线段绘制箭头(粗1像素x N段)
        int steps = length;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) (cx + (tipX - cx) * t);
            int py = (int) (cy + (tipY - cy) * t);
            ctx.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
        // 箭尖三角(垂直方向偏移)
        double perpX = -dz;
        double perpY = dx;
        for (int i = -3; i <= 3; i++) {
            double offset = (3 - Math.abs(i)) * 0.5;
            int px = (int) (tipX + perpX * i * 0.6 - dx * Math.abs(i) * 0.8);
            int py = (int) (tipY + perpY * i * 0.6 - dz * Math.abs(i) * 0.8);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    // ==================== 坐标轴刻度 ====================

    private void drawAxisLabels(DrawContext ctx) {
        float blocksPerPixel = worldViewW / mapW;
        int labelSpacing;
        if (blocksPerPixel < 2) labelSpacing = 128;
        else if (blocksPerPixel < 8) labelSpacing = 512;
        else labelSpacing = 1024;

        int startX = ((int) worldMinX / labelSpacing) * labelSpacing;
        for (int wx = startX; wx <= worldMinX + worldViewW; wx += labelSpacing) {
            int sx = worldToScreenX(wx);
            if (sx >= mapLeft + 20 && sx <= mapLeft + mapW - 20) {
                String label = String.valueOf(wx);
                int lw = this.textRenderer.getWidth(label);
                ctx.drawText(this.textRenderer, label, sx - lw / 2, mapTop + mapH + 2, AXIS_TEXT, false);
            }
        }

        int startZ = ((int) worldMinZ / labelSpacing) * labelSpacing;
        for (int wz = startZ; wz <= worldMinZ + worldViewH; wz += labelSpacing) {
            int sy = worldToScreenZ(wz);
            if (sy >= mapTop + 6 && sy <= mapTop + mapH - 6) {
                String label = String.valueOf(wz);
                int lw = this.textRenderer.getWidth(label);
                ctx.drawText(this.textRenderer, label, mapLeft - lw - 4, sy - 4, AXIS_TEXT, false);
            }
        }
    }

    // ==================== 左侧信息面板 ====================

    private void drawInfoPanel(DrawContext ctx, int mouseX, int mouseY) {
        int px = 4, py = 4;
        int pw = 156, ph = this.height - 8;

        // 面板背景
        ctx.fill(px, py, px + pw, py + ph, PANEL_BG);
        ctx.fill(px, py, px + pw, py + 1, PANEL_BORDER);
        ctx.fill(px, py + ph - 1, px + pw, py + ph, PANEL_BORDER);
        ctx.fill(px, py, px + 1, py + ph, PANEL_BORDER);
        ctx.fill(px + pw - 1, py, px + pw, py + ph, PANEL_BORDER);

        int tx = px + 6;
        int ty = py + 6;
        int lineH = 11;

        // 标题
        ctx.drawText(this.textRenderer, "§e§l⚔ 世界地图", tx, ty, TEXT_YELLOW, true);
        ty += lineH + 4;

        // 分隔线
        ctx.fill(tx, ty, px + pw - 6, ty + 1, PANEL_BORDER);
        ty += 4;

        // 玩家信息
        ctx.drawText(this.textRenderer, "§7职业: §f" + HudDataCache.getSelfRoleName(), tx, ty, TEXT_WHITE, false);
        ty += lineH;

        // 缩放级别
        ctx.drawText(this.textRenderer, String.format("§7缩放: §f%.1fx", zoom), tx, ty, TEXT_WHITE, false);
        ty += lineH;

        // 视野范围
        ctx.drawText(this.textRenderer, String.format("§7视野: §f%d×%d", (int) worldViewW, (int) worldViewH), tx, ty, TEXT_WHITE, false);
        ty += lineH;

        // 中心坐标
        ctx.drawText(this.textRenderer, String.format("§7中心: §f%d, %d", (int) mapCenterX, (int) mapCenterZ), tx, ty, TEXT_WHITE, false);
        ty += lineH + 4;

        // 分隔线
        ctx.fill(tx, ty, px + pw - 6, ty + 1, PANEL_BORDER);
        ty += 4;

        // 边界信息
        int borderRadius = MapDataCache.getBorderRadius();
        ctx.drawText(this.textRenderer, "§7边界: §c" + borderRadius + " §7格", tx, ty, TEXT_WHITE, false);
        ty += lineH;
        ctx.drawText(this.textRenderer, "§7距边界: §e" + HudDataCache.getDistToBorder() + " §7格", tx, ty, TEXT_WHITE, false);
        ty += lineH + 4;

        // 分隔线
        ctx.fill(tx, ty, px + pw - 6, ty + 1, PANEL_BORDER);
        ty += 4;

        // 图例标题
        ctx.drawText(this.textRenderer, "§e§l图例", tx, ty, TEXT_YELLOW, true);
        ty += lineH + 2;

        // 图例项
        drawLegendItem(ctx, tx, ty, 0xFFFF2222, "世界边界"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFFFF3333, "红色涂色"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFF3333FF, "蓝色涂色"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFF33FF33, "绿色涂色"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFF00CCDD, "♦ 迪克"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFF999999, "⛃ 蛛网"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFFFF8800, "⚡ 躯体"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFF8B008B, "† 墓碑"); ty += lineH;
        drawLegendItem(ctx, tx, ty, 0xFF9B59B6, "🖼 画"); ty += lineH + 4;

        // 分隔线
        ctx.fill(tx, ty, px + pw - 6, ty + 1, PANEL_BORDER);
        ty += 4;

        // 操作说明
        ctx.drawText(this.textRenderer, "§e§l操作", tx, ty, TEXT_YELLOW, true);
        ty += lineH + 2;
        ctx.drawText(this.textRenderer, "§7鼠标拖拽  §f平移", tx, ty, TEXT_WHITE, false);
        ty += lineH;
        ctx.drawText(this.textRenderer, "§7滚轮      §f缩放", tx, ty, TEXT_WHITE, false);
        ty += lineH;
        ctx.drawText(this.textRenderer, "§7WASD      §f平移", tx, ty, TEXT_WHITE, false);
        ty += lineH;
        ctx.drawText(this.textRenderer, "§7ESC/M     §f关闭", tx, ty, TEXT_WHITE, false);
    }

    private void drawLegendItem(DrawContext ctx, int x, int y, int color, String text) {
        ctx.fill(x, y + 2, x + 6, y + 8, color);
        ctx.drawText(this.textRenderer, text, x + 10, y, TEXT_WHITE, false);
    }

    // ==================== 底部状态栏 ====================

    private void drawBottomBar(DrawContext ctx, int mouseX, int mouseY) {
        int barY = this.height - 14;
        // 鼠标悬停坐标
        if (mouseX >= mapLeft && mouseX <= mapLeft + mapW && mouseY >= mapTop && mouseY <= mapTop + mapH) {
            float relX = (float) (mouseX - mapLeft) / mapW;
            float relZ = (float) (mouseY - mapTop) / mapH;
            int hx = (int) (worldMinX + relX * worldViewW);
            int hz = (int) (worldMinZ + relZ * worldViewH);
            String hoverStr = String.format("光标: %d, %d", hx, hz);
            ctx.drawText(this.textRenderer, hoverStr, mapLeft, barY, TEXT_CYAN, true);
        }

        // POPCORN右键提示
        String roleId = HudDataCache.getSelfRoleId();
        if ("popcorn".equals(roleId)) {
            String laserTip = "§c右键地图发射轨道镭射";
            int ltw = this.textRenderer.getWidth(laserTip);
            ctx.drawText(this.textRenderer, laserTip, (this.width - ltw) / 2, barY, 0xFFFF4444, true);
        } else if ("heli".equals(roleId)) {
            String tpTip = "§e右键地图传送到最近被揭露玩家(1minCD)";
            int ltw = this.textRenderer.getWidth(tpTip);
            ctx.drawText(this.textRenderer, tpTip, (this.width - ltw) / 2, barY, 0xFFFFDD44, true);
        }

        // 右下角: 游戏时间
        int gameTime = HudDataCache.getGameTimeSec();
        String timeStr = String.format("游戏时间: %d:%02d", gameTime / 60, gameTime % 60);
        int tw = this.textRenderer.getWidth(timeStr);
        ctx.drawText(this.textRenderer, timeStr, this.width - tw - 8, barY, TEXT_GRAY, false);
    }

    // ==================== 滚轮缩放(标准Override, MC 1.21.11签名: DDDD) ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= mapLeft && mouseX <= mapLeft + mapW
                && mouseY >= mapTop && mouseY <= mapTop + mapH && worldViewW > 0) {
            // 以鼠标位置为中心缩放
            float relX = (float) (mouseX - mapLeft) / mapW;
            float relZ = (float) (mouseY - mapTop) / mapH;
            float worldMouseX = worldMinX + relX * worldViewW;
            float worldMouseZ = worldMinZ + relZ * worldViewH;

            float oldZoom = targetZoom;
            if (verticalAmount > 0) {
                targetZoom = Math.min(targetZoom * 1.25f, 30.0f);
            } else if (verticalAmount < 0) {
                targetZoom = Math.max(targetZoom / 1.25f, 0.15f);
            }

            // 保持鼠标下的世界坐标不变
            float zoomRatio = targetZoom / oldZoom;
            mapCenterX = worldMouseX + (mapCenterX - worldMouseX) / zoomRatio;
            mapCenterZ = worldMouseZ + (mapCenterZ - worldMouseZ) / zoomRatio;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ==================== 输入处理(GLFW轮询, 兼容MC 1.21.11) ====================

    private void handleKeyboardInput() {
        if (this.client == null) return;
        long window = this.client.getWindow().getHandle();
        int borderRadius = MapDataCache.getBorderRadius();
        float panSpeed = (borderRadius * 2.0f) / zoom * 0.015f;

        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_UP) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            mapCenterZ -= panSpeed;
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            mapCenterZ += panSpeed;
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            mapCenterX -= panSpeed;
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            mapCenterX += panSpeed;

        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            targetZoom = Math.min(targetZoom * 1.02f, 30.0f);
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            targetZoom = Math.max(targetZoom / 1.02f, 0.15f);

        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
            this.close();

        // 鼠标拖拽平移(GLFW轮询)
        double[] mxArr = new double[1], myArr = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, mxArr, myArr);
        double sf = this.client.getWindow().getScaleFactor();
        double curMouseX = mxArr[0] / sf;
        double curMouseY = myArr[0] / sf;
        boolean leftPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (leftPressed && lastLeftPressed && dragging && mapW > 0 && mapH > 0) {
            double dx = curMouseX - lastMouseX;
            double dy = curMouseY - lastMouseY;
            mapCenterX -= (float) (dx / mapW * worldViewW);
            mapCenterZ -= (float) (dy / mapH * worldViewH);
        }
        if (leftPressed && !lastLeftPressed) {
            // 按下瞬间, 判断是否在地图内
            if (curMouseX >= mapLeft && curMouseX <= mapLeft + mapW
                    && curMouseY >= mapTop && curMouseY <= mapTop + mapH) {
                dragging = true;
            }
        }
        if (!leftPressed) {
            dragging = false;
        }
        lastMouseX = curMouseX;
        lastMouseY = curMouseY;
        lastLeftPressed = leftPressed;
    }


    // POPCORN: 右键地图发射轨道镭射 / HELI: 右键地图传送到最近被揭露玩家
    private void handlePopcornRightClick() {
        if (this.client == null) return;
        String role = HudDataCache.getSelfRoleId();
        boolean isPopcorn = "popcorn".equals(role);
        boolean isHeli = "heli".equals(role);
        if (!isPopcorn && !isHeli) return;
        long window = this.client.getWindow().getHandle();
        boolean rightNow = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (rightNow && !rightClickHeld && worldViewW > 0) {
            double[] mx = new double[1], my = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, mx, my);
            double sf = this.client.getWindow().getScaleFactor();
            double smx = mx[0] / sf, smy = my[0] / sf;
            if (smx >= mapLeft && smx <= mapLeft + mapW && smy >= mapTop && smy <= mapTop + mapH) {
                float relX = (float) (smx - mapLeft) / mapW;
                float relZ = (float) (smy - mapTop) / mapH;
                int worldX = (int) (worldMinX + relX * worldViewW);
                int worldZ = (int) (worldMinZ + relZ * worldViewH);
                ClientNetworkHandler.sendMapTeleport(worldX, worldZ);
            }
        }
        rightClickHeld = rightNow;
    }

    // ==================== 绘制辅助 ====================

    private int worldToScreenX(float worldX) {
        return mapLeft + (int) ((worldX - worldMinX) / worldViewW * mapW);
    }

    private int worldToScreenZ(float worldZ) {
        return mapTop + (int) ((worldZ - worldMinZ) / worldViewH * mapH);
    }

    private int clampX(int x) { return Math.max(mapLeft, Math.min(x, mapLeft + mapW)); }
    private int clampY(int y) { return Math.max(mapTop, Math.min(y, mapTop + mapH)); }

    private boolean inMapBounds(int sx, int sy) {
        return sx >= mapLeft && sx <= mapLeft + mapW && sy >= mapTop && sy <= mapTop + mapH;
    }

    private void drawCenteredText(DrawContext ctx, String text, int cx, int cy, int color) {
        int w = this.textRenderer.getWidth(text);
        ctx.drawText(this.textRenderer, text, cx - w / 2, cy, color, true);
    }

    private void drawDiamondMarker(DrawContext ctx, int cx, int cy, int color, int size) {
        for (int i = 0; i <= size; i++) {
            int w = size - i;
            ctx.fill(cx - w, cy - i, cx + w + 1, cy - i + 1, color);
            if (i > 0) ctx.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, color);
        }
    }

    private void drawGlowDiamond(DrawContext ctx, int cx, int cy, int color, int size) {
        // 外圈发光(半透明)
        int glowColor = (color & 0x00FFFFFF) | 0x44000000;
        drawDiamondMarker(ctx, cx, cy, glowColor, size + 3);
        // 中圈
        int midColor = (color & 0x00FFFFFF) | 0x88000000;
        drawDiamondMarker(ctx, cx, cy, midColor, size + 1);
        // 内核
        drawDiamondMarker(ctx, cx, cy, color, size);
        // 白色中心点
        ctx.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
