package baby.sv.yepvpfabirc.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * HUD左上角小地图: 复用MapDataCache的区块颜色, 维护独立的NativeImage纹理(版本变化时重建).
 * 同时显示玩家朝向箭头与下方坐标.
 */
public class MinimapRenderer {

    private static final Identifier MINIMAP_TEXTURE_ID = Identifier.of("yepvpfabirc", "minimap_dynamic");

    // ===== 配置 =====
    private static final int MINIMAP_SIZE = 140;        // 像素尺寸(正方形)
    private static final int VIEW_RADIUS_BLOCKS = 256;  // 显示半径(尽量大)
    private static final int FRAME_BORDER = 0xFF2ECC71; // 绿色边框
    private static final int BG_COLOR = 0xCC0A1A0A;
    private static final int CROSSHAIR_COLOR = 0xFFFFFFFF;
    private static final int SELF_COLOR = 0xFF44FF44;

    // ===== 纹理状态 =====
    private static NativeImageBackedTexture texture;
    private static boolean textureRegistered = false;
    private static long lastTextureVersion = -1;
    private static int texSize;        // 纹理像素边长
    private static int texMaxChunk;    // 纹理半边对应的区块数
    private static int texCenterChunkX;
    private static int texCenterChunkZ;

    public static void render(DrawContext ctx, MinecraftClient client, TextRenderer tr) {
        ClientPlayerEntity self = client.player;
        if (self == null) return;
        if (!MapDataCache.hasData()) return;

        int x = 4, y = 4;
        int s = MINIMAP_SIZE;

        // 背景+边框
        ctx.fill(x - 1, y - 1, x + s + 1, y + s + 1, FRAME_BORDER);
        ctx.fill(x, y, x + s, y + s, BG_COLOR);

        // 重建纹理(版本变化时)
        rebuildTextureIfNeeded(client);

        if (textureRegistered && texSize > 0) {
            // 视口: 玩家为中心, VIEW_RADIUS_BLOCKS半径
            float playerX = (float) self.getX();
            float playerZ = (float) self.getZ();
            float viewW = VIEW_RADIUS_BLOCKS * 2f;
            float viewH = VIEW_RADIUS_BLOCKS * 2f;
            float worldMinX = playerX - VIEW_RADIUS_BLOCKS;
            float worldMinZ = playerZ - VIEW_RADIUS_BLOCKS;

            int scale = 4;
            float uStart = (worldMinX / 16f - texCenterChunkX + texMaxChunk) * scale;
            float vStart = (worldMinZ / 16f - texCenterChunkZ + texMaxChunk) * scale;
            float uEnd = ((worldMinX + viewW) / 16f - texCenterChunkX + texMaxChunk) * scale;
            float vEnd = ((worldMinZ + viewH) / 16f - texCenterChunkZ + texMaxChunk) * scale;

            float clampedUStart = Math.max(uStart, 0);
            float clampedVStart = Math.max(vStart, 0);
            float clampedUEnd = Math.min(uEnd, texSize);
            float clampedVEnd = Math.min(vEnd, texSize);

            if (clampedUEnd > clampedUStart && clampedVEnd > clampedVStart) {
                float uRange = uEnd - uStart;
                float vRange = vEnd - vStart;
                int drawX1 = x + (int) ((clampedUStart - uStart) / uRange * s);
                int drawY1 = y + (int) ((clampedVStart - vStart) / vRange * s);
                int drawX2 = x + (int) ((clampedUEnd - uStart) / uRange * s);
                int drawY2 = y + (int) ((clampedVEnd - vStart) / vRange * s);
                int drawW = drawX2 - drawX1;
                int drawH = drawY2 - drawY1;
                if (drawW > 0 && drawH > 0) {
                    float regionW = clampedUEnd - clampedUStart;
                    float regionH = clampedVEnd - clampedVStart;
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, MINIMAP_TEXTURE_ID,
                            drawX1, drawY1,
                            clampedUStart, clampedVStart,
                            drawW, drawH,
                            (int) Math.ceil(regionW), (int) Math.ceil(regionH),
                            texSize, texSize);
                }
            }

            // 渲染特殊标记 + 玩家标记
            drawOverlay(ctx, tr, x, y, s, playerX, playerZ, viewW, viewH, self.getYaw());
        }

        // 中心十字+边框装饰
        int cx = x + s / 2;
        int cy = y + s / 2;
        ctx.fill(cx - 4, cy, cx + 5, cy + 1, 0x60FFFFFF);
        ctx.fill(cx, cy - 4, cx + 1, cy + 5, 0x60FFFFFF);

