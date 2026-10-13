package io.github.kokoroko1234563.axiomatic.gametest;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.item.CircuitData;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Proofs carried out by the real machines in a world, following the design doc: the opening
 * hour's first proof and the zero_add worked example, step for step.
 */
final class ProofGameTests {
    private ProofGameTests() {}

    private static final Term A = Term.var("a");
    private static final Term B = Term.var("b");
    private static final Term N = Term.var("n");

    private static MachineBlockEntity place(GameTestHelper h, int x, Block block) {
        BlockPos pos = new BlockPos(x, 1, 0);
        h.setBlock(pos, block);
        MachineBlockEntity m = h.getBlockEntity(pos, MachineBlockEntity.class);
        m.receiveEnergy(m.capacity());
        return m;
    }

    private static ItemStack circuit(Term t) {
        return MathItems.circuitStack(new CircuitData(t, t.toString()));
    }

    private static ItemStack variable(String name) {
        ItemStack stack = new ItemStack(ModRegistries.VARIABLE_ITEM.get());
        stack.set(ModRegistries.VARIABLE.get(), name);
        return stack;
    }

    private static ItemStack take(MachineBlockEntity m, int slot) {
        ItemStack out = m.getItem(slot).copy();
        m.setItem(slot, ItemStack.EMPTY);
        return out;
    }

    private static void clear(MachineBlockEntity m) {
        for (int i = 0; i < m.getContainerSize(); i++) {
            m.setItem(i, ItemStack.EMPTY);
        }
    }

    private static Runnable hasOutput(GameTestHelper h, MachineBlockEntity m, int slot) {
        return () -> h.assertTrue(!m.getItem(slot).isEmpty(), "waiting for output; status: " + m.status().getString());
    }

    private static StatementData data(ItemStack s) {
        return MathItems.statement(s);
    }

    /** Opening hour step 6: Even(Double(n)), by the Witness Bench with k := n. */
    static void doubleOutputsEvensByMachines(GameTestHelper h) {
        var level = h.getLevel();
        MachineBlockEntity drafting = place(h, 0, ModRegistries.DRAFTING_CHAMBER.get());
        MachineBlockEntity property = place(h, 1, ModRegistries.PROPERTY_BENCH.get());
        MachineBlockEntity witness = place(h, 2, ModRegistries.WITNESS_BENCH.get());
        var box = new Object() {
            ItemStack doubleDef;
        };
        h.startSequence()
                .thenExecute(() -> {
                    drafting.onButton(level, null, 0); // Define
                    ItemStack body = circuit(Term.app("add", N, N));
                    body.set(DataComponents.CUSTOM_NAME, Component.literal("Double"));
                    drafting.setItem(0, body);
                })
                .thenWaitUntil(hasOutput(h, drafting, 3))
                .thenExecute(() -> {
                    box.doubleDef = take(drafting, 3);
                    property.setItem(0, circuit(Term.app("Double", N))); // property defaults to Even
                })
                .thenWaitUntil(hasOutput(h, property, 3))
                .thenExecute(() -> {
                    witness.setItem(0, take(property, 3));
                    witness.setItem(1, variable("n"));
                    witness.setItem(2, box.doubleDef);
                })
                .thenWaitUntil(hasOutput(h, witness, 3))
                .thenExecute(() -> {
                    StatementData result = data(witness.getItem(3));
                    h.assertTrue(result.proven() && result.hypotheses().isEmpty(), "should be proven outright");
                    h.assertTrue(Syntax.alphaEquals(result.formula(), Formula.pred("Even", Term.app("Double", N))),
                            "got " + result.display());
                })
                .thenSucceed();
    }

