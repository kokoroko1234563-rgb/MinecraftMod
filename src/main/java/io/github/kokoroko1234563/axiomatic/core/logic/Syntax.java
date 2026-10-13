package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula.And;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Bottom;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Eq;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Exists;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Implies;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Pred;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.App;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Succ;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Var;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Zero;

/** Syntactic operations: free variables, capture-avoiding substitution, alpha-equivalence, rewriting. */
public final class Syntax {
    private Syntax() {}

    /** Player-chosen variable and definition names. Kernel-made names contain '#', so they never collide. */
    public static boolean isValidName(String name) {
        return name.matches("[A-Za-z][A-Za-z0-9_]*");
    }

    // ---- Free variables -------------------------------------------------------------------

    public static Set<String> freeVars(Term t) {
        Set<String> out = new LinkedHashSet<>();
        collect(t, out);
        return out;
    }

    public static Set<String> freeVars(Formula f) {
        Set<String> out = new LinkedHashSet<>();
        collect(f, Set.of(), out);
        return out;
    }

    private static void collect(Term t, Set<String> out) {
        switch (t) {
            case Zero z -> {}
            case Succ s -> collect(s.arg(), out);
            case Var v -> out.add(v.name());
            case App a -> a.args().forEach(arg -> collect(arg, out));
        }
    }

    private static void collect(Formula f, Set<String> bound, Set<String> out) {
        switch (f) {
            case Eq e -> {
                addUnbound(e.left(), bound, out);
                addUnbound(e.right(), bound, out);
            }
            case And a -> {
                collect(a.left(), bound, out);
                collect(a.right(), bound, out);
            }
            case Implies i -> {
                collect(i.premise(), bound, out);
                collect(i.conclusion(), bound, out);
            }
            case Exists ex -> {
                Set<String> inner = new LinkedHashSet<>(bound);
                inner.add(ex.var());
                collect(ex.body(), inner, out);
            }
            case Bottom b -> {}
            case Pred p -> p.args().forEach(arg -> addUnbound(arg, bound, out));
        }
    }

    private static void addUnbound(Term t, Set<String> bound, Set<String> out) {
        for (String v : freeVars(t)) {
            if (!bound.contains(v)) {
                out.add(v);
            }
        }
    }

    // ---- Substitution ---------------------------------------------------------------------

    public static Term substitute(Term t, String var, Term replacement) {
        return switch (t) {
            case Zero z -> z;
            case Succ s -> new Succ(substitute(s.arg(), var, replacement));
            case Var v -> v.name().equals(var) ? replacement : v;
            case App a -> new App(a.function(), a.args().stream().map(x -> substitute(x, var, replacement)).toList());
        };
    }

    /** Replaces free occurrences of {@code var}, renaming bound variables so nothing is captured. */
    public static Formula substitute(Formula f, String var, Term replacement) {
        return switch (f) {
            case Eq e -> new Eq(substitute(e.left(), var, replacement), substitute(e.right(), var, replacement));
            case And a -> new And(substitute(a.left(), var, replacement), substitute(a.right(), var, replacement));
            case Implies i -> new Implies(substitute(i.premise(), var, replacement),
                    substitute(i.conclusion(), var, replacement));
            case Exists ex -> {
                if (ex.var().equals(var) || !freeVars(ex.body()).contains(var)) {
                    yield ex;
                }
                Set<String> replacementVars = freeVars(replacement);
                if (!replacementVars.contains(ex.var())) {
                    yield new Exists(ex.var(), substitute(ex.body(), var, replacement));
                }
                Set<String> avoid = new LinkedHashSet<>(replacementVars);
                avoid.addAll(freeVars(ex.body()));
                avoid.add(var);
                String fresh = freshName(ex.var(), avoid);
                Formula renamed = substitute(ex.body(), ex.var(), new Var(fresh));
                yield new Exists(fresh, substitute(renamed, var, replacement));
            }
            case Bottom b -> b;
            case Pred p -> new Pred(p.name(), p.args().stream().map(x -> substitute(x, var, replacement)).toList());
        };
    }

    static String freshName(String base, Set<String> avoid) {
        String candidate = base + "'";
        while (avoid.contains(candidate)) {
            candidate += "'";
        }
        return candidate;
    }

    // ---- Alpha-equivalence ----------------------------------------------------------------

