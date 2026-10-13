package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * Soundness, tested by meaning: random chains of instantiation, rewriting, ∧ rules, assumptions,
 * discharge and modus ponens, starting
 * from the definitions of + and × and reflexivity, must only ever produce statements that are true
 * for every assignment tried. A wrong rule would show up as a false theorem.
 */
class KernelFuzzTest {
    private static final List<String> VARS = List.of("a", "b", "n");

    private Term term(Random r, int depth) {
        int pick = depth <= 0 ? r.nextInt(2) : r.nextInt(4);
        return switch (pick) {
            case 0 -> Term.var(VARS.get(r.nextInt(VARS.size())));
            case 1 -> Term.numeral(r.nextInt(3));
            case 2 -> Term.succ(term(r, depth - 1));
            default -> Term.app(r.nextBoolean() ? "add" : "mul", term(r, depth - 1), term(r, depth - 1));
        };
    }

    @Test
    void derivedTheoremsAreTrue() {
        MathState state = MathState.newWorld();
        Kernel k = state.kernel();
        Random r = new Random(42);
        List<Theorem> pool = new ArrayList<>();
        pool.addAll(k.definingStatements("add"));
        pool.addAll(k.definingStatements("mul"));
        int derived = 0;
        for (int i = 0; i < 4000; i++) {
            Theorem t;
            try {
                t = switch (r.nextInt(9)) {
                    case 0 -> k.reflexivity(term(r, 2));
                    case 1 -> {
                        Theorem p = pool.get(r.nextInt(pool.size()));
                        yield k.instantiate(p, VARS.get(r.nextInt(VARS.size())), term(r, 2));
                    }
                    case 2, 3 -> {
                        Theorem target = pool.get(r.nextInt(pool.size()));
                        Theorem eq = pool.get(r.nextInt(pool.size()));
                        yield k.rewrite(target, eq, r.nextBoolean(), r.nextInt(3) - 1);
                    }
                    case 4 -> k.andIntro(pool.get(r.nextInt(pool.size())), pool.get(r.nextInt(pool.size())));
                    case 5 -> {
                        Theorem p = pool.get(r.nextInt(pool.size()));
                        yield p.formula() instanceof Formula.And ? (r.nextBoolean() ? k.andLeft(p) : k.andRight(p)) : p;
                    }
                    case 6 -> k.assume(Formula.eq(term(r, 1), term(r, 1)));
                    case 7 -> {
                        Theorem p = pool.get(r.nextInt(pool.size()));
                        if (p.hypotheses().isEmpty()) {
                            yield p;
                        }
                        yield k.discharge(p, p.hypotheses().get(r.nextInt(p.hypotheses().size())));
                    }
                    default -> k.modusPonens(pool.get(r.nextInt(pool.size())), pool.get(r.nextInt(pool.size())));
                };
            } catch (ProofException e) {
                continue;
            }
            if (Syntax.freeVars(t.formula()).size() > 3 || t.formula().toString().length() > 400) {
                continue;
            }
            pool.add(t);
            derived++;
            for (int trial = 0; trial < 3; trial++) {
                Map<String, BigInteger> env = new HashMap<>();
                for (String v : VARS) {
                    env.put(v, BigInteger.valueOf(r.nextInt(5)));
                }
                Evaluator ev = new Evaluator(state.signature(), 10_000_000);
                Truth truth = new Truth(state.signature(), state.deciders(), ev, BigInteger.TEN);
                // Wherever the open hypotheses hold, the statement must hold.
                boolean hypothesesHold = t.hypotheses().stream()
                        .allMatch(hyp -> truth.evaluate(hyp.formula(), env) == Truth.Value.TRUE);
                if (!hypothesesHold) {
                    continue;
                }
                Truth.Value v = truth.evaluate(t.formula(), env);
                assertTrue(v != Truth.Value.FALSE, "kernel derived a false statement: " + t.formula()
                        + " from " + t.hypotheses() + " at " + env);
            }
        }
        assertTrue(derived > 1000, "derived " + derived);
    }
}
