package com.claudecode.rsstoragelocator;

import java.io.IOException;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;

/** One grid entry: an item (with its damage and NBT) or a fluid (with its NBT). */
public final class What {

    /** Empty for a fluid. */
    public final ItemStack item;
    /** Null for an item. */
    @Nullable
    public final FluidStack fluid;
    private final String key;

    private What(ItemStack item, @Nullable FluidStack fluid) {
        this.item = item;
        this.fluid = fluid;
        this.key = fluid != null ? "f|" + fluid.getFluid().getName() + "|" + fluid.tag
                : "i|" + item.getItem().getRegistryName() + "|" + item.getMetadata() + "|" + item.getTagCompound();
    }

    public static What of(ItemStack stack) {
        ItemStack one = stack.copy();
        one.setCount(1);
        return new What(one, null);
    }

    public static What of(FluidStack stack) {
        FluidStack one = stack.copy();
        one.amount = 1;
        return new What(ItemStack.EMPTY, one);
    }

    public boolean isFluid() {
        return fluid != null;
    }

    /** The localized name, for chat messages on the client. */
    public String name() {
        return fluid != null ? fluid.getLocalizedName() : item.getDisplayName();
    }

    /** True when `s` is this item, with the same damage and NBT. Client side too. */
    public boolean matchesItem(ItemStack s) {
        return fluid == null && ItemStack.areItemsEqual(item, s) && ItemStack.areItemStackTagsEqual(item, s);
    }

    boolean matches(ItemStack s) {
        return fluid == null && !s.isEmpty() && API.instance().getComparer().isEqualNoQuantity(item, s);
    }

    boolean matches(@Nullable FluidStack s) {
        return fluid != null && s != null && API.instance().getComparer().isEqual(fluid, s, IComparer.COMPARE_NBT);
    }

    /** Items are counted one by one, fluids in millibuckets. */
    String amount(long n) {
        return fluid != null ? String.format("%,d mB", n) : String.format("%,d", n);
    }

    void write(PacketBuffer buf) {
        buf.writeBoolean(fluid != null);
        if (fluid != null) buf.writeCompoundTag(fluid.writeToNBT(new NBTTagCompound()));
        else buf.writeItemStack(item);
    }

    @Nullable
    static What read(PacketBuffer buf) {
        boolean isFluid = buf.readBoolean();
        try {
            if (isFluid) {
                FluidStack f = FluidStack.loadFluidStackFromNBT(buf.readCompoundTag());
                return f == null ? null : of(f);
            }
            ItemStack i = buf.readItemStack();
            return i.isEmpty() ? null : of(i);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof What && ((What) o).key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
