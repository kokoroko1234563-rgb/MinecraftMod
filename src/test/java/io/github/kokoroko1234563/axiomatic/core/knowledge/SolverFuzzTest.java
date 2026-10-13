package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/**
 * The binary age solves ∃ conjuncts like x + v = t or v × v = t instead of searching. On random
 * bounded statements it must agree with plain exhaustive search, value for value.
 */
class SolverFuzzTest {
    private static final List<String> VARS = List.of("x", "y", "v");

    private Term term(Random r, int depth) {
        int pick = depth <= 0 ? r.nextInt(2) : r.nextInt(4);
        return switch (pick) {
            case 0 -> Term.var(VARS.get(r.nextInt(VARS.size())));
            case 1 -> Term.numeral(r.nextInt(4));
            case 2 -> Term.succ(term(r, depth - 1));
            default -> Term.app(r.nextBoolean() ? "add" : "mul", term(r, depth - 1), term(r, depth - 1));
        };
    }

    /** One of the solvable shapes, in a random orientation. */
    private Formula solvable(Random r, String v) {
        Term var = Term.var(v);
        Term x = term(r, 1);
        Term t = term(r, 2);
        String op = r.nextBoolean() ? "add" : "mul";
        Term side = switch (r.nextInt(3)) {
            case 0 -> Term.app(op, x, var);
            case 1 -> Term.app(op, var, x);
            default -> Term.app(op, var, var);
        };
        return r.nextBoolean() ? Formula.eq(side, t) : Formula.eq(t, side);
    }

    private Formula formula(Random r, int depth) {
        if (depth <= 0) {
            return Formula.eq(term(r, 1), term(r, 1));
        }
        return switch (r.nextInt(4)) {
            case 0 -> Formula.and(formula(r, depth - 1), formula(r, depth - 1));
            case 1 -> Formula.implies(formula(r, depth - 1), formula(r, depth - 1));
            default -> {
                String v = VARS.get(r.nextInt(VARS.size()));
                // Bounded (so plain search is exact), with a solvable conjunct somewhere.
                Formula body = Formula.and(Formula.pred("le", Term.var(v), Term.numeral(12)),
                        r.nextBoolean() ? Formula.and(solvable(r, v), formula(r, depth - 1))
                                : Formula.and(formula(r, depth - 1), solvable(r, v)));
                yield Formula.exists(v, body);
            }
        };
    }

    @Test
    void solvingAgreesWithSearch() {
        MathState plain = MathState.newWorld();
        MathState binary = MathState.newWorld();
        binary.signature().setNativeArithmetic(true);
        Random r = new Random(99);
        int decided = 0;
        for (int i = 0; i < 3000; i++) {
            Formula f = formula(r, 3);
            Map<String, BigInteger> env = new HashMap<>();
            for (String v : VARS) {
                env.put(v, BigInteger.valueOf(r.nextInt(5)));
            }
            Truth.Value searched = new Truth(plain.signature(), plain.deciders(),
                    new Evaluator(plain.signature(), 50_000_000), BigInteger.valueOf(20)).evaluate(f, env);
            Truth.Value solved = new Truth(binary.signature(), binary.deciders(),
                    new Evaluator(binary.signature(), 50_000_000), BigInteger.valueOf(20)).evaluate(f, env);
            assertEquals(searched, solved, f + " at " + env);
            if (searched != Truth.Value.UNKNOWN) {
                decided++;
            }
        }
        assertTrue(decided > 2000, "decided " + decided);
    }
}
