package baby.sv.yepvpfabirc.client.network;

import baby.sv.yepvpfabirc.client.hud.HiddenPlayersCache;
import baby.sv.yepvpfabirc.client.hud.HudDataCache;
import baby.sv.yepvpfabirc.client.hud.MapDataCache;
import baby.sv.yepvpfabirc.client.screen.GameStartScreen;
import baby.sv.yepvpfabirc.client.screen.JaneShopScreen;
import baby.sv.yepvpfabirc.client.screen.RevealScreen;
import baby.sv.yepvpfabirc.client.screen.WorldMapScreen;
import baby.sv.yepvpfabirc.network.NetworkHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetworkHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.HudSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                HudDataCache.updateFromJson(payload.jsonData());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.GameStartScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new GameStartScreen(payload.jsonData()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.RevealScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new RevealScreen(payload.jsonData()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.MapDataPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MapDataCache.updateFromBytes(payload.data());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.HiddenPlayersPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                HiddenPlayersCache.updateFromBytes(payload.data());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.VersionCheckPayload.ID, (payload, context) -> {
            // 收到服务端版本检查, 立即回复客户端版本
            ClientPlayNetworking.send(new NetworkHandler.VersionResponsePayload(NetworkHandler.MOD_PROTOCOL_VERSION));
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.JaneShopPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                var mc = context.client();
                if (mc.currentScreen instanceof JaneShopScreen shop) {
                    shop.updateData(payload.bounty(), payload.power(), payload.quickCharge(), payload.multishot());
                } else {
                    mc.setScreen(new JaneShopScreen(payload.bounty(), payload.power(), payload.quickCharge(), payload.multishot()));
                }
            });
        });
    }

    public static void sendSkillUse(int skillSlot) {
        ClientPlayNetworking.send(new NetworkHandler.SkillUsePayload(skillSlot));
    }

    public static void sendGreetingResponse() {
        ClientPlayNetworking.send(new NetworkHandler.GreetingResponsePayload(true));
    }

    public static void sendSpectatorSwitch(int direction) {
        ClientPlayNetworking.send(new NetworkHandler.SpectatorSwitchPayload(direction));
    }

    public static void sendMapRequest() {
        ClientPlayNetworking.send(new NetworkHandler.MapRequestPayload(true));
    }

    public static void sendMapTeleport(int x, int z) {
        ClientPlayNetworking.send(new NetworkHandler.MapTeleportPayload(x, z));
    }

    public static void sendJaneShopBuy(int upgradeType) {
        ClientPlayNetworking.send(new NetworkHandler.JaneShopBuyPayload(upgradeType));
    }
}
