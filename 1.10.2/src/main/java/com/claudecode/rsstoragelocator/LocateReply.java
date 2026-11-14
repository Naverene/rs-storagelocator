package com.claudecode.rsstoragelocator;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;


import com.claudecode.rsstoragelocator.client.ClientState;

/**
 * Server to client: the tooltip lines for one stack (empty when the open screen isn't on a network), and the blocks
 * holding it in the player's dimension.
 */
public final class LocateReply implements IMessage {

    public What what;
    public List<ITextComponent> lines = new ArrayList<>();
    public boolean highlight;
    public List<BlockPos> positions = new ArrayList<>();

    public LocateReply() {
    }

    public LocateReply(What what, List<ITextComponent> lines, boolean highlight, List<BlockPos> positions) {
        this.what = what;
        this.lines = lines;
        this.highlight = highlight;
        this.positions = positions;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        what.write(buf);
        int n = Math.min(lines.size(), 32);
        buf.writeInt(n);
        for (int i = 0; i < n; i++) ByteBufUtils.writeUTF8String(buf, ITextComponent.Serializer.componentToJson(lines.get(i)));
        buf.writeBoolean(highlight);
        int m = Math.min(positions.size(), 4096);
        buf.writeInt(m);
        for (int i = 0; i < m; i++) buf.writeLong(positions.get(i).toLong());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        what = What.read(buf);
        if (what == null) return;
        try {
            int n = Math.min(buf.readInt(), 32);
            for (int i = 0; i < n; i++) lines.add(ITextComponent.Serializer.jsonToComponent(ByteBufUtils.readUTF8String(buf)));
        } catch (RuntimeException e) {
            what = null;
            return;
        }
        highlight = buf.readBoolean();
        int m = Math.min(buf.readInt(), 4096);
        for (int i = 0; i < m; i++) positions.add(BlockPos.fromLong(buf.readLong()));
    }

    public static final class Handler implements IMessageHandler<LocateReply, IMessage> {
        @Override
        public IMessage onMessage(LocateReply msg, MessageContext ctx) {
            if (msg.what != null) ClientState.schedule(msg);
            return null;
        }
    }
}
