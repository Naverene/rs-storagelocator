package com.claudecode.rsstoragelocator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.INetworkNodeGraphEntry;
import com.refinedmods.refinedstorage.api.network.grid.IGrid;
import com.refinedmods.refinedstorage.api.network.grid.INetworkAwareGrid;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.api.storage.IStorage;
import com.refinedmods.refinedstorage.api.storage.IStorageProvider;
import com.refinedmods.refinedstorage.apiimpl.network.node.ExternalStorageNetworkNode;
import com.refinedmods.refinedstorage.apiimpl.network.node.diskdrive.DiskDriveNetworkNode;
import com.refinedmods.refinedstorage.container.GridContainerMenu;

/** Server side: finds the network of the player's open grid and every storage on it holding an item or fluid. */
final class Locator {

    private Locator() {
    }

    /** How many places the tooltip names; the rest are summed on one line. */
    private static final int SHOWN = 6;

    record Result(List<Component> lines, List<BlockPos> positions) {
    }

    private static final class Hit {
        final Component label;
        final @Nullable BlockPos pos;
        final @Nullable Level level;
        long amount;

        Hit(Component label, @Nullable BlockPos pos, @Nullable Level level) {
            this.label = label;
            this.pos = pos;
            this.level = level;
        }
    }

    static Result locate(ServerPlayer p, What what) {
        List<Component> lines = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        INetwork network = networkOf(p);
        if (network == null) return new Result(lines, new ArrayList<>(positions));

        Map<String, Hit> where = new LinkedHashMap<>();
        for (INetworkNodeGraphEntry entry : network.getNodeGraph().all()) {
            INetworkNode node = entry.getNode();
            try {
                if (node instanceof DiskDriveNetworkNode drive) {
                    drive(drive, what, where);
                } else if (node instanceof ExternalStorageNetworkNode ext) {
                    external(ext, what, where);
                } else if (node instanceof IStorageProvider provider) {
                    other(node, provider, what, where);
                }
            } catch (RuntimeException | LinkageError e) {
                StorageLocator.LOG.debug("rsstoragelocator: skipped {}", node, e);
            }
        }
        if (where.isEmpty()) {
            lines.add(Component.translatable("rsstoragelocator.not_stored").withStyle(ChatFormatting.DARK_GRAY));
            return new Result(lines, new ArrayList<>(positions));
        }

        List<Hit> hits = new ArrayList<>(where.values());
        hits.sort((a, b) -> Long.compare(b.amount, a.amount));
        lines.add((hits.size() == 1 ? Component.translatable("rsstoragelocator.stored_in_one")
                : Component.translatable("rsstoragelocator.stored_in", hits.size())).withStyle(ChatFormatting.GRAY));
        long rest = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.pos != null && h.level == p.level() && positions.size() < 4096) positions.add(h.pos);
            if (i >= SHOWN) {
                rest += h.amount;
                continue;
            }
            lines.add(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(h.label.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(what.amount(h.amount)).withStyle(ChatFormatting.GREEN)));
        }
        if (hits.size() > SHOWN) {
            lines.add(Component.translatable("rsstoragelocator.more", hits.size() - SHOWN, what.amount(rest))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return new Result(lines, new ArrayList<>(positions));
    }

    /** Disk drives: each disk slot on its own, so the label can say which disk and slot. */
    private static void drive(DiskDriveNetworkNode drive, What what, Map<String, Hit> where) {
        IStorage<?>[] disks = what.isFluid() ? drive.getFluidDisks() : drive.getItemDisks();
        Component holder = blockName(drive);
        for (int slot = 0; slot < disks.length; slot++) {
            long c = count(disks[slot], what);
            if (c <= 0) continue;
            ItemStack disk = drive.getDisks().getStackInSlot(slot);
            Component diskName = disk.isEmpty() ? Component.literal("?") : disk.getHoverName();
            MutableComponent label = Component.translatable("rsstoragelocator.disk_in", diskName, holder);
            label.append(at(drive.getPos()));
            label.append(", ").append(Component.translatable("rsstoragelocator.slot", slot + 1));
            add(where, "d|" + drive.getPos().asLong() + "|" + slot, label, c, drive.getPos(), drive.getLevel());
        }
    }

    /** An external storage: name the block it faces, and highlight that block. */
    private static void external(ExternalStorageNetworkNode ext, What what, Map<String, Hit> where) {
        long c = 0;
        for (IStorage<?> s : what.isFluid() ? ext.getFluidStorages() : ext.getItemStorages()) c += count(s, what);
        if (c <= 0) return;
        Direction side = ext.getDirection();
        Level level = ext.getLevel();
        BlockPos target = side == null ? ext.getPos() : ext.getPos().relative(side);
        MutableComponent label = level.getBlockState(target).getBlock().getName().copy().append(at(target));
        add(where, "e|" + target.asLong(), label, c, target, level);
    }

    /** Anything else that adds storage to the network (portable grids, storage blocks, add-ons). */
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
        MutableComponent label = blockName(node).copy().append(at(node.getPos()));
        add(where, "o|" + node.getPos().asLong(), label, c, node.getPos(), node.getLevel());
    }

    /** How much of `what` one storage holds. */
    private static long count(@Nullable IStorage<?> storage, What what) {
        if (storage == null) return 0;
        long c = 0;
        for (Object o : storage.getStacks()) {
            if (o instanceof ItemStack s && what.matches(s)) c += s.getCount();
            else if (o instanceof FluidStack f && what.matches(f)) c += f.getAmount();
        }
        return c;
    }

    private static Component blockName(INetworkNode node) {
        Level level = node.getLevel();
        if (level == null) return node.getItemStack().getHoverName();
        return level.getBlockState(node.getPos()).getBlock().getName();
    }

    private static void add(Map<String, Hit> where, String key, Component label, long amount, @Nullable BlockPos pos,
            @Nullable Level level) {
        where.computeIfAbsent(key, k -> new Hit(label, pos, level)).amount += amount;
    }

    private static Component at(BlockPos pos) {
        return Component.literal(" @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The network of the grid the player has open (a Grid block or a wireless grid), or null. */
    @Nullable
    static INetwork networkOf(ServerPlayer p) {
        if (!(p.containerMenu instanceof GridContainerMenu menu)) return null;
        IGrid grid = menu.getGrid();
        return grid instanceof INetworkAwareGrid aware ? aware.getNetwork() : null;
    }
}
