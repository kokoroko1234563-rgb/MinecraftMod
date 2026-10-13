package io.github.kokoroko1234563.axiomatic.gametest;

import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Structures;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The Rewriter with a certified equivalence: Same(a, b) := a = b is certified an equivalence, a
 * congruence law for S is named in the Library, and the Rewriter rewrites under S with it.
 */
final class EquivalenceGameTest {
    private EquivalenceGameTest() {}

    private static final String SAME = "Same";
    private static final Term A = Term.var("a");
    private static final Term B = Term.var("b");
    private static final Term C = Term.var("c");

    private static Formula same(Term x, Term y) {
        return Formula.pred(SAME, x, y);
    }

    /** From x = y, Same(x, y). */
    private static Theorem toSame(Kernel k, Theorem eq) {
        Formula.Eq e = (Formula.Eq) eq.formula();
        Theorem intro = k.instantiate(k.definingStatements(SAME).get(1), Map.of("a", e.left(), "b", e.right()));
        return k.modusPonens(intro, eq);
    }

    /** From Same(x, y), x = y. */
    private static Theorem fromSame(Kernel k, Theorem s) {
        Formula.Pred p = (Formula.Pred) s.formula();
        Theorem elim = k.instantiate(k.definingStatements(SAME).get(0), Map.of("a", p.args().get(0), "b", p.args().get(1)));
        return k.modusPonens(elim, s);
    }

    /** Reflexivity, symmetry and transitivity of Same, then the congruence law for S. */
    static List<Theorem> laws(Kernel k) {
        Theorem refl = toSame(k, k.reflexivity(A));
        Theorem hab = k.assume(same(A, B));
        Theorem ab = fromSame(k, hab);                                             // a = b
        Theorem ba = k.rewrite(k.reflexivity(A), ab, false, 0);                    // b = a
        Theorem sym = k.discharge(toSame(k, ba), hab.hypotheses().get(0));
        Theorem hab2 = k.assume(same(A, B));
        Theorem hbc = k.assume(same(B, C));
        Theorem ac = k.rewrite(fromSame(k, hab2), fromSame(k, hbc), false, 0);    // a = c
        Theorem trans = k.discharge(k.discharge(toSame(k, ac), hbc.hypotheses().get(0)), hab2.hypotheses().get(0));
        Theorem hs = k.assume(same(A, B));
        Theorem sab = k.rewrite(k.reflexivity(Term.succ(A)), fromSame(k, hs), false, 1);  // S(a) = S(b)
        Theorem congS = k.discharge(toSame(k, sab), hs.hypotheses().get(0));
        return List.of(refl, sym, trans, congS);
    }

    private static synchronized void setUp(MathState state) {
        Kernel k = state.kernel();
        if (state.signature().get(SAME) == null) {
            k.define(new Definition.Predicate(SAME, List.of("a", "b"), Formula.eq(A, B), null));
        }
        List<Theorem> laws = laws(k);
        state.library().certifyStructure(k, Structures.EQUIVALENCE, laws.subList(0, 3));
        if (state.library().theorem("sameSucc") == null) {
            state.library().name("sameSucc", laws.get(3));
        }
    }

    static void rewritesUnderSuccessor(GameTestHelper h) {
        MathState state = AxiomaticData.get(h.getLevel().getServer()).state();
        setUp(state);
        Kernel k = state.kernel();
        Printer printer = new Printer(state.signature());
        Term one = Term.succ(Term.zero());
        Term onePlusZero = Term.app("add", one, Term.zero());
        // Same(1 + 0, 1), from 1 + 0 = 1.
        Theorem eqv = toSame(k, k.instantiate(k.definingStatements("add").get(0), "a", one));
        // S(1 + 0) = S(1 + 0), rewritten on its left: Same(S(1), S(1 + 0)).
        Theorem target = k.reflexivity(Term.succ(onePlusZero));

        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModRegistries.REWRITER.get());
        MachineBlockEntity rewriter = h.getBlockEntity(pos, MachineBlockEntity.class);
        rewriter.receiveEnergy(rewriter.capacity());
        var level = h.getLevel();
        rewriter.onButton(level, null, 1); // occurrence #1
        rewriter.setItem(0, MathItems.statementStack(StatementData.of(target, printer)));
        rewriter.setItem(1, MathItems.statementStack(StatementData.of(eqv, printer)));
        Formula expected = same(Term.succ(one), Term.succ(onePlusZero));
        h.succeedWhen(() -> {
            StatementData out = MathItems.statement(rewriter.getItem(2));
            h.assertTrue(out != null, "waiting for the rewritten statement; status: " + rewriter.status().getString());
            h.assertTrue(out.proven() && Syntax.alphaEquals(out.formula(), expected), "got " + out.display());
        });
    }
}
