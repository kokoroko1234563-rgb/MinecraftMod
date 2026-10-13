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
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/**
 * Capture-avoiding substitution, checked by meaning: φ[x := s] must hold exactly when φ holds
 * with x set to the value of s, even when s mentions variables that φ binds. Bounded ∃ (via ≤)
 * keeps every formula decidable, so the comparison is exact.
 */
class SubstitutionFuzzTest {
    private static final List<String> VARS = List.of("x", "y", "z", "k");
    private final MathState state = MathState.newWorld();

    private Term term(Random r, int depth) {
        int pick = depth <= 0 ? r.nextInt(2) : r.nextInt(5);
        return switch (pick) {
            case 0 -> Term.var(VARS.get(r.nextInt(VARS.size())));
            case 1 -> Term.numeral(r.nextInt(3));
            case 2 -> Term.succ(term(r, depth - 1));
            case 3 -> Term.app("add", term(r, depth - 1), term(r, depth - 1));
            default -> Term.app("mul", term(r, depth - 1), term(r, depth - 1));
        };
    }

    private Formula formula(Random r, int depth) {
        int pick = depth <= 0 ? 0 : r.nextInt(5);
        return switch (pick) {
            case 0 -> Formula.eq(term(r, 2), term(r, 2));
            case 1 -> Formula.and(formula(r, depth - 1), formula(r, depth - 1));
            case 2 -> Formula.implies(formula(r, depth - 1), formula(r, depth - 1));
            case 3 -> Formula.pred("le", term(r, 1), term(r, 1));
            default -> {
                String v = VARS.get(r.nextInt(VARS.size()));
                // ∃v. v ≤ 4 ∧ φ: bounded, so decidable.
                yield Formula.exists(v, Formula.and(Formula.pred("le", Term.var(v), Term.numeral(4)), formula(r, depth - 1)));
            }
        };
    }

    private Truth.Value truth(Formula f, Map<String, BigInteger> env) {
        Evaluator ev = new Evaluator(state.signature(), 50_000_000);
        return new Truth(state.signature(), state.deciders(), ev, BigInteger.valueOf(50)).evaluate(f, env);
    }

    @Test
    void substitutionPreservesMeaning() {
        Random r = new Random(20261013);
        int checked = 0;
        for (int i = 0; i < 1500; i++) {
            Formula f = formula(r, 3);
            String x = VARS.get(r.nextInt(VARS.size()));
            Term s = term(r, 2);
            Map<String, BigInteger> env = new HashMap<>();
            for (String v : VARS) {
                env.put(v, BigInteger.valueOf(r.nextInt(4)));
            }
            Evaluator ev = new Evaluator(state.signature(), 1_000_000);
            Map<String, BigInteger> shifted = new HashMap<>(env);
            shifted.put(x, ev.eval(s, env));
            Formula substituted = Syntax.substitute(f, x, s);
            Map<String, BigInteger> envWide = new HashMap<>(env);
            for (String v : Syntax.freeVars(substituted)) {
                envWide.putIfAbsent(v, BigInteger.ZERO);
            }
            Truth.Value direct = truth(substituted, envWide);
            Truth.Value meant = truth(f, shifted);
            if (direct != Truth.Value.UNKNOWN && meant != Truth.Value.UNKNOWN) {
                assertEquals(meant, direct, "φ = " + f + ", " + x + " := " + s + ", env " + env + ", φ[x:=s] = " + substituted);
                checked++;
            }
            // Free variables after substitution: those of φ without x, plus those of s if x was free.
            var expectedFree = new java.util.HashSet<>(Syntax.freeVars(f));
            boolean xFree = expectedFree.remove(x);
            if (xFree) {
                expectedFree.addAll(Syntax.freeVars(s));
            }
            assertEquals(expectedFree, Syntax.freeVars(substituted), "free variables of " + substituted);
        }
        assertTrue(checked > 1000, "decided " + checked + " cases");
    }

    @Test
    void substitutingForAnAbsentVariableChangesNothing() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            Formula f = formula(r, 3);
            // "unused" is not free in f: the result must be the same formula (up to bound names).
            Formula g = Syntax.substitute(f, "unused", Term.var("x"));
            assertTrue(Syntax.alphaEquals(f, g), f + " vs " + g);
            Map<String, BigInteger> env = new HashMap<>();
            for (String v : VARS) {
                env.put(v, BigInteger.valueOf(r.nextInt(4)));
            }
            assertEquals(truth(f, env), truth(g, env));
        }
    }
}
