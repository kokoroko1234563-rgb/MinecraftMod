package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.numeral;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;

import java.util.List;

/**
 * Definitions a player would make in the opening hour, written out from Peano for tests:
 * arithmetic, Even, and a trial-division prime decider.
 */
public final class Prelude {
    private Prelude() {}

    public static void define(Kernel kernel) {
        Term a = var("a");
        Term b = var("b");
        Term n = var("n");
        Term y = var("y");
        Term d = var("d");
        Term k = var("k");
        kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a, succ(app("add", a, b)), "+"));
        kernel.define(new Definition.PrimitiveRecursive("mul", List.of("a", "b"), zero(),
                app("add", app("mul", a, b), a), "×"));
        kernel.define(new Definition.Explicit("Double", List.of("n"), app("add", n, n), null));
        kernel.define(new Definition.Predicate("Even", List.of("n"),
                Formula.exists("k", Formula.eq(n, app("add", k, k))), null));
        // Deciders are built from 0/1-valued helpers.
        kernel.define(new Definition.PrimitiveRecursive("isZero", List.of("y"), numeral(1), zero(), null));
        kernel.define(new Definition.PrimitiveRecursive("isEven", List.of("y"), numeral(1),
                app("isZero", app("isEven", y)), null));
        kernel.define(new Definition.PrimitiveRecursive("pred", List.of("y"), zero(), y, null));
        kernel.define(new Definition.PrimitiveRecursive("sub", List.of("a", "b"), a, app("pred", app("sub", a, b)), "∸"));
        kernel.define(new Definition.Explicit("eqb", List.of("a", "b"),
                app("isZero", app("add", app("sub", a, b), app("sub", b, a))), null));
        // modR(d, n) = n mod d: count up, wrapping to 0 on reaching d.
        Term next = succ(app("modR", d, n));
        kernel.define(new Definition.PrimitiveRecursive("modR", List.of("d", "n"), zero(),
                app("mul", next, app("isZero", app("eqb", next, d))), null));
        kernel.define(new Definition.Explicit("divides", List.of("d", "n"), app("isZero", app("modR", d, n)), null));
        // countDiv(n, k) = how many of 1..k divide n.
        kernel.define(new Definition.PrimitiveRecursive("countDiv", List.of("n", "k"), zero(),
                app("add", app("countDiv", n, k), app("divides", succ(k), n)), null));
        kernel.define(new Definition.Explicit("isPrime", List.of("n"),
                app("eqb", app("countDiv", n, n), numeral(2)), null));
    }
}
