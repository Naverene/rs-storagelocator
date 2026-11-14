package com.claudecode.rsstoragelocator.client;

import java.util.OptionalDouble;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

/** Lines drawn on top of everything, for the part of a highlight that is behind other blocks. */
final class HighlightRenderType extends RenderType {

    private HighlightRenderType(String name, VertexFormat format, int drawMode, int bufferSize, boolean useDelegate,
            boolean needsSorting, Runnable setup, Runnable clear) {
        super(name, format, drawMode, bufferSize, useDelegate, needsSorting, setup, clear);
    }

    static final RenderType LINES_THROUGH_WALLS = makeType("rsstoragelocator_lines_through_walls",
            DefaultVertexFormats.POSITION_COLOR, GL11.GL_LINES, 256,
            RenderType.State.getBuilder()
                    .line(new LineState(OptionalDouble.of(2.0)))
                    .layer(VIEW_OFFSET_Z_LAYERING)
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .target(ITEM_ENTITY_TARGET)
                    .writeMask(COLOR_WRITE)
                    .cull(CULL_DISABLED)
                    .depthTest(DEPTH_ALWAYS)
                    .build(false));
}
