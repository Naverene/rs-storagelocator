package com.claudecode.rsstoragelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

import com.claudecode.rsstoragelocator.client.ClientState;

/**
 * Hover an item or fluid in a Refined Storage grid and its tooltip lists where the network keeps it: which disk in
 * which Disk Drive slot, which block each External Storage faces, and how much each. The client asks the server once
 * per entry (cached a few seconds); the server answers from the network of the grid the player has open. Pressing the
 * highlight key (Insert) on an entry closes the grid and outlines those blocks in the world.
 * <p>
 * Optional on either side: without it on the server the tooltip just doesn't get the extra lines.
 */
@Mod(StorageLocator.MOD_ID)
public class StorageLocator {

    public static final String MOD_ID = "rsstoragelocator";
    public static final Logger LOG = LogUtils.getLogger();

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    public StorageLocator(IEventBus modBus) {
        modBus.addListener(StorageLocator::registerPayloads);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientState.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlerEvent e) {
        // Optional, so players and servers without the mod can still join each other.
        IPayloadRegistrar r = e.registrar(MOD_ID).versioned("1").optional();
        r.play(LocateRequest.ID, LocateRequest::read, h -> h.server(LocateRequest::handle));
        r.play(LocateReply.ID, LocateReply::read, h -> h.client(LocateReply::handle));
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
