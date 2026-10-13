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

    private boolean unboundedSometimes;

    private Formula formula(Random r, int depth) {
        if (depth <= 0) {
            return Formula.eq(term(r, 1), term(r, 1));
        }
        return switch (r.nextInt(4)) {
            case 0 -> Formula.and(formula(r, depth - 1), formula(r, depth - 1));
            case 1 -> Formula.implies(formula(r, depth - 1), formula(r, depth - 1));
            default -> {
                String v = VARS.get(r.nextInt(VARS.size()));
                // Bounded (so plain search is exact), with a solvable conjunct somewhere. In the
                // oracle test the explicit bound is sometimes left out, so the solvable conjunct
                // must bound v by itself.
                Formula core = r.nextBoolean() ? Formula.and(solvable(r, v), formula(r, depth - 1))
                        : Formula.and(formula(r, depth - 1), solvable(r, v));
                Formula body = unboundedSometimes && r.nextBoolean() ? core
                        : Formula.and(Formula.pred("le", Term.var(v), Term.numeral(12)), core);
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

    /** Plain semantics, no bounds or solving: ∃ searched from 0 to 60. */
    private static boolean oracle(Formula f, Map<String, BigInteger> env, Evaluator ev) {
        return switch (f) {
            case Formula.Eq e -> ev.eval(e.left(), env).equals(ev.eval(e.right(), env));
            case Formula.And a -> oracle(a.left(), env, ev) && oracle(a.right(), env, ev);
            case Formula.Implies i -> !oracle(i.premise(), env, ev) || oracle(i.conclusion(), env, ev);
            case Formula.Bottom b -> false;
            case Formula.Exists ex -> {
                Map<String, BigInteger> inner = new HashMap<>(env);
                for (int k = 0; k <= 60; k++) {
                    inner.put(ex.var(), BigInteger.valueOf(k));
                    if (oracle(ex.body(), inner, ev)) {
                        yield true;
                    }
                }
                yield false;
            }
            case Formula.Pred p when p.name().equals("le") ->
                    ev.eval(p.args().get(0), env).compareTo(ev.eval(p.args().get(1), env)) <= 0;
            case Formula.Pred p -> throw new IllegalStateException(p.toString());
        };
    }

    @Test
    void bothModesAgreeWithPlainSemantics() {
        unboundedSometimes = true;
        Random r = new Random(1234);
        int decided = 0;
        for (boolean binary : new boolean[] {false, true}) {
            MathState state = MathState.newWorld();
            state.signature().setNativeArithmetic(binary);
            for (int i = 0; i < 3000; i++) {
                Formula f = formula(r, 3);
                Map<String, BigInteger> env = new HashMap<>();
                for (String v : VARS) {
                    // Zero often, to exercise 0 × v.
                    env.put(v, BigInteger.valueOf(r.nextInt(3) == 0 ? 0 : r.nextInt(5)));
                }
                Truth.Value claimed;
                boolean actual;
                try {
                    claimed = new Truth(state.signature(), state.deciders(),
                            new Evaluator(state.signature(), 5_000_000), BigInteger.valueOf(20)).evaluate(f, env);
                    if (claimed == Truth.Value.UNKNOWN) {
                        continue;
                    }
                    actual = oracle(f, env, new Evaluator(state.signature(), 50_000_000));
                } catch (Evaluator.OutOfBudget e) {
                    continue;
                }
                assertEquals(actual, claimed == Truth.Value.TRUE, "binary=" + binary + ": " + f + " at " + env);
                decided++;
            }
        }
        assertTrue(decided > 2000, "decided " + decided);
    }
}
