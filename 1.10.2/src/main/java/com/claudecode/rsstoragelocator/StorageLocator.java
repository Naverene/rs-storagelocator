package com.claudecode.rsstoragelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.claudecode.rsstoragelocator.client.ClientState;

/**
 * Hover an item or fluid in a Refined Storage grid and its tooltip lists where the network keeps it: which disk in
 * which Disk Drive slot, which block each External Storage faces, and how much each. The client asks the server once
 * per entry (cached a few seconds); the server answers from the network of the grid the player has open. Pressing the
 * highlight key (Insert) on an entry closes the grid and outlines those blocks in the world.
 * <p>
 * Optional on either side: without it on the server the tooltip just doesn't get the extra lines.
 */
@Mod(modid = StorageLocator.MOD_ID, name = "RS Storage Locator", useMetadata = true,
        acceptedMinecraftVersions = "[1.10.2]", dependencies = "required-after:refinedstorage@[1.2,1.3)",
        acceptableRemoteVersions = "*")
public class StorageLocator {

    public static final String MOD_ID = "rsstoragelocator";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        NET.registerMessage(LocateRequest.Handler.class, LocateRequest.class, 0, Side.SERVER);
        NET.registerMessage(LocateReply.Handler.class, LocateReply.class, 1, Side.CLIENT);
        NET.registerMessage(Hello.Handler.class, Hello.class, 2, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(this);
        Config.load(e.getSuggestedConfigurationFile());
        if (e.getSide().isClient()) ClientState.init();
    }

    /** Tells a joining player's client that this server answers lookups. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) NET.sendTo(new Hello(), (EntityPlayerMP) e.player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        synchronized (RATE) {
            RATE.remove(e.player.getUniqueID());
        }
    }

    /** At most 20 lookups a second per player. */
    static boolean allow(EntityPlayerMP p) {
        long now = System.currentTimeMillis();
        synchronized (RATE) {
            long[] r = RATE.computeIfAbsent(p.getUniqueID(), k -> new long[] { now, 0 });
            if (now - r[0] > 1000) {
                r[0] = now;
                r[1] = 0;
            }
            return ++r[1] <= 20;
        }
    }
}
