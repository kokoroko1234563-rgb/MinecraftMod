package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Recognition: a rebuilt design is the same function, whatever it is called. */
class DesignsTest {
    private Kernel kernel;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        Term a = var("a");
        Term b = var("b");
        kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a, succ(app("add", a, b)), "+"));
        // The same addition, rebuilt with other names.
        Term x = var("x");
        Term y = var("y");
        kernel.define(new Definition.PrimitiveRecursive("plus", List.of("x", "y"), x, succ(app("plus", x, y)), null));
        kernel.define(new Definition.Explicit("dbl", List.of("n"), app("add", var("n"), var("n")), null));
        kernel.define(new Definition.Explicit("twice", List.of("m"), app("plus", var("m"), var("m")), null));
        // Not the same: starts from 0 instead of x.
        kernel.define(new Definition.PrimitiveRecursive("other", List.of("x", "y"), zero(), succ(app("other", x, y)), null));
        kernel.define(new Definition.Predicate("Ev", List.of("n"),
                Formula.exists("k", Formula.eq(var("n"), app("add", var("k"), var("k")))), null));
        kernel.define(new Definition.Predicate("Even2", List.of("m"),
                Formula.exists("j", Formula.eq(var("m"), app("plus", var("j"), var("j")))), null));
    }

    @Test
    void rebuiltDesignsAreRecognised() {
        Theorem t = kernel.sameDesign("add", "plus").get(0);
        assertTrue(Syntax.alphaEquals(Formula.eq(app("add", var("a"), var("b")), app("plus", var("a"), var("b"))), t.formula()));
        // Recursively: dbl uses add, twice uses plus.
        kernel.sameDesign("dbl", "twice");
        // Properties give both implications.
        assertEquals(2, kernel.sameDesign("Ev", "Even2").size());
    }

    @Test
    void differentDesignsAreNot() {
        assertThrows(ProofException.class, () -> kernel.sameDesign("add", "other"));
        assertThrows(ProofException.class, () -> kernel.sameDesign("dbl", "add"));
    }
}
