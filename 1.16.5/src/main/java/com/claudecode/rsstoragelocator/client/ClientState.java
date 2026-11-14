package com.claudecode.rsstoragelocator.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.blaze3d.matrix.MatrixStack;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.gui.GuiUtils;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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

    public static final KeyBinding HIGHLIGHT = new KeyBinding("key.rsstoragelocator.highlight",
            InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, "key.categories.rsstoragelocator");

    private static final long FRESH_MS = 5000, RETRY_MS = 1500;

    private static final class Answer {
        final long at;
        final List<ITextComponent> lines;

        Answer(long at, List<ITextComponent> lines) {
            this.at = at;
            this.lines = lines;
        }
    }

    private static final Map<What, Answer> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<What, Long> ASKED = new ConcurrentHashMap<>();

    private static volatile List<BlockPos> highlighted = Collections.emptyList();
    private static volatile RegistryKey<World> highlightedIn;
    private static volatile long highlightedUntil;

    public static void init(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> ClientRegistry.registerKeyBinding(HIGHLIGHT)));
        MinecraftForge.EVENT_BUS.addListener(ClientState::onItemTooltip);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onFluidTooltip);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onKey);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onRenderWorld);
    }

    public static void onReply(LocateReply r) {
        ANSWERS.put(r.what, new Answer(System.currentTimeMillis(), r.lines));
        ASKED.remove(r.what);
        if (!r.highlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int seconds = Config.HIGHLIGHT_SECONDS.get();
        if (r.positions.isEmpty()) {
            mc.player.sendStatusMessage(new TranslationTextComponent("rsstoragelocator.nothing_to_highlight",
                    r.what.name()).mergeStyle(TextFormatting.GRAY), false);
            return;
        }
        highlighted = Collections.unmodifiableList(new ArrayList<>(r.positions));
        highlightedIn = mc.player.world.getDimensionKey();
        highlightedUntil = System.currentTimeMillis() + seconds * 1000L;
        mc.player.sendStatusMessage(new TranslationTextComponent("rsstoragelocator.highlighted",
                r.positions.size(), r.what.name(), seconds).mergeStyle(TextFormatting.GRAY), false);
    }

    /** False when the server doesn't have the mod, so there is nobody to ask. */
    private static boolean serverHasMod() {
        ClientPlayNetHandler conn = Minecraft.getInstance().getConnection();
        return conn != null && StorageLocator.NET.isRemotePresent(conn.getNetworkManager());
    }

    /** Tooltip lines for a grid entry, asking the server when we have nothing fresh. */
    private static List<ITextComponent> linesFor(What what) {
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
        return Collections.singletonList(
                new TranslationTextComponent("rsstoragelocator.locating").mergeStyle(TextFormatting.DARK_GRAY));
    }

    /** The grid entry under the mouse, or null. */
    @Nullable
    private static What hovered() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.currentScreen instanceof GridScreen) || mc.player == null) return null;
        if (!mc.player.inventory.getItemStack().isEmpty()) return null;
        GridScreen screen = (GridScreen) mc.currentScreen;
        int slot = screen.getSlotNumber();
        List<IGridStack> stacks = screen.getView().getStacks();
        if (slot < 0 || slot >= stacks.size()) return null;
        IGridStack stack = stacks.get(slot);
        if (stack instanceof ItemGridStack && !((ItemGridStack) stack).getStack().isEmpty()) {
            return What.of(((ItemGridStack) stack).getStack());
        }
        if (stack instanceof FluidGridStack && !((FluidGridStack) stack).getStack().isEmpty()) {
            return What.of(((FluidGridStack) stack).getStack());
        }
        return null;
    }

    /** Item entries: Refined Storage builds their tooltip from the item's own, which fires this event. */
    private static void onItemTooltip(ItemTooltipEvent e) {
        What what = hovered();
        if (what == null || !what.matchesItem(e.getItemStack())) return;
        e.getToolTip().addAll(linesFor(what));
    }

    private static boolean redrawing;

    /**
     * Fluid entries: Refined Storage builds their tooltip itself, and the event's lines are read-only, so draw the
     * tooltip again with the lines added and cancel the original.
     */
    private static void onFluidTooltip(RenderTooltipEvent.Pre e) {
        if (redrawing) return;
        What what = hovered();
        if (what == null || !what.isFluid() || !e.getStack().isEmpty()) return;
        List<ITextComponent> extra = linesFor(what);
        if (extra.isEmpty()) return;
        List<ITextProperties> lines = new ArrayList<>(e.getLines());
        lines.addAll(extra);
        e.setCanceled(true);
        redrawing = true;
        try {
            GuiUtils.drawHoveringText(e.getMatrixStack(), lines, e.getX(), e.getY(), e.getScreenWidth(),
                    e.getScreenHeight(), e.getMaxWidth(), e.getFontRenderer());
        } finally {
            redrawing = false;
        }
    }

    private static void onKey(GuiScreenEvent.KeyboardKeyPressedEvent.Pre e) {
        if (!HIGHLIGHT.isActiveAndMatches(InputMappings.getInputByCode(e.getKeyCode(), e.getScanCode()))) return;
        What what = hovered();
        if (what == null || !serverHasMod()) return;
        StorageLocator.NET.sendToServer(new LocateRequest(what, true));
        e.setCanceled(true);
    }

    private static void onRenderWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> blocks = highlighted;
        if (blocks.isEmpty() || mc.world == null) return;
        if (System.currentTimeMillis() > highlightedUntil || mc.world.getDimensionKey() != highlightedIn) {
            if (System.currentTimeMillis() > highlightedUntil) highlighted = Collections.emptyList();
            return;
        }
        // Blink, so the outline stands out from the block's own edges.
        float alpha = (System.currentTimeMillis() / 500 % 2 == 0) ? 1f : 0.45f;
        Vector3d cam = mc.gameRenderer.getActiveRenderInfo().getProjectedView();
        MatrixStack pose = e.getMatrixStack();
        IRenderTypeBuffer.Impl buffers = mc.getRenderTypeBuffers().getBufferSource();
        RenderType throughWalls = HighlightRenderType.LINES_THROUGH_WALLS;
        pose.push();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // Drawn twice: bright where the block is in view, dimmer through whatever is in front of it.
        for (BlockPos pos : blocks) {
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.005);
            WorldRenderer.drawBoundingBox(pose, buffers.getBuffer(throughWalls), box, 1f, 0.55f, 0f, alpha * 0.5f);
            WorldRenderer.drawBoundingBox(pose, buffers.getBuffer(RenderType.getLines()), box, 1f, 0.55f, 0f, alpha);
        }
        pose.pop();
        buffers.finish(throughWalls);
        buffers.finish(RenderType.getLines());
    }
}
