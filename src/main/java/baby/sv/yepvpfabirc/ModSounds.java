package baby.sv.yepvpfabirc;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final Identifier EXPLOSION_ID = Identifier.of("yepvpfabirc", "explosion");
    public static final SoundEvent EXPLOSION = SoundEvent.of(EXPLOSION_ID);

    public static final Identifier CIALLO_ID = Identifier.of("yepvpfabirc", "ciallo");
    public static final SoundEvent CIALLO = SoundEvent.of(CIALLO_ID);

    public static final Identifier GURENGE_ID = Identifier.of("yepvpfabirc", "gurenge");
    public static final SoundEvent GURENGE = SoundEvent.of(GURENGE_ID);

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, EXPLOSION_ID, EXPLOSION);
        Registry.register(Registries.SOUND_EVENT, CIALLO_ID, CIALLO);
        Registry.register(Registries.SOUND_EVENT, GURENGE_ID, GURENGE);
    }
}
