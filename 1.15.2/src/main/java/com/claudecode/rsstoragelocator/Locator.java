package com.claudecode.rsstoragelocator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.grid.IGrid;
import com.refinedmods.refinedstorage.api.network.grid.INetworkAwareGrid;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.storage.IStorage;
import com.refinedmods.refinedstorage.api.storage.IStorageProvider;
import com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode;
import com.refinedmods.refinedstorage.apiimpl.network.node.diskdrive.DiskDriveNetworkNode;
import com.refinedmods.refinedstorage.container.GridContainer;

/** Server side: finds the network of the player's open grid and every storage on it holding an item or fluid. */
final class Locator {

    private Locator() {
    }

    /** How many places the tooltip names; the rest are summed on one line. */
    private static final int SHOWN = 6;

    static final class Result {
        final List<ITextComponent> lines;
        final List<BlockPos> positions;

        Result(List<ITextComponent> lines, List<BlockPos> positions) {
            this.lines = lines;
            this.positions = positions;
        }
    }

    private static final class Hit {
        final ITextComponent label;
        @Nullable
        final BlockPos pos;
        @Nullable
        final World world;
        long amount;

        Hit(ITextComponent label, @Nullable BlockPos pos, @Nullable World world) {
            this.label = label;
            this.pos = pos;
            this.world = world;
        }
    }

