package com.claudecode.rsstoragelocator;

import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import com.claudecode.rsstoragelocator.client.ClientState;

/** Server to client on login: this server has the mod, so tooltips may ask it. */
public final class Hello {

    static void write(Hello msg, PacketBuffer buf) {
    }

    static Hello read(PacketBuffer buf) {
        return new Hello();
    }

    static void handle(Hello msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> ClientState.serverHasMod = true));
        ctx.get().setPacketHandled(true);
    }
}
