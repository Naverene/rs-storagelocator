package com.claudecode.rsstoragelocator;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/rsstoragelocator-client.toml. The highlight key itself is a normal key binding (Options > Controls). */
public final class Config {

    private Config() {
    }

    static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue HIGHLIGHT_SECONDS;

    static {
        var b = new ForgeConfigSpec.Builder();
        HIGHLIGHT_SECONDS = b
                .comment("Seconds the blocks holding an item stay highlighted after you press the highlight key "
                        + "(default Insert) on it in a Refined Storage grid.")
                .defineInRange("highlightSeconds", 30, 1, 3600);
        SPEC = b.build();
    }
}
