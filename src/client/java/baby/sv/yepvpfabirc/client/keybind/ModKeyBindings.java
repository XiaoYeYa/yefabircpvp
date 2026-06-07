package baby.sv.yepvpfabirc.client.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static KeyBinding SKILL_Z;
    public static KeyBinding SKILL_X;
    public static KeyBinding SKILL_C;
    public static KeyBinding SKILL_V;
    public static KeyBinding MAP_KEY;

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(net.minecraft.util.Identifier.of("yepvpfabirc", "skills"));

    public static void register() {
        SKILL_Z = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yepvpfabirc.skill_z",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                CATEGORY
        ));

        SKILL_X = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yepvpfabirc.skill_x",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                CATEGORY
        ));

        SKILL_C = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yepvpfabirc.skill_c",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY
        ));

        SKILL_V = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yepvpfabirc.skill_v",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));

        MAP_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yepvpfabirc.map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));

    }
}
