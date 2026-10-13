package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Implementation Bench: a proven-equal fast function computes the slow one. */
class ImplementationTest {
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term n = var("n");
    private Kernel kernel;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a, succ(app("add", a, b)), "+"));
        // slow(n) = n + 0, fast(n) = n.
        kernel.define(new Definition.Explicit("slow", List.of("n"), app("add", n, zero()), null));
        kernel.define(new Definition.Explicit("fast", List.of("n"), n, null));
    }

    /** fast(n) = slow(n): fast(n) = n = n + 0 = slow(n). */
    private Theorem proof() {
        Theorem slowDef = kernel.definingStatements("slow").get(0);      // slow(n) = n + 0
        Theorem fastDef = kernel.definingStatements("fast").get(0);      // fast(n) = n
        Theorem addZero = kernel.definingStatements("add").get(0);       // a + 0 = a
        Theorem t = kernel.reflexivity(app("fast", n));                  // fast(n) = fast(n)
        t = Tactics.rewriteMatching(kernel, t, fastDef, false, 1);        // fast(n) = n
        t = Tactics.rewriteMatching(kernel, t, addZero, true, 2);         // fast(n) = n + 0 (the right n)
        return Tactics.rewriteMatching(kernel, t, slowDef, true, 0);      // fast(n) = slow(n)
    }

    @Test
    void slowRunsFastOnceProven() {
        Evaluator before = new Evaluator(kernel.signature(), 1_000_000);
        before.apply("slow", List.of(BigInteger.valueOf(1000)));
        String[] pair = kernel.implement(proof());
        assertEquals("slow", pair[0]);
        Evaluator after = new Evaluator(kernel.signature(), 1_000_000);
        assertEquals(BigInteger.valueOf(1000), after.apply("slow", List.of(BigInteger.valueOf(1000))));
        assertTrue(after.steps() < before.steps(), after.steps() + " vs " + before.steps());
    }

    @Test
    void refusesWhatIsNotAProvenEquality() {
        assertThrows(ProofException.class, () -> kernel.implement(kernel.conjecture("x", eq(app("fast", n), app("slow", n)))));
        assertThrows(ProofException.class, () -> kernel.implement(kernel.assume(eq(app("fast", n), app("slow", n)))));
        assertThrows(ProofException.class, () -> kernel.implement(kernel.reflexivity(app("fast", n))));
        // A function that itself computes slow cannot implement it (it would loop).
        kernel.define(new Definition.Explicit("viaSlow", List.of("n"), app("slow", n), null));
        Theorem t = kernel.reflexivity(app("viaSlow", n));
        Theorem viaDef = kernel.definingStatements("viaSlow").get(0);
        Theorem loopy = Tactics.rewriteMatching(kernel, t, viaDef, false, 1);  // viaSlow(n) = slow(n)
        assertThrows(ProofException.class, () -> kernel.implement(loopy));
    }

    /** addFast(a, b) = add(a, b) by induction on b, from the two definitions. */
    private Theorem addFastIsAdd() {
        List<Theorem> fast = kernel.define(new Definition.PrimitiveRecursive("addFast", List.of("a", "b"), a,
                succ(app("addFast", a, b)), null));
        Theorem addZero = kernel.definingStatements("add").get(0);
        Theorem addSucc = kernel.definingStatements("add").get(1);
        Theorem base = kernel.reflexivity(app("addFast", a, zero()));
        base = Tactics.rewriteMatching(kernel, base, fast.get(0), false, 1);   // addFast(a, 0) = a
        base = Tactics.rewriteMatching(kernel, base, addZero, true, 3);        // = add(a, 0); the pattern matches every subterm
        Theorem ih = kernel.assume(eq(app("addFast", a, b), app("add", a, b)));
        Theorem step = kernel.reflexivity(app("addFast", a, succ(b)));
        step = Tactics.rewriteMatching(kernel, step, fast.get(1), false, 1);   // = S(addFast(a, b))
        step = Tactics.rewriteMatching(kernel, step, ih, false, 0);            // = S(add(a, b))
        step = Tactics.rewriteMatching(kernel, step, addSucc, true, 0);        // = add(a, S(b))
        return kernel.induction(base, kernel.discharge(step, ih.hypotheses().get(0)), "b");
    }

    @Test
    void binaryNumeralsNeedATrulyFastAddition() {
        assertTrue(!kernel.signature().binaryNumerals());
        // An implementation of something else is not the upgrade.
        kernel.implement(proof());
        assertTrue(!kernel.signature().binaryNumerals());
        // Nor is an unproven claim about addition.
        assertThrows(ProofException.class, () -> kernel.implement(
                kernel.conjecture("fastAdd", eq(app("add", a, b), app("slow", a)))));
        // A renamed copy of addition implements it, but is no faster: the cheat is refused.
        String[] pair = kernel.implement(addFastIsAdd());
        assertEquals("add", pair[0]);
        assertTrue(!kernel.signature().binaryNumerals());
    }

    @Test
    void survivesSaveAndLoad() throws java.io.IOException {
        var state = io.github.kokoroko1234563.axiomatic.core.knowledge.MathState.newWorld();
        kernel = state.kernel();
        kernel.define(new Definition.Explicit("slow", List.of("n"), app("add", n, zero()), null));
        kernel.define(new Definition.Explicit("fast", List.of("n"), n, null));
        kernel.implement(proof());
        var loaded = io.github.kokoroko1234563.axiomatic.core.knowledge.MathState.fromBytes(state.toBytes());
        assertEquals("fast", loaded.signature().implementation("slow"));
    }
}
