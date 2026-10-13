package io.github.kokoroko1234563.axiomatic.gametest;

import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Structures;
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

/** The Structure Bench: certifying (add, 0) as a monoid and applying a generic theorem to it. */
final class StructureGameTests {
    private StructureGameTests() {}

    private static final Term A = Term.var("a");
    private static final Term B = Term.var("b");
    private static final Term C = Term.var("c");
    private static final Term X = Term.var("x");
    private static final Term N = Term.var("n");

    /** The three monoid laws for (add, zc), proven from Peano by the kernel. */
    static List<Theorem> addLaws(Kernel k) {
        Theorem addZero = k.definingStatements("add").get(0);
        Theorem addSucc = k.definingStatements("add").get(1);
        Term ab = Term.app("add", A, B);
        Theorem base = k.rewrite(k.instantiate(addZero, "a", ab), k.instantiate(addZero, "a", B), true, 1);
        Theorem hyp = k.assume(Formula.eq(Term.app("add", ab, C), Term.app("add", A, Term.app("add", B, C))));
        Theorem s = k.rewrite(k.instantiate(addSucc, Map.of("a", ab, "b", C)), hyp, false, -1);
        s = k.rewrite(s, k.instantiate(addSucc, Map.of("a", A, "b", Term.app("add", B, C))), true, -1);
        s = k.rewrite(s, k.instantiate(addSucc, Map.of("a", B, "b", C)), true, -1);
        Theorem assoc = k.induction(base, k.discharge(s, hyp.hypotheses().get(0)), "c");

        Theorem zdef = k.definingStatements("zc").get(0);
        Theorem zh = k.assume(Formula.eq(Term.app("add", Term.zero(), A), A));
        Theorem zs = k.rewrite(k.instantiate(addSucc, Map.of("a", Term.zero(), "b", A)), zh, false, -1);
        Theorem zeroAdd = k.induction(k.instantiate(addZero, "a", Term.zero()), k.discharge(zs, zh.hypotheses().get(0)), "a");
        return List.of(assoc, k.rewrite(zeroAdd, zdef, true, 0), k.rewrite(addZero, zdef, true, 0));
    }

    private static synchronized void setUp(MathState state) {
        Kernel k = state.kernel();
        if (state.signature().get("zc") == null) {
            k.define(new Definition.Explicit("zc", List.of(), Term.zero(), null));
        }
        k.structure(Structures.MONOID);
        if (state.signature().get("gpow") == null) {
            k.define(new Definition.PrimitiveRecursive("gpow", List.of("x", "n"), new Term.App("Monoid.e", List.of()),
                    Term.app("Monoid.op", Term.app("gpow", X, N), X), null));
        }
    }

    static void certifyAndApply(GameTestHelper h) {
        MathState state = AxiomaticData.get(h.getLevel().getServer()).state();
        setUp(state);
        Kernel k = state.kernel();
        Printer printer = new Printer(state.signature());
        // gpow(x, 1) = x for any monoid.
        List<Theorem> pow = k.definingStatements("gpow");
        Theorem generic = k.rewrite(k.rewrite(k.instantiate(pow.get(1), "n", Term.zero()), pow.get(0), false, -1),
                k.instantiate(k.structureAxiom(Structures.MONOID, 1), "a", X), false, -1);

        BlockPos pos = new BlockPos(2, 1, 4);
        h.setBlock(pos, ModRegistries.STRUCTURE_BENCH.get());
        MachineBlockEntity bench = h.getBlockEntity(pos, MachineBlockEntity.class);
        bench.receiveEnergy(bench.capacity());
        var level = h.getLevel();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!bench.getItem(3).isEmpty(), "Axioms mode should show axiom 1"))
                .thenExecute(() -> {
                    bench.setItem(3, net.minecraft.world.item.ItemStack.EMPTY);
                    bench.onButton(level, null, 0); // Certify
                    List<Theorem> laws = addLaws(k);
                    for (int i = 0; i < 3; i++) {
                        bench.setItem(i, MathItems.statementStack(StatementData.of(laws.get(i), printer)));
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(state.library().certificates().stream()
                        .anyMatch(c -> "add".equals(c.mapping().get("Monoid.op"))), "waiting for the certificate; status: "
                        + bench.status().getString()))
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        bench.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    bench.onButton(level, null, 0); // Apply
                    bench.setItem(0, MathItems.statementStack(StatementData.of(generic, printer)));
                    Term design = Term.app("add", A, B);
                    bench.setItem(1, MathItems.circuitStack(new CircuitData(design, "a + b")));
                })
                .thenWaitUntil(() -> h.assertTrue(!bench.getItem(3).isEmpty(), "waiting for the applied theorem; status: "
                        + bench.status().getString()))
                .thenExecute(() -> {
                    StatementData out = MathItems.statement(bench.getItem(3));
                    h.assertTrue(out.proven() && out.dependencies().stream().noneMatch(d -> d.startsWith("structure:")),
                            "applied theorem rests on add's laws, not on the axioms");
                    h.assertTrue(out.formula().toString().contains("gpow⟨"), "got " + out.display());
                })
                .thenSucceed();
    }
}
