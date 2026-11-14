package com.claudecode.rsstoragelocator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.network.node.INetworkNode;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.externalstorage.NetworkNodeExternalStorage;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.diskdrive.NetworkNodeDiskDrive;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import com.raoulvdberge.refinedstorage.tile.grid.IGrid;

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

    private static ITextComponent colored(ITextComponent c, TextFormatting color) {
        c.getStyle().setColor(color);
        return c;
    }

    static Result locate(EntityPlayerMP p, What what) {
        List<ITextComponent> lines = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        INetwork network = networkOf(p);
        if (network == null) return new Result(lines, new ArrayList<>(positions));

        Map<String, Hit> where = new LinkedHashMap<>();
        for (INetworkNode node : network.getNodeGraph().all()) {
            try {
                if (node instanceof NetworkNodeDiskDrive) {
                    drive((NetworkNodeDiskDrive) node, what, where);
                } else if (node instanceof NetworkNodeExternalStorage) {
                    external((NetworkNodeExternalStorage) node, what, where);
                } else if (node instanceof IStorageProvider) {
                    other(node, (IStorageProvider) node, what, where);
                }
            } catch (RuntimeException | LinkageError e) {
                StorageLocator.LOG.debug("rsstoragelocator: skipped {}", node, e);
            }
        }
        if (where.isEmpty()) {
            lines.add(colored(new TextComponentTranslation("rsstoragelocator.not_stored"), TextFormatting.DARK_GRAY));
            return new Result(lines, new ArrayList<>(positions));
        }

        List<Hit> hits = new ArrayList<>(where.values());
        hits.sort((a, b) -> Long.compare(b.amount, a.amount));
        lines.add(colored(hits.size() == 1 ? new TextComponentTranslation("rsstoragelocator.stored_in_one")
                : new TextComponentTranslation("rsstoragelocator.stored_in", hits.size()), TextFormatting.GRAY));
        long rest = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.pos != null && h.world == p.world && positions.size() < 4096) positions.add(h.pos);
            if (i >= SHOWN) {
                rest += h.amount;
                continue;
            }
            lines.add(colored(new TextComponentString(" - "), TextFormatting.DARK_GRAY)
                    .appendSibling(colored(h.label.createCopy(), TextFormatting.WHITE))
                    .appendSibling(colored(new TextComponentString(": "), TextFormatting.GRAY))
                    .appendSibling(colored(new TextComponentString(what.amount(h.amount)), TextFormatting.GREEN)));
        }
        if (hits.size() > SHOWN) {
            lines.add(colored(new TextComponentTranslation("rsstoragelocator.more", hits.size() - SHOWN,
                    what.amount(rest)), TextFormatting.DARK_GRAY));
        }
        return new Result(lines, new ArrayList<>(positions));
    }

    private static ITextComponent nameOf(ItemStack stack) {
        if (stack.hasDisplayName()) return new TextComponentString(stack.getDisplayName());
        return new TextComponentTranslation(stack.getUnlocalizedName() + ".name");
    }

    private static ITextComponent nameOf(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item != net.minecraft.init.Items.AIR) {
            ItemStack stack = new ItemStack(item, 1, state.getBlock().damageDropped(state));
            if (!stack.isEmpty()) return nameOf(stack);
        }
        return new TextComponentTranslation(state.getBlock().getUnlocalizedName() + ".name");
    }

    /** Disk drives: each disk slot on its own, so the label can say which disk and slot. */
    private static void drive(NetworkNodeDiskDrive drive, What what, Map<String, Hit> where) {
        World world = drive.getWorld();
        if (world == null) return;
        IStorage<?>[] disks = what.isFluid() ? drive.getFluidStorages() : drive.getItemStorages();
        ITextComponent holder = nameOf(world, drive.getPos());
        for (int slot = 0; slot < disks.length; slot++) {
            long c = count(disks[slot], what);
            if (c <= 0) continue;
            ItemStack disk = drive.getDisks().getStackInSlot(slot);
            ITextComponent diskName = disk.isEmpty() ? new TextComponentString("?") : nameOf(disk);
            ITextComponent label = new TextComponentTranslation("rsstoragelocator.disk_in", diskName, holder);
            label.appendSibling(at(drive.getPos())).appendText(", ")
                    .appendSibling(new TextComponentTranslation("rsstoragelocator.slot", slot + 1));
            add(where, "d|" + drive.getPos().toLong() + "|" + slot, label, c, drive.getPos(), world);
        }
    }

    /** An external storage: name the block it faces, and highlight that block. */
    private static void external(NetworkNodeExternalStorage ext, What what, Map<String, Hit> where) {
        World world = ext.getWorld();
        if (world == null) return;
        long c = 0;
        for (IStorage<?> s : what.isFluid() ? ext.getFluidStorages() : ext.getItemStorages()) c += count(s, what);
        if (c <= 0) return;
        EnumFacing side = ext.getDirection();
        BlockPos target = side == null ? ext.getPos() : ext.getPos().offset(side);
        ITextComponent label = nameOf(world, target);
        label.appendSibling(at(target));
        add(where, "e|" + target.toLong(), label, c, target, world);
    }

    /** Anything else that adds storage to the network (storage blocks, add-ons). */
    private static void other(INetworkNode node, IStorageProvider provider, What what, Map<String, Hit> where) {
        World world = node.getWorld();
        if (world == null) return;
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
        ITextComponent label = nameOf(world, node.getPos());
        label.appendSibling(at(node.getPos()));
        add(where, "o|" + node.getPos().toLong(), label, c, node.getPos(), world);
    }

    /** How much of `what` one storage holds. */
    private static long count(@Nullable IStorage<?> storage, What what) {
        if (storage == null) return 0;
        long c = 0;
        for (Object o : storage.getStacks()) {
            if (o instanceof ItemStack && what.matches((ItemStack) o)) c += ((ItemStack) o).getCount();
            else if (o instanceof FluidStack && what.matches((FluidStack) o)) c += ((FluidStack) o).amount;
        }
        return c;
    }

    private static void add(Map<String, Hit> where, String key, ITextComponent label, long amount,
            @Nullable BlockPos pos, @Nullable World world) {
        where.computeIfAbsent(key, k -> new Hit(label, pos, world)).amount += amount;
    }

    private static ITextComponent at(BlockPos pos) {
        return new TextComponentString(" @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The network of the grid the player has open (a Grid block or a wireless grid), or null. */
    @Nullable
    static INetwork networkOf(EntityPlayerMP p) {
        if (!(p.openContainer instanceof ContainerGrid)) return null;
        IGrid grid = ((ContainerGrid) p.openContainer).getGrid();
        return grid == null ? null : grid.getNetwork();
    }
}
