package io.github.kokoroko1234563.axiomatic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.CircuitData;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Opening hour step 8, by machines: even + even = even, with nested Assumption Chambers, ∃
 * opened as named variables, pattern rewrites and the Witness Bench. Associativity and
 * commutativity of + are supplied (their own proofs are tested elsewhere).
 */
final class EvenAddGameTest {
    private EvenAddGameTest() {}

    private static final Term A = Term.var("a");
    private static final Term B = Term.var("b");
    private static final Term J = Term.var("j");
    private static final Term K = Term.var("k");

    private static Term add(Term x, Term y) {
        return Term.app("add", x, y);
    }

    /** One machine run: set inputs, wait for an output, hand it on. */
    private record Step(MachineBlockEntity machine, Runnable setup, int outSlot, Consumer<ItemStack> take) {}

    static void evenAdd(GameTestHelper h) {
        MathState state = AxiomaticData.get(h.getLevel().getServer()).state();
        Kernel k = state.kernel();
        Printer printer = new Printer(state.signature());
        var level = h.getLevel();

        // Supplied lemmas: associativity (proven) and commutativity (proven) of +.
        Theorem assoc = assocProof(k);
        Theorem comm = commProof(k);

        int[] x = {0};
        java.util.function.Function<Block, MachineBlockEntity> place = block -> {
            BlockPos pos = new BlockPos(x[0] % 7, 1 + x[0] / 7, 6);
            x[0]++;
            h.setBlock(pos, block);
            MachineBlockEntity m = h.getBlockEntity(pos, MachineBlockEntity.class);
            m.receiveEnergy(m.capacity());
            return m;
        };
        MachineBlockEntity property = place.apply(ModRegistries.PROPERTY_BENCH.get());
        MachineBlockEntity drafting = place.apply(ModRegistries.DRAFTING_CHAMBER.get());
        MachineBlockEntity inst = place.apply(ModRegistries.INSTANTIATOR.get());
        MachineBlockEntity mp = place.apply(ModRegistries.MODUS_PONENS_PRESS.get());
        MachineBlockEntity chA = place.apply(ModRegistries.ASSUMPTION_CHAMBER.get());
        MachineBlockEntity chB = place.apply(ModRegistries.ASSUMPTION_CHAMBER.get());
        MachineBlockEntity openA = place.apply(ModRegistries.ASSUMPTION_CHAMBER.get());
        MachineBlockEntity openB = place.apply(ModRegistries.ASSUMPTION_CHAMBER.get());
        MachineBlockEntity equation = place.apply(ModRegistries.EQUATION_BENCH.get());
        MachineBlockEntity rewriter = place.apply(ModRegistries.REWRITER.get());
        MachineBlockEntity witness = place.apply(ModRegistries.WITNESS_BENCH.get());

        Box s = new Box();
        List<Step> steps = new ArrayList<>();
        // Claims Even(a), Even(b) and the goal Even(a + b).
        steps.add(new Step(property, () -> property.setItem(0, circuit(A, printer)), 3, i -> s.evenA = i));
        steps.add(new Step(property, () -> property.setItem(0, circuit(B, printer)), 3, i -> s.evenB = i));
        steps.add(new Step(property, () -> property.setItem(0, circuit(add(A, B), printer)), 3, i -> s.goal = i));
        // Assume them in two chambers.
        steps.add(new Step(chA, () -> chA.setItem(0, s.evenA.copy()), 2, i -> s.hypA = i));
        steps.add(new Step(chB, () -> chB.setItem(0, s.evenB.copy()), 2, i -> s.hypB = i));
        // Even's defining statement Even(n) → ∃k. n = k + k, looked up in the Drafting Chamber.
        steps.add(new Step(drafting, () -> {
            drafting.onButton(level, null, 0); // Define
            drafting.setItem(0, s.evenA.copy());
        }, 3, i -> s.unfold = i));
        // Unfold the hypotheses: instantiate n := a, then modus ponens with the stamped hypothesis.
        steps.add(new Step(inst, () -> {
            inst.setItem(0, s.unfold.copy());
            inst.setItem(1, variable("n"));
            inst.setItem(2, variable("a"));
        }, 3, i -> mp.setItem(0, i)));
        steps.add(new Step(mp, () -> mp.setItem(1, s.hypA.copy()), 2, i -> s.exA = i));
        steps.add(new Step(inst, () -> {
            inst.setItem(0, s.unfold.copy());
            inst.setItem(2, variable("b"));
        }, 3, i -> mp.setItem(0, i)));
        steps.add(new Step(mp, () -> mp.setItem(1, s.hypB.copy()), 2, i -> s.exB = i));
        // Open the two ∃ as j and k.
        steps.add(new Step(openA, () -> {
            openA.setItem(4, variable("j"));
            openA.setItem(0, s.exA);
        }, 2, i -> s.aIsJJ = i));
        steps.add(new Step(openB, () -> {
            openB.setItem(4, variable("k"));
            openB.setItem(0, s.exB);
        }, 2, i -> s.bIsKK = i));
        // a + b = a + b, then rewrite a and b on the right.
        steps.add(new Step(equation, () -> {
            equation.setItem(0, circuit(add(A, B), printer));
            equation.setItem(1, circuit(add(A, B), printer));
        }, 2, i -> s.eqn = i));
        rewrite(steps, rewriter, s, () -> s.aIsJJ, 1, false);   // a + b = (j + j) + b
        rewrite(steps, rewriter, s, () -> s.bIsKK, 1, false);   // a + b = (j + j) + (k + k)
        ItemStack assocItem = MathItems.statementStack(StatementData.of(assoc, printer));
        ItemStack commItem = MathItems.statementStack(StatementData.of(comm, printer));
        rewrite(steps, rewriter, s, () -> assocItem, 0, false);   // = j + (j + (k + k))
        rewrite(steps, rewriter, s, () -> assocItem, 1, true);    // = j + ((j + k) + k)
        rewrite(steps, rewriter, s, () -> commItem, 2, false);    // = j + (k + (j + k))
        rewrite(steps, rewriter, s, () -> assocItem, 0, true);    // = (j + k) + (j + k)
        // Witness Bench: Even(a + b) with k := j + k.
        steps.add(new Step(witness, () -> {
            witness.setItem(0, s.goal);
            witness.setItem(1, circuit(add(J, K), printer));
            witness.setItem(2, s.eqn);
        }, 3, i -> s.proof = i));
        // Close the openings, then discharge the assumptions, innermost first.
        steps.add(new Step(openB, () -> openB.setItem(1, s.proof), 2, i -> s.proof = i));
        steps.add(new Step(openA, () -> openA.setItem(1, s.proof), 2, i -> s.proof = i));
        steps.add(new Step(chB, () -> chB.setItem(1, s.proof), 2, i -> s.proof = i));
        steps.add(new Step(chA, () -> chA.setItem(1, s.proof), 2, i -> s.proof = i));

        GameTestSequence seq = h.startSequence();
        for (Step step : steps) {
            seq = seq.thenExecute(step.setup())
                    .thenWaitUntil(() -> h.assertTrue(!step.machine().getItem(step.outSlot()).isEmpty(),
                            "waiting at " + step.machine().getBlockState().getBlock().getName().getString() + ": "
                                    + step.machine().status().getString()))
                    .thenExecute(() -> {
                        ItemStack out = step.machine().getItem(step.outSlot()).copy();
                        step.machine().setItem(step.outSlot(), ItemStack.EMPTY);
                        step.take().accept(out);
                    });
        }
        seq.thenExecute(() -> {
            StatementData result = MathItems.statement(s.proof);
            Formula expected = Formula.implies(Formula.pred("Even", A),
                    Formula.implies(Formula.pred("Even", B), Formula.pred("Even", add(A, B))));
            h.assertTrue(result.proven() && result.hypotheses().isEmpty(), "should be proven outright: " + result.display());
            h.assertTrue(Syntax.alphaEquals(result.formula(), expected), "got " + result.display());
        }).thenSucceed();
    }

