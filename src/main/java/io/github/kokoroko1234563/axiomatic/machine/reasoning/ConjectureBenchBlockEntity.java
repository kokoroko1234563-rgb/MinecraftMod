package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Conjecture Bench: turns an unproven statement, renamed in an anvil, into a conjecture for a
 * small fee. Out come the conjectural statement (usable like a theorem card) and the conjecture
 * block that must be placed and powered to keep it working.
 */
public class ConjectureBenchBlockEntity extends ReasoningBlockEntity {
    public static final long FEE = 200;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.conjecture_statement"),
            MachineLayout.output(116, 31, null), MachineLayout.output(134, 31, null)), List.of());

    public ConjectureBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CONJECTURE_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(0);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0);
    }

    @Override
    protected long cost(Context ctx) {
        return FEE;
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        ItemStack in = items.get(0);
        StatementData st = MathItems.statement(in);
        if (st == null || st.proven()) {
            throw new ProofException(Component.translatable("status.axiomatic.conjecture_needs_unproven").getString());
        }
        Component custom = in.getCustomName();
        String name = custom == null ? null : custom.getString().trim();
        if (name == null || !Syntax.isValidName(name)) {
            throw new ProofException(Component.translatable("status.axiomatic.needs_name").getString());
        }
        if (!outputsFit(List.of(new ItemStack(ModRegistries.STATEMENT_ITEM.get()),
                new ItemStack(ModRegistries.CONJECTURE_BLOCK.get())))) {
            return List.of();
        }
        Theorem thm = ctx.state().library().conjecture(ctx.state().kernel(), ctx.state().deciders(), name, st.formula());
        ItemStack card = ctx.proven(thm);
        ItemStack block = new ItemStack(ModRegistries.CONJECTURE_BLOCK.get());
        block.set(ModRegistries.CONJECTURE.get(), name);
        block.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.translatable("item.axiomatic.conjecture_block.named", name));
        return List.of(card, block);
    }
}
