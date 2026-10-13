package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Recognition of designs: two definitions are the same design when their defining equations are
 * identical after renaming the defined name, its inputs, bound variables, and the definitions they
 * use (compared the same way, recursively). Same design means same function (or property): a
 * definition's equations determine it, since every definition is total.
 *
 * <p>Part of the trusted kernel (used by {@link Kernel#sameDesign}).
 */
final class Designs {
    private final Signature signature;
    /** Pairs already being compared (assumed equal while comparing, for self-reference). */
    private final Map<String, String> assumed = new HashMap<>();

    Designs(Signature signature) {
        this.signature = signature;
    }

    boolean same(String f, String g) {
        if (f.equals(g)) {
            return true;
        }
        if (g.equals(assumed.get(f))) {
            return true;
        }
        Definition df = signature.get(f);
        Definition dg = signature.get(g);
        if (df == null || dg == null || df.getClass() != dg.getClass() || df.params().size() != dg.params().size()) {
            return false;
        }
        if (df instanceof Definition.Abstract) {
            return false;
        }
        assumed.put(f, g);
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i < df.params().size(); i++) {
            vars.put(df.params().get(i), dg.params().get(i));
        }
        boolean result = switch (df) {
            case Definition.Explicit e -> term(e.body(), ((Definition.Explicit) dg).body(), vars);
            case Definition.PrimitiveRecursive r -> {
                var s = (Definition.PrimitiveRecursive) dg;
                yield term(r.base(), s.base(), vars) && term(r.step(), s.step(), vars);
            }
            case Definition.GeneralRecursive r -> {
                var s = (Definition.GeneralRecursive) dg;
                yield term(r.body(), s.body(), vars) && term(r.measure(), s.measure(), vars);
            }
            case Definition.Predicate p -> formula(p.body(), ((Definition.Predicate) dg).body(), vars);
            case Definition.Abstract a -> false;
        };
        assumed.remove(f);
        return result;
    }

    private boolean term(Term a, Term b, Map<String, String> vars) {
        return switch (a) {
            case Term.Zero z -> b instanceof Term.Zero;
            case Term.Succ s -> b instanceof Term.Succ t && term(s.arg(), t.arg(), vars);
            case Term.Var v -> b instanceof Term.Var w && Objects.equals(vars.get(v.name()), w.name());
            case Term.App x -> b instanceof Term.App y && x.args().size() == y.args().size()
                    && same(x.function(), y.function()) && all(x.args(), y.args(), vars);
        };
    }

    private boolean all(List<Term> as, List<Term> bs, Map<String, String> vars) {
        for (int i = 0; i < as.size(); i++) {
            if (!term(as.get(i), bs.get(i), vars)) {
                return false;
            }
        }
        return true;
    }

    private boolean formula(Formula a, Formula b, Map<String, String> vars) {
        return switch (a) {
            case Formula.Eq e -> b instanceof Formula.Eq g && term(e.left(), g.left(), vars) && term(e.right(), g.right(), vars);
            case Formula.And x -> b instanceof Formula.And y && formula(x.left(), y.left(), vars) && formula(x.right(), y.right(), vars);
            case Formula.Implies x -> b instanceof Formula.Implies y && formula(x.premise(), y.premise(), vars)
                    && formula(x.conclusion(), y.conclusion(), vars);
            case Formula.Exists x -> {
                if (!(b instanceof Formula.Exists y)) {
                    yield false;
                }
                Map<String, String> inner = new HashMap<>(vars);
                // The bound variables correspond to each other; nothing else may map to y's.
                inner.values().removeIf(v -> v.equals(y.var()));
                inner.put(x.var(), y.var());
                yield formula(x.body(), y.body(), inner);
            }
            case Formula.Bottom x -> b instanceof Formula.Bottom;
            case Formula.Pred x -> b instanceof Formula.Pred y && x.args().size() == y.args().size()
                    && same(x.name(), y.name()) && all(x.args(), y.args(), vars);
        };
    }
}
