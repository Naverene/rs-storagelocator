package com.claudecode.rsstoragelocator;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fluids.FluidStack;

import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;

/** One grid entry: an item or a fluid (one of the two is empty), compared with its NBT. */
public final class What {

    public final ItemStack item;
    public final FluidStack fluid;
    private final String key;

    private What(ItemStack item, FluidStack fluid) {
        this.item = item;
        this.fluid = fluid;
        this.key = isFluid() ? "f|" + fluid.getFluid().getRegistryName() + "|" + fluid.getTag()
                : "i|" + item.getItem().getRegistryName() + "|" + item.getTag();
    }

    public static What of(ItemStack stack) {
        ItemStack one = stack.copy();
        one.setCount(1);
        return new What(one, FluidStack.EMPTY);
    }

    public static What of(FluidStack stack) {
        FluidStack one = stack.copy();
        one.setAmount(1);
        return new What(ItemStack.EMPTY, one);
    }

    public boolean isFluid() {
        return !fluid.isEmpty();
    }

    public ITextComponent name() {
        return isFluid() ? fluid.getDisplayName() : item.getDisplayName();
    }

    /** True when `s` is this item, with the same NBT. Client side too. */
    public boolean matchesItem(ItemStack s) {
        return !isFluid() && ItemStack.areItemsEqual(item, s) && ItemStack.areItemStackTagsEqual(item, s);
    }

    boolean matches(ItemStack s) {
        return !isFluid() && !s.isEmpty() && API.instance().getComparer().isEqual(item, s, IComparer.COMPARE_NBT);
    }

    boolean matches(FluidStack s) {
        return isFluid() && !s.isEmpty() && API.instance().getComparer().isEqual(fluid, s, IComparer.COMPARE_NBT);
    }

    /** Items are counted one by one, fluids in millibuckets. */
    String amount(long n) {
        return isFluid() ? String.format("%,d mB", n) : String.format("%,d", n);
    }

    void write(PacketBuffer buf) {
        buf.writeBoolean(isFluid());
        if (isFluid()) buf.writeFluidStack(fluid);
        else buf.writeItemStack(item);
    }

    @Nullable
    static What read(PacketBuffer buf) {
        boolean isFluid = buf.readBoolean();
        try {
            if (isFluid) {
                FluidStack f = buf.readFluidStack();
                return f.isEmpty() ? null : of(f);
            }
            ItemStack i = buf.readItemStack();
            return i.isEmpty() ? null : of(i);
        } catch (RuntimeException e) {
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
