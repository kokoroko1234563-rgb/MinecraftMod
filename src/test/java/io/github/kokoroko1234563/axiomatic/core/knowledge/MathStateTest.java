package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.numeral;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

class MathStateTest {

    @Test
    void newWorldHasStarterDefinitions() {
        MathState state = MathState.newWorld();
        assertNotNull(state.signature().get("add"));
        assertNotNull(state.signature().get("Even"));
        assertTrue(state.library().theorems().isEmpty(), "no theorems are given away");
    }

    @Test
    void everythingSurvivesSaveAndLoad() throws IOException {
        MathState state = MathState.newWorld();
        Theorem addZero = state.kernel().definingStatements("add").get(0);
        state.library().name("add_zero", addZero);
        state.kernel().define(new Definition.Explicit("Double", List.of("n"), app("add", var("n"), var("n")), null));
        Formula claim = Formula.eq(app("Double", var("n")), app("add", var("n"), var("n")));
        state.library().recordTest(claim, new TestBench.Passed(BigInteger.TEN, 11));
        Formula bad = Formula.eq(succ(var("n")), var("n"));
        state.library().recordTest(bad, new TestBench.Counterexample(Map.of("n", BigInteger.ZERO)));
        Formula conj = Formula.eq(app("add", var("n"), numeral(0)), var("n"));
        state.library().conjecture(state.kernel(), state.deciders(), "addZeroGuess", conj);
        long nextId = state.kernel().assume(conj).hypotheses().get(0).id() + 1;

        MathState loaded = MathState.fromBytes(state.toBytes());

        assertEquals(state.signature().all().size(), loaded.signature().all().size());
        Theorem restored = loaded.library().theorem("add_zero");
        assertTrue(Syntax.alphaEquals(addZero.formula(), restored.formula()));
        assertEquals(addZero.dependencies(), restored.dependencies());
        assertInstanceOf(Library.Tested.class, loaded.library().status(claim));
        assertInstanceOf(Library.Refuted.class, loaded.library().status(bad));
        assertInstanceOf(Library.Proven.class, loaded.library().status(addZero.formula()));
        assertEquals(Library.ConjectureState.ACTIVE, loaded.library().conjectureNamed("addZeroGuess").state());
        assertTrue(loaded.kernel().assume(conj).hypotheses().get(0).id() >= nextId, "stamps stay unique");
        assertEquals(BigInteger.valueOf(14),
                new Evaluator(loaded.signature(), 1000).apply("Double", List.of(BigInteger.valueOf(7))));
    }
}
