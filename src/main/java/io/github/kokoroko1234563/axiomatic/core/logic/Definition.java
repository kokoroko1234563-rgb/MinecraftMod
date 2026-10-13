package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.List;

/**
 * A player definition. Compiling one (see {@link Signature#define}) checks it and yields its
 * defining statements as proven theorems (see {@link Kernel#definingStatements}).
 *
 * <p>{@code symbol} is an optional infix symbol used only for display, such as "+".
 */
public sealed interface Definition
        permits Definition.Explicit, Definition.PrimitiveRecursive, Definition.Predicate {

    String name();

    List<String> params();

    String symbol();

    /** {@code name(params) = body}, for example Double(n) = n + n. */
    record Explicit(String name, List<String> params, Term body, String symbol) implements Definition {
        public Explicit {
            params = List.copyOf(params);
        }
    }

    /**
     * Primitive recursion on the <b>last</b> parameter y, with the others x̄ held fixed:
     * <pre>
     *   name(x̄, 0)    = base          (base uses only x̄)
     *   name(x̄, S(y)) = step          (step uses x̄, y and name(x̄, y) only)
     * </pre>
     * Every such definition is total. Example: addition, a + 0 = a and a + S(b) = S(a + b).
     */
    record PrimitiveRecursive(String name, List<String> params, Term base, Term step, String symbol)
            implements Definition {
        public PrimitiveRecursive {
            params = List.copyOf(params);
        }

        public List<String> fixedParams() {
            return params.subList(0, params.size() - 1);
        }

        public String recursionParam() {
            return params.get(params.size() - 1);
        }
    }

    /** A property or relation defined by a statement, for example Even(n) :⇔ ∃k. n = k + k. */
    record Predicate(String name, List<String> params, Formula body, String symbol) implements Definition {
        public Predicate {
            params = List.copyOf(params);
        }
    }
}
