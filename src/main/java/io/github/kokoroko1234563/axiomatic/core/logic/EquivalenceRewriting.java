package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Pred;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.App;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Succ;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Var;

/**
 * Rewriting with an equivalence instead of an equation (prime path stage 5): from s ≡ t and a
 * statement u ≡ v (or u = v), replaces matches of s by t inside u or v, giving u' ≡ v'. This is
 * only sound where every function between the statement's root and the match respects ≡, so each
 * one needs a proven congruence law, for example a ≡ b → c ≡ d → a + c ≡ b + d.
 *
 * <p>Like {@link Tactics}, this is not a kernel rule: the result is built from instantiation,
 * modus ponens and the laws themselves, so the kernel checks every step.
 */
public final class EquivalenceRewriting {
    private EquivalenceRewriting() {}

    /**
     * The laws of a relation R: reflexivity R(a, a), symmetry R(a, b) → R(b, a), transitivity
     * R(a, b) → R(b, c) → R(a, c), and any number of congruence laws, one per function respected
     * (for S: R(a, b) → R(S(a), S(b))). Laws may use any variable names.
     */
    public record Laws(String relation, Theorem reflexive, Theorem symmetric, Theorem transitive,
            List<Theorem> congruences) {}

    /** Key for the successor in congruence lookups. */
    private static final String SUCC = "S";

    public static Theorem rewrite(Kernel kernel, Theorem target, Theorem equivalence, Laws laws, boolean rightToLeft,
            int occurrence) {
        String r = laws.relation();
        if (!(equivalence.formula() instanceof Pred eqv) || !eqv.name().equals(r) || eqv.args().size() != 2) {
            throw new ProofException("Rewriting with " + r + " needs a proven " + r + "(s, t), got " + equivalence.formula());
        }
        for (Theorem law : List.of(laws.reflexive(), laws.symmetric(), laws.transitive())) {
            requireClosed(law);
        }
        Term u;
        Term v;
        Theorem start;
        switch (target.formula()) {
            case Pred p when p.name().equals(r) && p.args().size() == 2 -> {
                u = p.args().get(0);
                v = p.args().get(1);
                start = target;
            }
            case Formula.Eq e -> {
                u = e.left();
                v = e.right();
                // u = v gives R(u, v): rewrite the second u in R(u, u).
                Theorem refl = use(kernel, laws.reflexive(), Formula.pred(r, u, u));
                start = kernel.rewrite(refl, target, false, 1);
            }
            default -> throw new ProofException("Rewriting with " + r + " works in a statement " + r + "(u, v) or u = v");
        }

        // The pattern: s (or t), its variables matching anything except those locked by assumptions.
        Term pattern = rightToLeft ? eqv.args().get(1) : eqv.args().get(0);
        Set<String> patternVars = new java.util.HashSet<>(Syntax.freeVars(eqv));
        equivalence.hypotheses().forEach(h -> patternVars.removeAll(Syntax.freeVars(h.formula())));

        // Matches in reading order through u then v; nested matches inside a chosen one are skipped.
        List<Integer> positions = new ArrayList<>();
        List<Map<String, Term>> subs = new ArrayList<>();
        List<Term> all = new ArrayList<>();
        collect(u, all);
        int split = all.size();
        collect(v, all);
        for (int i = 0; i < all.size(); i++) {
            Map<String, Term> sub = new HashMap<>();
            if (match(pattern, all.get(i), patternVars, sub)) {
                positions.add(i);
                subs.add(sub);
            }
        }
        if (positions.isEmpty()) {
            throw new ProofException("Nothing matches " + pattern + " in " + target.formula());
        }
        if (occurrence >= positions.size()) {
            throw new ProofException("Only " + positions.size() + " matches of " + pattern);
        }
        Map<Integer, Theorem> chosen = new LinkedHashMap<>();
        int skipUntil = -1;
        for (int k = 0; k < positions.size(); k++) {
            if (occurrence >= 0 && k != occurrence) {
                continue;
            }
            int pos = positions.get(k);
            if (pos < skipUntil) {
                continue;
            }
            Theorem instance = kernel.instantiate(equivalence, complete(subs.get(k), patternVars));
            if (rightToLeft) {
                Pred ip = (Pred) instance.formula();
                instance = kernel.modusPonens(use(kernel, laws.symmetric(),
                        Formula.implies(ip, Formula.pred(r, ip.args().get(1), ip.args().get(0)))), instance);
            }
            chosen.put(pos, instance);
            skipUntil = pos + size(all.get(pos));
        }

        Theorem uStep = congruent(kernel, laws, u, chosen, new int[] {0});
        Theorem vStep = congruent(kernel, laws, v, shift(chosen, split), new int[] {0});
        Theorem result = start;
        if (uStep != null) {
            // R(u, u') gives R(u', u); with R(u, v), R(u', v).
            Pred step = (Pred) uStep.formula();
            Theorem back = kernel.modusPonens(use(kernel, laws.symmetric(),
                    Formula.implies(step, Formula.pred(r, step.args().get(1), step.args().get(0)))), uStep);
            result = transitive(kernel, laws, back, result);
        }
        if (vStep != null) {
            result = transitive(kernel, laws, result, vStep);
        }
        return result;
    }

