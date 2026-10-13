package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Term.App;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Succ;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Var;

/**
 * Conveniences built only from {@link Kernel} rules, so they need no trust of their own: whatever
 * they produce, the kernel has checked step by step.
 */
public final class Tactics {
    private Tactics() {}

    /**
     * Rewriting with a general equation, like a proof assistant's {@code rw}: the equation's free
     * variables (except those locked by its open assumptions) match any subterm. Matches are
     * numbered in reading order; {@code occurrence} picks one, or all if negative.
     *
     * <p>For example "x + y = y + x" rewrites the second match in "(a + b) + c = c + (a + b)".
     * Done by instantiating the equation at the match (Instantiator) and rewriting with the
     * instance (Rewriter).
     */
    public static Theorem rewriteMatching(Kernel kernel, Theorem target, Theorem equation, boolean rightToLeft,
            int occurrence) {
        if (!(equation.formula() instanceof Formula.Eq eq)) {
            throw new ProofException("The Rewriter needs a proven equation, got: " + equation.formula());
        }
        Term pattern = rightToLeft ? eq.right() : eq.left();
        Set<String> locked = new java.util.HashSet<>();
        equation.hypotheses().forEach(h -> locked.addAll(Syntax.freeVars(h.formula())));
        Set<String> patternVars = new java.util.HashSet<>(Syntax.freeVars(eq));
        patternVars.removeAll(locked);

        // Collect matches in reading order, outside binders that bind a variable of the target term.
        List<Term> subterms = new ArrayList<>();
        collect(target.formula(), subterms, Set.of());
        List<Map<String, Term>> matches = new ArrayList<>();
        List<Term> matched = new ArrayList<>();
        List<Integer> matchPositions = new ArrayList<>();
        for (int i = 0; i < subterms.size(); i++) {
            Term t = subterms.get(i);
            Map<String, Term> sub = new HashMap<>();
            if (match(pattern, t, patternVars, sub)) {
                matches.add(sub);
                matched.add(t);
                matchPositions.add(i);
            }
        }
        if (matches.isEmpty()) {
            throw new ProofException("Nothing matches " + pattern + " in " + target.formula());
        }
        if (occurrence >= matches.size()) {
            throw new ProofException("Only " + matches.size() + " matches of " + pattern);
        }
        Theorem result = target;
        if (occurrence >= 0) {
            Theorem instance = kernel.instantiate(equation, complete(matches.get(occurrence), patternVars));
            Formula.Eq ieq = (Formula.Eq) instance.formula();
            Term from = rightToLeft ? ieq.right() : ieq.left();
            Term to = rightToLeft ? ieq.left() : ieq.right();
            // The statement we want: exactly this match replaced. Find which literal occurrence
            // the kernel's Rewriter must replace to get it.
            int position = matchPositions.get(occurrence);
            Formula wanted = replaceAt(target.formula(), position, to, new int[] {0});
            for (int k = 0; k < subterms.size(); k++) {
                Formula candidate = Syntax.rewrite(target.formula(), from, to, k);
                if (candidate == null) {
                    break;
                }
                if (candidate.equals(wanted)) {
                    return kernel.rewrite(result, instance, rightToLeft, k);
                }
            }
            throw new ProofException("Could not rewrite that match");
        }
        // All matches: rewrite each distinct instance everywhere, left to right.
        Map<Term, Map<String, Term>> distinct = new LinkedHashMap<>();
        for (int i = 0; i < matches.size(); i++) {
            distinct.putIfAbsent(matched.get(i), matches.get(i));
        }
        for (Map<String, Term> sub : distinct.values()) {
            Theorem instance = kernel.instantiate(equation, complete(sub, patternVars));
            try {
                result = kernel.rewrite(result, instance, rightToLeft, -1);
            } catch (ProofException e) {
                // An earlier rewrite already replaced this instance (overlapping matches).
            }
        }
        return result;
    }

