package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TacticsTest {
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term c = var("c");
    private final Term n = var("n");
    private Kernel kernel;
    private Theorem addZero;
    private Theorem addSucc;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        List<Theorem> add = kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a,
                succ(app("add", a, b)), "+"));
        addZero = add.get(0);
        addSucc = add.get(1);
    }

    private static Term add(Term x, Term y) {
        return app("add", x, y);
    }

    private static void assertProves(Formula expected, Theorem thm) {
        assertTrue(Syntax.alphaEquals(expected, thm.formula()), () -> "expected " + expected + " but got " + thm);
    }

    @Test
    void zeroAddWithoutInstantiating() {
        // Base: 0 + 0 = 0 straight from reflexivity and a + 0 = a matched at 0 + 0.
        Theorem base = Tactics.rewriteMatching(kernel, kernel.reflexivity(add(zero(), zero())), addZero, false, 1);
        assertProves(eq(add(zero(), zero()), zero()), base);
        // Step: 0 + S(n) = 0 + S(n) → S(0 + n) → S(n).
        Theorem hyp = kernel.assume(eq(add(zero(), n), n));
        Theorem s = Tactics.rewriteMatching(kernel, kernel.reflexivity(add(zero(), succ(n))), addSucc, false, 1);
        s = Tactics.rewriteMatching(kernel, s, hyp, false, -1);
        Theorem zeroAdd = kernel.induction(base, kernel.discharge(s, hyp.hypotheses().get(0)), "n");
        assertProves(eq(add(zero(), n), n), zeroAdd);
    }

    @Test
    void picksTheRequestedMatch() {
        // With a commutativity "axiom" (a conjecture here), rewrite only the second match.
        Theorem comm = kernel.conjecture("comm", eq(add(a, b), add(b, a)));
        Theorem target = kernel.reflexivity(add(add(a, b), c));        // (a+b)+c = (a+b)+c
        // Matches in reading order: (a+b)+c, a+b, (a+b)+c, a+b. Pick #1, the inner a+b on the left.
        Theorem r = Tactics.rewriteMatching(kernel, target, comm, false, 1);
        assertProves(eq(add(add(b, a), c), add(add(a, b), c)), r);
        Theorem all = Tactics.rewriteMatching(kernel, target, comm, false, 0);
        assertProves(eq(add(c, add(a, b)), add(add(a, b), c)), all);
        assertThrows(ProofException.class, () -> Tactics.rewriteMatching(kernel, target, comm, false, 9));
    }

    @Test
    void lockedVariablesMatchOnlyThemselves() {
        Theorem hyp = kernel.assume(eq(add(n, zero()), n));     // n is locked
        Theorem target = kernel.reflexivity(add(var("m"), zero()));
        // n + 0 = n must not match m + 0: n is a fixed (assumed) number, not a pattern.
        assertThrows(ProofException.class, () -> Tactics.rewriteMatching(kernel, target, hyp, false, -1));
    }
}