    /** From R(a, b) and R(b, c), R(a, c). */
    private static Theorem transitive(Kernel kernel, Laws laws, Theorem ab, Theorem bc) {
        Pred p = (Pred) ab.formula();
        Pred q = (Pred) bc.formula();
        String r = laws.relation();
        Formula wanted = Formula.implies(p, Formula.implies(q, Formula.pred(r, p.args().get(0), q.args().get(1))));
        return kernel.modusPonens(kernel.modusPonens(use(kernel, laws.transitive(), wanted), ab), bc);
    }

    private static Map<Integer, Theorem> shift(Map<Integer, Theorem> chosen, int by) {
        Map<Integer, Theorem> out = new LinkedHashMap<>();
        chosen.forEach((k, t) -> {
            if (k >= by) {
                out.put(k - by, t);
            }
        });
        return out;
    }

    /**
     * R(t, t') where t' replaces the chosen positions (pre-order indexes within t), or null if
     * none lies inside t. Each function on the way needs a congruence law.
     */
    private static Theorem congruent(Kernel kernel, Laws laws, Term t, Map<Integer, Theorem> chosen, int[] counter) {
        int here = counter[0]++;
        Theorem replaced = chosen.get(here);
        if (replaced != null) {
            counter[0] += size(t) - 1;
            return replaced;
        }
        List<Term> args = switch (t) {
            case Succ s -> List.of(s.arg());
            case App a -> a.args();
            default -> List.of();
        };
        List<Theorem> steps = new ArrayList<>();
        boolean changed = false;
        for (Term arg : args) {
            Theorem step = congruent(kernel, laws, arg, chosen, counter);
            changed |= step != null;
            steps.add(step);
        }
        if (!changed) {
            return null;
        }
        String r = laws.relation();
        List<Term> newArgs = new ArrayList<>();
        List<Formula> premises = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            Theorem step = steps.get(i);
            if (step == null) {
                step = use(kernel, laws.reflexive(), Formula.pred(r, args.get(i), args.get(i)));
                steps.set(i, step);
            }
            Pred p = (Pred) step.formula();
            premises.add(p);
            newArgs.add(p.args().get(1));
        }
        String function = t instanceof App a ? a.function() : SUCC;
        Term rebuilt = t instanceof App a ? new App(a.function(), newArgs) : Term.succ(newArgs.get(0));
        Formula wanted = Formula.pred(r, t, rebuilt);
        for (int i = premises.size() - 1; i >= 0; i--) {
            wanted = Formula.implies(premises.get(i), wanted);
        }
        Theorem law = null;
        for (Theorem candidate : laws.congruences()) {
            if (candidate.isClosed() && congruenceFunction(candidate.formula(), r, args.size()) instanceof String f
                    && f.equals(function)) {
                try {
                    law = use(kernel, candidate, wanted);
                    break;
                } catch (ProofException e) {
                    // Not this one.
                }
            }
        }
        if (law == null) {
            throw new ProofException("No proven law says " + r + " respects " + function
                    + " (needed to rewrite inside it)");
        }
        for (Theorem step : steps) {
            law = kernel.modusPonens(law, step);
        }
        return law;
    }

    /** If {@code f} has a congruence law's shape for R and n arguments, the function it is about. */
    public static String congruenceFunction(Formula f, String relation, int arity) {
        for (int i = 0; i < arity; i++) {
            if (!(f instanceof Formula.Implies imp) || !(imp.premise() instanceof Pred p) || !p.name().equals(relation)) {
                return null;
            }
            f = imp.conclusion();
        }
        if (f instanceof Pred p && p.name().equals(relation) && p.args().size() == 2) {
            Term left = p.args().get(0);
            if (left instanceof App a && a.args().size() == arity && p.args().get(1) instanceof App b
                    && b.function().equals(a.function())) {
                return a.function();
            }
            if (arity == 1 && left instanceof Succ && p.args().get(1) instanceof Succ) {
                return SUCC;
            }
        }
        return null;
    }

    /**
     * Instantiates a law to exactly {@code wanted}: the law's free variables are matched against
     * {@code wanted}, and the kernel does the instantiation.
     */
    private static Theorem use(Kernel kernel, Theorem law, Formula wanted) {
        requireClosed(law);
        Map<String, Term> sub = new HashMap<>();
        if (!matchFormula(law.formula(), wanted, Syntax.freeVars(law.formula()), sub)) {
            throw new ProofException("The law " + law.formula() + " does not give " + wanted);
        }
        Theorem instance = kernel.instantiate(law, complete(sub, Syntax.freeVars(law.formula())));
        if (!Syntax.alphaEquals(instance.formula(), wanted)) {
            throw new ProofException("The law " + law.formula() + " does not give " + wanted);
        }
        return instance;
    }

    private static void requireClosed(Theorem law) {
        if (!law.isClosed()) {
            throw new ProofException("A law must not rest on open assumptions: " + law.formula());
        }
    }

    private static boolean matchFormula(Formula pattern, Formula f, Set<String> vars, Map<String, Term> sub) {
        return switch (pattern) {
            case Formula.Eq e -> f instanceof Formula.Eq g && match(e.left(), g.left(), vars, sub)
                    && match(e.right(), g.right(), vars, sub);
            case Formula.And a -> f instanceof Formula.And b && matchFormula(a.left(), b.left(), vars, sub)
                    && matchFormula(a.right(), b.right(), vars, sub);
            case Formula.Implies a -> f instanceof Formula.Implies b && matchFormula(a.premise(), b.premise(), vars, sub)
                    && matchFormula(a.conclusion(), b.conclusion(), vars, sub);
            case Formula.Bottom b -> f instanceof Formula.Bottom;
            case Pred p -> {
                if (!(f instanceof Pred q) || !q.name().equals(p.name()) || q.args().size() != p.args().size()) {
                    yield false;
                }
                for (int i = 0; i < p.args().size(); i++) {
                    if (!match(p.args().get(i), q.args().get(i), vars, sub)) {
                        yield false;
                    }
                }
                yield true;
            }
            // Laws with binders are matched only literally; the alpha check after instantiating decides.
            case Formula.Exists ex -> f instanceof Formula.Exists;
        };
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

    private static Map<String, Term> complete(Map<String, Term> sub, Set<String> vars) {
        Map<String, Term> out = new HashMap<>(sub);
        for (String v : vars) {
            out.putIfAbsent(v, Term.var(v));
        }
        return out;
    }

    private static void collect(Term t, List<Term> out) {
        out.add(t);
        switch (t) {
            case Succ s -> collect(s.arg(), out);
            case App a -> a.args().forEach(x -> collect(x, out));
            default -> {}
        }
    }

    private static int size(Term t) {
        return switch (t) {
            case Succ s -> 1 + size(s.arg());
            case App a -> 1 + a.args().stream().mapToInt(EquivalenceRewriting::size).sum();
            default -> 1;
        };
    }
}
