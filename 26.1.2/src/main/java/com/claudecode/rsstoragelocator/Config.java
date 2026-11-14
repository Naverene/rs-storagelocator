package com.claudecode.rsstoragelocator;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/rsstoragelocator-client.toml. The highlight key itself is a normal key binding (Options > Controls). */
public final class Config {

    private Config() {
    }

    static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue HIGHLIGHT_SECONDS;

    static {
        var b = new ModConfigSpec.Builder();
        HIGHLIGHT_SECONDS = b
                .comment("Seconds the blocks holding an item stay highlighted after you press the highlight key "
                        + "(default Insert) on it in a Refined Storage grid.")
                .defineInRange("highlightSeconds", 30, 1, 3600);
        SPEC = b.build();
    }
}
