package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;

/**
 * Decides statements about concrete numbers by computation, the way the Test Bench and the
 * background checks of conjectures do. The answer is three-valued: an ∃ is settled TRUE by finding
 * a witness below the search cap, but not finding one only gives UNKNOWN, since a witness could
 * be larger. Properties with a linked decider are computed with it; others are unfolded.
 */
public final class Truth {
    public enum Value {
        TRUE, FALSE, UNKNOWN;

        static Value of(boolean b) {
            return b ? TRUE : FALSE;
        }
    }

    private final Signature signature;
    private final Deciders deciders;
    private final Evaluator evaluator;
    private final BigInteger searchCap;
    private final Set<String> dependencies = new HashSet<>();

    /**
     * @param evaluator does the computation and counts its cost; its budget bounds the total work
     * @param searchCap ∃ witnesses are searched from 0 up to and including this value
     */
    public Truth(Signature signature, Deciders deciders, Evaluator evaluator, BigInteger searchCap) {
        this.signature = signature;
        this.deciders = deciders;
        this.evaluator = evaluator;
        this.searchCap = searchCap;
    }

    /** Link-theorem dependencies of the deciders used so far (a conjectural link taints the answer). */
    public Set<String> dependencies() {
        return dependencies;
    }

    public Evaluator evaluator() {
        return evaluator;
    }

    /** Evaluates {@code f}; every free variable must have a value in {@code env}. */
    public Value evaluate(Formula f, Map<String, BigInteger> env) {
        return switch (f) {
            case Formula.Eq e -> Value.of(evaluator.eval(e.left(), env).equals(evaluator.eval(e.right(), env)));
            case Formula.Bottom b -> Value.FALSE;
            case Formula.And a -> {
                Value l = evaluate(a.left(), env);
                if (l == Value.FALSE) {
                    yield Value.FALSE;
                }
                Value r = evaluate(a.right(), env);
                yield r == Value.FALSE ? Value.FALSE : (l == Value.TRUE && r == Value.TRUE ? Value.TRUE : Value.UNKNOWN);
            }
            case Formula.Implies i -> {
                Value p = evaluate(i.premise(), env);
                if (p == Value.FALSE) {
                    yield Value.TRUE;
                }
                Value q = evaluate(i.conclusion(), env);
                yield q == Value.TRUE ? Value.TRUE : (p == Value.TRUE && q == Value.FALSE ? Value.FALSE : Value.UNKNOWN);
            }
            case Formula.Exists ex -> {
                Map<String, BigInteger> inner = new HashMap<>(env);
                for (BigInteger k = BigInteger.ZERO; k.compareTo(searchCap) <= 0; k = k.add(BigInteger.ONE)) {
                    inner.put(ex.var(), k);
                    if (evaluate(ex.body(), inner) == Value.TRUE) {
                        yield Value.TRUE;
                    }
                }
                yield Value.UNKNOWN;
            }
            case Formula.Pred p -> {
                List<BigInteger> args = p.args().stream().map(t -> evaluator.eval(t, env)).toList();
                yield property(p.name(), args);
            }
        };
    }

    /** Whether the property or relation holds for these numbers. */
    public Value property(String name, List<BigInteger> args) {
        Deciders.Link link = deciders.get(name);
        if (link != null) {
            dependencies.addAll(link.dependencies());
            return Value.of(evaluator.apply(link.function(), args).equals(BigInteger.ONE));
        }
        Definition.Predicate def = (Definition.Predicate) signature.get(name);
        Map<String, BigInteger> env = new HashMap<>();
        for (int i = 0; i < args.size(); i++) {
            env.put(def.params().get(i), args.get(i));
        }
        return evaluate(def.body(), env);
    }

    /**
     * True when a false instance of {@code f} can always be detected by computation: every ∃ it
     * relies on positively is behind a linked decider. Only such statements may be conjectured,
     * since no machine could ever catch the others failing.
     */
    public static boolean isRefutable(Formula f, Signature signature, Deciders deciders) {
        return refutable(f, true, signature, deciders, new HashSet<>());
    }

    private static boolean refutable(Formula f, boolean positive, Signature sig, Deciders deciders, Set<String> seen) {
        return switch (f) {
            case Formula.Eq e -> true;
            case Formula.Bottom b -> true;
            case Formula.And a -> refutable(a.left(), positive, sig, deciders, seen)
                    && refutable(a.right(), positive, sig, deciders, seen);
            case Formula.Implies i -> refutable(i.premise(), !positive, sig, deciders, seen)
                    && refutable(i.conclusion(), positive, sig, deciders, seen);
            // A positive ∃ that is false can never be shown false by search; a negative one is
            // refuted by finding its witness.
            case Formula.Exists ex -> !positive && refutable(ex.body(), positive, sig, deciders, seen);
            case Formula.Pred p -> {
                if (deciders.get(p.name()) != null) {
                    yield true;
                }
                String key = p.name() + (positive ? "+" : "-");
                if (!seen.add(key)) {
                    yield true;
                }
                yield refutable(((Definition.Predicate) sig.get(p.name())).body(), positive, sig, deciders, seen);
            }
        };
    }
}
