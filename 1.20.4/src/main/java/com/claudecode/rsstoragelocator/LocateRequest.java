package com.claudecode.rsstoragelocator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;


/**
 * Client to server: where is this item or fluid kept in the network of my open grid? With highlight set, the
 * server also closes the grid and the client outlines the blocks.
 */
public record LocateRequest(What what, boolean highlight) implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation(StorageLocator.MOD_ID, "locate");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        what.write(buf);
        buf.writeBoolean(highlight);
    }

    static LocateRequest read(FriendlyByteBuf buf) {
        What what = What.read(buf);
        return new LocateRequest(what, buf.readBoolean());
    }

    static void handle(LocateRequest msg, PlayPayloadContext ctx) {
        ctx.workHandler().submitAsync(() -> answer(msg, ctx));
    }

    private static void answer(LocateRequest msg, PlayPayloadContext ctx) {
        if (!(ctx.player().orElse(null) instanceof ServerPlayer p) || msg.what == null || !StorageLocator.allow(p)) return;
        try {
            var found = Locator.locate(p, msg.what);
            PacketDistributor.PLAYER.with(p).send(
                    new LocateReply(msg.what, found.lines(), msg.highlight, found.positions()));
            if (msg.highlight && !found.positions().isEmpty()) p.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("rsstoragelocator: lookup failed", e);
        }
    }
}
