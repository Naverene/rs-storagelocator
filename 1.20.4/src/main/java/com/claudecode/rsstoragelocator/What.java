package com.claudecode.rsstoragelocator;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.API;

/** One grid entry: an item or a fluid (one of the two is empty), compared with its NBT. */
public final class What {

    public final ItemStack item;
    public final FluidStack fluid;
    private final String key;

    private What(ItemStack item, FluidStack fluid) {
        this.item = item;
        this.fluid = fluid;
        this.key = isFluid() ? "f|" + fluid.getFluid() + "|" + fluid.getTag()
                : "i|" + item.getItem() + "|" + item.getTag();
    }

    public static What of(ItemStack stack) {
        return new What(stack.copyWithCount(1), FluidStack.EMPTY);
    }

    public static What of(FluidStack stack) {
        FluidStack one = stack.copy();
        one.setAmount(1);
        return new What(ItemStack.EMPTY, one);
    }

    public boolean isFluid() {
        return !fluid.isEmpty();
    }

    public Component name() {
        return isFluid() ? fluid.getDisplayName() : item.getHoverName();
    }

    /** True when `s` is this item, with the same NBT. Client side too. */
    public boolean matchesItem(ItemStack s) {
        return !isFluid() && ItemStack.isSameItemSameTags(item, s);
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

    void write(FriendlyByteBuf buf) {
        buf.writeBoolean(isFluid());
        if (isFluid()) buf.writeFluidStack(fluid);
        else buf.writeItem(item);
    }

    @Nullable
    static What read(FriendlyByteBuf buf) {
        boolean isFluid = buf.readBoolean();
        try {
            if (isFluid) {
                FluidStack f = buf.readFluidStack();
                return f.isEmpty() ? null : of(f);
            }
            ItemStack i = buf.readItem();
            return i.isEmpty() ? null : of(i);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof What w && w.key.equals(key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
