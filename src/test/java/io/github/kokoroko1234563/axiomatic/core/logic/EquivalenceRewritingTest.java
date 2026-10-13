package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.implies;
import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.pred;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Rewriting with an equivalence: only through functions that provably respect it. */
class EquivalenceRewritingTest {
    private static final String R = "Equivalence.R";
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term c = var("c");
    private final Term d = var("d");
    private final Term x = var("x");
    private final Term y = var("y");
    private Kernel kernel;
    private EquivalenceRewriting.Laws laws;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a, succ(app("add", a, b)), "+"));
        kernel.define(new Definition.PrimitiveRecursive("mul", List.of("a", "b"), Term.zero(),
                app("add", app("mul", a, b), a), "×"));
        kernel.structure(Structures.EQUIVALENCE);
        // R respects + (stated with other variable names, to show any names work) and S.
        Theorem congAdd = kernel.conjecture("congAdd", implies(pred(R, x, y),
                implies(pred(R, c, d), pred(R, app("add", x, c), app("add", y, d)))));
        Theorem congSucc = kernel.conjecture("congSucc", implies(pred(R, a, b), pred(R, succ(a), succ(b))));
        laws = new EquivalenceRewriting.Laws(R, kernel.structureAxiom(Structures.EQUIVALENCE, 0),
                kernel.structureAxiom(Structures.EQUIVALENCE, 1), kernel.structureAxiom(Structures.EQUIVALENCE, 2),
                List.of(congSucc, congAdd));
    }

    @Test
    void rewritesInsideASum() {
        Theorem target = kernel.assume(pred(R, app("add", a, b), c));     // a + b ≡ c
        Theorem eqv = kernel.assume(pred(R, b, d));                        // b ≡ d
        Theorem out = EquivalenceRewriting.rewrite(kernel, target, eqv, laws, false, 0);
        assertTrue(Syntax.alphaEquals(pred(R, app("add", a, d), c), out.formula()), out.formula().toString());
        assertEquals(2, out.hypotheses().size());
        assertTrue(out.dependencies().contains("conjecture:congAdd") || out.isConjectural(), out.dependencies().toString());
    }

    @Test
    void rewritesBothSidesOfAnEquationAndUnderSuccessor() {
        // S(a) = b + a, with a ≡ d everywhere: S(d) ≡ b + d.
        Theorem target = kernel.assume(eq(succ(a), app("add", b, a)));
        Theorem eqv = kernel.assume(pred(R, a, d));
        Theorem out = EquivalenceRewriting.rewrite(kernel, target, eqv, laws, false, -1);
        assertTrue(Syntax.alphaEquals(pred(R, succ(d), app("add", b, d)), out.formula()), out.formula().toString());
    }

    @Test
    void patternVariablesAndRightToLeft() {
        // ∀z. R(z + z, z): rewriting right to left turns c into c + c.
        Theorem twice = kernel.conjecture("twice", pred(R, app("add", var("z"), var("z")), var("z")));
        Theorem target = kernel.assume(pred(R, c, b));
        Theorem out = EquivalenceRewriting.rewrite(kernel, target, twice, laws, true, 0);
        assertTrue(Syntax.alphaEquals(pred(R, app("add", c, c), b), out.formula()), out.formula().toString());
    }

    @Test
    void refusedInsideAFunctionWithoutACongruenceLaw() {
        // Nothing says R respects ×: a ≡ d does not give a × b ≡ d × b.
        Theorem target = kernel.assume(pred(R, app("mul", a, b), c));
        Theorem eqv = kernel.assume(pred(R, a, d));
        assertThrows(ProofException.class, () -> EquivalenceRewriting.rewrite(kernel, target, eqv, laws, false, 0));
    }

    @Test
    void refusesBadLawsAndBadInputs() {
        Theorem target = kernel.assume(pred(R, app("add", a, b), c));
        Theorem eqv = kernel.assume(pred(R, b, d));
        // A law resting on an open assumption is not a law.
        var assumedCong = new EquivalenceRewriting.Laws(R, laws.reflexive(), laws.symmetric(), laws.transitive(),
                List.of(kernel.assume(implies(pred(R, x, y), implies(pred(R, c, d), pred(R, app("add", x, c), app("add", y, d)))))));
        assertThrows(ProofException.class, () -> EquivalenceRewriting.rewrite(kernel, target, eqv, assumedCong, false, 0));
        // Symmetry in place of transitivity.
        var swapped = new EquivalenceRewriting.Laws(R, laws.reflexive(), laws.symmetric(), laws.symmetric(), laws.congruences());
        assertThrows(ProofException.class, () -> EquivalenceRewriting.rewrite(kernel, target, eqv, swapped, false, 0));
        // An equation is not an equivalence statement for R, and a target of another shape is refused.
        assertThrows(ProofException.class, () -> EquivalenceRewriting.rewrite(kernel, target, kernel.reflexivity(a), laws, false, 0));
        Theorem other = kernel.assume(implies(pred(R, a, b), pred(R, b, a)));
        assertThrows(ProofException.class, () -> EquivalenceRewriting.rewrite(kernel, other, eqv, laws, false, 0));
        // Occurrence out of range.
        assertThrows(ProofException.class, () -> EquivalenceRewriting.rewrite(kernel, target, eqv, laws, false, 3));
    }

    @Test
    void congruenceShapesAreRecognised() {
        assertEquals("add", EquivalenceRewriting.congruenceFunction(laws.congruences().get(1).formula(), R, 2));
        assertEquals("S", EquivalenceRewriting.congruenceFunction(laws.congruences().get(0).formula(), R, 1));
        assertEquals(null, EquivalenceRewriting.congruenceFunction(laws.symmetric().formula(), R, 1));
    }
}
