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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import com.raoulvdberge.refinedstorage.api.network.INetworkMaster;
import com.raoulvdberge.refinedstorage.api.network.INetworkNode;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.fluid.IFluidStorage;
import com.raoulvdberge.refinedstorage.api.storage.fluid.IFluidStorageProvider;
import com.raoulvdberge.refinedstorage.api.storage.item.IItemStorage;
import com.raoulvdberge.refinedstorage.api.storage.item.IItemStorageProvider;
import com.raoulvdberge.refinedstorage.container.ContainerGrid;
import com.raoulvdberge.refinedstorage.tile.TileBase;
import com.raoulvdberge.refinedstorage.tile.TileDiskDrive;
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
        INetworkMaster network = networkOf(p);
        if (network == null) return new Result(lines, new ArrayList<>(positions));

        Map<String, Hit> where = new LinkedHashMap<>();
        for (INetworkNode node : network.getNodeGraph().all()) {
            try {
                if (node instanceof TileDiskDrive) {
                    drive((TileDiskDrive) node, what, where);
                } else if (node instanceof IItemStorageProvider || node instanceof IFluidStorageProvider) {
                    other(node, what, where);
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
            if (h.pos != null && h.world == p.worldObj && positions.size() < 4096) positions.add(h.pos);
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
        if (item != null) return nameOf(new ItemStack(item, 1, state.getBlock().damageDropped(state)));
        return new TextComponentTranslation(state.getBlock().getUnlocalizedName() + ".name");
    }

    /**
     * Disk drives: each disk slot on its own, so the label can say which disk and slot. This Refined Storage keeps a
     * drive's disks in private arrays; when they can't be read the drive counts as a whole.
     */
    private static void drive(TileDiskDrive drive, What what, Map<String, Hit> where) {
        World world = drive.getWorld();
        if (world == null) return;
        Object[] disks = diskArray(drive, what.isFluid() ? "fluidStorages" : "itemStorages");
        if (disks == null) {
            other(drive, what, where);
            return;
        }
        ITextComponent holder = nameOf(world, drive.getPos());
        for (int slot = 0; slot < disks.length; slot++) {
            long c = count(disks[slot] instanceof IStorage ? (IStorage<?>) disks[slot] : null, what);
            if (c <= 0) continue;
            ItemStack disk = drive.getDisks().getStackInSlot(slot);
            ITextComponent diskName = disk == null ? new TextComponentString("?") : nameOf(disk);
            ITextComponent label = new TextComponentTranslation("rsstoragelocator.disk_in", diskName, holder);
            label.appendSibling(at(drive.getPos())).appendText(", ")
                    .appendSibling(new TextComponentTranslation("rsstoragelocator.slot", slot + 1));
            add(where, "d|" + drive.getPos().toLong() + "|" + slot, label, c, drive.getPos(), world);
        }
    }

    @Nullable
    private static Object[] diskArray(TileDiskDrive drive, String field) {
        try {
            java.lang.reflect.Field f = TileDiskDrive.class.getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(drive);
            return v instanceof Object[] ? (Object[]) v : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Any other node that adds storage to the network. An External Storage is named after, and highlights, the block
     * it faces.
     */
    private static void other(INetworkNode node, What what, Map<String, Hit> where) {
        World world = node.getNodeWorld();
        if (world == null) return;
        long c = 0;
        if (what.isFluid()) {
            if (!(node instanceof IFluidStorageProvider)) return;
            List<IFluidStorage> list = new ArrayList<>();
            ((IFluidStorageProvider) node).addFluidStorages(list);
            for (IFluidStorage s : list) c += count(s, what);
        } else {
            if (!(node instanceof IItemStorageProvider)) return;
            List<IItemStorage> list = new ArrayList<>();
            ((IItemStorageProvider) node).addItemStorages(list);
            for (IItemStorage s : list) c += count(s, what);
        }
        if (c <= 0) return;
        BlockPos pos = node.getPosition();
        // Named by class so this doesn't need the multipart library External Storage builds on.
        if (node.getClass().getSimpleName().equals("TileExternalStorage") && node instanceof TileBase) {
            EnumFacing side = ((TileBase) node).getDirection();
            if (side != null) pos = pos.offset(side);
        }
        ITextComponent label = nameOf(world, pos);
        label.appendSibling(at(pos));
        add(where, "o|" + pos.toLong(), label, c, pos, world);
    }

    /** How much of `what` one storage holds. */
    private static long count(@Nullable IStorage<?> storage, What what) {
        if (storage == null) return 0;
        long c = 0;
        for (Object o : storage.getStacks()) {
            if (o instanceof ItemStack && what.matches((ItemStack) o)) c += ((ItemStack) o).stackSize;
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
    static INetworkMaster networkOf(EntityPlayerMP p) {
        if (!(p.openContainer instanceof ContainerGrid)) return null;
        IGrid grid = ((ContainerGrid) p.openContainer).getGrid();
        if (grid instanceof INetworkNode) return ((INetworkNode) grid).getNetwork();
        // A wireless grid only knows where its controller is, and in which dimension.
        BlockPos pos = grid == null ? null : grid.getNetworkPosition();
        if (pos == null) return null;
        World world = p.worldObj;
        try {
            java.lang.reflect.Field f = grid.getClass().getDeclaredField("controllerDimension");
            f.setAccessible(true);
            World w = DimensionManager.getWorld(f.getInt(grid));
            if (w != null) world = w;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Same dimension as the player, then.
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof INetworkMaster ? (INetworkMaster) tile : null;
    }
}
