package com.claudecode.rsstoragelocator;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;


/**
 * Client to server: where is this item or fluid kept in the network of my open grid? With highlight set, the
 * server also closes the grid and the client outlines the blocks.
 */
public record LocateRequest(What what, boolean highlight) {

    void write(FriendlyByteBuf buf) {
        what.write(buf);
        buf.writeBoolean(highlight);
    }

    static LocateRequest read(FriendlyByteBuf buf) {
        What what = What.read(buf);
        return new LocateRequest(what, buf.readBoolean());
    }

    static void handle(LocateRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || msg.what == null || !StorageLocator.allow(p)) return;
        try {
            var found = Locator.locate(p, msg.what);
            StorageLocator.NET.send(PacketDistributor.PLAYER.with(() -> p),
                    new LocateReply(msg.what, found.lines(), msg.highlight, found.positions()));
            if (msg.highlight && !found.positions().isEmpty()) p.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("rsstoragelocator: lookup failed", e);
        }
    }
}
