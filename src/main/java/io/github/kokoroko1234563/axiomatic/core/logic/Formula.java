package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.List;

/**
 * A statement. There is no separate "for all": a proven statement holds for every value of its
 * free variables. "not P" is {@code P → ⊥}.
 */
public sealed interface Formula
        permits Formula.Eq, Formula.And, Formula.Implies, Formula.Exists, Formula.Bottom, Formula.Pred {

    /** left = right, made on the Equation Bench. */
    record Eq(Term left, Term right) implements Formula {
        @Override
        public String toString() {
            return left + " = " + right;
        }
    }

    /** P ∧ Q, made on the Conjunction Press. */
    record And(Formula left, Formula right) implements Formula {
        @Override
        public String toString() {
            return "(" + left + " ∧ " + right + ")";
        }
    }

    /** P → Q, made by discharging the Assumption Chamber. */
    record Implies(Formula premise, Formula conclusion) implements Formula {
        @Override
        public String toString() {
            return "(" + premise + " → " + conclusion + ")";
        }
    }

    /** ∃var. body, made by sealing a variable. */
    record Exists(String var, Formula body) implements Formula {
        @Override
        public String toString() {
            return "∃" + var + ". " + body;
        }
    }

    /** Contradiction ⊥. */
    record Bottom() implements Formula {
        @Override
        public String toString() {
            return "⊥";
        }
    }

    /** A defined property or relation applied to terms, such as Even(n) or a ≤ b. */
    record Pred(String name, List<Term> args) implements Formula {
        public Pred {
            args = List.copyOf(args);
        }

        @Override
        public String toString() {
            return name + args.toString().replace('[', '(').replace(']', ')');
        }
    }

    Bottom BOTTOM = new Bottom();

    static Formula eq(Term left, Term right) {
        return new Eq(left, right);
    }

    static Formula and(Formula left, Formula right) {
        return new And(left, right);
    }

    static Formula implies(Formula premise, Formula conclusion) {
        return new Implies(premise, conclusion);
    }

    static Formula exists(String var, Formula body) {
        return new Exists(var, body);
    }

    static Formula not(Formula p) {
        return new Implies(p, BOTTOM);
    }

    static Formula pred(String name, Term... args) {
        return new Pred(name, List.of(args));
    }
}
