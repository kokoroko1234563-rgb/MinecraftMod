package io.github.kokoroko1234563.axiomatic.item;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;

import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** The items that carry mathematics: statements, circuits, variables and axiom tablets. */
public final class MathItems {
    private MathItems() {}

    /** Largest numeral a machine will turn into a unary circuit (S(S(…0))). */
    public static final int MAX_NUMERAL_IN_CIRCUIT = 64;

    /**
     * The circuit an item stands for: a circuit item's term, a Variable block's variable, or a
     * small numeral. Returns null for anything else.
     */
    public static Term termOf(ItemStack stack) {
        CircuitData circuit = stack.get(ModRegistries.CIRCUIT.get());
        if (circuit != null) {
            return circuit.term();
        }
        if (stack.is(ModRegistries.VARIABLE_ITEM.get())) {
            return Term.var(VariableItem.name(stack));
        }
        if (stack.is(ModRegistries.NUMERAL_ITEM.get())) {
            BigInteger v = NumeralItem.data(stack).value();
            if (v.compareTo(BigInteger.valueOf(MAX_NUMERAL_IN_CIRCUIT)) <= 0) {
                return Term.numeral(v.intValue());
            }
        }
        return null;
    }

    public static StatementData statement(ItemStack stack) {
        return stack.get(ModRegistries.STATEMENT.get());
    }

    public static ItemStack statementStack(StatementData data) {
        ItemStack stack = new ItemStack(ModRegistries.STATEMENT_ITEM.get());
        stack.set(ModRegistries.STATEMENT.get(), data);
        return stack;
    }

    public static ItemStack circuitStack(CircuitData data) {
        ItemStack stack = new ItemStack(ModRegistries.CIRCUIT_ITEM.get());
        stack.set(ModRegistries.CIRCUIT.get(), data);
        return stack;
    }

    /** A statement: unproven (a claim) or proven (a theorem). */
    public static class StatementItem extends Item {
        public StatementItem(Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            StatementData data = statement(stack);
            if (data == null) {
                return super.getName(stack);
            }
            String key = !data.proven() ? "item.axiomatic.statement.unproven"
                    : data.hypotheses().isEmpty() ? "item.axiomatic.statement.proven" : "item.axiomatic.statement.stamped";
            return Component.translatable(key);
        }

        @Override
        public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot slot) {
            io.github.kokoroko1234563.axiomatic.risk.Paradox.inventoryTick(stack, level, owner);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            StatementData data = statement(stack);
            return data != null && data.proven() && data.hypotheses().isEmpty();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> builder, TooltipFlag flag) {
            StatementData data = statement(stack);
            if (data != null) {
                data.addToTooltip(builder);
            }
        }
    }

    /** A circuit: a term built in the Drafting Chamber. */
    public static class CircuitItem extends Item {
        public CircuitItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> builder, TooltipFlag flag) {
            CircuitData data = stack.get(ModRegistries.CIRCUIT.get());
            if (data != null) {
                builder.accept(Component.literal(data.display()).withStyle(ChatFormatting.WHITE));
            }
        }
    }

    /** A Variable block: stands for any number. Use it in the air to change its name. */
    public static class VariableItem extends Item {
        public static final List<String> NAMES = List.of("n", "a", "b", "c", "d", "k", "m", "x", "y", "z");

        public VariableItem(Properties properties) {
            super(properties);
        }

        public static String name(ItemStack stack) {
            return stack.getOrDefault(ModRegistries.VARIABLE.get(), "n");
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            int i = NAMES.indexOf(name(stack));
            int next = Math.floorMod(i + (player.isSecondaryUseActive() ? -1 : 1), NAMES.size());
            stack.set(ModRegistries.VARIABLE.get(), NAMES.get(next));
            return InteractionResult.SUCCESS;
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable("item.axiomatic.variable.named", name(stack));
        }
    }

    /** Axiom Tablet: supplies Peano axiom 3 or 4 as a proven statement in the Drafting Chamber. */
    public static class AxiomTabletItem extends Item {
        private final String axiom;

        public AxiomTabletItem(Properties properties, String axiom) {
            super(properties);
            this.axiom = axiom;
        }

        public String axiom() {
            return axiom;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> builder, TooltipFlag flag) {
            builder.accept(Component.translatable("tooltip.axiomatic.axiom." + axiom).withStyle(ChatFormatting.GRAY));
        }
    }
}
