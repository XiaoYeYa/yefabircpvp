package baby.sv.yepvpfabirc.game;

import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PlayerData {
    // ===== 通用字段 =====
    private final UUID playerUuid;
    private final String playerName;
    private Role role;
    private int lives = 5;
    private int kills = 0;
    private int deaths = 0;
    private int score = 0;
    private boolean revealed = false;
    private BlockPos spawnPoint;
    private long lastSkillZTime = 0;
    private long lastSkillXTime = 0;
    private long lastSkillVTime = 0;
    private long lastSkillCTime = 0;
    private long respawnInvincibleUntil = 0;
    private long revealUntilTick = 0; // 位置暴露截止tick(多职业共用)
    private long lastCorridorExitTick = 0; // 离开画中世界通道的tick(5秒进入冷却,防止反复进入)
    private long corridorFallImmuneUntilTick = 0; // 进入画中世界后20秒摔落免疫截止tick
    private long needsBorderSafeTeleportUntilTick = 0; // 刚重生后若在圈外自动传送的期限(窗口), 0=不触发

    // ===== 左下角提示消息(替代actionbar) =====
    private String tipMessage = "";
    private long tipExpireTick = 0;

    // ===== YUSUI 鱼碎 =====
    private final Set<UUID> nanniangTargets = new HashSet<>();
    private boolean isNanniang = false;
    private UUID nanniangMaster = null;
    private long lastSnowballGiveTick = 0;
    private double yusuiBonusMaxHealth = 0.0; // 男娘被淘汰累积的+2心, 死亡保留

    // ===== XLL =====
    private boolean glassCubeActive = false;
    private BlockPos glassCubeCenter = null;
    private long glassCubeEndTick = 0;
    private final Set<BlockPos> glassRoadBlocks = new HashSet<>(); // 主动2: 永久玻璃长道方块

    // ===== BOBBY 罪人 =====
    private boolean waitingForRevive = false;
    private BlockPos deathPos = null;
    private int apostleStrengthStacks = 0; // 与RETOUR共享, 最多5
    private boolean retourDead = false;
    private boolean bobbyCanRevive = true; // 但丁死后变false
    private int sinResource = 0; // 罪孽资源
    private long lastBobbyGhostTick = 0; // 灵体CD
    private long bobbyGhostEndTick = 0; // 灵体结束tick
    private net.minecraft.util.math.Vec3d bobbyGhostStartPos = null; // 灵体开始位置

    // ===== RETOUR 但丁 =====
    // apostleStrengthStacks 共享
    private long retourRevealedUntilTick = 0; // 复活罪人后地图暴露截止tick
    private boolean retourLaserActive = false; // 激光是否激活

    // ===== ST 柚子厨 =====
    private long lastCialloTick = 0; // Z键Ciallo冷却
    private long cialloRevealUntilTick = 0; // 自己暴露截止tick
    private long cialloSeeAllUntilTick = 0; // 看到所有人截止tick
    private long lastSonicTick = 0; // V键声波冷却
    private long sonicPendingTick = 0; // 声波8秒蓄力开始tick(0=无)
    private boolean sonicReadyNotified = false; // 声波冷却完毕广播已发
    private BlockPos sonicLockPos = null; // 蓄力期间冻结位置
    private UUID sonicAutoAimTarget = null; // 自瞄锁定目标UUID
    private long sonicAutoAimStartTick = 0; // 自瞄开始tick
    private int sonicSelectIndex = 0; // 目标选择循环索引

    // ===== JIEZU 节足动物 =====
    private final List<BlockPos> webPositions = new ArrayList<>(); // 最多3个蛛网
    private long lastWebGainTick = 0; // 上次获得蛛网的tick
    private long lastCocoonGainTick = 0; // 上次获得蜘蛛茧(晕眩雪球)的tick, 30s一个
    private long jiezuNightEndTick = 0; // 强制夜晚结束tick(30秒后回白天)
    private int jiezuWebTpIndex = 0; // 传送目标蛛网索引(循环)
    private long stunUntilTick = 0; // 晕眩(完全定身)截止tick

    // ===== DAMAI 不死者 =====
    private BlockPos tombstonePos = null;
    private boolean skeletonForm = false;
    private long skeletonFormStartTick = 0;
    private long skeletonFormEndTick = 0;
    private int skeletonKills = 0;
    private boolean damaiWaitingForSkeleton = false; // 死亡后3秒等待转化期
    private long damaiDeathTick = 0; // 死亡时间(用于3秒延迟)
    private long tombstoneProtectedUntilTick = 0; // 墓碑保护截止tick

    // ===== DASHA 鲨鱼人 =====
    private boolean inWater = false;
    private long lastOutOfWaterTime = 0;
    private int dashaKillBonusStrength = 0; // 最多5
    private int dashaKillBonusHealth = 0;   // 最多5
    private long dashaVisibleUntilTick = 0; // 攻击/冲刺后显形截止tick

    // ===== NIHAO 问好 =====
    private UUID helloTargetUuid = null; // 当前问好目标
    private int helloCode = 0; // 6位随机数
    private long helloExpiryTick = 0; // 问好超时tick
    private int helloTimerSeconds = 15; // 回复倒计时(初始15秒, 击杀-1, 死亡+1)
    private long nihaoRevealUntilTick = 0; // 问好失败后暴露截止tick
    private long lastHelloBroadcastTick = -1; // 每10分钟广播你好(-1表示未初始化,开局时设为gameStartTick)
    // 全体问好(广播版)
    private final Map<UUID, Integer> broadcastHelloCodes = new HashMap<>(); // 目标UUID→验证码
    private long broadcastHelloExpiryTick = 0;
    private final Set<UUID> broadcastHelloResponded = new HashSet<>(); // 已回复的玩家

    // ===== HELI =====
    private final Set<UUID> revealedPlayers = new HashSet<>();
    private final Map<UUID, Long> revealedPlayerExpiry = new HashMap<>(); // 揭露过期时间
    private final Map<UUID, Long> heliRevealStartTick = new HashMap<>(); // 揭露开始tick(15s内双倍伤害)
    private int heliTpIndex = 0; // 传送循环索引

    // ===== JVJV 大爆炸 =====
    private boolean explosionUsed = false;
    private int lastExplosionKills = 0;
    private int jvjvTotalExplosionKills = 0; // 累计大爆炸击杀数
    private boolean canRedLotus = false;
    private boolean redLotusUsed = false;
    private long redLotusActiveTick = 0; // 红莲华激活时间(冻结期间)
    private BlockPos redLotusPos = null; // 红莲华激活位置(冻结锁定点)
    private long lastJvjvHealTick = 0; // 小技能(进食回血)上次使用tick
    private long lastJvjvEatTick = 0; // 吃人CD
    private UUID jvjvEatenPlayer = null; // 被吃的玩家UUID
    private long jvjvDigestEndTick = 0; // 消化结束tick
    private BlockPos jvjvStomachRoomPos = null; // 胃袋房间位置
    private net.minecraft.util.math.Vec3d jvjvEatenOriginalPos = null; // 被吃玩家原位置

    // ===== ALLAND 喷射战士 =====
    private int paintCharges = 0; // 颜料弹次数, 最多4
    private long lastPaintChargeGainTick = 0; // 上次获得次数的tick

    // ===== MACHA 钓鱼 =====
    private int machaFishCount = 0; // 总钓鱼次数
    private UUID machaFishingTarget = null;
    private long machaFishingStartTick = 0;
    private final java.util.Set<String> machaObtainedWeapons = new java.util.HashSet<>(); // 已获得的下界合金武器
    private final java.util.Set<String> machaObtainedArmor = new java.util.HashSet<>(); // 已获得的下界合金装备
    private int machaFishPlayerCharges = 0; // 钓人次数(每10次出钩+1)

    // ===== POPCORN 智械危机 =====
    private final List<BlockPos> backupBodies = new ArrayList<>();
    private int backupBodyCharges = 2; // 开局2次
    private long lastLaserTick = 0;
    private BlockPos laserTarget = null;
    private long laserEndTick = 0;
    private boolean popcornReviving = false; // 被动复活进行中(等待新实体)
    private BlockPos popcornRevivePos = null; // 被动复活目标位置
    private long popcornBonusHealthExpiry = 0; // 临时+5心到期tick(0=无)
    private boolean laserReadyNotified = false; // 镭射CD完毕广播已发
    private BlockPos laserPendingTarget = null; // 5秒预警中的镭射目标
    private long laserPendingStartTick = 0; // 预警开始tick
    private UUID popcornCameraTarget = null; // 当前视角目标UUID(null=自己)
    private int popcornBodyTpIndex = 0; // 躯体传送循环索引
    private long popcornBodySelectTick = 0; // 躯体选择模式开始tick(0=未选择)
    private boolean popcornSpectating = false; // 是否正在旁观他人
    private long popcornSpectateSelectTick = 0; // 旁观选择模式开始tick(0=未选择)
    private int popcornSpectateSelectIndex = 0; // 旁观选择循环索引
    private float popcornBodyLastHealth = 0; // 躯壳上次血量(检测伤害用)
    private double popcornSavedX, popcornSavedY, popcornSavedZ; // 旁观前保存的位置
    private float popcornSavedYaw, popcornSavedPitch; // 旁观前保存的朝向

    // ===== SANCHEZ 诸神黄昏 =====
    private double alphaWolfHealth = 40.0; // 20心=40hp
    private boolean alphaWolfDead = false;
    private long alphaWolfDeathTick = 0;
    private int wolfKillCharges = 0;
    private UUID alphaWolfEntityUuid = null; // 头狼实体UUID
    private final List<UUID> packWolfUuids = new ArrayList<>(); // 狼群实体UUID
    private long sanchezHuntEndTick = 0; // 狩猎模式结束tick
    private UUID sanchezAttackTarget = null; // 指令锁定目标UUID

    // ===== JANE 赏金猎人 =====
    private int bounty = 0;
    private boolean darkVisionActive = false;
    private int crossbowPower = 0; // 力量附魔等级
    private int crossbowQuickCharge = 0; // 快速装填附魔等级
    private int crossbowMultishot = 0; // 多重射击附魔等级
    private UUID janeTopTargetUuid = null; // 头号目标UUID

    // ===== SHUBING 薯饼 =====
    private int paintingCount = 2; // 开局2幅画(背包里)
    private long lastPaintingGainTick = 0;
    private boolean paintingModeHorizontal = false; // false=垂直模式, true=水平模式(X键切换)
    private long lastShubingSummonTick = 0; // 主动2: 画中世界传送随机玩家 2min CD
    // 每幅画: 位置 + 实体UUID + 是否水平放置 + 所属通道索引(-1=未配对)
    private final List<ShubingPainting> placedPaintings = new ArrayList<>();
    // 每条通道: origin + 方向 + 两端画索引 + 封口状态
    private final List<ShubingCorridor> corridors = new ArrayList<>();
    // 通道内玩家: playerUUID -> CorridorEntry(corridorIndex, entryTick, enteredFromEnd 0或1)
    private final Map<UUID, CorridorEntry> corridorPlayers = new HashMap<>();

    // 内部数据类
    public static class ShubingPainting {
        public BlockPos pos;
        public UUID entityUuid; // 方块标识UUID
        public boolean horizontal; // true=水平放置, false=垂直放置
        public int corridorIndex = -1; // 所属通道索引, -1=未配对
        public int corridorEnd = -1; // 在通道中是哪一端: 0=入口端, 1=出口端
        public String hologramTag = null; // 悬浮文字text_display的命令标签

        public ShubingPainting(BlockPos pos, UUID entityUuid, boolean horizontal) {
            this.pos = pos;
            this.entityUuid = entityUuid;
            this.horizontal = horizontal;
        }
    }

    public static class ShubingCorridor {
        public BlockPos origin; // 通道方块起点(天空中)
        public boolean vertical; // true=垂直通道(攀爬), false=水平通道(行走)
        public int painting0Index; // 第一幅画在placedPaintings中的索引(入口端)
        public int painting1Index; // 第二幅画在placedPaintings中的索引(出口端)
        public boolean end0Sealed = false; // 入口端是否被封
        public boolean end1Sealed = false; // 出口端是否被封

        public ShubingCorridor(BlockPos origin, boolean vertical, int p0, int p1) {
            this.origin = origin;
            this.vertical = vertical;
            this.painting0Index = p0;
            this.painting1Index = p1;
        }
    }

    public static class CorridorEntry {
        public int corridorIndex;
        public long entryTick;
        public int enteredFromEnd; // 0=从入口端进入, 1=从出口端进入

        public CorridorEntry(int corridorIndex, long entryTick, int enteredFromEnd) {
            this.corridorIndex = corridorIndex;
            this.entryTick = entryTick;
            this.enteredFromEnd = enteredFromEnd;
        }
    }

    // ===== YOUZHA 油炸意面 =====
    private long lastYouzhaUltimateTick = 0; // 噬元兽CD
    private long lastYouzhaEatTick = 0; // 大吃特吃CD
    private final List<UUID> youzhaTrappedPlayers = new ArrayList<>(); // 被吞入异次元的玩家
    private final Map<UUID, net.minecraft.util.math.Vec3d> youzhaTrappedOriginalPos = new HashMap<>(); // 被吞玩家的原始位置
    private long youzhaTrappedReleaseTick = 0; // 异次元释放时间
    private BlockPos youzhaTrapRoomPos = null; // 异次元小房间位置

    // ===== LAYI(蜡翼) =====
    private boolean layiFlying = false; // 是否处于持续飞行状态
    private boolean layiFlightActivatedThisLife = false; // 本命是否已激活飞行
    private long lastLayiArrowBarrageTick = 0; // X技能CD
    private int layiArrowBurstRemaining = 0; // 连射剩余箭矢数
    private long layiArrowBurstNextTick = 0; // 下一支箭发射时间
    private final List<BlockPos> layiSpecialChestPositions = new ArrayList<>(); // 特殊烟花箱子位置
    private final List<UUID> layiMissileEntities = new ArrayList<>(); // 活跃的导弹实体UUID

    // ===== MAYPOOR(天锤) =====
    private long maypoorFlightEndTick = 0; // 创造飞行结束tick
    private boolean maypoorSlamArmed = false; // 技能2是否已激活(等待下落+落地)
    private double maypoorSlamStartY = 0; // 激活时的Y坐标(用于计算下落距离)

    public PlayerData(UUID playerUuid, String playerName, Role role) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.role = role;
    }

    // ===== 通用 getter/setter =====
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public int getLives() { return lives; }
    public void setLives(int lives) { this.lives = lives; }
    public void loseLife() { if (this.lives != Integer.MAX_VALUE) this.lives--; }
    public void gainLife() { if (this.lives != Integer.MAX_VALUE) this.lives++; }
    public boolean isAlive() { return this.lives > 0; }
    public int getKills() { return kills; }
    public void addKill() { this.kills++; this.score += 10; }
    public int getDeaths() { return deaths; }
    public void addDeath() { this.deaths++; }
    public int getScore() { return score; }
    public void addScore(int amount) { this.score += amount; }
    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }
    public BlockPos getSpawnPoint() { return spawnPoint; }
    public void setSpawnPoint(BlockPos spawnPoint) { this.spawnPoint = spawnPoint; }
    public long getLastSkillZTime() { return lastSkillZTime; }
    public void setLastSkillZTime(long time) { this.lastSkillZTime = time; }
    public long getLastSkillXTime() { return lastSkillXTime; }
    public void setLastSkillXTime(long time) { this.lastSkillXTime = time; }
    public long getLastSkillVTime() { return lastSkillVTime; }
    public void setLastSkillVTime(long time) { this.lastSkillVTime = time; }
    public long getLastSkillCTime() { return lastSkillCTime; }
    public void setLastSkillCTime(long time) { this.lastSkillCTime = time; }
    public long getRespawnInvincibleUntil() { return respawnInvincibleUntil; }
    public void setRespawnInvincibleUntil(long tick) { this.respawnInvincibleUntil = tick; }
    public boolean isInvincible(long currentTick) { return currentTick < respawnInvincibleUntil; }
    public long getRevealUntilTick() { return revealUntilTick; }
    public void setRevealUntilTick(long tick) { this.revealUntilTick = tick; }
    public boolean isPositionExposed(long currentTick) { return revealUntilTick > currentTick || revealed; }
    public long getLastCorridorExitTick() { return lastCorridorExitTick; }
    public void setLastCorridorExitTick(long tick) { this.lastCorridorExitTick = tick; }
    public long getCorridorFallImmuneUntilTick() { return corridorFallImmuneUntilTick; }
    public long getNeedsBorderSafeTeleportUntilTick() { return needsBorderSafeTeleportUntilTick; }
    public void setNeedsBorderSafeTeleportUntilTick(long tick) { this.needsBorderSafeTeleportUntilTick = tick; }
    public void setCorridorFallImmuneUntilTick(long tick) { this.corridorFallImmuneUntilTick = tick; }

    // ===== 提示消息 =====
    public String getTipMessage() { return tipMessage; }
    public long getTipExpireTick() { return tipExpireTick; }
    public void setTip(String msg, long expireTick) { this.tipMessage = msg; this.tipExpireTick = expireTick; }

    // ===== YUSUI =====
    public Set<UUID> getNanniangTargets() { return nanniangTargets; }
    public boolean isNanniang() { return isNanniang; }
    public void setNanniang(boolean nanniang) { this.isNanniang = nanniang; }
    public UUID getNanniangMaster() { return nanniangMaster; }
    public void setNanniangMaster(UUID master) { this.nanniangMaster = master; }
    public long getLastSnowballGiveTick() { return lastSnowballGiveTick; }
    public void setLastSnowballGiveTick(long tick) { this.lastSnowballGiveTick = tick; }
    public double getYusuiBonusMaxHealth() { return yusuiBonusMaxHealth; }
    public void addYusuiBonusMaxHealth(double add) { this.yusuiBonusMaxHealth += add; }

    // ===== XLL =====
    public boolean isGlassCubeActive() { return glassCubeActive; }
    public void setGlassCubeActive(boolean active) { this.glassCubeActive = active; }
    public BlockPos getGlassCubeCenter() { return glassCubeCenter; }
    public void setGlassCubeCenter(BlockPos pos) { this.glassCubeCenter = pos; }
    public long getGlassCubeEndTick() { return glassCubeEndTick; }
    public void setGlassCubeEndTick(long tick) { this.glassCubeEndTick = tick; }
    public Set<BlockPos> getGlassRoadBlocks() { return glassRoadBlocks; }

    // ===== BOBBY =====
    public boolean isWaitingForRevive() { return waitingForRevive; }
    public void setWaitingForRevive(boolean waiting) { this.waitingForRevive = waiting; }
    public BlockPos getDeathPos() { return deathPos; }
    public void setDeathPos(BlockPos pos) { this.deathPos = pos; }
    public int getApostleStrengthStacks() { return apostleStrengthStacks; }
    public void setApostleStrengthStacks(int stacks) { this.apostleStrengthStacks = Math.min(stacks, 5); }
    public void addApostleStrengthStack() { this.apostleStrengthStacks = Math.min(apostleStrengthStacks + 1, 5); }
    public boolean isRetourDead() { return retourDead; }
    public void setRetourDead(boolean dead) { this.retourDead = dead; }
    public boolean isBobbyCanRevive() { return bobbyCanRevive; }
    public void setBobbyCanRevive(boolean can) { this.bobbyCanRevive = can; }

    public int getSinResource() { return sinResource; }
    public void setSinResource(int sin) { this.sinResource = sin; }
    public void addSinResource(int amount) { this.sinResource += amount; }

    public long getLastBobbyGhostTick() { return lastBobbyGhostTick; }
    public void setLastBobbyGhostTick(long tick) { this.lastBobbyGhostTick = tick; }
    public long getBobbyGhostEndTick() { return bobbyGhostEndTick; }
    public void setBobbyGhostEndTick(long tick) { this.bobbyGhostEndTick = tick; }
    public net.minecraft.util.math.Vec3d getBobbyGhostStartPos() { return bobbyGhostStartPos; }
    public void setBobbyGhostStartPos(net.minecraft.util.math.Vec3d pos) { this.bobbyGhostStartPos = pos; }

    // ===== RETOUR =====
    public long getRetourRevealedUntilTick() { return retourRevealedUntilTick; }
    public void setRetourRevealedUntilTick(long tick) { this.retourRevealedUntilTick = tick; }
    public boolean isRetourLaserActive() { return retourLaserActive; }
    public void setRetourLaserActive(boolean active) { this.retourLaserActive = active; }

    // ===== ST =====
    public long getLastCialloTick() { return lastCialloTick; }
    public void setLastCialloTick(long tick) { this.lastCialloTick = tick; }
    public long getCialloRevealUntilTick() { return cialloRevealUntilTick; }
    public void setCialloRevealUntilTick(long tick) { this.cialloRevealUntilTick = tick; }
    public long getCialloSeeAllUntilTick() { return cialloSeeAllUntilTick; }
    public void setCialloSeeAllUntilTick(long tick) { this.cialloSeeAllUntilTick = tick; }
    public long getLastSonicTick() { return lastSonicTick; }
    public void setLastSonicTick(long tick) { this.lastSonicTick = tick; }
    public long getSonicPendingTick() { return sonicPendingTick; }
    public void setSonicPendingTick(long tick) { this.sonicPendingTick = tick; }
    public boolean isSonicReadyNotified() { return sonicReadyNotified; }
    public void setSonicReadyNotified(boolean v) { this.sonicReadyNotified = v; }
    public BlockPos getSonicLockPos() { return sonicLockPos; }
    public void setSonicLockPos(BlockPos pos) { this.sonicLockPos = pos; }
    public UUID getSonicAutoAimTarget() { return sonicAutoAimTarget; }
    public void setSonicAutoAimTarget(UUID uuid) { this.sonicAutoAimTarget = uuid; }
    public long getSonicAutoAimStartTick() { return sonicAutoAimStartTick; }
    public void setSonicAutoAimStartTick(long tick) { this.sonicAutoAimStartTick = tick; }
    public int getSonicSelectIndex() { return sonicSelectIndex; }
    public void setSonicSelectIndex(int idx) { this.sonicSelectIndex = idx; }

    // ===== JIEZU =====
    public List<BlockPos> getWebPositions() { return webPositions; }
    public long getLastWebGainTick() { return lastWebGainTick; }
    public void setLastWebGainTick(long tick) { this.lastWebGainTick = tick; }
    public long getLastCocoonGainTick() { return lastCocoonGainTick; }
    public void setLastCocoonGainTick(long tick) { this.lastCocoonGainTick = tick; }
    public long getJiezuNightEndTick() { return jiezuNightEndTick; }
    public void setJiezuNightEndTick(long tick) { this.jiezuNightEndTick = tick; }
    public int getJiezuWebTpIndex() { return jiezuWebTpIndex; }
    public void setJiezuWebTpIndex(int idx) { this.jiezuWebTpIndex = idx; }
    public long getStunUntilTick() { return stunUntilTick; }
    public void setStunUntilTick(long tick) { this.stunUntilTick = tick; }
    public boolean isStunned(long currentTick) { return currentTick < stunUntilTick; }

    // ===== DAMAI =====
    public BlockPos getTombstonePos() { return tombstonePos; }
    public void setTombstonePos(BlockPos pos) { this.tombstonePos = pos; }
    public boolean isSkeletonForm() { return skeletonForm; }
    public void setSkeletonForm(boolean form) { this.skeletonForm = form; }
    public long getSkeletonFormStartTick() { return skeletonFormStartTick; }
    public void setSkeletonFormStartTick(long tick) { this.skeletonFormStartTick = tick; }
    public long getSkeletonFormEndTick() { return skeletonFormEndTick; }
    public void setSkeletonFormEndTick(long tick) { this.skeletonFormEndTick = tick; }
    public void extendSkeletonForm(long ticks) { this.skeletonFormEndTick += ticks; }
    public int getSkeletonKills() { return skeletonKills; }
    public void addSkeletonKill() { this.skeletonKills++; }
    public void resetSkeletonKills() { this.skeletonKills = 0; }
    public boolean isDamaiWaitingForSkeleton() { return damaiWaitingForSkeleton; }
    public void setDamaiWaitingForSkeleton(boolean waiting) { this.damaiWaitingForSkeleton = waiting; }
    public long getDamaiDeathTick() { return damaiDeathTick; }
    public void setDamaiDeathTick(long tick) { this.damaiDeathTick = tick; }
    public long getTombstoneProtectedUntilTick() { return tombstoneProtectedUntilTick; }
    public void setTombstoneProtectedUntilTick(long tick) { this.tombstoneProtectedUntilTick = tick; }

    // ===== DASHA =====
    public boolean isInWater() { return inWater; }
    public void setInWater(boolean inWater) { this.inWater = inWater; }
    public long getLastOutOfWaterTime() { return lastOutOfWaterTime; }
    public void setLastOutOfWaterTime(long time) { this.lastOutOfWaterTime = time; }
    public int getDashaKillBonusStrength() { return dashaKillBonusStrength; }
    public void addDashaKillBonusStrength() { if (dashaKillBonusStrength < 5) dashaKillBonusStrength++; }
    public int getDashaKillBonusHealth() { return dashaKillBonusHealth; }
    public void addDashaKillBonusHealth() { if (dashaKillBonusHealth < 5) dashaKillBonusHealth++; }
    public void resetDashaBonus() { dashaKillBonusStrength = 0; dashaKillBonusHealth = 0; }
    public long getDashaVisibleUntilTick() { return dashaVisibleUntilTick; }
    public void setDashaVisibleUntilTick(long tick) { this.dashaVisibleUntilTick = tick; }

    // ===== NIHAO =====
    public UUID getHelloTargetUuid() { return helloTargetUuid; }
    public void setHelloTargetUuid(UUID uuid) { this.helloTargetUuid = uuid; }
    public int getHelloCode() { return helloCode; }
    public void setHelloCode(int code) { this.helloCode = code; }
    public long getHelloExpiryTick() { return helloExpiryTick; }
    public void setHelloExpiryTick(long tick) { this.helloExpiryTick = tick; }
    public int getHelloTimerSeconds() { return helloTimerSeconds; }
    public void setHelloTimerSeconds(int s) { this.helloTimerSeconds = Math.max(3, s); }
    public long getNihaoRevealUntilTick() { return nihaoRevealUntilTick; }
    public void setNihaoRevealUntilTick(long tick) { this.nihaoRevealUntilTick = tick; }
    public long getLastHelloBroadcastTick() { return lastHelloBroadcastTick; }
    public void setLastHelloBroadcastTick(long tick) { this.lastHelloBroadcastTick = tick; }
    public Map<UUID, Integer> getBroadcastHelloCodes() { return broadcastHelloCodes; }
    public long getBroadcastHelloExpiryTick() { return broadcastHelloExpiryTick; }
    public void setBroadcastHelloExpiryTick(long tick) { this.broadcastHelloExpiryTick = tick; }
    public Set<UUID> getBroadcastHelloResponded() { return broadcastHelloResponded; }
    public void clearBroadcastHello() {
        broadcastHelloCodes.clear();
        broadcastHelloExpiryTick = 0;
        broadcastHelloResponded.clear();
    }

    // ===== HELI =====
    public Set<UUID> getRevealedPlayers() { return revealedPlayers; }
    public Map<UUID, Long> getRevealedPlayerExpiry() { return revealedPlayerExpiry; }
    public Map<UUID, Long> getHeliRevealStartTick() { return heliRevealStartTick; }
    public int getHeliTpIndex() { return heliTpIndex; }
    public void setHeliTpIndex(int idx) { this.heliTpIndex = idx; }
    /** 检查目标是否在被揭露的前15秒内(双倍伤害窗口) */
    public boolean isInHeliDoubleDamageWindow(UUID targetUuid, long currentTick) {
        Long start = heliRevealStartTick.get(targetUuid);
        return start != null && (currentTick - start) < 300; // 15秒=300tick
    }

    // ===== JVJV =====
    public boolean isExplosionUsed() { return explosionUsed; }
    public void setExplosionUsed(boolean used) { this.explosionUsed = used; }
    public void resetExplosionForNewLife() { this.explosionUsed = false; this.lastExplosionKills = 0; }
    public int getLastExplosionKills() { return lastExplosionKills; }
    public void setLastExplosionKills(int kills) { this.lastExplosionKills = kills; }
    public int getJvjvTotalExplosionKills() { return jvjvTotalExplosionKills; }
    public void addJvjvTotalExplosionKills(int kills) { this.jvjvTotalExplosionKills += kills; }
    public boolean canRedLotus() { return canRedLotus; }
    public void setCanRedLotus(boolean can) { this.canRedLotus = can; }
    public boolean isRedLotusUsed() { return redLotusUsed; }
    public void setRedLotusUsed(boolean used) { this.redLotusUsed = used; }
    public long getRedLotusActiveTick() { return redLotusActiveTick; }
    public void setRedLotusActiveTick(long tick) { this.redLotusActiveTick = tick; }
    public boolean isRedLotusCharging(long currentTick) { return redLotusActiveTick > 0 && currentTick < redLotusActiveTick + 200; }
    public BlockPos getRedLotusPos() { return redLotusPos; }
    public void setRedLotusPos(BlockPos pos) { this.redLotusPos = pos; }
    public long getLastJvjvHealTick() { return lastJvjvHealTick; }
    public void setLastJvjvHealTick(long tick) { this.lastJvjvHealTick = tick; }
    public long getLastJvjvEatTick() { return lastJvjvEatTick; }
    public void setLastJvjvEatTick(long tick) { this.lastJvjvEatTick = tick; }
    public UUID getJvjvEatenPlayer() { return jvjvEatenPlayer; }
    public void setJvjvEatenPlayer(UUID uuid) { this.jvjvEatenPlayer = uuid; }
    public long getJvjvDigestEndTick() { return jvjvDigestEndTick; }
    public void setJvjvDigestEndTick(long tick) { this.jvjvDigestEndTick = tick; }
    public BlockPos getJvjvStomachRoomPos() { return jvjvStomachRoomPos; }
    public void setJvjvStomachRoomPos(BlockPos pos) { this.jvjvStomachRoomPos = pos; }
    public net.minecraft.util.math.Vec3d getJvjvEatenOriginalPos() { return jvjvEatenOriginalPos; }
    public void setJvjvEatenOriginalPos(net.minecraft.util.math.Vec3d pos) { this.jvjvEatenOriginalPos = pos; }

    // ===== ALLAND =====
    public int getPaintCharges() { return paintCharges; }
    public void setPaintCharges(int charges) { this.paintCharges = Math.min(charges, 4); }
    public void usePaintCharge() { if (paintCharges > 0) paintCharges--; }
    public long getLastPaintChargeGainTick() { return lastPaintChargeGainTick; }
    public void setLastPaintChargeGainTick(long tick) { this.lastPaintChargeGainTick = tick; }

    // ===== MACHA =====
    public int getMachaFishCount() { return machaFishCount; }
    public void incrementMachaFishCount() { this.machaFishCount++; }
    public UUID getMachaFishingTarget() { return machaFishingTarget; }
    public void setMachaFishingTarget(UUID target) { this.machaFishingTarget = target; }
    public long getMachaFishingStartTick() { return machaFishingStartTick; }
    public void setMachaFishingStartTick(long tick) { this.machaFishingStartTick = tick; }
    public java.util.Set<String> getMachaObtainedWeapons() { return machaObtainedWeapons; }
    public java.util.Set<String> getMachaObtainedArmor() { return machaObtainedArmor; }
    public int getMachaFishPlayerCharges() { return machaFishPlayerCharges; }
    public void addMachaFishPlayerCharge() { this.machaFishPlayerCharges++; }
    public void useMachaFishPlayerCharge() { if (machaFishPlayerCharges > 0) machaFishPlayerCharges--; }

    // ===== POPCORN =====
    public List<BlockPos> getBackupBodies() { return backupBodies; }
    public int getBackupBodyCharges() { return backupBodyCharges; }
    public void setBackupBodyCharges(int charges) { this.backupBodyCharges = charges; }
    public void addBackupBodyCharge() { this.backupBodyCharges++; }
    public long getLastLaserTick() { return lastLaserTick; }
    public void setLastLaserTick(long tick) { this.lastLaserTick = tick; }
    public BlockPos getLaserTarget() { return laserTarget; }
    public void setLaserTarget(BlockPos pos) { this.laserTarget = pos; }
    public long getLaserEndTick() { return laserEndTick; }
    public void setLaserEndTick(long tick) { this.laserEndTick = tick; }
    public boolean isPopcornReviving() { return popcornReviving; }
    public void setPopcornReviving(boolean v) { this.popcornReviving = v; }
    public BlockPos getPopcornRevivePos() { return popcornRevivePos; }
    public void setPopcornRevivePos(BlockPos pos) { this.popcornRevivePos = pos; }
    public long getPopcornBonusHealthExpiry() { return popcornBonusHealthExpiry; }
    public void setPopcornBonusHealthExpiry(long tick) { this.popcornBonusHealthExpiry = tick; }
    public boolean isLaserReadyNotified() { return laserReadyNotified; }
    public void setLaserReadyNotified(boolean v) { this.laserReadyNotified = v; }
    public BlockPos getLaserPendingTarget() { return laserPendingTarget; }
    public void setLaserPendingTarget(BlockPos pos) { this.laserPendingTarget = pos; }
    public long getLaserPendingStartTick() { return laserPendingStartTick; }
    public void setLaserPendingStartTick(long tick) { this.laserPendingStartTick = tick; }
    public UUID getPopcornCameraTarget() { return popcornCameraTarget; }
    public void setPopcornCameraTarget(UUID uuid) { this.popcornCameraTarget = uuid; }
    public int getPopcornBodyTpIndex() { return popcornBodyTpIndex; }
    public void setPopcornBodyTpIndex(int idx) { this.popcornBodyTpIndex = idx; }
    public long getPopcornBodySelectTick() { return popcornBodySelectTick; }
    public void setPopcornBodySelectTick(long tick) { this.popcornBodySelectTick = tick; }
    public boolean isPopcornSpectating() { return popcornSpectating; }
    public void setPopcornSpectating(boolean v) { this.popcornSpectating = v; }
    public long getPopcornSpectateSelectTick() { return popcornSpectateSelectTick; }
    public void setPopcornSpectateSelectTick(long tick) { this.popcornSpectateSelectTick = tick; }
    public int getPopcornSpectateSelectIndex() { return popcornSpectateSelectIndex; }
    public void setPopcornSpectateSelectIndex(int idx) { this.popcornSpectateSelectIndex = idx; }
    public float getPopcornBodyLastHealth() { return popcornBodyLastHealth; }
    public void setPopcornBodyLastHealth(float h) { this.popcornBodyLastHealth = h; }
    public double getPopcornSavedX() { return popcornSavedX; }
    public double getPopcornSavedY() { return popcornSavedY; }
    public double getPopcornSavedZ() { return popcornSavedZ; }
    public float getPopcornSavedYaw() { return popcornSavedYaw; }
    public float getPopcornSavedPitch() { return popcornSavedPitch; }
    public void savePopcornPosition(double x, double y, double z, float yaw, float pitch) {
        this.popcornSavedX = x; this.popcornSavedY = y; this.popcornSavedZ = z;
        this.popcornSavedYaw = yaw; this.popcornSavedPitch = pitch;
    }

    // ===== SANCHEZ =====
    public double getAlphaWolfHealth() { return alphaWolfHealth; }
    public void setAlphaWolfHealth(double hp) { this.alphaWolfHealth = Math.max(0, hp); }
    public boolean isAlphaWolfDead() { return alphaWolfDead; }
    public void setAlphaWolfDead(boolean dead) { this.alphaWolfDead = dead; }
    public long getAlphaWolfDeathTick() { return alphaWolfDeathTick; }
    public void setAlphaWolfDeathTick(long tick) { this.alphaWolfDeathTick = tick; }
    public int getWolfKillCharges() { return wolfKillCharges; }
    public void addWolfKillCharge() { this.wolfKillCharges++; }
    public void useWolfKillCharge() { if (wolfKillCharges > 0) wolfKillCharges--; }
    public UUID getAlphaWolfEntityUuid() { return alphaWolfEntityUuid; }
    public void setAlphaWolfEntityUuid(UUID uuid) { this.alphaWolfEntityUuid = uuid; }
    public List<UUID> getPackWolfUuids() { return packWolfUuids; }
    public long getSanchezHuntEndTick() { return sanchezHuntEndTick; }
    public void setSanchezHuntEndTick(long tick) { this.sanchezHuntEndTick = tick; }
    public UUID getSanchezAttackTarget() { return sanchezAttackTarget; }
    public void setSanchezAttackTarget(UUID uuid) { this.sanchezAttackTarget = uuid; }
    public void resetSanchezWolves() {
        this.alphaWolfEntityUuid = null;
        this.packWolfUuids.clear();
        this.sanchezAttackTarget = null;
    }

    // ===== SHUBING =====
    public int getPaintingCount() { return paintingCount; }
    public void setPaintingCount(int count) { this.paintingCount = count; }
    public void usePainting() { if (paintingCount > 0) paintingCount--; }
    public long getLastPaintingGainTick() { return lastPaintingGainTick; }
    public void setLastPaintingGainTick(long tick) { this.lastPaintingGainTick = tick; }
    public List<ShubingPainting> getPlacedPaintings() { return placedPaintings; }
    public List<ShubingCorridor> getCorridors() { return corridors; }
    public Map<UUID, CorridorEntry> getCorridorPlayers() { return corridorPlayers; }
    public boolean isPaintingModeHorizontal() { return paintingModeHorizontal; }
    public void togglePaintingMode() { this.paintingModeHorizontal = !this.paintingModeHorizontal; }
    public long getLastShubingSummonTick() { return lastShubingSummonTick; }
    public void setLastShubingSummonTick(long tick) { this.lastShubingSummonTick = tick; }
    // 获取场上仍存活的画数量
    public int getActivePaintingCount() {
        int count = 0;
        for (ShubingPainting p : placedPaintings) {
            if (p.pos != null && p.corridorIndex != -2) count++;
        }
        return count;
    }

    // 找到第一幅未配对的画的索引, 没有返回-1
    public int findUnpairedPaintingIndex() {
        for (int i = 0; i < placedPaintings.size(); i++) {
            ShubingPainting p = placedPaintings.get(i);
            if (p.pos != null && p.corridorIndex == -1) return i;
        }
        return -1;
    }

    // 根据实体UUID找到画的索引
    public int findPaintingByEntityUuid(UUID entityUuid) {
        for (int i = 0; i < placedPaintings.size(); i++) {
            ShubingPainting p = placedPaintings.get(i);
            if (p.pos != null && p.entityUuid.equals(entityUuid)) return i;
        }
        return -1;
    }

    // 根据位置找到画的索引
    public int findPaintingByPos(BlockPos pos) {
        if (pos == null) return -1;
        for (int i = 0; i < placedPaintings.size(); i++) {
            ShubingPainting p = placedPaintings.get(i);
            if (p.pos != null && p.pos.equals(pos)) return i;
        }
        return -1;
    }

    // 找到画所属的通道,以及画在通道中是哪端(0或1)
    public ShubingCorridor findCorridorForPainting(int paintingIndex) {
        ShubingPainting p = placedPaintings.get(paintingIndex);
        if (p.corridorIndex >= 0 && p.corridorIndex < corridors.size()) {
            return corridors.get(p.corridorIndex);
        }
        return null;
    }

    // ===== JANE =====
    public int getBounty() { return bounty; }
    public void setBounty(int bounty) { this.bounty = Math.max(0, bounty); }
    public void addBounty(int amount) { this.bounty += amount; }
    public boolean isDarkVisionActive() { return darkVisionActive; }
    public void setDarkVisionActive(boolean active) { this.darkVisionActive = active; }
    public int getCrossbowPower() { return crossbowPower; }
    public void setCrossbowPower(int level) { this.crossbowPower = level; }
    public int getCrossbowQuickCharge() { return crossbowQuickCharge; }
    public void setCrossbowQuickCharge(int level) { this.crossbowQuickCharge = level; }
    public int getCrossbowMultishot() { return crossbowMultishot; }
    public void setCrossbowMultishot(int level) { this.crossbowMultishot = level; }
    public UUID getJaneTopTargetUuid() { return janeTopTargetUuid; }
    public void setJaneTopTargetUuid(UUID uuid) { this.janeTopTargetUuid = uuid; }
    public void resetJaneOnDeath() { this.bounty = 0; }

    // ===== 死亡重置方法 =====
    public void resetNihaoOnDeath() {
        this.helloTimerSeconds = Math.min(helloTimerSeconds + 1, 30); // 死亡+1秒, 上隙0s
        this.helloTargetUuid = null;
        this.helloCode = 0;
        this.helloExpiryTick = 0;
    }

    public void resetAllandOnDeath() {
        // 死亡不重置颜料弹次数
    }

    public void resetDashaOnDeath() {
        this.dashaKillBonusStrength = 0;
        this.dashaKillBonusHealth = 0;
    }

    public void resetDamaiOnDeath() {
        this.tombstonePos = null;
        this.skeletonForm = false;
        this.skeletonFormEndTick = 0;
        this.skeletonKills = 0;
        this.damaiWaitingForSkeleton = false;
        this.damaiDeathTick = 0;
        this.tombstoneProtectedUntilTick = 0;
    }

    // ===== YOUZHA =====
    public long getLastYouzhaUltimateTick() { return lastYouzhaUltimateTick; }
    public void setLastYouzhaUltimateTick(long tick) { this.lastYouzhaUltimateTick = tick; }
    public long getLastYouzhaEatTick() { return lastYouzhaEatTick; }
    public void setLastYouzhaEatTick(long tick) { this.lastYouzhaEatTick = tick; }
    public List<UUID> getYouzhaTrappedPlayers() { return youzhaTrappedPlayers; }
    public Map<UUID, net.minecraft.util.math.Vec3d> getYouzhaTrappedOriginalPos() { return youzhaTrappedOriginalPos; }
    public long getYouzhaTrappedReleaseTick() { return youzhaTrappedReleaseTick; }
    public void setYouzhaTrappedReleaseTick(long tick) { this.youzhaTrappedReleaseTick = tick; }
    public BlockPos getYouzhaTrapRoomPos() { return youzhaTrapRoomPos; }
    public void setYouzhaTrapRoomPos(BlockPos pos) { this.youzhaTrapRoomPos = pos; }

    // ===== LAYI =====
    public boolean isLayiFlying() { return layiFlying; }
    public void setLayiFlying(boolean v) { this.layiFlying = v; }
    public boolean isLayiFlightActivatedThisLife() { return layiFlightActivatedThisLife; }
    public void setLayiFlightActivatedThisLife(boolean v) { this.layiFlightActivatedThisLife = v; }
    public long getLastLayiArrowBarrageTick() { return lastLayiArrowBarrageTick; }
    public void setLastLayiArrowBarrageTick(long tick) { this.lastLayiArrowBarrageTick = tick; }
    public int getLayiArrowBurstRemaining() { return layiArrowBurstRemaining; }
    public void setLayiArrowBurstRemaining(int n) { this.layiArrowBurstRemaining = n; }
    public long getLayiArrowBurstNextTick() { return layiArrowBurstNextTick; }
    public void setLayiArrowBurstNextTick(long t) { this.layiArrowBurstNextTick = t; }
    public List<BlockPos> getLayiSpecialChestPositions() { return layiSpecialChestPositions; }
    public List<UUID> getLayiMissileEntities() { return layiMissileEntities; }

    // ===== MAYPOOR(天锤) =====
    public long getMaypoorFlightEndTick() { return maypoorFlightEndTick; }
    public void setMaypoorFlightEndTick(long tick) { this.maypoorFlightEndTick = tick; }
    public boolean isMaypoorFlying(long currentTick) { return currentTick < maypoorFlightEndTick; }
    public boolean isMaypoorSlamArmed() { return maypoorSlamArmed; }
    public void setMaypoorSlamArmed(boolean v) { this.maypoorSlamArmed = v; }
    public double getMaypoorSlamStartY() { return maypoorSlamStartY; }
    public void setMaypoorSlamStartY(double y) { this.maypoorSlamStartY = y; }
}