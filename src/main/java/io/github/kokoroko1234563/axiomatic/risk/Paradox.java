package io.github.kokoroko1234563.axiomatic.risk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The cascade: anything built on a refuted conjecture fails with it. Items remember the
 * conjectures they depend on (certificates, statements, and parts crafted from conjectural
 * certificates); once one is refuted they turn into paradox junk.
 */
public final class Paradox {
    private Paradox() {}

    /** The conjectures an item depends on. */
    public static Set<String> conjecturesOf(ItemStack stack) {
        Set<String> deps = new HashSet<>();
        if (stack.is(ModRegistries.NUMERAL_ITEM.get())) {
            for (Fact f : NumeralItem.data(stack).facts()) {
                deps.addAll(f.dependencies());
            }
        }
        StatementData st = MathItems.statement(stack);
        if (st != null) {
            deps.addAll(st.dependencies());
        }
        List<String> crafted = stack.get(ModRegistries.DEPENDS.get());
        if (crafted != null) {
            deps.addAll(crafted);
        }
        deps.removeIf(d -> !d.startsWith("conjecture:"));
        return deps;
    }

    /** Whether the item depends on a refuted conjecture. */
    public static boolean doomed(ServerLevel level, ItemStack stack) {
        Set<String> deps = conjecturesOf(stack);
        return !deps.isEmpty() && !AxiomaticData.get(level.getServer()).state().library().refuted(deps).isEmpty();
    }

    /** Called from the items' inventory tick: a doomed item in a player's inventory turns to junk. */
    public static void inventoryTick(ItemStack stack, ServerLevel level, Entity owner) {
        if (level.getGameTime() % 20 != 0 || !(owner instanceof Player player) || !doomed(level, stack)) {
            return;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) {
                inv.setItem(i, new ItemStack(ModRegistries.PARADOX_JUNK.get(), stack.getCount()));
                player.sendSystemMessage(Component.translatable("message.axiomatic.junk", stack.getHoverName()));
                return;
            }
        }
    }

    /** Parts crafted from conjectural certificates carry the conjectures' risk. */
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Set<String> deps = new HashSet<>();
        var grid = event.getInventory();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            deps.addAll(conjecturesOf(grid.getItem(i)));
        }
        if (!deps.isEmpty()) {
            event.getCrafting().set(ModRegistries.DEPENDS.get(), deps.stream().sorted().toList());
        }
    }
}
