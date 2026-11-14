package com.claudecode.rsstoragelocator;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

/**
 * Client to server: where is this item or fluid kept in the network of my open grid? With highlight set, the
 * server also closes the grid and the client outlines the blocks.
 */
public record LocateRequest(PlatformResourceKey what, boolean highlight) implements CustomPacketPayload {

    public static final Type<LocateRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(StorageLocator.MOD_ID, "locate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocateRequest> STREAM_CODEC = StreamCodec.composite(
            ResourceCodecs.STREAM_CODEC, LocateRequest::what,
            ByteBufCodecs.BOOL, LocateRequest::highlight,
            LocateRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
