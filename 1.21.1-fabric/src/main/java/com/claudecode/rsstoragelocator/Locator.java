package com.claudecode.rsstoragelocator;

import java.lang.reflect.Field;
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
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractStorageContainerNetworkNode;
import com.refinedmods.refinedstorage.api.network.impl.node.externalstorage.ExternalStorageNetworkNode;
import com.refinedmods.refinedstorage.api.network.impl.node.relay.RelayOutputNetworkNode;
import com.refinedmods.refinedstorage.api.network.impl.node.storage.StorageNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer;
import com.refinedmods.refinedstorage.api.network.storage.StorageProvider;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.storage.AbstractDiskContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.AbstractDirectionalBlock;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;

/** Server side: finds the network of the player's open grid and every storage on it holding a resource. */
final class Locator {

    private Locator() {
    }

    /** How many places the tooltip names; the rest are summed on one line. */
    private static final int SHOWN = 6;

    record Result(List<Component> lines, List<BlockPos> positions) {
    }

    private static final class Hit {
        final Component label;
        final GlobalPos pos;
        long amount;

        Hit(Component label, GlobalPos pos) {
            this.label = label;
            this.pos = pos;
        }
    }

    static Result locate(ServerPlayer p, PlatformResourceKey what) {
        List<Component> lines = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        Network network = networkOf(p);
        if (network == null) return new Result(lines, new ArrayList<>(positions));

        Map<String, Hit> where = new LinkedHashMap<>();
        for (NetworkNodeContainer container : network.getComponent(GraphNetworkComponent.class).getContainers()) {
            if (!(container instanceof InWorldNetworkNodeContainer in)) continue;
            NetworkNode node = container.getNode();
            try {
                if (node instanceof StorageNetworkNode drive) {
                    drive(p, in, drive, what, where);
                } else if (node instanceof ExternalStorageNetworkNode ext) {
                    external(p, in, ext, what, where);
                } else if (node instanceof StorageProvider provider && !(node instanceof RelayOutputNetworkNode)) {
                    // A relay output passes another network's storage through; that is counted where it is kept.
                    add(where, "o|" + in.getPosition(), blockName(in).copy().append(at(in.getLocalPosition())),
                            count(provider.getStorage(), what), in.getPosition());
                }
            } catch (RuntimeException | LinkageError e) {
                StorageLocator.LOG.debug("rsstoragelocator: skipped {}", node, e);
            }
        }
        where.values().removeIf(h -> h.amount <= 0);
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
            if (h.pos.dimension() == p.level().dimension() && positions.size() < 4096) positions.add(h.pos.pos());
            if (i >= SHOWN) {
                rest += h.amount;
                continue;
            }
            lines.add(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(h.label.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(amount(what, h.amount)).withStyle(ChatFormatting.GREEN)));
        }
        if (hits.size() > SHOWN) {
            lines.add(Component.translatable("rsstoragelocator.more", hits.size() - SHOWN, amount(what, rest))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return new Result(lines, new ArrayList<>(positions));
    }

    /** Disk drives (and storage blocks): each disk slot on its own, so the label can say which disk and slot. */
    private static void drive(ServerPlayer p, InWorldNetworkNodeContainer in, StorageNetworkNode drive, PlatformResourceKey what,
            Map<String, Hit> where) {
        Storage[] slots = slots(drive);
        if (slots == null) {
            add(where, "d|" + in.getPosition(), blockName(in).copy().append(at(in.getLocalPosition())),
                    count(drive.getStorage(), what), in.getPosition());
            return;
        }
        BlockEntity be = blockEntity(p, in);
        for (int slot = 0; slot < slots.length; slot++) {
            long c = count(slots[slot], what);
            if (c <= 0) continue;
            MutableComponent label;
            if (be instanceof AbstractDiskContainerBlockEntity<?> disks && slots.length > 1) {
                ItemStack disk = disks.getDiskInventory().getItem(slot);
                Component diskName = disk.isEmpty() ? Component.literal("?") : disk.getHoverName();
                label = Component.translatable("rsstoragelocator.disk_in", diskName, blockName(in));
                label.append(at(in.getLocalPosition()));
                label.append(", ").append(Component.translatable("rsstoragelocator.slot", slot + 1));
            } else {
                label = blockName(in).copy().append(at(in.getLocalPosition()));
            }
            add(where, "d|" + in.getPosition() + "|" + slot, label, c, in.getPosition());
        }
    }

    /** An external storage: name the block it faces, and highlight that block. */
    private static void external(ServerPlayer p, InWorldNetworkNodeContainer in, ExternalStorageNetworkNode ext,
            PlatformResourceKey what, Map<String, Hit> where) {
        long c = count(ext.getStorage(), what);
        if (c <= 0) return;
        Direction side = AbstractDirectionalBlock.tryExtractDirection(in.getBlockState());
        GlobalPos at = in.getPosition();
        BlockPos target = side == null ? at.pos() : at.pos().relative(side);
        Component name = blockName(in);
        ServerLevel level = p.level().getServer().getLevel(at.dimension());
        if (level != null) name = level.getBlockState(target).getBlock().getName();
        add(where, "e|" + at.dimension() + "|" + target.asLong(), name.copy().append(at(target)), c,
                GlobalPos.of(at.dimension(), target));
    }

    /** The per-slot storages of a disk drive, or null when they can't be read. */
    @Nullable
    private static Storage[] slots(AbstractStorageContainerNetworkNode node) {
        try {
            return (Storage[]) STORAGES.get(node);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static final Field STORAGES = field(AbstractStorageContainerNetworkNode.class, "storages");
    private static final Field GRID = field(AbstractGridContainerMenu.class, "grid");

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            StorageLocator.LOG.warn("rsstoragelocator: no field {} in {}", name, owner.getName());
            return null;
        }
    }

    /** The block entity of a node container, or null when its level isn't loaded. */
    @Nullable
    private static BlockEntity blockEntity(ServerPlayer p, InWorldNetworkNodeContainer in) {
        ServerLevel level = p.level().getServer().getLevel(in.getPosition().dimension());
        return level == null ? null : level.getBlockEntity(in.getLocalPosition());
    }

    /** How much of `what` one storage holds. */
    private static long count(@Nullable Storage storage, PlatformResourceKey what) {
        if (storage == null) return 0;
        long c = 0;
        for (ResourceAmount ra : storage.getAll()) {
            if (what.equals(ra.resource())) c += ra.amount();
        }
        return c;
    }

    /** Items are counted one by one, fluids in millibuckets. */
    private static String amount(PlatformResourceKey what, long n) {
        if (what instanceof FluidResource) {
            return String.format("%,d mB", n * 1000 / Math.max(1, Platform.INSTANCE.getBucketAmount()));
        }
        return String.format("%,d", n);
    }

    private static Component blockName(InWorldNetworkNodeContainer in) {
        return in.getBlockState().getBlock().getName();
    }

    private static void add(Map<String, Hit> where, String key, Component label, long amount, GlobalPos pos) {
        if (amount <= 0) return;
        where.computeIfAbsent(key, k -> new Hit(label, pos)).amount += amount;
    }

    private static Component at(BlockPos pos) {
        return Component.literal(" @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The network of the grid the player has open (a Grid block or a wireless grid), or null. */
    @Nullable
    static Network networkOf(ServerPlayer p) {
        if (!(p.containerMenu instanceof AbstractGridContainerMenu menu) || GRID == null) return null;
        Object grid;
        try {
            grid = GRID.get(menu);
        } catch (ReflectiveOperationException e) {
            return null;
        }
        if (grid instanceof AbstractBaseNetworkNodeContainerBlockEntity<?> be) return be.getNetworkForItem();
        // The wireless grid keeps the item's network context in a field.
        if (grid != null) {
            for (Field f : grid.getClass().getDeclaredFields()) {
                if (!NetworkItemContext.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    var context = (NetworkItemContext) f.get(grid);
                    return context == null ? null : context.resolveNetwork().orElse(null);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
