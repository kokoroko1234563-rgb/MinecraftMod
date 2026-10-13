package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
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

    static List<Formula> conjuncts(Formula f) {
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

    /** No v satisfies the conjunct. */
    static final BigInteger NONE = BigInteger.valueOf(-1);

    /**
     * With binary arithmetic, solves a conjunct x + v = t or x × v = t (either order, either side,
     * x and t without v), or v + v = t or v × v = t, for v: the only possible value, {@link #NONE} if there is none, or null
     * if the conjunct does not pin v down (not of this shape, or 0 × v = 0).
     */
    static BigInteger solve(Signature sig, String v, Formula c, Evaluator ev, java.util.Map<String, BigInteger> env) {
        if (!(c instanceof Formula.Eq e)) {
            return null;
        }
        BigInteger r = solveSide(sig, v, e.left(), e.right(), ev, env);
        return r != null ? r : solveSide(sig, v, e.right(), e.left(), ev, env);
    }

    private static BigInteger solveSide(Signature sig, String v, Term side, Term other, Evaluator ev,
            java.util.Map<String, BigInteger> env) {
        if (!(side instanceof Term.App a) || a.args().size() != 2 || Syntax.freeVars(other).contains(v)) {
            return null;
        }
        boolean add = isAdd(sig, a.function());
        if (!add && !isMul(sig, a.function())) {
            return null;
        }
        Term.Var var = new Term.Var(v);
        if (a.args().get(0).equals(var) && a.args().get(1).equals(var)) {
            // v + v = t or v × v = t.
            BigInteger t = ev.eval(other, env);
            if (add) {
                return t.testBit(0) ? NONE : t.shiftRight(1);
            }
            BigInteger root = t.sqrt();
            return root.multiply(root).equals(t) ? root : NONE;
        }
        Term x;
        if (a.args().get(1).equals(var)) {
            x = a.args().get(0);
        } else if (a.args().get(0).equals(var)) {
            x = a.args().get(1);
        } else {
            return null;
        }
        if (Syntax.freeVars(x).contains(v)) {
            return null;
        }
        BigInteger xv = ev.eval(x, env);
        BigInteger t = ev.eval(other, env);
        if (add) {
            BigInteger d = t.subtract(xv);
            return d.signum() < 0 ? NONE : d;
        }
        if (xv.signum() == 0) {
            return t.signum() == 0 ? null : NONE;
        }
        BigInteger[] qr = t.divideAndRemainder(xv);
        return qr[1].signum() == 0 ? qr[0] : NONE;
    }
}
