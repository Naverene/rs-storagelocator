package com.claudecode.rsstoragelocator.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;


import com.refinedmods.refinedstorage.screen.grid.GridScreen;
import com.refinedmods.refinedstorage.screen.grid.stack.FluidGridStack;
import com.refinedmods.refinedstorage.screen.grid.stack.IGridStack;
import com.refinedmods.refinedstorage.screen.grid.stack.ItemGridStack;

import com.claudecode.rsstoragelocator.Config;
import com.claudecode.rsstoragelocator.LocateReply;
import com.claudecode.rsstoragelocator.LocateRequest;
import com.claudecode.rsstoragelocator.StorageLocator;
import com.claudecode.rsstoragelocator.What;

/** Client side: the tooltip lines, the highlight key, and the in-world highlight. */
public final class ClientState {

    private ClientState() {
    }

    public static final KeyMapping HIGHLIGHT = new KeyMapping("key.rsstoragelocator.highlight",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, "key.categories.rsstoragelocator");

    private static final long FRESH_MS = 5000, RETRY_MS = 1500;

    private record Answer(long at, List<Component> lines) {
    }

    private static final Map<What, Answer> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<What, Long> ASKED = new ConcurrentHashMap<>();

    private static volatile List<BlockPos> highlighted = List.of();
    private static volatile ResourceKey<Level> highlightedIn;
    private static volatile long highlightedUntil;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(HIGHLIGHT));
        MinecraftForge.EVENT_BUS.addListener(ClientState::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onKey);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onRenderLevel);
    }

    public static void onReply(LocateReply r) {
        ANSWERS.put(r.what(), new Answer(System.currentTimeMillis(), r.lines()));
        ASKED.remove(r.what());
        if (!r.highlight()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int seconds = Config.HIGHLIGHT_SECONDS.get();
        if (r.positions().isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("rsstoragelocator.nothing_to_highlight",
                    r.what().name()).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        highlighted = List.copyOf(r.positions());
        highlightedIn = mc.player.level.dimension();
        highlightedUntil = System.currentTimeMillis() + seconds * 1000L;
        mc.player.displayClientMessage(Component.translatable("rsstoragelocator.highlighted",
                r.positions().size(), r.what().name(), seconds).withStyle(ChatFormatting.GRAY), false);
    }

    /** False when the server doesn't have the mod, so there is nobody to ask. */
    private static boolean serverHasMod() {
        var conn = Minecraft.getInstance().getConnection();
        return conn != null && StorageLocator.NET.isRemotePresent(conn.getConnection());
    }

    /** Tooltip lines for a grid entry, asking the server when we have nothing fresh. */
    private static List<Component> linesFor(What what) {
        if (!serverHasMod()) return Collections.emptyList();
        long now = System.currentTimeMillis();
        Answer a = ANSWERS.get(what);
        if (a == null || now - a.at > FRESH_MS) {
            Long asked = ASKED.get(what);
            if (asked == null || now - asked > RETRY_MS) {
                ASKED.put(what, now);
                if (ANSWERS.size() > 2048) ANSWERS.clear();
                StorageLocator.NET.sendToServer(new LocateRequest(what, false));
            }
        }
        if (a != null) return a.lines;
        return List.of(Component.translatable("rsstoragelocator.locating").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The grid entry under the mouse, or null. */
    private static What hoveredKey() {
        if (!(Minecraft.getInstance().screen instanceof GridScreen screen)) return null;
        if (!screen.getMenu().getCarried().isEmpty()) return null;
        int slot = screen.getSlotNumber();
        List<IGridStack> stacks = screen.getView().getStacks();
        if (slot < 0 || slot >= stacks.size()) return null;
        IGridStack stack = stacks.get(slot);
        if (stack instanceof ItemGridStack item && !item.getStack().isEmpty()) return What.of(item.getStack());
        if (stack instanceof FluidGridStack fluid && !fluid.getStack().isEmpty()) return What.of(fluid.getStack());
        return null;
    }

    private static void onTooltip(RenderTooltipEvent.GatherComponents e) {
        What what = hoveredKey();
        if (what == null) return;
        // Item entries are drawn with their stack; make sure this tooltip is the hovered entry's.
        if (what.isFluid() ? !e.getItemStack().isEmpty() : !what.matchesItem(e.getItemStack())) return;
        for (Component line : linesFor(what)) e.getTooltipElements().add(Either.<FormattedText, TooltipComponent>left(line));
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre e) {
        if (!HIGHLIGHT.isActiveAndMatches(InputConstants.getKey(e.getKeyCode(), e.getScanCode()))) return;
        What what = hoveredKey();
        if (what == null || !serverHasMod()) return;
        StorageLocator.NET.sendToServer(new LocateRequest(what, true));
        e.setCanceled(true);
    }

    private static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        List<BlockPos> blocks = highlighted;
        if (blocks.isEmpty() || mc.level == null) return;
        if (System.currentTimeMillis() > highlightedUntil || mc.level.dimension() != highlightedIn) {
            if (System.currentTimeMillis() > highlightedUntil) highlighted = List.of();
            return;
        }
        // Blink, so the outline stands out from the block's own edges.
        float alpha = (System.currentTimeMillis() / 500 % 2 == 0) ? 1f : 0.45f;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // Drawn twice: bright where the block is in view, dimmer through whatever is in front of it.
        for (BlockPos pos : blocks) {
            AABB box = new AABB(pos).inflate(0.005);
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(HighlightRenderType.LINES_THROUGH_WALLS), box,
                    1f, 0.55f, 0f, alpha * 0.5f);
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 1f, 0.55f, 0f, alpha);
        }
        pose.popPose();
        buffers.endBatch(HighlightRenderType.LINES_THROUGH_WALLS);
        buffers.endBatch(RenderType.lines());
    }
}
