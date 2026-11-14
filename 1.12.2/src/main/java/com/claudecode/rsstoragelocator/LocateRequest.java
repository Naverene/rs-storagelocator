package com.claudecode.rsstoragelocator;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;


/**
 * Client to server: where is this item or fluid kept in the network of my open grid? With highlight set, the
 * server also closes the grid and the client outlines the blocks.
 */
public final class LocateRequest implements IMessage {

    What what;
    boolean highlight;

    public LocateRequest() {
    }

    public LocateRequest(What what, boolean highlight) {
        this.what = what;
        this.highlight = highlight;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        what.write(new PacketBuffer(buf));
        buf.writeBoolean(highlight);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        what = What.read(new PacketBuffer(buf));
        highlight = what != null && buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<LocateRequest, IMessage> {
        @Override
        public IMessage onMessage(LocateRequest msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> answer(msg, p));
            return null;
        }
    }

    private static void answer(LocateRequest msg, EntityPlayerMP p) {
        if (msg.what == null || !StorageLocator.allow(p)) return;
        try {
            Locator.Result found = Locator.locate(p, msg.what);
            StorageLocator.NET.sendTo(new LocateReply(msg.what, found.lines, msg.highlight, found.positions), p);
            if (msg.highlight && !found.positions.isEmpty()) p.closeScreen();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("rsstoragelocator: lookup failed", e);
        }
    }
}
