package baby.sv.yepvpfabirc.config;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.Role;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AprilFoolsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "aprilfools.json";

    private final Map<String, String> playerRoles = new HashMap<>();
    private final Map<String, int[]> playerSpawns = new HashMap<>();
    private int[] arenaCenter = {0, 64, 0};
    private int arenaRadius = 50;

    public void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILE);

        if (!Files.exists(configFile)) {
            generateDefault(configFile);
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root.has("arena")) {
                JsonObject arena = root.getAsJsonObject("arena");
                if (arena.has("center")) {
                    JsonArray c = arena.getAsJsonArray("center");
                    arenaCenter = new int[]{c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt()};
                }
                if (arena.has("radius")) {
                    arenaRadius = arena.get("radius").getAsInt();
                }
            }

            if (root.has("players")) {
                JsonObject players = root.getAsJsonObject("players");
                for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                    JsonObject playerObj = entry.getValue().getAsJsonObject();
                    String playerName = entry.getKey();
                    if (playerObj.has("role")) {
                        playerRoles.put(playerName.toLowerCase(), playerObj.get("role").getAsString());
                    }
                    if (playerObj.has("spawn")) {
                        JsonArray s = playerObj.getAsJsonArray("spawn");
                        playerSpawns.put(playerName.toLowerCase(), new int[]{s.get(0).getAsInt(), s.get(1).getAsInt(), s.get(2).getAsInt()});
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[YePvP] Failed to load config: " + e.getMessage());
        }
    }

    public void applyToGameManager() {
        GameManager gm = GameManager.getInstance();

        gm.setArena(new BlockPos(arenaCenter[0], arenaCenter[1], arenaCenter[2]), arenaRadius);

        for (Map.Entry<String, String> entry : playerRoles.entrySet()) {
            Role role = Role.fromId(entry.getValue());
            if (role != null) {
                gm.assignRole(entry.getKey(), role);
            }
        }

        for (Map.Entry<String, int[]> entry : playerSpawns.entrySet()) {
            int[] s = entry.getValue();
            gm.setSpawnPoint(entry.getKey(), new BlockPos(s[0], s[1], s[2]));
        }
    }

    public void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILE);

        JsonObject root = new JsonObject();

        JsonObject arena = new JsonObject();
        JsonArray center = new JsonArray();
        center.add(arenaCenter[0]);
        center.add(arenaCenter[1]);
        center.add(arenaCenter[2]);
        arena.add("center", center);
        arena.addProperty("radius", arenaRadius);
        root.add("arena", arena);

        JsonObject players = new JsonObject();
        GameManager gm = GameManager.getInstance();
        for (Map.Entry<String, Role> entry : gm.getPlayerNameToRole().entrySet()) {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("role", entry.getValue().getPlayerId());
            if (playerSpawns.containsKey(entry.getKey())) {
                int[] s = playerSpawns.get(entry.getKey());
                JsonArray spawn = new JsonArray();
                spawn.add(s[0]);
                spawn.add(s[1]);
                spawn.add(s[2]);
                playerObj.add("spawn", spawn);
            }
            players.add(entry.getKey(), playerObj);
        }
        root.add("players", players);

        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            System.err.println("[YePvP] Failed to save config: " + e.getMessage());
        }
    }

    private void generateDefault(Path configFile) {
        JsonObject root = new JsonObject();

        JsonObject arena = new JsonObject();
        JsonArray center = new JsonArray();
        center.add(0); center.add(64); center.add(0);
        arena.add("center", center);
        arena.addProperty("radius", 50);
        root.add("arena", arena);

        JsonObject players = new JsonObject();
        for (Role role : Role.values()) {
            JsonObject p = new JsonObject();
            p.addProperty("role", role.getPlayerId());
            JsonArray spawn = new JsonArray();
            spawn.add(0); spawn.add(64); spawn.add(0);
            p.add("spawn", spawn);
            players.add(role.getPlayerId(), p);
        }
        root.add("players", players);

        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            System.err.println("[YePvP] Failed to generate default config: " + e.getMessage());
        }
    }

    public Map<String, int[]> getPlayerSpawns() { return playerSpawns; }
}