    /** The items passed between steps. */
    private static final class Box {
        ItemStack evenA, evenB, hypA, hypB, unfold, exA, exB, aIsJJ, bIsKK, eqn, goal, proof;
    }

    /** A Rewriter run on the running equation {@code s.eqn}. */
    private static void rewrite(List<Step> steps, MachineBlockEntity rewriter, Box s,
            java.util.function.Supplier<ItemStack> equation, int occurrence, boolean rightToLeft) {
        steps.add(new Step(rewriter, () -> {
            setRewriter(rewriter, occurrence, rightToLeft);
            rewriter.setItem(0, s.eqn);
            rewriter.setItem(1, equation.get().copy());
        }, 2, i -> s.eqn = i));
    }

    /** Sets the Rewriter's direction and occurrence through its buttons. */
    private static void setRewriter(MachineBlockEntity rewriter, int occurrence, boolean rightToLeft) {
        var tag = rewriter.saveWithoutMetadata(rewriter.getLevel().registryAccess());
        boolean rtl = tag.getBooleanOr("right_to_left", false);
        int occ = tag.getIntOr("occurrence", -1);
        var level = (net.minecraft.server.level.ServerLevel) rewriter.getLevel();
        if (rtl != rightToLeft) {
            rewriter.onButton(level, null, 0);
        }
        while (occ != occurrence) {
            rewriter.onButton(level, null, 1);
            occ = occ >= 7 ? -1 : occ + 1;
        }
    }

