package baby.sv.yepvpfabirc.game;

public enum Role {
    YUSUI("yusui", "鱼碎-男娘后宫", "被动速度2。每1分钟获特殊雪球(仅场上无男娘时)，命中使对方成男娘。被男娘击杀也变男娘。基于男娘数获等值力量。男娘被淘汰永久+2心。3命。"),
    XLL("xll", "Xll-立体艺术", "Z:边长10不可破坏玻璃正方体(5minCD,8s,内部失明+每秒10%maxHP真伤)。X:向前3×50永久玻璃长道(2minCD,只能东西南北,清空上方2格)。被动:自己玻璃上速度5,他人在你玻璃上失明。8心。"),
    BOBBY("bobby", "罪人-神曲", "无限命。击杀+1力量(共享最多5)。与但丁>100格每秒+1罪孽。Z:消耗300罪孽→30秒回复4+抗性2+速度3(双方)+但丁+1命。死后灵体等复活。但丁死→失去力量+永久死亡。地图互见。"),
    RETOUR("retour", "但丁-神曲", "+10心。击杀+1力量(共享最多5)。Z:受10心伤害复活30格内罪人(30sCD)。X:消耗双方1力量无距离复活。复活暴露30秒。2命。地图互见。"),
    ST("st", "ST-柚子厨", "/Ciallo暴露自己+看到所有人30秒(2minCD)。V:8秒蓄力后结算自瞄声波,0-100格伤害线性提升(0->20心),100-200格线性衰减(20->10心),远距离保底10心(2minCD,冷却广播)。"),
    JIEZU("jiezu", "Spiderhachis-节足动物", "夜晚隐身(地图不可探测)+夜视+力量3+速度3+爬墙+免摔。蛛网中极快。每1min+1蛛网(最多3个,50格探测)。每2min传送蛛网。Z:5minCD强制夜晚+全体失明30s。蛛网位置全图可见。8心。"),
    DAMAI("damai", "大麦-不死者", "死亡留墓碑，3秒后变骷髅(力5抗5速2跳2)5分钟。骷髅杀人+命。3命5心。"),
    DASHA("dasha", "大鲨-鲨鱼人", "铁桶+无限激流三叉戟。水/雨中隐身(地图不可探测)+水下呼吸+速掘+夜视+力量3。地图显示最近玩家。击杀+1力量+1心(上限5)。Z:5minCD下雨1min。离水窒息。攻击/冲刺显形2s。死亡增益重置。"),
    NIHAO("nihao", "Nihao-问好", "/hello问好玩家,15秒内不/helloback+6位数=秒杀。成功→暴露位置30s。1minCD。击杀减1秒倒计时,死亡加1秒。"),
    HELI("heli", "Heli-揭开帷幕", "被动:攻击揭露对方1min(地图+边框),前15s受伤翻倍,揭露中不可重复揭露。Z:1minCD传送到被揭露玩家身旁。X:3minCD全视之眼揭露所有玩家1min。完全隐身免疫位置暴露。8心。"),
    JVJV("jvjv", "JVJV-大爆炸", "每命1次大爆炸3s后8格秒杀+自己死。炸2+人额外命。累计炸死5人解锁红莲华(10s警告+100格秒杀+自己存活+10s无法操作)。8心。"),
    ALLAND("alland", "ACD-喷涂战士", "三色颜料涂方块。2000红+1力量/2000蓝+1速度/2000绿+2心。只能在涂色区移动。"),
    MACHA("macha", "抹茶巴菲-钓鱼", "Z:30sCD钓鱼(5%合金武器/5%合金装备/5%自己随机传送/10%钓人到面前/35%随机道具/38%鱼/1%+1命/1%暴毙)。每10次必钓人(3s警告)。道具:药水/鞘翅/烟花/末影珍珠/TNT/致密5重锤/风弹/盾牌/力量5弓弩/箭/岩浆/蛛网。"),
    POPCORN("popcorn", "爆米花-智械危机", "Z:放置备用躯体(开局2次,击杀+1)。X:选择并传送到备用躯体(将其消耗,5格晕眩5s,复活/传送后满血+5临时心)。C:灵魂出窍附身他人。V:2minCD轨道镭射(地图点击,3s预警,10格半径,6心/s真伤,持续10s,含自伤)。被动:死亡时若有躯体则消耗随机躯体复活。地图显示所有玩家+躯体。完全隐身免疫探测。技能广播。1命。"),
    SANCHEZ("sanchez", "Sanchez-诸神黄昏", "Z:治愈头狼(30sCD,唯一治愈手段)。X:召唤1心狼(-1心,-1次数)。V:狩猎模式(2minCD,全军无敌10s+速度2)。击杀/头狼击杀生物+1次数。攻击时狼群锁定目标,空闲自动攻击。头狼死→全狼死(3min复活)。6心。"),
    SHUBING("shubing", "薯饼-画中世界", "初始2画,每1min+1(上限2)。放2幅画连接50格画中直道。碰画进入通道。停留10秒死亡。画被拆出口关闭。画无掉落物。X:切换垂直/水平模式。V:2minCD画中世界传送随机玩家到身旁。画位置全图可见。"),
    JANE("jane", "Jane-赏金猎人", "被动速度2+无限弩。非玩家伤害翻倍。击杀友好+1/中立敌对+2/玩家+100赏金。头号目标(最多击杀者)额外+100。Z:切换暗黑视域(失明+看到所有人边框)。X:商店(75赏金升级弩:力量/快速装填/多重射击)。8心。死亡清空赏金。"),
    YOUZHA("youzha", "油炸意面-噬元兽", "Z:噬元兽(2minCD)在指针处召唤猫,1秒后吸入5格内所有人到异次元小房间15秒,出来掉半管血+扣1心上限。X:大吃特吃(消耗5饱食度,30sCD)随机获得加速/跳跃/生命恢复等buff。"),
    LAYI("layi", "蜡翼-伊卡洛斯", "无限鞘翅+飞行中免击落。Z:每命1次激活持续飞行+刷2特殊烟花箱(他人可拿,用后变追踪导弹10s后爆炸秒杀)。X:30sCD飞行中连射20箭(5%maxHP真伤)。位置持续暴露。离开飞行或被导弹炸到=秒杀。"),
    MAYPOOR("maypoor", "Maypoor-天锤", "被动免疫摔落伤害。Z:30sCD获得创造飞行10秒。X:10sCD,下落中激活,落地时制造爆炸(基础0格半径/0伤害,激活后每下落1格,伤害+1,半径+0.5)。10心。");

    private final String playerId;
    private final String displayName;
    private final String description;

    Role(String playerId, String displayName, String description) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.description = description;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static Role fromId(String id) {
        for (Role role : values()) {
            if (role.playerId.equalsIgnoreCase(id)) {
                return role;
            }
        }
        return null;
    }
}
