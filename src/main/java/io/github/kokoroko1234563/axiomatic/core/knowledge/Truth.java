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
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

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
    private String ignoredDependency;

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

    /**
     * Do not use deciders whose link depends on {@code dependency}. Checking a conjecture must not
     * rely on a decider linked by that same conjecture, or it could never fail.
     */
    public Truth ignoringDecidersFrom(String dependency) {
        this.ignoredDependency = dependency;
        return this;
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
                Term boundTerm = Bounds.bound(signature, ex);
                if (boundTerm != null) {
                    // A bounded ∃: searching up to the bound settles it either way.
                    BigInteger bound = evaluator.eval(boundTerm, env);
                    boolean unknown = false;
                    for (BigInteger k = BigInteger.ZERO; k.compareTo(bound) <= 0; k = k.add(BigInteger.ONE)) {
                        inner.put(ex.var(), k);
                        Value v = evaluate(ex.body(), inner);
                        if (v == Value.TRUE) {
                            yield Value.TRUE;
                        }
                        unknown |= v == Value.UNKNOWN;
                    }
                    yield unknown ? Value.UNKNOWN : Value.FALSE;
                }
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
        if (link != null && ignoredDependency != null && link.dependencies().contains(ignoredDependency)) {
            link = null;
        }
        if (link != null) {
            dependencies.addAll(link.dependencies());
            return Value.of(evaluator.apply(link.function(), args).equals(BigInteger.ONE));
        }
        if (!(signature.get(name) instanceof Definition.Predicate def)) {
            // An abstract relation has no meaning of its own to compute.
            return Value.UNKNOWN;
        }
        Map<String, BigInteger> env = new HashMap<>();
        for (int i = 0; i < args.size(); i++) {
            env.put(def.params().get(i), args.get(i));
        }
        return evaluate(def.body(), env);
    }

    /**
     * True when computation can catch {@code f} failing on at least some numbers, which is the
     * Conjecture Bench's rule: a statement no machine could ever catch failing (such as "∀n.
     * ∃k. n = k + k") is refused. Prime(n) := 2 ≤ n ∧ ¬∃d. … qualifies, because finding a divisor
     * shows it false, even though 2 ≤ n on its own could never be shown false by search.
     */
    public static boolean isRefutable(Formula f, Signature signature, Deciders deciders) {
        return canBeFalse(f, signature, deciders);
    }

    private static boolean canBeFalse(Formula f, Signature sig, Deciders d) {
        return switch (f) {
            case Formula.Eq e -> true;
            case Formula.Bottom b -> true;
            case Formula.And a -> canBeFalse(a.left(), sig, d) || canBeFalse(a.right(), sig, d);
            case Formula.Implies i -> canBeTrue(i.premise(), sig, d) && canBeFalse(i.conclusion(), sig, d);
            // Not finding a witness shows an ∃ false only when it is bounded.
            case Formula.Exists ex -> Bounds.bound(sig, ex) != null && canBeFalse(ex.body(), sig, d);
            // Definitions cannot refer to themselves, so unfolding always ends.
            case Formula.Pred p -> d.get(p.name()) != null
                    || (sig.get(p.name()) instanceof Definition.Predicate && canBeFalse(body(sig, p), sig, d));
        };
    }

    private static boolean canBeTrue(Formula f, Signature sig, Deciders d) {
        return switch (f) {
            case Formula.Eq e -> true;
            case Formula.Bottom b -> false;
            case Formula.And a -> canBeTrue(a.left(), sig, d) && canBeTrue(a.right(), sig, d);
            case Formula.Implies i -> canBeFalse(i.premise(), sig, d) || canBeTrue(i.conclusion(), sig, d);
            case Formula.Exists ex -> canBeTrue(ex.body(), sig, d);
            case Formula.Pred p -> d.get(p.name()) != null
                    || (sig.get(p.name()) instanceof Definition.Predicate && canBeTrue(body(sig, p), sig, d));
        };
    }

    private static Formula body(Signature sig, Formula.Pred p) {
        return ((Definition.Predicate) sig.get(p.name())).body();
    }
}
