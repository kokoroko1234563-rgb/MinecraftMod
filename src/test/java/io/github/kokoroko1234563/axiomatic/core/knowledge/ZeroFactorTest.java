package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/**
 * x × v = t bounds v by t only when x is not 0 (found by audit). NoFive(x) :⇔ ¬∃v. x × v = 0 ∧
 * v = 5 is false at x = 0 (v = 5 works), so no certificate or test may say otherwise.
 */
class ZeroFactorTest {
    private static MathState world(boolean binary) {
        MathState state = MathState.newWorld();
        Term x = Term.var("x");
        Term v = Term.var("v");
        state.kernel().define(new Definition.Predicate("NoFive", List.of("x"), Formula.not(Formula.exists("v",
                Formula.and(Formula.eq(Term.app("mul", x, v), Term.zero()), Formula.eq(v, Term.numeral(5))))), null));
        state.signature().setNativeArithmetic(binary);
        return state;
    }

    private static Truth truth(MathState state) {
        return new Truth(state.signature(), state.deciders(), new Evaluator(state.signature(), 10_000_000), BigInteger.valueOf(10));
    }

    @Test
    void noFalseCertificateAtZero() {
        for (boolean binary : new boolean[] {false, true}) {
            MathState state = world(binary);
            assertFalse(truth(state).property("NoFive", List.of(BigInteger.ZERO)) == Truth.Value.TRUE, "binary=" + binary);
            assertNull(Certifier.byComputation(truth(state), "NoFive", List.of(BigInteger.ZERO)), "binary=" + binary);
            // Where the bound is valid it still decides: NoFive(1) holds.
            assertEquals(Truth.Value.TRUE, truth(state).property("NoFive", List.of(BigInteger.ONE)), "binary=" + binary);
            // The Test Bench may not pass ∀x. NoFive(x).
            var result = TestBench.test(Formula.pred("NoFive", Term.var("x")), BigInteger.TEN, truth(state));
            assertFalse(result instanceof TestBench.Passed, "binary=" + binary + ": " + result);
        }
    }

    @Test
    void zeroTimesAnythingIsNotANonzero() {
        // ∃v. 0 × v = 3 is false outright.
        MathState state = world(false);
        Formula f = Formula.exists("v", Formula.eq(Term.app("mul", Term.zero(), Term.var("v")), Term.numeral(3)));
        assertEquals(Truth.Value.FALSE, truth(state).evaluate(f, java.util.Map.of()));
        assertTrue(Bounds.bounds(state.signature(), (Formula.Exists) f).getFirst().factor() != null);
    }

    /** A refuted conjecture takes the decider links resting on it with it. */
    @Test
    void refutedConjecturesDropTheirDeciders() {
        MathState state = MathState.newWorld();
        state.deciders().restore(new Deciders.Link("Even", "add", java.util.Set.of("conjecture:evenHope")));
        state.library().conjecture(state.kernel(), state.deciders(), "evenHope", Formula.pred("Even", Term.var("n")));
        assertTrue(state.deciders().get("Even") != null);
        state.library().refute("evenHope", java.util.Map.of("n", BigInteger.ONE));
        assertNull(state.deciders().get("Even"));
    }
}
