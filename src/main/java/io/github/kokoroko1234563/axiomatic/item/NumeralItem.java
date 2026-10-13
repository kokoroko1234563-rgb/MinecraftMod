package io.github.kokoroko1234563.axiomatic.item;

import java.util.function.Consumer;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A natural number as an item. Its value and certificates are in the {@link NumeralData} component. */
public class NumeralItem extends Item {
    public NumeralItem(Properties properties) {
        super(properties);
    }

    public static NumeralData data(ItemStack stack) {
        return stack.getOrDefault(ModRegistries.NUMERAL.get(), NumeralData.ZERO);
    }

    public static ItemStack stack(NumeralData data, int count) {
        ItemStack stack = new ItemStack(ModRegistries.NUMERAL_ITEM.get(), count);
        stack.set(ModRegistries.NUMERAL.get(), data);
        return stack;
    }

    /** Short display of a possibly huge value: 123456…789 (40 digits). */
    public static String display(java.math.BigInteger value) {
        String s = value.toString();
        return s.length() <= 24 ? s : s.substring(0, 8) + "…" + s.substring(s.length() - 6) + " (" + s.length() + " digits)";
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.axiomatic.numeral.value", display(data(stack).value()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        data(stack).addToTooltip(context, builder, flag, stack);
    }
}
