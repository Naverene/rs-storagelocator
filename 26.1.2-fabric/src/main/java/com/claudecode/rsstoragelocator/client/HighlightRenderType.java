package com.claudecode.rsstoragelocator.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.claudecode.rsstoragelocator.StorageLocator;

/** Lines drawn only where something is in front of them, for the part of a highlight that is behind other blocks. */
final class HighlightRenderType {

    private HighlightRenderType() {
    }

    /** Built like the vanilla lines pipeline, with the depth test inverted. */
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(StorageLocator.MOD_ID, "pipeline/lines_behind_block"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
            .build();

    static final RenderType LINES_BEHIND_BLOCK = RenderType.create(
            StorageLocator.MOD_ID + ":lines_behind_block",
            RenderSetup.builder(PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());
}
