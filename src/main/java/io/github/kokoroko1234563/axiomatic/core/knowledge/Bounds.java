package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/**
 * Bounded ∃: an ∃v. φ where a conjunct of φ forces v ≤ t for a term t without v. Then searching
 * v = 0 … t settles it, so "no witness" really means false. The conjuncts recognised are
 * x + v = t, x × v = t (either order, either side) and S…S(v) ≤ t, and only when +, × and ≤ are
 * defined exactly as addition, multiplication and ∃k. a + k = b (checked by shape, whatever their
 * names), since the bound follows from what they mean.
 */
public final class Bounds {
    private Bounds() {}

    /** The term bounding the ∃'s variable, or null if none is recognised. */
    public static Term bound(Signature sig, Formula.Exists ex) {
        String v = ex.var();
        for (Formula c : conjuncts(ex.body())) {
            Term t = boundFrom(sig, v, c);
            if (t != null && !Syntax.freeVars(t).contains(v)) {
                return t;
            }
        }
        return null;
    }

    private static List<Formula> conjuncts(Formula f) {
        List<Formula> out = new ArrayList<>();
        if (f instanceof Formula.And a) {
            out.addAll(conjuncts(a.left()));
            out.addAll(conjuncts(a.right()));
        } else {
            out.add(f);
        }
        return out;
    }

    private static Term boundFrom(Signature sig, String v, Formula c) {
        if (c instanceof Formula.Eq e) {
            Term fromLeft = sumOrProductOf(sig, v, e.left()) ? e.right() : null;
            if (fromLeft != null) {
                return fromLeft;
            }
            return sumOrProductOf(sig, v, e.right()) ? e.left() : null;
        }
        if (c instanceof Formula.Pred p && p.args().size() == 2 && isLe(sig, p.name())) {
            Term s = p.args().get(0);
            while (s instanceof Term.Succ su) {
                s = su.arg();
            }
            return s instanceof Term.Var var && var.name().equals(v) ? p.args().get(1) : null;
        }
        return null;
    }

    /** t is x + v, v + x, x × v or v × x for canonical + and ×. */
    private static boolean sumOrProductOf(Signature sig, String v, Term t) {
        if (!(t instanceof Term.App a) || a.args().size() != 2) {
            return false;
        }
        if (!isAdd(sig, a.function()) && !isMul(sig, a.function())) {
            return false;
        }
        Term.Var var = new Term.Var(v);
        return a.args().get(0).equals(var) || a.args().get(1).equals(var);
    }

    /** a + 0 = a, a + S(b) = S(a + b). */
    public static boolean isAdd(Signature sig, String name) {
        return sig.get(name) instanceof Definition.PrimitiveRecursive r && r.params().size() == 2
                && r.base().equals(new Term.Var(r.params().get(0)))
                && r.step().equals(new Term.Succ(new Term.App(name,
                        List.of(new Term.Var(r.params().get(0)), new Term.Var(r.params().get(1))))));
    }

    /** a × 0 = 0, a × S(b) = (a × b) + a, with canonical +. */
    public static boolean isMul(Signature sig, String name) {
        return sig.get(name) instanceof Definition.PrimitiveRecursive r && r.params().size() == 2
                && r.base() instanceof Term.Zero
                && r.step() instanceof Term.App add && isAdd(sig, add.function()) && add.args().size() == 2
                && add.args().get(0).equals(new Term.App(name,
                        List.of(new Term.Var(r.params().get(0)), new Term.Var(r.params().get(1)))))
                && add.args().get(1).equals(new Term.Var(r.params().get(0)));
    }

    /** a ≤ b :⇔ ∃k. a + k = b, with canonical +. */
    public static boolean isLe(Signature sig, String name) {
        return sig.get(name) instanceof Definition.Predicate p && p.params().size() == 2
                && p.body() instanceof Formula.Exists ex && !p.params().contains(ex.var())
                && ex.body() instanceof Formula.Eq e && e.right().equals(new Term.Var(p.params().get(1)))
                && e.left() instanceof Term.App add && isAdd(sig, add.function())
                && add.args().equals(List.of(new Term.Var(p.params().get(0)), new Term.Var(ex.var())));
    }
}