    /** True when the formulas are equal up to renaming of bound (∃) variables. */
    public static boolean alphaEquals(Formula a, Formula b) {
        return canonical(a, new HashMap<>(), 0).equals(canonical(b, new HashMap<>(), 0));
    }

    /**
     * A key that is equal for statements that differ only in variable names, free or bound:
     * "Even(Double(n))" and "Even(Double(m))" are the same design property.
     */
    public static String key(Formula f) {
        Map<String, String> renaming = new HashMap<>();
        int i = 0;
        for (String v : freeVars(f)) {
            renaming.put(v, "$" + i++);
        }
        return canonical(f, renaming, 0).toString();
    }

    private static Formula canonical(Formula f, Map<String, String> renaming, int depth) {
        return switch (f) {
            case Eq e -> new Eq(rename(e.left(), renaming), rename(e.right(), renaming));
            case And a -> new And(canonical(a.left(), renaming, depth), canonical(a.right(), renaming, depth));
            case Implies i -> new Implies(canonical(i.premise(), renaming, depth),
                    canonical(i.conclusion(), renaming, depth));
            case Exists ex -> {
                Map<String, String> inner = new HashMap<>(renaming);
                String name = "#" + depth;
                inner.put(ex.var(), name);
                yield new Exists(name, canonical(ex.body(), inner, depth + 1));
            }
            case Bottom b -> b;
            case Pred p -> new Pred(p.name(), p.args().stream().map(x -> rename(x, renaming)).toList());
        };
    }

    private static Term rename(Term t, Map<String, String> renaming) {
        return switch (t) {
            case Zero z -> z;
            case Succ s -> new Succ(rename(s.arg(), renaming));
            case Var v -> renaming.containsKey(v.name()) ? new Var(renaming.get(v.name())) : v;
            case App a -> new App(a.function(), a.args().stream().map(x -> rename(x, renaming)).toList());
        };
    }

    // ---- Rewriting ------------------------------------------------------------------------

    /**
     * Replaces occurrences of {@code from} with {@code to}. Occurrences are numbered in pre-order
     * (left to right, outer before inner); {@code occurrence < 0} replaces all of them. Positions
     * under an ∃ that binds a variable of {@code from} or {@code to} are skipped, because there
     * the variable means something else.
     *
     * @return the rewritten formula, or null when no occurrence was replaced
     */
    public static Formula rewrite(Formula f, Term from, Term to, int occurrence) {
        Set<String> eqVars = new LinkedHashSet<>(freeVars(from));
        eqVars.addAll(freeVars(to));
        int[] counter = {0};
        boolean[] changed = {false};
        Formula result = rewrite(f, from, to, occurrence, eqVars, counter, changed);
        return changed[0] ? result : null;
    }

    private static Formula rewrite(Formula f, Term from, Term to, int occ, Set<String> eqVars, int[] counter,
            boolean[] changed) {
        return switch (f) {
            case Eq e -> new Eq(rewrite(e.left(), from, to, occ, counter, changed),
                    rewrite(e.right(), from, to, occ, counter, changed));
            case And a -> new And(rewrite(a.left(), from, to, occ, eqVars, counter, changed),
                    rewrite(a.right(), from, to, occ, eqVars, counter, changed));
            case Implies i -> new Implies(rewrite(i.premise(), from, to, occ, eqVars, counter, changed),
                    rewrite(i.conclusion(), from, to, occ, eqVars, counter, changed));
            case Exists ex -> eqVars.contains(ex.var())
                    ? ex
                    : new Exists(ex.var(), rewrite(ex.body(), from, to, occ, eqVars, counter, changed));
            case Bottom b -> b;
            case Pred p -> new Pred(p.name(),
                    p.args().stream().map(x -> rewrite(x, from, to, occ, counter, changed)).toList());
        };
    }

    private static Term rewrite(Term t, Term from, Term to, int occ, int[] counter, boolean[] changed) {
        if (t.equals(from)) {
            int index = counter[0]++;
            if (occ < 0 || occ == index) {
                changed[0] = true;
                return to;
            }
        }
        return switch (t) {
            case Zero z -> z;
            case Var v -> v;
            case Succ s -> new Succ(rewrite(s.arg(), from, to, occ, counter, changed));
            case App a -> {
                List<Term> args = new ArrayList<>();
                for (Term arg : a.args()) {
                    args.add(rewrite(arg, from, to, occ, counter, changed));
                }
                yield new App(a.function(), args);
            }
        };
    }
}
