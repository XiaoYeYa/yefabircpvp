package baby.sv.yepvpfabirc;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Identifier SHOTGUN_ID = Identifier.of("yepvpfabirc", "shotgun");
    public static final RegistryKey<Item> SHOTGUN_KEY = RegistryKey.of(RegistryKeys.ITEM, SHOTGUN_ID);

    // 赏金猎人的散弹枪(jane 专用武器载体, 开火逻辑在服务端 GameManager 中处理)
    public static Item SHOTGUN;

    public static void register() {
        SHOTGUN = Registry.register(Registries.ITEM, SHOTGUN_KEY,
                new Item(new Item.Settings().registryKey(SHOTGUN_KEY).maxCount(1)));
    }
}
