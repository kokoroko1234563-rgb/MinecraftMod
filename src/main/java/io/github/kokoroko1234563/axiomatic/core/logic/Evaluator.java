package io.github.kokoroko1234563.axiomatic.core.logic;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.logic.Term.App;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Succ;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Var;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Zero;

/**
 * Runs circuits on concrete numbers, the way compiled machines compute. Every 0, S and function
 * step costs one unit, which is what the machine pays in energy. Recursion runs as a loop, so
 * large inputs do not overflow the stack, and a budget stops runaway computations.
 */
public final class Evaluator {
    /** Thrown when a computation needs more steps than the budget allows. */
    public static class OutOfBudget extends RuntimeException {
        public OutOfBudget() {
            super("Computation ran out of energy");
        }
    }

    private final Signature signature;
    private final long budget;
    private long steps;

    public Evaluator(Signature signature, long budget) {
        this.signature = signature;
        this.budget = budget;
    }

    /** Steps used so far. */
    public long steps() {
        return steps;
    }

    /** Evaluates a term with the given variable values. */
    public BigInteger eval(Term t, Map<String, BigInteger> env) {
        return eval(t, env, null, null);
    }

    public BigInteger eval(Term t) {
        return eval(t, Map.of());
    }

    // recursiveName/recursiveValue: inside a step case, the call name(x̄, y) one level down.
    private BigInteger eval(Term t, Map<String, BigInteger> env, String recursiveName, BigInteger recursiveValue) {
        tick();
        return switch (t) {
            case Zero z -> BigInteger.ZERO;
            case Succ s -> eval(s.arg(), env, recursiveName, recursiveValue).add(BigInteger.ONE);
            case Var v -> {
                BigInteger value = env.get(v.name());
                if (value == null) {
                    throw new IllegalArgumentException("No value for variable " + v.name());
                }
                yield value;
            }
            case App a -> {
                if (a.function().equals(recursiveName)) {
                    yield recursiveValue;
                }
                List<BigInteger> args = a.args().stream().map(x -> eval(x, env, recursiveName, recursiveValue)).toList();
                yield apply(a.function(), args);
            }
        };
    }

    /** Applies a defined function to concrete inputs. */
    public BigInteger apply(String function, List<BigInteger> args) {
        Definition def = signature.get(function);
        Map<String, BigInteger> env = new HashMap<>();
        for (int i = 0; i < args.size(); i++) {
            env.put(def.params().get(i), args.get(i));
        }
        return switch (def) {
            case Definition.Explicit e -> eval(e.body(), env);
            case Definition.PrimitiveRecursive r -> {
                BigInteger n = env.get(r.recursionParam());
                BigInteger acc = eval(r.base(), env);
                for (BigInteger i = BigInteger.ZERO; i.compareTo(n) < 0; i = i.add(BigInteger.ONE)) {
                    env.put(r.recursionParam(), i);
                    acc = eval(r.step(), env, r.name(), acc);
                }
                yield acc;
            }
            case Definition.Predicate p -> throw new IllegalArgumentException(function + " is not a function");
            case Definition.Abstract a -> throw new IllegalArgumentException(function + " is abstract: it has no value");
        };
    }

    private void tick() {
        if (++steps > budget) {
            throw new OutOfBudget();
        }
    }
}
