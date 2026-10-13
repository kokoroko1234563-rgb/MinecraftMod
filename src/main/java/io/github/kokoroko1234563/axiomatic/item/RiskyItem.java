package io.github.kokoroko1234563.axiomatic.item;

import java.util.List;
import java.util.function.Consumer;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.risk.Paradox;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A crafted part that may carry conjectures' risk (Circuit Board, Prime Core). */
public class RiskyItem extends Item {
    public RiskyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        Paradox.inventoryTick(stack, level, owner);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        List<String> deps = stack.get(ModRegistries.DEPENDS.get());
        if (deps != null && !deps.isEmpty()) {
            builder.accept(Component.translatable("tooltip.axiomatic.depends", deps.size())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
