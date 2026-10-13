package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KernelTest {
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term n = var("n");

    private Kernel kernel;
    private Theorem addZero; // add(a, 0) = a
    private Theorem addSucc; // add(a, S(b)) = S(add(a, b))

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        List<Theorem> add = kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"),
                a, succ(app("add", a, b)), "+"));
        addZero = add.get(0);
        addSucc = add.get(1);
    }

    private static Term add(Term x, Term y) {
        return app("add", x, y);
    }

    private void assertProves(Formula expected, Theorem thm) {
        assertTrue(Syntax.alphaEquals(expected, thm.formula()), () -> "expected " + expected + " but got " + thm);
        assertTrue(thm.isClosed(), () -> "still has open assumptions: " + thm);
    }

    /** The worked example from the design doc, step for step. */
    private Theorem zeroAdd() {
        Theorem base = kernel.instantiate(addZero, "a", zero());                        // 1: 0 + 0 = 0
        Theorem hyp = kernel.assume(eq(add(zero(), n), n));                             // 2: 0 + n = n, n locked
        Theorem s = kernel.instantiate(addSucc, Map.of("a", zero(), "b", n));           // 3: 0 + S(n) = S(0 + n)
        Theorem r = kernel.rewrite(s, hyp, false, -1);                                  // 4: 0 + S(n) = S(n)
        Theorem step = kernel.discharge(r, hyp.hypotheses().get(0));                    // 5: the step
        return kernel.induction(base, step, "n");                                       // 6: 0 + n = n
    }

    @Test
    void zeroAddFollowsTheDesignDoc() {
        Theorem thm = zeroAdd();
        assertProves(eq(add(zero(), n), n), thm);
        assertTrue(thm.dependencies().contains("def:add"));
        assertTrue(thm.dependencies().contains("axiom:induction"));
        assertFalse(thm.isConjectural());
    }

    /** S(a) + b = S(a + b), by induction on b. */
    private Theorem succAdd() {
        Theorem base = kernel.instantiate(addZero, "a", succ(a));                       // S(a) + 0 = S(a)
        base = kernel.rewrite(base, addZero, true, 1);                                  // S(a) + 0 = S(a + 0)
        Theorem hyp = kernel.assume(eq(add(succ(a), b), succ(add(a, b))));
        Theorem s = kernel.instantiate(addSucc, "a", succ(a));                          // S(a) + S(b) = S(S(a) + b)
        s = kernel.rewrite(s, hyp, false, -1);                                          // ... = S(S(a + b))
        s = kernel.rewrite(s, addSucc, true, -1);                                       // ... = S(a + S(b))
        return kernel.induction(base, kernel.discharge(s, hyp.hypotheses().get(0)), "b");
    }

    @Test
    void additionIsCommutative() {
        Theorem zeroAdd = zeroAdd();
        Theorem succAdd = succAdd();
        assertProves(eq(add(succ(a), b), succ(add(a, b))), succAdd);

        // Base: a + 0 = 0 + a.
        Theorem base = kernel.rewrite(addZero, kernel.instantiate(zeroAdd, "n", a), true, 1);
        // Step: a + b = b + a  →  a + S(b) = S(b) + a.
        Theorem hyp = kernel.assume(eq(add(a, b), add(b, a)));
        Theorem s = kernel.rewrite(addSucc, hyp, false, -1);                            // a + S(b) = S(b + a)
        Theorem swapped = kernel.instantiate(succAdd, Map.of("a", b, "b", a));          // S(b) + a = S(b + a)
        assertProves(eq(add(succ(b), a), succ(add(b, a))), swapped);
        s = kernel.rewrite(s, swapped, true, -1);                                       // a + S(b) = S(b) + a
        Theorem comm = kernel.induction(base, kernel.discharge(s, hyp.hypotheses().get(0)), "b");

        assertProves(eq(add(a, b), add(b, a)), comm);
    }

    @Test
    void doubleOutputsEvens() {
        Theorem dbl = kernel.define(new Definition.Explicit("Double", List.of("n"), add(n, n), null)).get(0);
        Term k = var("k");
        List<Theorem> even = kernel.define(new Definition.Predicate("Even", List.of("n"),
                Formula.exists("k", eq(n, add(k, k))), null));

        Formula.Exists target = new Formula.Exists("k", eq(app("Double", n), add(k, k)));
        Theorem witnessed = kernel.existsIntro(target, n, dbl);                         // Witness Bench, k := n
        Theorem fold = kernel.instantiate(even.get(1), "n", app("Double", n));
        Theorem thm = kernel.modusPonens(fold, witnessed);

        assertProves(Formula.pred("Even", app("Double", n)), thm);
    }

    @Test
    void lockedVariablesCannotBeInstantiated() {
        Theorem hyp = kernel.assume(eq(add(zero(), n), n));
        ProofException e = assertThrows(ProofException.class, () -> kernel.instantiate(hyp, "n", Term.numeral(5)));
        assertTrue(e.getMessage().contains("locked"));
    }

    @Test
    void inductionRejectsAStepForADifferentStatement() {
        Theorem base = kernel.instantiate(addZero, "a", zero());
        Theorem hyp = kernel.assume(eq(add(zero(), n), n));
        // Discharging the hypothesis unchanged gives P(n) → P(n), not P(n) → P(S(n)).
        Theorem bogusStep = kernel.discharge(hyp, hyp.hypotheses().get(0));
        assertThrows(ProofException.class, () -> kernel.induction(base, bogusStep, "n"));
    }

    @Test
    void inductionRejectsAVariableLockedByAnOpenAssumption() {
        Theorem hyp = kernel.assume(eq(n, n));
        Theorem base = kernel.reflexivity(zero());
        Theorem step = kernel.discharge(kernel.reflexivity(succ(n)), hyp.hypotheses().get(0)); // still fine
        Theorem lockedBase = kernel.andLeft(kernel.andIntro(base, hyp));                        // carries the n-hypothesis
        assertThrows(ProofException.class,
                () -> kernel.induction(lockedBase, step, "n"));
    }

    @Test
    void openedExistsVariableMustNotEscape() {
        Term k = var("k");
        Formula.Exists ex = new Formula.Exists("k", eq(k, k));
        Theorem existence = kernel.existsIntro(ex, zero(), kernel.reflexivity(zero()));
        Kernel.Opening opening = kernel.openExists(ex);
        // The conclusion mentions the opened variable, so it cannot leave the chamber.
        assertThrows(ProofException.class, () -> kernel.closeExists(existence, opening, opening.hypothesis()));
        // A conclusion that does not mention it is fine.
        Theorem ok = kernel.closeExists(existence, opening, kernel.reflexivity(zero()));
        assertProves(eq(zero(), zero()), ok);
    }

    @Test
    void stampedExistsOpensAsANamedVariable() {
        Term k = var("k");
        Term j = var("j");
        List<Theorem> even = kernel.define(new Definition.Predicate("Even", List.of("n"),
                Formula.exists("k", eq(n, add(k, k))), null));
        // Chamber 1 assumes Even(a); unfolding gives ∃k. a = k + k, stamped with that assumption.
        Theorem hA = kernel.assume(Formula.pred("Even", a));
        Theorem ex = kernel.modusPonens(kernel.instantiate(even.get(0), "n", a), hA);
        assertFalse(ex.isClosed());
        // Chamber 2 opens it as j, a name the player can use in circuits.
        Kernel.Opening opening = kernel.openExists((Formula.Exists) ex.formula(), "j");
        assertProvesLoosely(eq(a, add(j, j)), opening.hypothesis());
        Formula.Exists target = new Formula.Exists("m", eq(a, add(var("m"), var("m"))));
        Theorem q = kernel.existsIntro(target, j, opening.hypothesis());
        Theorem closed = kernel.closeExists(ex, opening, q);
        assertEquals(hA.hypotheses(), closed.hypotheses(), "still depends on Even(a), not on the opened j");
        Theorem result = kernel.discharge(closed, hA.hypotheses().get(0));
        assertProves(Formula.implies(Formula.pred("Even", a), target), result);
        // Opening as a variable the ∃ statement mentions is refused.
        assertThrows(ProofException.class, () -> kernel.openExists((Formula.Exists) ex.formula(), "a"));
    }

    private void assertProvesLoosely(Formula expected, Theorem thm) {
        assertTrue(Syntax.alphaEquals(expected, thm.formula()), () -> "expected " + expected + " but got " + thm);
    }

    @Test
    void openingWithAVariableTheStatementMentionsIsRefused() {
        // ∃k. k = S(x) is true, but opening it as "x = S(x)" would let us derive nonsense.
        Term x = var("x");
        Formula.Exists ex = new Formula.Exists("k", eq(var("k"), succ(x)));
        Theorem existence = kernel.existsIntro(ex, succ(x), kernel.reflexivity(succ(x)));
        Kernel.Opening forged = new Kernel.Opening(kernel.assume(eq(x, succ(x))), "x");
        assertThrows(ProofException.class, () -> kernel.closeExists(existence, forged, kernel.reflexivity(zero())));
    }

    @Test
    void rewritingSkipsPositionsWhereTheVariableIsBound() {
        Formula.Exists ex = new Formula.Exists("a", eq(add(var("a"), zero()), var("a")));
        Theorem existence = kernel.existsIntro(ex, zero(), kernel.instantiate(addZero, "a", zero()));
        // a + 0 = a talks about the free a; inside ∃a the bound a is a different variable.
        assertThrows(ProofException.class, () -> kernel.rewrite(existence, addZero, false, -1));
    }

    @Test
    void nonPrimitiveRecursionIsRefused() {
        Term x = var("x");
        Term y = var("y");
        assertThrows(ProofException.class, () -> kernel.define(new Definition.PrimitiveRecursive("loop",
                List.of("x", "y"), x, app("loop", succ(x), y), null)));
        assertThrows(ProofException.class, () -> kernel.define(new Definition.Explicit("self",
                List.of("x"), app("self", x), null)));
        assertThrows(ProofException.class, () -> kernel.define(new Definition.Explicit("free",
                List.of("x"), y, null)));
    }

    @Test
    void modusPonensNeedsTheMatchingPremise() {
        Theorem inj = kernel.succInjective();
        assertThrows(ProofException.class, () -> kernel.modusPonens(inj, kernel.reflexivity(zero())));
        Theorem fact = kernel.modusPonens(kernel.instantiate(inj, Map.of("x", zero(), "y", zero())),
                kernel.reflexivity(succ(zero())));
        assertProves(eq(zero(), zero()), fact);
    }

    @Test
    void exFalsoNeedsAProofOfBottom() {
        // S(0) = 0 is refuted by axiom 3, so assuming it gives ⊥ and then anything.
        Theorem hyp = kernel.assume(eq(succ(zero()), zero()));
        Theorem bottom = kernel.modusPonens(kernel.instantiate(kernel.succNotZero(), "x", zero()), hyp);
        Theorem anything = kernel.exFalso(bottom, eq(Term.numeral(1), Term.numeral(2)));
        assertEquals(hyp.hypotheses(), anything.hypotheses(), "still depends on the absurd assumption");
        Theorem result = kernel.discharge(anything, hyp.hypotheses().get(0));
        assertProves(Formula.implies(eq(succ(zero()), zero()), eq(Term.numeral(1), Term.numeral(2))), result);
        assertThrows(ProofException.class, () -> kernel.exFalso(kernel.reflexivity(zero()), eq(zero(), succ(zero()))));
    }

    @Test
    void conjecturesTaintWhatIsBuiltOnThem() {
        Theorem conj = kernel.conjecture("twoNPlusOne", eq(n, n));
        Theorem derived = kernel.instantiate(conj, "n", Term.numeral(4));
        assertTrue(derived.isConjectural());
        assertFalse(zeroAdd().isConjectural());
    }

    @Test
    void stepsCountTheWholeProof() {
        assertEquals(1, addZero.steps());
        assertTrue(zeroAdd().steps() > 6);
    }
}
