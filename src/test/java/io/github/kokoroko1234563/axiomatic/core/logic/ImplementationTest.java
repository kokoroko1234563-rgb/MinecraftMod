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
