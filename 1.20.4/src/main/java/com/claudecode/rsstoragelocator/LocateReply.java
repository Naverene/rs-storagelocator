package com.claudecode.rsstoragelocator;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;


import com.claudecode.rsstoragelocator.client.ClientState;

/**
 * Server to client: the tooltip lines for one key (empty when the open screen isn't on a network), and the blocks
 * holding it in the player's dimension.
 */
public record LocateReply(What what, List<Component> lines, boolean highlight, List<BlockPos> positions)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation(StorageLocator.MOD_ID, "located");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        what.write(buf);
        int n = Math.min(lines.size(), 32);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeComponent(lines.get(i));
        buf.writeBoolean(highlight);
        int m = Math.min(positions.size(), 4096);
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) buf.writeBlockPos(positions.get(i));
    }

    static LocateReply read(FriendlyByteBuf buf) {
        What what = What.read(buf);
        int n = Math.min(buf.readVarInt(), 32);
        List<Component> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readComponent());
        boolean highlight = buf.readBoolean();
        int m = Math.min(buf.readVarInt(), 4096);
        List<BlockPos> positions = new ArrayList<>(m);
        for (int i = 0; i < m; i++) positions.add(buf.readBlockPos());
        return new LocateReply(what, lines, highlight, positions);
    }

    static void handle(LocateReply msg, PlayPayloadContext ctx) {
        if (msg.what == null) return;
        ctx.workHandler().submitAsync(() -> ClientState.onReply(msg));
    }
}
