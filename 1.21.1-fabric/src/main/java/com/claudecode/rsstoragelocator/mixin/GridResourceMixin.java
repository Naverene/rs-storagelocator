package com.claudecode.rsstoragelocator.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;

import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.grid.view.FluidGridResource;
import com.refinedmods.refinedstorage.common.grid.view.ItemGridResource;

import com.claudecode.rsstoragelocator.client.ClientState;

/**
 * Adds the "Stored in" lines to the tooltip of a grid entry. NeoForge does this with its tooltip event; Fabric's
 * tooltip callback only sees items, not fluids, so the lines are added where Refined Storage builds the tooltip (it
 * only asks for it to draw the hovered entry).
 */
@Mixin(value = { ItemGridResource.class, FluidGridResource.class }, remap = false)
public abstract class GridResourceMixin {

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void rsstoragelocator$addLocations(CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(ClientState.withLocations((GridResource) (Object) this, cir.getReturnValue()));
    }
}
