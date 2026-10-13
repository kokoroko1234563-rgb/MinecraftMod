package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** General recursion: accepted only with a proof that the measure shrinks at every call. */
class GeneralRecursionTest {
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term n = var("n");
    private final Term y = var("y");
    private Kernel kernel;
    private Theorem addZero;
    private Theorem addSucc;
    private Theorem predZero;
    private Theorem predSucc;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        List<Theorem> add = kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a,
                succ(app("add", a, b)), "+"));
        addZero = add.get(0);
        addSucc = add.get(1);
        List<Theorem> pred = kernel.define(new Definition.PrimitiveRecursive("pred", List.of("y"), zero(), y, null));
        predZero = pred.get(0);
        predSucc = pred.get(1);
        // ifz(t, e, c): e when c = 0, t otherwise. A selector, evaluated lazily.
        kernel.define(new Definition.PrimitiveRecursive("ifz", List.of("t", "e", "c"), var("e"), var("t"), null));
    }

    /** f(n) = ifz(f(pred(n)), 0, n): counts down to 0. */
    private Definition.GeneralRecursive countdown() {
        return new Definition.GeneralRecursive("f", List.of("n"),
                app("ifz", app("f", app("pred", n)), zero(), n), n, null);
    }

    @Test
    void selectorsAndAdditionAreRecognised() {
        assertArrayEquals(new int[] {1, 0}, kernel.signature().selector("ifz"));
        assertEquals("add", kernel.signature().canonicalAddition());
        assertEquals(null, kernel.signature().selector("pred"));
    }

    @Test
    void obligationIsTheGuardedDecrease() {
        Formula ob = kernel.terminationObligation(countdown());
        // ¬(n = 0) → ∃k. pred(n) + S(k) = n
        Formula expected = Formula.implies(Formula.not(eq(n, zero())),
                Formula.exists("k", eq(app("add", app("pred", n), succ(var("k"))), n)));
        assertTrue(Syntax.alphaEquals(expected, ob), ob.toString());
    }

    /** Proves ¬(n = 0) → ∃k. pred(n) + S(k) = n by induction on n. */
    private Theorem countdownTermination() {
        Formula goal = kernel.terminationObligation(countdown());
        Formula.Exists shrink = (Formula.Exists) ((Formula.Implies) goal).conclusion();
        // Base: ¬(0 = 0) gives ⊥ with 0 = 0, then anything.
        Theorem notZero = kernel.assume(Formula.not(eq(zero(), zero())));
        Theorem bottom = kernel.modusPonens(notZero, kernel.reflexivity(zero()));
        Theorem baseBody = kernel.exFalso(bottom, Syntax.substitute(shrink, "n", zero()));
        Theorem base = kernel.discharge(baseBody, notZero.hypotheses().get(0));
        // Step: whatever P(n) says, ∃k. pred(S(n)) + S(k) = S(n) holds with k = 0.
        Theorem ih = kernel.assume(Syntax.substitute(goal, "n", n));
        Theorem notSucc = kernel.assume(Formula.not(eq(succ(n), zero())));
        Theorem e1 = Tactics.rewriteMatching(kernel, kernel.reflexivity(app("add", app("pred", succ(n)), succ(zero()))),
                predSucc, false, 1);                                   // pred(S n) + S0 = n + S0
        e1 = Tactics.rewriteMatching(kernel, e1, addSucc, false, 1);    // = S(n + 0)
        e1 = Tactics.rewriteMatching(kernel, e1, addZero, false, 0);    // = S(n)
        Theorem witnessed = kernel.existsIntro((Formula.Exists) Syntax.substitute(shrink, "n", succ(n)), zero(), e1);
        Theorem step = kernel.discharge(kernel.discharge(witnessed, notSucc.hypotheses().get(0)), ih.hypotheses().get(0));
        return kernel.induction(base, step, "n");
    }

    @Test
    void countdownIsAcceptedWithItsProofAndComputes() {
        Theorem proof = countdownTermination();
        List<Theorem> eqs = kernel.defineRecursive(countdown(), proof);
        assertEquals(1, eqs.size());
        Evaluator ev = new Evaluator(kernel.signature(), 1_000_000);
        assertEquals(BigInteger.ZERO, ev.apply("f", List.of(BigInteger.valueOf(500))));
    }

    @Test
    void refusedWithoutAProperProof() {
        // Not through the plain door.
        assertThrows(ProofException.class, () -> kernel.signature().define(countdown()));
        // Not with a proof of something else.
        assertThrows(ProofException.class, () -> kernel.defineRecursive(countdown(), kernel.reflexivity(zero())));
        // Not with a conjecture: a definition is forever.
        Theorem conj = kernel.conjecture("trustMe", kernel.terminationObligation(countdown()));
        assertThrows(ProofException.class, () -> kernel.defineRecursive(countdown(), conj));
        // Not with an open assumption.
        Theorem assumed = kernel.assume(kernel.terminationObligation(countdown()));
        assertThrows(ProofException.class, () -> kernel.defineRecursive(countdown(), assumed));
    }

    @Test
    void nonTerminatingFunctionsHaveUnprovableObligations() {
        // loop(n) = loop(S(n)): the obligation S(n) < n is false, so no proof exists.
        var loop = new Definition.GeneralRecursive("loop", List.of("n"), app("loop", succ(n)), n, null);
        Formula ob = kernel.terminationObligation(loop);
        assertTrue(Syntax.alphaEquals(ob, Formula.exists("k", eq(app("add", succ(n), succ(var("k"))), n))), ob.toString());
        // Nested calls and calls in the measure are refused outright.
        assertThrows(ProofException.class, () -> kernel.terminationObligation(new Definition.GeneralRecursive("g",
                List.of("n"), app("g", app("g", n)), n, null)));
        assertThrows(ProofException.class, () -> kernel.terminationObligation(new Definition.GeneralRecursive("h",
                List.of("n"), app("h", n), app("h", n), null)));
    }

    @Test
    void survivesSaveAndLoad() throws java.io.IOException {
        var state = io.github.kokoroko1234563.axiomatic.core.knowledge.MathState.newWorld();
        kernel = state.kernel();
        addZero = kernel.definingStatements("add").get(0);
        addSucc = kernel.definingStatements("add").get(1);
        List<Theorem> pred = kernel.define(new Definition.PrimitiveRecursive("pred", List.of("y"), zero(), y, null));
        predZero = pred.get(0);
        predSucc = pred.get(1);
        kernel.define(new Definition.PrimitiveRecursive("ifz", List.of("t", "e", "c"), var("e"), var("t"), null));
        kernel.defineRecursive(countdown(), countdownTermination());
        var loaded = io.github.kokoroko1234563.axiomatic.core.knowledge.MathState.fromBytes(state.toBytes());
        assertEquals(BigInteger.ZERO, new Evaluator(loaded.signature(), 10_000).apply("f", List.of(BigInteger.TEN)));
        assertTrue(loaded.signature().get("f") instanceof Definition.GeneralRecursive);
    }
}
