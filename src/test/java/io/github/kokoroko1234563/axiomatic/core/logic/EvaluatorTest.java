package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluatorTest {
    private final Signature sig = new Signature();

    EvaluatorTest() {
        Term a = var("a");
        Term b = var("b");
        sig.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a, succ(app("add", a, b)), "+"));
        sig.define(new Definition.PrimitiveRecursive("mul", List.of("a", "b"), zero(),
                app("add", app("mul", a, b), a), "×"));
    }

    @Test
    void computesArithmetic() {
        Evaluator ev = new Evaluator(sig, 1_000_000);
        assertEquals(BigInteger.valueOf(7), ev.eval(app("add", Term.numeral(3), Term.numeral(4))));
        assertEquals(BigInteger.valueOf(42), ev.apply("mul", List.of(BigInteger.valueOf(6), BigInteger.valueOf(7))));
    }

    @Test
    void largeInputsDoNotOverflowTheStack() {
        Evaluator ev = new Evaluator(sig, 10_000_000);
        assertEquals(BigInteger.valueOf(200_000),
                ev.apply("add", List.of(BigInteger.valueOf(100_000), BigInteger.valueOf(100_000))));
    }

    @Test
    void runsOutOfBudget() {
        Evaluator ev = new Evaluator(sig, 100);
        assertThrows(Evaluator.OutOfBudget.class,
                () -> ev.apply("mul", List.of(BigInteger.valueOf(100), BigInteger.valueOf(100))));
    }
}
