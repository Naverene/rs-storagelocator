package com.claudecode.rsstoragelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hover an item or fluid in a Refined Storage grid and its tooltip lists where the network keeps it: which disk in
 * which Disk Drive slot, which block each External Storage faces, and how much each. The client asks the server once
 * per entry (cached a few seconds); the server answers from the network of the grid the player has open. Pressing the
 * highlight key (Insert) on an entry closes the grid and outlines those blocks in the world.
 * <p>
 * Optional on either side: without it on the server the tooltip just doesn't get the extra lines.
 */
public class StorageLocator implements ModInitializer {

    public static final String MOD_ID = "rsstoragelocator";
    public static final Logger LOG = LogUtils.getLogger();

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(LocateRequest.TYPE, LocateRequest.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(LocateReply.TYPE, LocateReply.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LocateRequest.TYPE,
                (req, context) -> answer(context.player(), req));
    }

    private static void answer(ServerPlayer p, LocateRequest req) {
        if (req.what() == null || !allow(p)) return;
        try {
            var found = Locator.locate(p, req.what());
            ServerPlayNetworking.send(p, new LocateReply(req.what(), found.lines(), req.highlight(), found.positions()));
            if (req.highlight() && !found.positions().isEmpty()) p.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("rsstoragelocator: lookup failed", e);
        }
    }

    /** At most 20 lookups a second per player. */
    static boolean allow(ServerPlayer p) {
        long now = System.currentTimeMillis();
        synchronized (RATE) {
            long[] r = RATE.computeIfAbsent(p.getUUID(), k -> new long[] { now, 0 });
            if (now - r[0] > 1000) {
                r[0] = now;
                r[1] = 0;
            }
            return ++r[1] <= 20;
        }
    }
}