        // 下方实时坐标
        String coord = String.format("X:%d Y:%d Z:%d", (int) self.getX(), (int) self.getY(), (int) self.getZ());
        int cw = tr.getWidth(coord);
        int coordX = x + (s - cw) / 2;
        int coordY = y + s + 3;
        ctx.fill(coordX - 3, coordY - 1, coordX + cw + 3, coordY + 9, 0xCC0A1A0A);
        ctx.fill(coordX - 3, coordY - 1, coordX + cw + 3, coordY, FRAME_BORDER);
        ctx.fill(coordX - 3, coordY + 9, coordX + cw + 3, coordY + 10, FRAME_BORDER);
        ctx.drawText(tr, coord, coordX, coordY, 0xFFFFFFFF, true);
    }

    private static void drawOverlay(DrawContext ctx, TextRenderer tr,
                                    int mx, int my, int size,
                                    float playerX, float playerZ,
                                    float viewW, float viewH, float selfYaw) {
        float worldMinX = playerX - viewW / 2f;
        float worldMinZ = playerZ - viewH / 2f;

        // 特殊标记
        for (int[] p : MapDataCache.getDickPositions()) drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], 0xFF00CCDD, 2);
        for (int[] p : MapDataCache.getWebPositions()) drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], 0xFF999999, 2);
        for (int[] p : MapDataCache.getBackupBodyPositions()) drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], 0xFFFF8800, 2);
        for (int[] p : MapDataCache.getTombstonePositions()) drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], 0xFF8B008B, 2);
        for (int[] p : MapDataCache.getPaintingPositions()) drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], 0xFF9B59B6, 2);
        for (int[] p : MapDataCache.getLayiChestPositions()) drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], 0xFFFF2222, 3);
        for (int[] p : MapDataCache.getBossPositions()) {
            int color = p[2] == 1 ? 0xFFFF4444 : 0xFFFFD700;
            drawDot(ctx, mx, my, size, worldMinX, worldMinZ, viewW, viewH, p[0], p[1], color, 3);
        }

        // 其他玩家(yaw小箭头)
        boolean isSelf = true;
        for (MapDataCache.PlayerMarker pm : MapDataCache.getPlayerMarkers()) {
            if (isSelf) { isSelf = false; continue; } // 第一个为自己, 跳过(用客户端player渲染)
            int sx = mx + (int) ((pm.x() - worldMinX) / viewW * size);
            int sy = my + (int) ((pm.z() - worldMinZ) / viewH * size);
            if (sx < mx || sx >= mx + size || sy < my || sy >= my + size) continue;
            int color = 0xFF000000 | pm.color();
            // 圆点
            ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, color);
            ctx.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFFFFFF);
            // 朝向短箭头
            drawArrow(ctx, sx, sy, pm.yaw(), 5, color);
        }

        // 自己永远显示在中心 + 朝向箭头
        int cx = mx + size / 2;
        int cy = my + size / 2;
        // 外发光
        ctx.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0x60FFFFFF);
        ctx.fill(cx - 3, cy - 3, cx + 4, cy + 4, SELF_COLOR);
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        drawArrow(ctx, cx, cy, selfYaw, 8, SELF_COLOR);
    }

    private static void drawDot(DrawContext ctx, int mx, int my, int size,
                                float worldMinX, float worldMinZ, float viewW, float viewH,
                                int wx, int wz, int color, int radius) {
        int sx = mx + (int) ((wx - worldMinX) / viewW * size);
        int sy = my + (int) ((wz - worldMinZ) / viewH * size);
        if (sx < mx || sx >= mx + size || sy < my || sy >= my + size) return;
        ctx.fill(sx - radius, sy - radius, sx + radius + 1, sy + radius + 1, color);
    }

    // 在(cx,cy)处朝yaw方向画长length的箭头
    // MC约定: yaw=0面向+Z(南/屏幕下), yaw=90面向-X(西/屏幕左), yaw=-90面向+X(东/屏幕右)
    private static void drawArrow(DrawContext ctx, int cx, int cy, float yawDeg, int length, int color) {
        double rad = Math.toRadians(yawDeg);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        double tipX = cx + dx * length;
        double tipY = cy + dz * length;
        int steps = length;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) (cx + (tipX - cx) * t);
            int py = (int) (cy + (tipY - cy) * t);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    // ==================== 纹理重建 ====================

    private static void rebuildTextureIfNeeded(MinecraftClient client) {
        long ver = MapDataCache.getVersion();
        if (ver == lastTextureVersion && texture != null) return;

        int borderRadius = MapDataCache.getBorderRadius();
        if (borderRadius <= 0) return;
        int maxChunk = borderRadius / 16 + 4;
        int scale = 4;
        int size = maxChunk * 2 * scale;
        if (size < 16) size = 16;

        int centerCX = MapDataCache.getBorderCenterX() >> 4;
        int centerCZ = MapDataCache.getBorderCenterZ() >> 4;

        if (texture == null || texSize != size) {
            destroyTexture(client);
            texture = new NativeImageBackedTexture("yepvp_minimap", size, size, false);
            texSize = size;
            texMaxChunk = maxChunk;
        }
        texCenterChunkX = centerCX;
        texCenterChunkZ = centerCZ;

        NativeImage img = texture.getImage();
        if (img == null) return;
        img.fillRect(0, 0, size, size, 0xFF141428);

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

        // 绘制涂色区
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

        texture.upload();
        lastTextureVersion = ver;

        if (!textureRegistered && client != null) {
            client.getTextureManager().registerTexture(MINIMAP_TEXTURE_ID, texture);
            textureRegistered = true;
        }
    }

    public static void destroyTexture(MinecraftClient client) {
        if (textureRegistered && client != null) {
            client.getTextureManager().destroyTexture(MINIMAP_TEXTURE_ID);
            textureRegistered = false;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        lastTextureVersion = -1;
    }
}
