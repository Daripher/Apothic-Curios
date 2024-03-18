package daripher.apothiccurios;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHIFT_SOCKET_TOOLTIP;

    static {
        SHIFT_SOCKET_TOOLTIP = BUILDER.comment("Whether the socket tooltip should be moved up by 1 (in case mods are present which add the mod name at the bottom)").define("shift_socket_tooltip", false);
        SPEC = BUILDER.build();
    }
}