    /** The design doc's worked example: 0 + n = n in six machine operations, then named. */
    static void zeroAddByMachines(GameTestHelper h) {
        var level = h.getLevel();
        MachineBlockEntity drafting = place(h, 0, ModRegistries.DRAFTING_CHAMBER.get());
        MachineBlockEntity equation = place(h, 1, ModRegistries.EQUATION_BENCH.get());
        MachineBlockEntity inst = place(h, 2, ModRegistries.INSTANTIATOR.get());
        MachineBlockEntity chamber = place(h, 3, ModRegistries.ASSUMPTION_CHAMBER.get());
        MachineBlockEntity rewriter = place(h, 4, ModRegistries.REWRITER.get());
        MachineBlockEntity induction = place(h, 5, ModRegistries.INDUCTION_ENGINE.get());
        var box = new Object() {
            ItemStack addZero, addSucc, base, hyp, partial, step;
        };
        h.startSequence()
                // The Adder's defining equations, looked up from the starter definition.
                .thenExecute(() -> {
                    drafting.onButton(level, null, 0); // Define
                    drafting.setItem(0, circuit(Term.app("add", A, B)));
                })
                .thenWaitUntil(hasOutput(h, drafting, 4))
                .thenExecute(() -> {
                    box.addZero = take(drafting, 3);
                    box.addSucc = take(drafting, 4);
                    clear(drafting);
                    // Step 1: a + 0 = a with a := 0.
                    inst.setItem(0, box.addZero.copy());
                    inst.setItem(1, variable("a"));
                    inst.setItem(2, NumeralItem.stack(NumeralData.of(0), 1));
                })
                .thenWaitUntil(hasOutput(h, inst, 3))
                .thenExecute(() -> {
                    box.base = take(inst, 3);
                    clear(inst);
                    equation.setItem(0, circuit(Term.app("add", Term.zero(), N)));
                    equation.setItem(1, variable("n"));
                })
                .thenWaitUntil(hasOutput(h, equation, 2))
                // Step 2: assume 0 + n = n.
                .thenExecute(() -> chamber.setItem(0, take(equation, 2)))
                .thenWaitUntil(hasOutput(h, chamber, 2))
                .thenExecute(() -> {
                    box.hyp = take(chamber, 2);
                    // Step 3a: a + S(b) = S(a + b) with a := 0.
                    inst.setItem(0, box.addSucc.copy());
                    inst.setItem(1, variable("a"));
                    inst.setItem(2, NumeralItem.stack(NumeralData.of(0), 1));
                })
                .thenWaitUntil(hasOutput(h, inst, 3))
                .thenExecute(() -> {
                    box.partial = take(inst, 3);
                    clear(inst);
                    // Step 3b: then b := n.
                    inst.setItem(0, box.partial);
                    inst.setItem(1, variable("b"));
                    inst.setItem(2, variable("n"));
                })
                .thenWaitUntil(hasOutput(h, inst, 3))
                .thenExecute(() -> {
                    // Step 4: rewrite with the hypothesis.
                    rewriter.setItem(0, take(inst, 3));
                    rewriter.setItem(1, box.hyp);
                })
                .thenWaitUntil(hasOutput(h, rewriter, 2))
                // Step 5: discharge.
                .thenExecute(() -> chamber.setItem(1, take(rewriter, 2)))
                .thenWaitUntil(hasOutput(h, chamber, 2))
                .thenExecute(() -> {
                    box.step = take(chamber, 2);
                    // Step 6: induction on n.
                    induction.setItem(0, box.base);
                    induction.setItem(1, box.step);
                    induction.setItem(2, variable("n"));
                })
                .thenWaitUntil(hasOutput(h, induction, 3))
                .thenExecute(() -> {
                    StatementData result = data(induction.getItem(3));
                    h.assertTrue(result.proven() && result.hypotheses().isEmpty(), "should be proven outright");
                    h.assertTrue(Syntax.alphaEquals(result.formula(), Formula.eq(Term.app("add", Term.zero(), N), N)),
                            "got " + result.display());
                    // Compile and name it.
                    ItemStack named = take(induction, 3);
                    named.set(DataComponents.CUSTOM_NAME, Component.literal("zero_add"));
                    clear(drafting);
                    drafting.onButton(level, null, 0); // Name
                    drafting.setItem(0, named);
                })
                .thenWaitUntil(hasOutput(h, drafting, 3))
                .thenExecute(() -> h.assertTrue(
                        AxiomaticData.get(level.getServer()).state().library().theorem("zero_add") != null,
                        "zero_add should be in the Library"))
                .thenSucceed();
    }
}