    /** Pattern variables that the match did not mention keep their own names. */
    private static Map<String, Term> complete(Map<String, Term> sub, Set<String> patternVars) {
        Map<String, Term> out = new HashMap<>(sub);
        for (String v : patternVars) {
            out.putIfAbsent(v, Term.var(v));
        }
        return out;
    }

    /** Replaces the subterm at pre-order position {@code target} (in {@link #collect} order). */
    private static Formula replaceAt(Formula f, int target, Term to, int[] counter) {
        return switch (f) {
            case Formula.Eq e -> {
                Term l = replaceAt(e.left(), target, to, counter);
                Term r = replaceAt(e.right(), target, to, counter);
                yield Formula.eq(l, r);
            }
            case Formula.And a -> {
                Formula l = replaceAt(a.left(), target, to, counter);
                yield Formula.and(l, replaceAt(a.right(), target, to, counter));
            }
            case Formula.Implies i -> {
                Formula l = replaceAt(i.premise(), target, to, counter);
                yield Formula.implies(l, replaceAt(i.conclusion(), target, to, counter));
            }
            case Formula.Exists ex -> ex;
            case Formula.Bottom b -> b;
            case Formula.Pred p -> {
                List<Term> args = new ArrayList<>();
                for (Term t : p.args()) {
                    args.add(replaceAt(t, target, to, counter));
                }
                yield new Formula.Pred(p.name(), args);
            }
        };
    }

    private static Term replaceAt(Term t, int target, Term to, int[] counter) {
        if (counter[0]++ == target) {
            // Skip the replaced subterm's own subterms in the count, as collect() visits them.
            counter[0] += size(t) - 1;
            return to;
        }
        return switch (t) {
            case Succ s -> Term.succ(replaceAt(s.arg(), target, to, counter));
            case App a -> {
                List<Term> args = new ArrayList<>();
                for (Term x : a.args()) {
                    args.add(replaceAt(x, target, to, counter));
                }
                yield new App(a.function(), args);
            }
            default -> t;
        };
    }

    private static int size(Term t) {
        return switch (t) {
            case Succ s -> 1 + size(s.arg());
            case App a -> 1 + a.args().stream().mapToInt(Tactics::size).sum();
            default -> 1;
        };
    }

    private static void collect(Formula f, List<Term> out, Set<String> bound) {
        switch (f) {
            case Formula.Eq e -> {
                collect(e.left(), out);
                collect(e.right(), out);
            }
            case Formula.And a -> {
                collect(a.left(), out, bound);
                collect(a.right(), out, bound);
            }
            case Formula.Implies i -> {
                collect(i.premise(), out, bound);
                collect(i.conclusion(), out, bound);
            }
            // Under a binder the rewrite rule skips positions anyway; keep the numbering simple.
            case Formula.Exists ex -> {}
            case Formula.Bottom b -> {}
            case Formula.Pred p -> p.args().forEach(t -> collect(t, out));
        }
    }

    private static void collect(Term t, List<Term> out) {
        out.add(t);
        switch (t) {
            case Succ s -> collect(s.arg(), out);
            case App a -> a.args().forEach(x -> collect(x, out));
            default -> {}
        }
    }

    private static boolean match(Term pattern, Term t, Set<String> vars, Map<String, Term> sub) {
        return switch (pattern) {
            case Var v when vars.contains(v.name()) -> {
                Term prev = sub.putIfAbsent(v.name(), t);
                yield prev == null || prev.equals(t);
            }
            case Var v -> t instanceof Var w && w.name().equals(v.name());
            case Term.Zero z -> t instanceof Term.Zero;
            case Succ s -> t instanceof Succ ts && match(s.arg(), ts.arg(), vars, sub);
            case App a -> {
                if (!(t instanceof App ta) || !ta.function().equals(a.function()) || ta.args().size() != a.args().size()) {
                    yield false;
                }
                for (int i = 0; i < a.args().size(); i++) {
                    if (!match(a.args().get(i), ta.args().get(i), vars, sub)) {
                        yield false;
                    }
                }
                yield true;
            }
        };
    }
}
