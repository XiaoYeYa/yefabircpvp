package baby.sv.yepvpfabirc.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class HudDataCache {
    private static boolean gameActive = false;
    private static boolean revealed = false;
    private static boolean gameEnded = false;
    private static boolean hasEverReceived = false;
    private static String winnerName = "";
    private static String winnerRole = "";

    // Self info
    private static String selfName = "";
    private static String selfRoleId = "";
    private static String selfRoleName = "";
    private static String selfRoleDesc = "";
    private static int selfLives = 0;
    private static int selfKills = 0;
    private static int selfDeaths = 0;
    private static int selfScore = 0;
    private static boolean selfAlive = true;

    // Skills
    private static final List<SkillInfo> skills = new ArrayList<>();

    // Scoreboard
    private static final List<ScoreEntry> scoreboard = new ArrayList<>();

    // Greeting
    private static boolean hasGreeting = false;
    private static String greetingFrom = "";

    // Tip message (bottom-left)
    private static String tipMessage = "";
    private static long tipReceiveTime = 0;

    // Border info
    private static int gameTimeSec = 0;
    private static int currentBorderRadius = 1000;
    private static int targetBorderRadius = 1000;
    private static int nextShrinkSec = 0;
    private static int shrinkCount = 0;
    private static int distToBorder = 0;

    public static void updateFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            hasEverReceived = true;

            gameActive = root.get("gameActive").getAsBoolean();
            revealed = root.get("revealed").getAsBoolean();
            gameEnded = root.has("gameEnded") && root.get("gameEnded").getAsBoolean();
            winnerName = root.has("winnerName") ? root.get("winnerName").getAsString() : "";
            winnerRole = root.has("winnerRole") ? root.get("winnerRole").getAsString() : "";
            hasGreeting = root.get("hasGreeting").getAsBoolean();
            if (root.has("greetingFrom")) {
                greetingFrom = root.get("greetingFrom").getAsString();
            } else {
                greetingFrom = "";
            }

            JsonObject self = root.getAsJsonObject("self");
            selfName = self.get("name").getAsString();
            selfRoleId = self.get("role").getAsString();
            selfRoleName = self.get("roleName").getAsString();
            selfRoleDesc = self.get("roleDesc").getAsString();
            selfLives = self.get("lives").getAsInt();
            selfKills = self.get("kills").getAsInt();
            selfDeaths = self.get("deaths").getAsInt();
            selfScore = self.get("score").getAsInt();
            selfAlive = self.get("alive").getAsBoolean();

            skills.clear();
            JsonArray skillArr = root.getAsJsonArray("skills");
            for (int i = 0; i < skillArr.size(); i++) {
                JsonObject s = skillArr.get(i).getAsJsonObject();
                skills.add(new SkillInfo(
                        s.get("key").getAsString(),
                        s.get("name").getAsString(),
                        s.get("desc").getAsString()
                ));
            }

            scoreboard.clear();
            JsonArray sbArr = root.getAsJsonArray("scoreboard");
            for (int i = 0; i < sbArr.size(); i++) {
                JsonObject e = sbArr.get(i).getAsJsonObject();
                scoreboard.add(new ScoreEntry(
                        e.get("name").getAsString(),
                        e.get("role").getAsString(),
                        e.get("lives").getAsInt(),
                        e.get("kills").getAsInt(),
                        e.get("score").getAsInt(),
                        e.get("alive").getAsBoolean()
                ));
            }
            // Tip message
            if (root.has("tipMessage")) {
                tipMessage = root.get("tipMessage").getAsString();
                tipReceiveTime = System.currentTimeMillis();
            } else {
                // 如果服务端不再发送tip, 3秒后清除
                if (System.currentTimeMillis() - tipReceiveTime > 3000) {
                    tipMessage = "";
                }
            }

            // Border info
            if (root.has("border")) {
                JsonObject border = root.getAsJsonObject("border");
                gameTimeSec = border.get("gameTimeSec").getAsInt();
                currentBorderRadius = border.get("currentRadius").getAsInt();
                targetBorderRadius = border.get("targetRadius").getAsInt();
                nextShrinkSec = border.get("nextShrinkSec").getAsInt();
                shrinkCount = border.get("shrinkCount").getAsInt();
                distToBorder = border.get("distToBorder").getAsInt();
            }
        } catch (Exception e) {
            System.err.println("[YePvP Client] Failed to parse HUD data: " + e.getMessage());
        }
    }

    public static void reset() {
        gameActive = false;
        revealed = false;
        gameEnded = false;
        hasEverReceived = false;
        winnerName = "";
        winnerRole = "";
        selfName = "";
        selfRoleId = "";
        selfRoleName = "";
        selfRoleDesc = "";
        selfLives = 0;
        selfKills = 0;
        selfDeaths = 0;
        selfScore = 0;
        selfAlive = true;
        skills.clear();
        scoreboard.clear();
        hasGreeting = false;
        greetingFrom = "";
        tipMessage = "";
        tipReceiveTime = 0;
        gameTimeSec = 0;
        currentBorderRadius = 1000;
        targetBorderRadius = 1000;
        nextShrinkSec = 0;
        shrinkCount = 0;
        distToBorder = 0;
    }

    public static boolean isGameActive() { return gameActive; }
    public static boolean isRevealed() { return revealed; }
    public static boolean isGameEnded() { return gameEnded; }
    public static boolean hasEverReceived() { return hasEverReceived; }
    public static String getWinnerName() { return winnerName; }
    public static String getWinnerRole() { return winnerRole; }
    public static String getSelfName() { return selfName; }
    public static String getSelfRoleId() { return selfRoleId; }
    public static String getSelfRoleName() { return selfRoleName; }
    public static String getSelfRoleDesc() { return selfRoleDesc; }
    public static int getSelfLives() { return selfLives; }
    public static int getSelfKills() { return selfKills; }
    public static int getSelfDeaths() { return selfDeaths; }
    public static int getSelfScore() { return selfScore; }
    public static boolean isSelfAlive() { return selfAlive; }
    public static List<SkillInfo> getSkills() { return skills; }
    public static List<ScoreEntry> getScoreboard() { return scoreboard; }
    public static boolean hasGreeting() { return hasGreeting; }
    public static String getGreetingFrom() { return greetingFrom; }

    // Tip getters
    public static String getTipMessage() { return tipMessage; }
    public static long getTipReceiveTime() { return tipReceiveTime; }

    // Border getters
    public static int getGameTimeSec() { return gameTimeSec; }
    public static int getCurrentBorderRadius() { return currentBorderRadius; }
    public static int getTargetBorderRadius() { return targetBorderRadius; }
    public static int getNextShrinkSec() { return nextShrinkSec; }
    public static int getShrinkCount() { return shrinkCount; }
    public static int getDistToBorder() { return distToBorder; }

    public record SkillInfo(String key, String name, String desc) {}
    public record ScoreEntry(String name, String role, int lives, int kills, int score, boolean alive) {}
}
