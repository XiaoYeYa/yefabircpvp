package baby.sv.yepvpfabirc.network;

import baby.sv.yepvpfabirc.game.GameManager;
import baby.sv.yepvpfabirc.game.PlayerData;
import baby.sv.yepvpfabirc.game.Role;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HudSyncManager {

    public static void syncAllPlayers(MinecraftServer server) {
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerData selfData = gm.getPlayerData(player.getUuid());
            if (selfData != null) {
                String json = buildHudJson(selfData, gm, player);
                NetworkHandler.syncHudToPlayer(player, json);
            } else {
                // 非参赛观战者也发送基本游戏信息
                String json = buildSpectatorHudJson(gm, player);
                NetworkHandler.syncHudToPlayer(player, json);
            }
        }
    }

    public static void syncSinglePlayer(ServerPlayerEntity player) {
        GameManager gm = GameManager.getInstance();
        if (!gm.isGameActive()) return;
        PlayerData selfData = gm.getPlayerData(player.getUuid());
        if (selfData == null) return;
        String json = buildHudJson(selfData, gm, player);
        NetworkHandler.syncHudToPlayer(player, json);
    }

    private static String buildHudJson(PlayerData self, GameManager gm, ServerPlayerEntity player) {
        JsonObject root = new JsonObject();

        // Self info
        JsonObject selfObj = new JsonObject();
        selfObj.addProperty("name", self.getPlayerName());
        selfObj.addProperty("role", self.getRole().getPlayerId());
        selfObj.addProperty("roleName", self.getRole().getDisplayName());
        selfObj.addProperty("roleDesc", self.getRole().getDescription());
        selfObj.addProperty("lives", self.getLives() == Integer.MAX_VALUE ? -1 : self.getLives());
        selfObj.addProperty("kills", self.getKills());
        selfObj.addProperty("deaths", self.getDeaths());
        selfObj.addProperty("score", self.getScore());
        selfObj.addProperty("alive", self.isAlive());
        root.add("self", selfObj);

        // Skill info based on role
        JsonArray skills = new JsonArray();
        switch (self.getRole()) {
            case XLL -> {
                skills.add(makeSkill("Z", "玻璃正方体", "3minCD，边长10玻璃正方体8秒，内部失明+每秒10%maxHP真伤"));
                skills.add(makeSkill("X", "玻璃长道", "2minCD，向前(东西南北)展开3×50永久玻璃长道"));
                skills.add(makeSkill("-", "被动", "自己的玻璃上速度5+力量2，其他人在你的玻璃上失明"));
            }
            case JIEZU -> {
                skills.add(makeSkill("Z", "强制黑夜", "5min CD，强制夜晚+全体失明30s，30s后回白天"));
                skills.add(makeSkill("X", "蛛网传送", "2min CD，传送至蛛网(循环选择)，已放置" + self.getWebPositions().size() + "/3"));
                skills.add(makeSkill("-", "蜘蛛茧", "每30s获得1雪球(命中晕眩5秒)"));
            }
            case YUSUI -> {
                skills.add(makeSkill("-", "被动", "每30秒获得男娘雪球，命中转化男娘"));
            }
            case NIHAO -> {
                skills.add(makeSkill("-", "/hello", "1minCD，/hello <名> 问好。" + self.getHelloTimerSeconds() + "秒内不/helloback=秒杀。成功→暴露30s"));
            }
            case DAMAI -> {
                skills.add(makeSkill("Z", "墓碑传送", "30s CD，传送至墓碑位置"));
            }
            case DASHA -> {
                skills.add(makeSkill("Z", "召唤雨天", "5min CD，下雨1min。击杀加成: +" + self.getDashaKillBonusStrength() + "力量 +" + self.getDashaKillBonusHealth() + "心"));
            }
            case JVJV -> {
                skills.add(makeSkill("Z", "大爆炸", "每命1次，2s后8格强制秒杀+自己死，每2人+1命" + (self.isExplosionUsed() ? " [已使用]" : "")));
                skills.add(makeSkill("X", "红莲华", "累计炸死" + self.getJvjvTotalExplosionKills() + "/5解锁，10s警告+100格秒杀+存活" + (self.canRedLotus() ? " [已解锁]" : "")));
            }
            case ST -> {
                skills.add(makeSkill("Z", "/Ciallo", "2minCD，暴露自己+看到所有人30秒"));
                skills.add(makeSkill("V", "Ciallo声波", "2minCD，8秒蓄力后结算，0-100格(0->20心)，100-200格(20->10心)，200格+(10心)"));
            }
            case BOBBY -> {
                long bobbyTick = gm.getServer() != null ? gm.getServer().getOverworld().getTime() : 0;
                long bobbyGhostCd = Math.max(0, 2400 - (bobbyTick - self.getLastBobbyGhostTick())) / 20;
                String bobbyGhostState = self.getBobbyGhostEndTick() > bobbyTick
                        ? "§b灵体中(" + ((self.getBobbyGhostEndTick() - bobbyTick) / 20) + "s)"
                        : (bobbyGhostCd > 0 ? "§7冷却" + bobbyGhostCd + "s" : "§a可用");
                skills.add(makeSkill("Z", "灵体形态", "2minCD，30秒灵体(旁观者)，结束就地恢复 [" + bobbyGhostState + "§r]"));
                skills.add(makeSkill("-", "被动", "15心。被动抗性2+虚弱2(肉盾)。命数与但丁共享"));
            }
            case RETOUR -> {
                long retourTick = gm.getServer() != null ? gm.getServer().getOverworld().getTime() : 0;
                long swapCd = Math.max(0, 1200 - (retourTick - self.getLastSkillZTime())) / 20;
                String swapState = swapCd > 0 ? "§7冷却" + swapCd + "s" : "§a可用";
                skills.add(makeSkill("Z", "位置交换", "1minCD，与罪人(Bobby)交换位置 [" + swapState + "§r]"));
                skills.add(makeSkill("V", "激光", "切换激光(无CD)，准星方向30格内每秒1心真伤" + (self.isRetourLaserActive() ? " §c[已开启]" : " §7[关闭]")));
                skills.add(makeSkill("-", "被动", "10心。命数与罪人共享。地图互见"));
            }
            case HELI -> {
                long now = player.getEntityWorld().getTime();
                int revealedNow = (int) self.getRevealedPlayerExpiry().values().stream().filter(t -> t > now).count();
                skills.add(makeSkill("Z", "循环传送", "1minCD 循环选择被揭露玩家(当前" + revealedNow + "人)"));
                skills.add(makeSkill("-", "地图传送", "§eM打开地图→右键选择被揭露玩家传送(共享1minCD)"));
                skills.add(makeSkill("X", "全视之眼", "3minCD 揭露所有玩家1分钟"));
                skills.add(makeSkill("-", "被动", "攻击揭露对方1min(前15s双倍伤害)。完全隐身免疫位置暴露"));
            }
            case ALLAND -> {
                skills.add(makeSkill("Z", "红色颜料弹", "力量I+10s(红+红=3心爆炸 红+蓝=弹飞 红+绿=着火)"));
                skills.add(makeSkill("X", "蓝色颜料弹", "抗性I+10s(蓝+蓝=禁锢 蓝+绿=回血 蓝+黄=雷)"));
                skills.add(makeSkill("C", "绿色颜料弹", "速度I+10s(绿+绿=隐身区域)"));
                skills.add(makeSkill("V", "黄色颜料弹", "敌减速I+10s(黄+黄=分摊雷 蓝+黄=群雷)"));
                skills.add(makeSkill("-", "颜料弹", "§a" + self.getPaintCharges() + "/4 §7(15s+1)"));
            }
            case MACHA -> {
                skills.add(makeSkill("Z", "出钩", "30sCD 第" + self.getMachaFishCount() + "次 (每10次必钓人)"));
                skills.add(makeSkill("X", "钓人", "3s警告+传送到面前 剩余: §a" + self.getMachaFishPlayerCharges() + "§7次"));
            }
            case POPCORN -> {
                skills.add(makeSkill("Z", "放置躯体", "充能" + self.getBackupBodyCharges() + "次 躯体" + self.getBackupBodies().size() + "个(击杀+1充能)"));
                skills.add(makeSkill("X", "躯体传送", "X选择躯体(3s后自动传送,脉冲晕眩5格5s+满血+5心)"));
                skills.add(makeSkill("C", "旁观视角", "旁观者附身他人(再按C返回)"));
                skills.add(makeSkill("V", "轨道镭射", "2minCD 准心/地图发射(3s预警,10格6心/s,10s,全高度)"));
                skills.add(makeSkill("-", "被动", "死亡时有躯体→不扣命+随机躯体复活+满血+5心"));
            }
            case SANCHEZ -> {
                String alphaStatus = self.isAlphaWolfDead() ? "§c死亡" : "§a" + (int)(self.getAlphaWolfHealth() / 2) + "♥";
                int packSize = self.getPackWolfUuids().size();
                skills.add(makeSkill("Z", "治愈头狼", "30sCD 恢复头狼全血(唯一治愈手段)"));
                skills.add(makeSkill("X", "召唤狼", "消耗1心召唤1心狼(次数:" + self.getWolfKillCharges() + " 狼群:" + packSize + "只)"));
                skills.add(makeSkill("V", "狩猎模式", "2minCD 全军无敌10s+速度2"));
                skills.add(makeSkill("C", "召集狼群", "30sCD 传送所有狼到自己位置"));
                skills.add(makeSkill("-", "头狼", alphaStatus + " §7| 攻击锁定+空闲自动攻击 | 头狼死→全狼死(3min复活)"));
            }
            case SHUBING -> {
                skills.add(makeSkill("Z", "放置画", "初始2画,每1min+1(上限2)。碰画进入50格直道。停留10秒死亡。(当前" + self.getPaintingCount() + "/2幅)"));
                skills.add(makeSkill("X", "切换模式", "垂直/水平画模式切换"));
                skills.add(makeSkill("V", "画中传送", "2min CD，画中世界内传送随机玩家到身旁"));
            }
            case JANE -> {
                String topName = "无";
                if (self.getJaneTopTargetUuid() != null) {
                    PlayerData topData = gm.getAllPlayerData().get(self.getJaneTopTargetUuid());
                    if (topData != null) topName = topData.getPlayerName() + "(" + topData.getKills() + "杀)";
                }
                String vision = self.isDarkVisionActive() ? "§c暗黑" : "§a常态";
                skills.add(makeSkill("Z", "暗黑视域", "切换常态/" + vision + " (失明+看到所有人边框)"));
                skills.add(makeSkill("X", "商店", "打开升级商店(力量" + self.getCrossbowPower() + "/5 装填" + self.getCrossbowQuickCharge() + "/3 多重" + self.getCrossbowMultishot() + "/1) 每次75赏金"));
                skills.add(makeSkill("-", "赏金", "§e" + self.getBounty() + " §7| 头号: §c" + topName));
                skills.add(makeSkill("-", "被动", "速度2+无限弩+非玩家伤害翻倍。友好+1/中立敌对+2/玩家+100/头号+100赏金。8心。死亡清赏金"));
            }
            case YOUZHA -> {
                skills.add(makeSkill("Z", "噬元兽", "2minCD 指针处召唤猫,1秒吞噬5格内敌人入异次元,消化2分钟"));
                skills.add(makeSkill("-", "消化期", "自己获得缓慢II+抗性II+回复II；消化完成被困者死亡；你死=全员释放"));
                skills.add(makeSkill("X", "大吃特吃", "无CD 消耗5饱食度随机获得10秒buff(速度/跳跃/回复/力量/抗性/急迫)"));
            }
            case LAYI -> {
                String flightStatus = self.isLayiFlying() ? "§a飞行中" : (self.isLayiFlightActivatedThisLife() ? "§c已用" : "§e可用");
                skills.add(makeSkill("Z", "蜡翼起飞", "每命1次 激活持续飞行+刷2导弹箱 [" + flightStatus + "§r]"));
                long tick = gm.getServer() != null ? gm.getServer().getOverworld().getTime() : 0;
                long arrowCd = Math.max(0, 600 - (tick - self.getLastLayiArrowBarrageTick())) / 20;
                String arrowCdStr = arrowCd > 0 ? "§c" + arrowCd + "s" : "§a就绪";
                skills.add(makeSkill("X", "箭雨", "30sCD 飞行中连射20箭(5%maxHP真伤) [" + arrowCdStr + "§r]"));
                skills.add(makeSkill("-", "被动", "无限鞘翅+飞行免击落+位置暴露。着陆或被导弹炸到=秒杀"));
            }
            case MAYPOOR -> {
                long tick = gm.getServer() != null ? gm.getServer().getOverworld().getTime() : 0;
                long flyCd = Math.max(0, 600 - (tick - self.getLastSkillZTime())) / 20;
                String flyState;
                if (self.isMaypoorFlying(tick)) {
                    flyState = "§a飞行中(" + ((self.getMaypoorFlightEndTick() - tick) / 20) + "s)";
                } else if (flyCd > 0) {
                    flyState = "§c冷却" + flyCd + "s";
                } else {
                    flyState = "§a可用";
                }
                skills.add(makeSkill("Z", "翱翔", "30sCD 创造飞行10秒 [" + flyState + "§r]"));
                long slamCd = Math.max(0, 200 - (tick - self.getLastSkillXTime())) / 20;
                String slamState;
                if (self.isMaypoorSlamArmed()) {
                    slamState = "§e已激活,等待落地";
                } else if (slamCd > 0) {
                    slamState = "§c冷却" + slamCd + "s";
                } else {
                    slamState = "§a可用";
                }
                skills.add(makeSkill("X", "重锤", "10sCD 下落中激活,落地爆炸,每下落1格 半径+0.5/伤害+1 [" + slamState + "§r]"));
                skills.add(makeSkill("-", "被动", "免疫摔落伤害"));
            }
        }
        root.add("skills", skills);

        // Scoreboard - all players
        JsonArray scoreboard = new JsonArray();
        List<PlayerData> sorted = new ArrayList<>(gm.getAllPlayerData().values());
        sorted.sort((a, b) -> b.getScore() - a.getScore());
        for (PlayerData pd : sorted) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", pd.getPlayerName());
            entry.addProperty("lives", pd.getLives() == Integer.MAX_VALUE ? -1 : pd.getLives());
            entry.addProperty("kills", pd.getKills());
            entry.addProperty("score", pd.getScore());
            entry.addProperty("alive", pd.isAlive());
            if (gm.isRevealed()) {
                entry.addProperty("role", pd.getRole().getDisplayName());
            } else if (pd.getPlayerUuid().equals(self.getPlayerUuid())) {
                entry.addProperty("role", pd.getRole().getDisplayName());
            } else {
                entry.addProperty("role", "???");
            }
            scoreboard.add(entry);
        }
        root.add("scoreboard", scoreboard);

        root.addProperty("revealed", gm.isRevealed());
        root.addProperty("gameActive", gm.isGameActive());
        root.addProperty("hasGreeting", false);

        // 左下角提示消息
        long currentTick = gm.getServer() != null ? gm.getServer().getOverworld().getTime() : 0;
        if (self.getTipExpireTick() > currentTick && !self.getTipMessage().isEmpty()) {
            root.addProperty("tipMessage", self.getTipMessage());
        }

        // 边界和游戏时间信息
        JsonObject borderObj = new JsonObject();
        long elapsedTicks = gm.getGameElapsedTicks();
        borderObj.addProperty("gameTimeSec", (int)(elapsedTicks / 20));
        borderObj.addProperty("currentRadius", (int) gm.getCurrentBorderRadius());
        borderObj.addProperty("targetRadius", (int) gm.getTargetBorderRadius());
        borderObj.addProperty("nextShrinkSec", (int)(gm.getNextShrinkTicks() / 20));
        borderObj.addProperty("shrinkCount", gm.getPoisonShrinkCount());
        borderObj.addProperty("distToBorder", (int) gm.getDistanceToBorder(player));
        root.add("border", borderObj);

        return root.toString();
    }

    // 非参赛观战者的HUD数据（只有记分板、边界信息）
    private static String buildSpectatorHudJson(GameManager gm, ServerPlayerEntity player) {
        JsonObject root = new JsonObject();

        // 观战者自身信息（简化）
        JsonObject selfObj = new JsonObject();
        selfObj.addProperty("name", player.getGameProfile().name());
        selfObj.addProperty("role", "spectator");
        selfObj.addProperty("roleName", "观战者");
        selfObj.addProperty("roleDesc", "← → 切换观察玩家 | ↓ 自由视角");
        selfObj.addProperty("lives", 0);
        selfObj.addProperty("kills", 0);
        selfObj.addProperty("deaths", 0);
        selfObj.addProperty("score", 0);
        selfObj.addProperty("alive", false);
        root.add("self", selfObj);

        root.add("skills", new JsonArray());

        // 记分板
        JsonArray scoreboard = new JsonArray();
        List<PlayerData> sorted = new ArrayList<>(gm.getAllPlayerData().values());
        sorted.sort((a, b) -> b.getScore() - a.getScore());
        for (PlayerData pd : sorted) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", pd.getPlayerName());
            entry.addProperty("lives", pd.getLives() == Integer.MAX_VALUE ? -1 : pd.getLives());
            entry.addProperty("kills", pd.getKills());
            entry.addProperty("score", pd.getScore());
            entry.addProperty("alive", pd.isAlive());
            entry.addProperty("role", gm.isRevealed() ? pd.getRole().getDisplayName() : "???");
            scoreboard.add(entry);
        }
        root.add("scoreboard", scoreboard);

        root.addProperty("revealed", gm.isRevealed());
        root.addProperty("gameActive", gm.isGameActive());
        root.addProperty("hasGreeting", false);

        // 边界信息
        JsonObject borderObj = new JsonObject();
        long elapsedTicks = gm.getGameElapsedTicks();
        borderObj.addProperty("gameTimeSec", (int)(elapsedTicks / 20));
        borderObj.addProperty("currentRadius", (int) gm.getCurrentBorderRadius());
        borderObj.addProperty("targetRadius", (int) gm.getTargetBorderRadius());
        borderObj.addProperty("nextShrinkSec", (int)(gm.getNextShrinkTicks() / 20));
        borderObj.addProperty("shrinkCount", gm.getPoisonShrinkCount());
        borderObj.addProperty("distToBorder", (int) gm.getDistanceToBorder(player));
        root.add("border", borderObj);

        return root.toString();
    }

    private static JsonObject makeSkill(String key, String name, String desc) {
        JsonObject skill = new JsonObject();
        skill.addProperty("key", key);
        skill.addProperty("name", name);
        skill.addProperty("desc", desc);
        return skill;
    }
}