    private static ItemStack circuit(Term t, Printer printer) {
        return MathItems.circuitStack(new CircuitData(t, printer.term(t)));
    }

    private static ItemStack variable(String name) {
        ItemStack stack = new ItemStack(ModRegistries.VARIABLE_ITEM.get());
        stack.set(ModRegistries.VARIABLE.get(), name);
        return stack;
    }

    private static Theorem assocProof(Kernel k) {
        Term c = Term.var("c");
        Theorem addZero = k.definingStatements("add").get(0);
        Theorem addSucc = k.definingStatements("add").get(1);
        Term ab = add(A, B);
        Theorem base = k.rewrite(k.instantiate(addZero, "a", ab), k.instantiate(addZero, "a", B), true, 1);
        Theorem hyp = k.assume(Formula.eq(add(ab, c), add(A, add(B, c))));
        Theorem s = k.rewrite(k.instantiate(addSucc, Map.of("a", ab, "b", c)), hyp, false, -1);
        s = k.rewrite(s, k.instantiate(addSucc, Map.of("a", A, "b", add(B, c))), true, -1);
        s = k.rewrite(s, k.instantiate(addSucc, Map.of("a", B, "b", c)), true, -1);
        return k.induction(base, k.discharge(s, hyp.hypotheses().get(0)), "c");
    }

    private static Theorem commProof(Kernel k) {
        Term n = Term.var("n");
        Theorem addZero = k.definingStatements("add").get(0);
        Theorem addSucc = k.definingStatements("add").get(1);
        // 0 + n = n
        Theorem zh = k.assume(Formula.eq(add(Term.zero(), n), n));
        Theorem zs = k.rewrite(k.instantiate(addSucc, Map.of("a", Term.zero(), "b", n)), zh, false, -1);
        Theorem zeroAdd = k.induction(k.instantiate(addZero, "a", Term.zero()), k.discharge(zs, zh.hypotheses().get(0)), "n");
        // S(a) + b = S(a + b)
        Theorem sb = k.rewrite(k.instantiate(addZero, "a", Term.succ(A)), addZero, true, 1);
        Theorem sh = k.assume(Formula.eq(add(Term.succ(A), B), Term.succ(add(A, B))));
        Theorem ss = k.instantiate(addSucc, "a", Term.succ(A));
        ss = k.rewrite(ss, sh, false, -1);
        ss = k.rewrite(ss, addSucc, true, -1);
        Theorem succAdd = k.induction(sb, k.discharge(ss, sh.hypotheses().get(0)), "b");
        // a + b = b + a
        Theorem base = k.rewrite(addZero, k.instantiate(zeroAdd, "n", A), true, 1);
        Theorem hyp = k.assume(Formula.eq(add(A, B), add(B, A)));
        Theorem s = k.rewrite(addSucc, hyp, false, -1);
        s = k.rewrite(s, k.instantiate(succAdd, Map.of("a", B, "b", A)), true, -1);
        return k.induction(base, k.discharge(s, hyp.hypotheses().get(0)), "b");
    }
}