    static Result locate(ServerPlayerEntity p, What what) {
        List<ITextComponent> lines = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        INetwork network = networkOf(p);
        if (network == null) return new Result(lines, new ArrayList<>(positions));

        Map<String, Hit> where = new LinkedHashMap<>();
        for (INetworkNode node : network.getNodeGraph().all()) {
            try {
                if (node instanceof DiskDriveNetworkNode) {
                    drive((DiskDriveNetworkNode) node, what, where);
                } else if (node instanceof ExternalStorageNetworkNode) {
                    external((ExternalStorageNetworkNode) node, what, where);
                } else if (node instanceof IStorageProvider) {
                    other(node, (IStorageProvider) node, what, where);
                }
            } catch (RuntimeException | LinkageError e) {
                StorageLocator.LOG.debug("rsstoragelocator: skipped {}", node, e);
            }
        }
        if (where.isEmpty()) {
            lines.add(new TranslationTextComponent("rsstoragelocator.not_stored").applyTextStyle(TextFormatting.DARK_GRAY));
            return new Result(lines, new ArrayList<>(positions));
        }

        List<Hit> hits = new ArrayList<>(where.values());
        hits.sort((a, b) -> Long.compare(b.amount, a.amount));
        lines.add((hits.size() == 1 ? new TranslationTextComponent("rsstoragelocator.stored_in_one")
                : new TranslationTextComponent("rsstoragelocator.stored_in", hits.size()))
                .applyTextStyle(TextFormatting.GRAY));
        long rest = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.pos != null && h.world == p.world && positions.size() < 4096) positions.add(h.pos);
            if (i >= SHOWN) {
                rest += h.amount;
                continue;
            }
            lines.add(new StringTextComponent(" - ").applyTextStyle(TextFormatting.DARK_GRAY)
                    .appendSibling(h.label.deepCopy().applyTextStyle(TextFormatting.WHITE))
                    .appendSibling(new StringTextComponent(": ").applyTextStyle(TextFormatting.GRAY))
                    .appendSibling(new StringTextComponent(what.amount(h.amount)).applyTextStyle(TextFormatting.GREEN)));
        }
        if (hits.size() > SHOWN) {
            lines.add(new TranslationTextComponent("rsstoragelocator.more", hits.size() - SHOWN, what.amount(rest))
                    .applyTextStyle(TextFormatting.DARK_GRAY));
        }
        return new Result(lines, new ArrayList<>(positions));
    }

    /** Disk drives: each disk slot on its own, so the label can say which disk and slot. */
    private static void drive(DiskDriveNetworkNode drive, What what, Map<String, Hit> where) {
        IStorage<?>[] disks = what.isFluid() ? drive.getFluidDisks() : drive.getItemDisks();
        ITextComponent holder = blockName(drive);
        for (int slot = 0; slot < disks.length; slot++) {
            long c = count(disks[slot], what);
            if (c <= 0) continue;
            ItemStack disk = drive.getDisks().getStackInSlot(slot);
            ITextComponent diskName = disk.isEmpty() ? new StringTextComponent("?") : disk.getDisplayName();
            ITextComponent label = new TranslationTextComponent("rsstoragelocator.disk_in", diskName, holder);
            label.appendSibling(at(drive.getPos())).appendText(", ")
                    .appendSibling(new TranslationTextComponent("rsstoragelocator.slot", slot + 1));
            add(where, "d|" + drive.getPos().toLong() + "|" + slot, label, c, drive.getPos(), drive.getWorld());
        }
    }

    /** An external storage: name the block it faces, and highlight that block. */
    private static void external(ExternalStorageNetworkNode ext, What what, Map<String, Hit> where) {
        long c = 0;
        for (IStorage<?> s : what.isFluid() ? ext.getFluidStorages() : ext.getItemStorages()) c += count(s, what);
        if (c <= 0) return;
        Direction side = ext.getDirection();
        World world = ext.getWorld();
        BlockPos target = side == null ? ext.getPos() : ext.getPos().offset(side);
        ITextComponent label = new TranslationTextComponent(world.getBlockState(target).getBlock().getTranslationKey())
                .appendSibling(at(target));
        add(where, "e|" + target.toLong(), label, c, target, world);
    }

    /** Anything else that adds storage to the network (storage blocks, add-ons). */
    private static void other(INetworkNode node, IStorageProvider provider, What what, Map<String, Hit> where) {
        long c = 0;
        if (what.isFluid()) {
            List<IStorage<FluidStack>> list = new ArrayList<>();
            provider.addFluidStorages(list);
            for (IStorage<FluidStack> s : list) c += count(s, what);
        } else {
            List<IStorage<ItemStack>> list = new ArrayList<>();
            provider.addItemStorages(list);
            for (IStorage<ItemStack> s : list) c += count(s, what);
        }
        if (c <= 0) return;
        ITextComponent label = blockName(node).deepCopy().appendSibling(at(node.getPos()));
        add(where, "o|" + node.getPos().toLong(), label, c, node.getPos(), node.getWorld());
    }

    /** How much of `what` one storage holds. */
    private static long count(@Nullable IStorage<?> storage, What what) {
        if (storage == null) return 0;
        long c = 0;
        for (Object o : storage.getStacks()) {
            if (o instanceof ItemStack && what.matches((ItemStack) o)) c += ((ItemStack) o).getCount();
            else if (o instanceof FluidStack && what.matches((FluidStack) o)) c += ((FluidStack) o).getAmount();
        }
        return c;
    }

    private static ITextComponent blockName(INetworkNode node) {
        World world = node.getWorld();
        if (world == null) return node.getItemStack().getDisplayName();
        return new TranslationTextComponent(world.getBlockState(node.getPos()).getBlock().getTranslationKey());
    }

    private static void add(Map<String, Hit> where, String key, ITextComponent label, long amount,
            @Nullable BlockPos pos, @Nullable World world) {
        where.computeIfAbsent(key, k -> new Hit(label, pos, world)).amount += amount;
    }

    private static ITextComponent at(BlockPos pos) {
        return new StringTextComponent(" @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The network of the grid the player has open (a Grid block or a wireless grid), or null. */
    @Nullable
    static INetwork networkOf(ServerPlayerEntity p) {
        if (!(p.openContainer instanceof GridContainer)) return null;
        IGrid grid = ((GridContainer) p.openContainer).getGrid();
        return grid instanceof INetworkAwareGrid ? ((INetworkAwareGrid) grid).getNetwork() : null;
    }
}
