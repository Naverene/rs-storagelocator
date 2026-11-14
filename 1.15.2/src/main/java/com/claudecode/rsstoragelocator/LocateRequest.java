package com.claudecode.rsstoragelocator;

import java.util.function.Supplier;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;


/**
 * Client to server: where is this item or fluid kept in the network of my open grid? With highlight set, the
 * server also closes the grid and the client outlines the blocks.
 */
public final class LocateRequest {

    final What what;
    final boolean highlight;

    public LocateRequest(What what, boolean highlight) {
        this.what = what;
        this.highlight = highlight;
    }

    static void write(LocateRequest msg, PacketBuffer buf) {
        msg.what.write(buf);
        buf.writeBoolean(msg.highlight);
    }

    static LocateRequest read(PacketBuffer buf) {
        What what = What.read(buf);
        return new LocateRequest(what, buf.readBoolean());
    }

    static void handle(LocateRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayerEntity p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> answer(msg, p));
        ctx.get().setPacketHandled(true);
    }

    private static void answer(LocateRequest msg, ServerPlayerEntity p) {
        if (p == null || msg.what == null || !StorageLocator.allow(p)) return;
        try {
            Locator.Result found = Locator.locate(p, msg.what);
            StorageLocator.NET.send(PacketDistributor.PLAYER.with(() -> p),
                    new LocateReply(msg.what, found.lines, msg.highlight, found.positions));
            if (msg.highlight && !found.positions.isEmpty()) p.closeScreen();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("rsstoragelocator: lookup failed", e);
        }
    }
}
