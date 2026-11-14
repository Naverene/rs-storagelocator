package com.claudecode.rsstoragelocator;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** config/rsstoragelocator.cfg. The highlight key itself is a normal key binding (Options > Controls). */
public final class Config {

    private Config() {
    }

    /** How long the in-world highlight of where an item is stored stays up. */
    public static int highlightSeconds = 30;

    static void load(File file) {
        Configuration cfg = new Configuration(file);
        highlightSeconds = cfg.getInt("highlightSeconds", Configuration.CATEGORY_CLIENT, 30, 1, 3600,
                "Seconds the blocks holding an item stay highlighted after you press the highlight key "
                        + "(default Insert) on it in a Refined Storage grid.");
        if (cfg.hasChanged()) cfg.save();
    }
}
