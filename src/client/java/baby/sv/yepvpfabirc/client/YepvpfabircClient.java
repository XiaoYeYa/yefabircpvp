package baby.sv.yepvpfabirc.client;

import baby.sv.yepvpfabirc.client.hud.GameHudRenderer;
import baby.sv.yepvpfabirc.client.hud.HudDataCache;
import baby.sv.yepvpfabirc.client.hud.MapDataCache;
import baby.sv.yepvpfabirc.client.keybind.ModKeyBindings;
import baby.sv.yepvpfabirc.client.network.ClientNetworkHandler;
import baby.sv.yepvpfabirc.client.screen.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class YepvpfabircClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModKeyBindings.register();
        ClientNetworkHandler.register();
        GameHudRenderer.register();

        // 断开服务器连接时重置HUD状态, 防止切换到无mod服务器时残留UI
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HudDataCache.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!HudDataCache.isGameActive()) return;
            if (client.player == null) return;

            // M键: 从缓存打开世界地图
            if (ModKeyBindings.MAP_KEY.wasPressed()) {
                if (MapDataCache.hasData() && client.currentScreen == null) {
                    client.setScreen(new WorldMapScreen());
                }
            }

            // 键位路由改用selfAlive判断:
            //   POPCORN灵魂出窍时客户端处于SPECTATOR但selfAlive=true, 按技能键仍应走sendSkillUse
            //   普通观战玩家(已死亡)selfAlive=false, 走旁观键位
            if (!HudDataCache.isSelfAlive()) {
                // 观战模式复用技能键：X=下一个 Z=上一个 V=自由视角
                if (ModKeyBindings.SKILL_X.wasPressed()) {
                    ClientNetworkHandler.sendSpectatorSwitch(1);
                }
                if (ModKeyBindings.SKILL_Z.wasPressed()) {
                    ClientNetworkHandler.sendSpectatorSwitch(-1);
                }
                if (ModKeyBindings.SKILL_V.wasPressed()) {
                    ClientNetworkHandler.sendSpectatorSwitch(0);
                }
                // POPCORN旁观模式: C键仍发送技能(用于返回自身)
                if (ModKeyBindings.SKILL_C.wasPressed()) {
                    ClientNetworkHandler.sendSkillUse(3);
                }
            } else {
                // 存活模式：Z/X/C/V技能
                if (ModKeyBindings.SKILL_Z.wasPressed()) {
                    ClientNetworkHandler.sendSkillUse(0);
                }
                if (ModKeyBindings.SKILL_X.wasPressed()) {
                    ClientNetworkHandler.sendSkillUse(1);
                }
                if (ModKeyBindings.SKILL_V.wasPressed()) {
                    ClientNetworkHandler.sendSkillUse(2);
                }
                if (ModKeyBindings.SKILL_C.wasPressed()) {
                    ClientNetworkHandler.sendSkillUse(3);
                }
            }
        });
    }
}
