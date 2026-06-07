package baby.sv.yepvpfabirc.client.hud;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端地图数据缓存: 服务端每2秒推送一次, HUD小地图和全屏地图共用
 */
public class MapDataCache {

    // 不可变快照 — 更新时原子替换引用, 渲染端直接读取无需复制
    private static volatile int borderRadius = 1000;
    private static volatile int borderCenterX = 0;
    private static volatile int borderCenterZ = 0;
    private static volatile Map<Long, int[]> chunkColors = Map.of();
    private static volatile List<PlayerMarker> playerMarkers = List.of();
    private static volatile List<PaintedChunk> paintedChunks = List.of();
    private static volatile List<int[]> dickPositions = List.of();
    private static volatile List<int[]> webPositions = List.of();
    private static volatile List<int[]> backupBodyPositions = List.of();
    private static volatile List<int[]> tombstonePositions = List.of();
    private static volatile List<int[]> paintingPositions = List.of();
    private static volatile List<int[]> bossPositions = List.of(); // x, z, type(0=预警,1=铁傀儡,2=监守者)
    private static volatile List<int[]> layiChestPositions = List.of();
    private static volatile long lastUpdateTime = 0;
    private static volatile long version = 0;

    public static void updateFromBytes(byte[] data) {
        try {
            // gzip解压
            byte[] decompressed;
            try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(data));
                 java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = gzis.read(buf)) > 0) bout.write(buf, 0, n);
                decompressed = bout.toByteArray();
            }
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decompressed));

            int br = dis.readInt();
            int bcx = dis.readInt();
            int bcz = dis.readInt();

            int chunkCount = dis.readInt();
            Map<Long, int[]> cc = new HashMap<>(chunkCount * 2);
            for (int i = 0; i < chunkCount; i++) {
                short cx = dis.readShort();
                short cz = dis.readShort();
                int[] subColors = new int[16];
                for (int j = 0; j < 16; j++) {
                    int r = dis.readUnsignedByte();
                    int g = dis.readUnsignedByte();
                    int b = dis.readUnsignedByte();
                    subColors[j] = (r << 16) | (g << 8) | b;
                }
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                cc.put(key, subColors);
            }

            int markerCount = dis.readInt();
            List<PlayerMarker> pm = new ArrayList<>(markerCount);
            for (int i = 0; i < markerCount; i++) {
                int x = dis.readInt();
                int z = dis.readInt();
                short nameLen = dis.readShort();
                byte[] nameBytes = new byte[nameLen];
                dis.readFully(nameBytes);
                String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                int color = dis.readInt();
                float yaw = dis.readFloat();
                pm.add(new PlayerMarker(x, z, name, color, yaw));
            }

            int paintedCount = dis.readInt();
            List<PaintedChunk> pc = new ArrayList<>(paintedCount);
            for (int i = 0; i < paintedCount; i++) {
                short cx = dis.readShort();
                short cz = dis.readShort();
                byte paintColor = dis.readByte();
                pc.add(new PaintedChunk(cx, cz, paintColor));
            }

            List<int[]> dp = new ArrayList<>();
            if (dis.available() >= 4) {
                int dickCount = dis.readInt();
                for (int i = 0; i < dickCount; i++) {
                    dp.add(new int[]{dis.readInt(), dis.readInt()});
                }
            }

            List<int[]> wp = new ArrayList<>();
            if (dis.available() >= 4) {
                int webCount = dis.readInt();
                for (int i = 0; i < webCount; i++) {
                    wp.add(new int[]{dis.readInt(), dis.readInt()});
                }
            }

            List<int[]> bbp = new ArrayList<>();
            if (dis.available() >= 4) {
                int bbCount = dis.readInt();
                for (int i = 0; i < bbCount; i++) {
                    bbp.add(new int[]{dis.readInt(), dis.readInt()});
                }
            }

            List<int[]> tsp = new ArrayList<>();
            if (dis.available() >= 4) {
                int tsCount = dis.readInt();
                for (int i = 0; i < tsCount; i++) {
                    tsp.add(new int[]{dis.readInt(), dis.readInt()});
                }
            }

            List<int[]> ppp = new ArrayList<>();
            if (dis.available() >= 4) {
                int ppCount = dis.readInt();
                for (int i = 0; i < ppCount; i++) {
                    ppp.add(new int[]{dis.readInt(), dis.readInt()});
                }
            }

            List<int[]> bp = new ArrayList<>();
            if (dis.available() >= 4) {
                int bossCount = dis.readInt();
                for (int i = 0; i < bossCount; i++) {
                    bp.add(new int[]{dis.readInt(), dis.readInt(), dis.readUnsignedByte()});
                }
            }

            List<int[]> lcp = new ArrayList<>();
            if (dis.available() >= 4) {
                int layiCount = dis.readInt();
                for (int i = 0; i < layiCount; i++) {
                    lcp.add(new int[]{dis.readInt(), dis.readInt()});
                }
            }

            // 原子替换所有引用
            borderRadius = br;
            borderCenterX = bcx;
            borderCenterZ = bcz;
            chunkColors = java.util.Collections.unmodifiableMap(cc);
            playerMarkers = java.util.Collections.unmodifiableList(pm);
            paintedChunks = java.util.Collections.unmodifiableList(pc);
            dickPositions = java.util.Collections.unmodifiableList(dp);
            webPositions = java.util.Collections.unmodifiableList(wp);
            backupBodyPositions = java.util.Collections.unmodifiableList(bbp);
            tombstonePositions = java.util.Collections.unmodifiableList(tsp);
            paintingPositions = java.util.Collections.unmodifiableList(ppp);
            bossPositions = java.util.Collections.unmodifiableList(bp);
            layiChestPositions = java.util.Collections.unmodifiableList(lcp);
            lastUpdateTime = System.currentTimeMillis();
            version++;
        } catch (Exception e) {
            System.err.println("[YePvP MapCache] Failed to parse: " + e.getMessage());
        }
    }

    public static boolean hasData() { return lastUpdateTime > 0; }
    public static int getBorderRadius() { return borderRadius; }
    public static int getBorderCenterX() { return borderCenterX; }
    public static int getBorderCenterZ() { return borderCenterZ; }
    public static long getVersion() { return version; }
    public static Map<Long, int[]> getChunkColors() { return chunkColors; }
    public static List<PlayerMarker> getPlayerMarkers() { return playerMarkers; }
    public static List<PaintedChunk> getPaintedChunks() { return paintedChunks; }
    public static List<int[]> getDickPositions() { return dickPositions; }
    public static List<int[]> getWebPositions() { return webPositions; }
    public static List<int[]> getBackupBodyPositions() { return backupBodyPositions; }
    public static List<int[]> getTombstonePositions() { return tombstonePositions; }
    public static List<int[]> getPaintingPositions() { return paintingPositions; }
    public static List<int[]> getBossPositions() { return bossPositions; }
    public static List<int[]> getLayiChestPositions() { return layiChestPositions; }

    public static void reset() {
        chunkColors = Map.of();
        playerMarkers = List.of();
        paintedChunks = List.of();
        dickPositions = List.of();
        webPositions = List.of();
        backupBodyPositions = List.of();
        tombstonePositions = List.of();
        paintingPositions = List.of();
        bossPositions = List.of();
        layiChestPositions = List.of();
        lastUpdateTime = 0;
        version++;
    }

    public record PlayerMarker(int x, int z, String name, int color, float yaw) {}
    public record PaintedChunk(int cx, int cz, byte paintColor) {}
}
