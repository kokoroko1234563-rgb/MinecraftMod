package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.List;

/**
 * A term is a circuit: 0, a successor, a variable, or a defined function applied to arguments.
 * Terms are immutable and compared structurally.
 */
public sealed interface Term permits Term.Zero, Term.Succ, Term.Var, Term.App {

    /** The numeral 0 (axiom 1, the Zero Source). */
    record Zero() implements Term {
        @Override
        public String toString() {
            return "0";
        }
    }

    /** S(arg) (axiom 2, the Successor). */
    record Succ(Term arg) implements Term {
        @Override
        public String toString() {
            return "S(" + arg + ")";
        }
    }

    /** A Variable block. Free variables in a proven statement range over all of ℕ. */
    record Var(String name) implements Term {
        @Override
        public String toString() {
            return name;
        }
    }

    /** A player-defined function applied to arguments, in port order. */
    record App(String function, List<Term> args) implements Term {
        public App {
            args = List.copyOf(args);
        }

        @Override
        public String toString() {
            return function + args.toString().replace('[', '(').replace(']', ')');
        }
    }

    Zero ZERO = new Zero();

    static Term zero() {
        return ZERO;
    }

    static Term succ(Term t) {
        return new Succ(t);
    }

    static Var var(String name) {
        return new Var(name);
    }

    static Term app(String function, Term... args) {
        return new App(function, List.of(args));
    }

    /** The unary numeral S(S(...S(0))) with n successors. */
    static Term numeral(int n) {
        Term t = ZERO;
        for (int i = 0; i < n; i++) {
            t = new Succ(t);
        }
        return t;
    }
}
