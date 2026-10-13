package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;

/**
 * The Test Bench's range mode: checks a statement for every value of its free variables from 0 up
 * to a bound, case by case. With k variables that is (bound + 1)^k cases, which is the point:
 * brute force explodes, proof does not.
 */
public final class TestBench {
    private TestBench() {}

    public sealed interface Result permits Passed, Counterexample, Inconclusive {}

    /** Every case up to {@code upTo} held. */
    public record Passed(BigInteger upTo, long cases) implements Result {}

    /** This case is false: the statement is refuted. */
    public record Counterexample(Map<String, BigInteger> assignment) implements Result {}

    /** This case could not be settled (an ∃ found no witness below the search cap). */
    public record Inconclusive(Map<String, BigInteger> assignment) implements Result {}

    public static Result test(Formula statement, BigInteger upTo, Truth truth) {
        List<String> vars = new ArrayList<>(Syntax.freeVars(statement));
        BigInteger[] values = new BigInteger[vars.size()];
        java.util.Arrays.fill(values, BigInteger.ZERO);
        long cases = 0;
        while (true) {
            Map<String, BigInteger> env = new LinkedHashMap<>();
            for (int i = 0; i < vars.size(); i++) {
                env.put(vars.get(i), values[i]);
            }
            cases++;
            switch (truth.evaluate(statement, env)) {
                case FALSE -> {
                    return new Counterexample(env);
                }
                case UNKNOWN -> {
                    return new Inconclusive(env);
                }
                case TRUE -> {}
            }
            // Odometer: advance the last variable, carrying into earlier ones.
            int i = vars.size() - 1;
            while (i >= 0 && values[i].compareTo(upTo) >= 0) {
                values[i] = BigInteger.ZERO;
                i--;
            }
            if (i < 0) {
                return new Passed(upTo, cases);
            }
            values[i] = values[i].add(BigInteger.ONE);
        }
    }
}
