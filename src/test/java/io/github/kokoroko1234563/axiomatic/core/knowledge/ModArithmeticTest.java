package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.EquivalenceRewriting;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Structures;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * Prime path stage 5, worked through: congruence mod 5, Cong5(a, b) :⇔ ∃j. ∃k. a + j×5 = b + k×5,
 * is proven an equivalence that respects +, certified, and used to rewrite inside a sum. Every
 * step is a kernel rule, as a player's machines would do it.
 */
class ModArithmeticTest {
    private static final String CONG = "Cong5";
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term c = var("c");
    private final Term d = var("d");
    private final Term x = var("x");
    private final Term y = var("y");
    private final Term five = Term.numeral(5);
    private MathState state;
    private Kernel k;
    private Theorem addZero;
    private Theorem addSucc;
    private Theorem mulZero;
    private Theorem mulSucc;
    private Theorem comm;
    private Theorem assoc;

    private static Term add(Term p, Term q) {
        return app("add", p, q);
    }

    private Term m5(Term t) {
        return app("mul", t, five);
    }

    private Formula cong(Term p, Term q) {
        return Formula.pred(CONG, p, q);
    }

    private Formula.Exists body(Term p, Term q) {
        return (Formula.Exists) Formula.exists("j", Formula.exists("k", eq(add(p, m5(var("j"))), add(q, m5(var("k"))))));
    }

    @BeforeEach
    void setUp() {
        state = MathState.newWorld();
        k = state.kernel();
        addZero = k.definingStatements("add").get(0);
        addSucc = k.definingStatements("add").get(1);
        mulZero = k.definingStatements("mul").get(0);     // a × 0 = 0
        mulSucc = k.definingStatements("mul").get(1);     // a × S(b) = (a × b) + a
        k.define(new Definition.Predicate(CONG, List.of("a", "b"), body(a, b), "≡₅"));
        comm = addComm();
        assoc = addAssoc();
    }

    // ---- Small tools ------------------------------------------------------------------------

    private Theorem inst(Theorem t, Object... pairs) {
        Map<String, Term> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Term) pairs[i + 1]);
        }
        return k.instantiate(t, m);
    }

    private static int count(Term t, Term pattern) {
        if (t.equals(pattern)) {
            return 1;
        }
        return switch (t) {
            case Term.Succ s -> count(s.arg(), pattern);
            case Term.App ap -> ap.args().stream().mapToInt(z -> count(z, pattern)).sum();
            default -> 0;
        };
    }

    /** Rewrites the first occurrence on the right-hand side of an equation. */
    private Theorem rw(Theorem t, Theorem by, boolean rightToLeft) {
        Formula.Eq e = (Formula.Eq) t.formula();
        Formula.Eq be = (Formula.Eq) by.formula();
        return k.rewrite(t, by, rightToLeft, count(e.left(), rightToLeft ? be.right() : be.left()));
    }

    /** From L = X and R = X, L = R. */
    private Theorem join(Theorem left, Theorem right) {
        return rw(left, right, true);
    }

    private Theorem flipSides(Theorem t) {
        return k.rewrite(k.reflexivity(((Formula.Eq) t.formula()).left()), t, false, 0);
    }

    /** On the right side, (X + Y) + (A + B) becomes (X + A) + (Y + B). */
    private Theorem shuffle(Theorem t, Term px, Term py, Term pa, Term pb) {
        t = rw(t, inst(assoc, "a", px, "b", py, "c", add(pa, pb)), false);   // X+(Y+(A+B))
        t = rw(t, inst(assoc, "a", py, "b", pa, "c", pb), true);             // X+((Y+A)+B)
        t = rw(t, inst(comm, "a", py, "b", pa), false);                      // X+((A+Y)+B)
        t = rw(t, inst(assoc, "a", pa, "b", py, "c", pb), false);            // X+(A+(Y+B))
        return rw(t, inst(assoc, "a", px, "b", pa, "c", add(py, pb)), true); // (X+A)+(Y+B)
    }

    private Theorem toCong(Theorem bodyProof, Term p, Term q) {
        return k.modusPonens(inst(k.definingStatements(CONG).get(1), "a", p, "b", q), bodyProof);
    }

    private Theorem fromCong(Theorem congProof) {
        Formula.Pred pr = (Formula.Pred) congProof.formula();
        return k.modusPonens(inst(k.definingStatements(CONG).get(0), "a", pr.args().get(0), "b", pr.args().get(1)),
                congProof);
    }

    /** ∃j. ∃k. p + j×5 = q + k×5 from p + J×5 = q + K×5. */
    private Theorem witness(Theorem eqProof, Term p, Term q, Term wj, Term wk) {
        Formula.Exists outer = body(p, q);
        Formula.Exists inner = (Formula.Exists) Syntax.substitute(outer.body(), "j", wj);
        return k.existsIntro(outer, wj, k.existsIntro(inner, wk, eqProof));
    }

    // ---- Arithmetic lemmas ------------------------------------------------------------------

    private Theorem addComm() {
        Theorem zh = k.assume(eq(add(zero(), a), a));
        Theorem zeroAdd = k.induction(inst(addZero, "a", zero()),
                k.discharge(k.rewrite(inst(addSucc, "a", zero(), "b", a), zh, false, -1), zh.hypotheses().get(0)), "a");
        Theorem sh = k.assume(eq(add(succ(a), b), succ(add(a, b))));
        Theorem ss = k.rewrite(k.rewrite(inst(addSucc, "a", succ(a), "b", b), sh, false, -1), addSucc, true, -1);
        Theorem succAdd = k.induction(k.rewrite(inst(addZero, "a", succ(a)), addZero, true, 1),
                k.discharge(ss, sh.hypotheses().get(0)), "b");
        Theorem ch = k.assume(eq(add(a, b), add(b, a)));
        Theorem cs = k.rewrite(k.rewrite(addSucc, ch, false, -1), inst(succAdd, "a", b, "b", a), true, -1);
        return k.induction(k.rewrite(addZero, zeroAdd, true, 1), k.discharge(cs, ch.hypotheses().get(0)), "b");
    }

    private Theorem addAssoc() {
        Term ab = add(a, b);
        Theorem base = k.rewrite(inst(addZero, "a", ab), inst(addZero, "a", b), true, 1);
        Theorem hyp = k.assume(eq(add(ab, c), add(a, add(b, c))));
        Theorem s = k.rewrite(inst(addSucc, "a", ab, "b", c), hyp, false, -1);
        s = k.rewrite(s, inst(addSucc, "a", a, "b", add(b, c)), true, -1);
        s = k.rewrite(s, inst(addSucc, "a", b, "b", c), true, -1);
        return k.induction(base, k.discharge(s, hyp.hypotheses().get(0)), "c");
    }

    /** (x + y) × c = x × c + y × c, by induction on c. */
    private Theorem distribute() {
        Term xy = add(x, y);
        Theorem left = rw(k.reflexivity(app("mul", xy, zero())), inst(mulZero, "a", xy), false);     // = 0
        Theorem right = k.reflexivity(add(app("mul", x, zero()), app("mul", y, zero())));
        right = rw(rw(right, inst(mulZero, "a", x), false), inst(mulZero, "a", y), false);           // = 0 + 0
        right = rw(right, inst(addZero, "a", zero()), false);                                         // = 0
        Theorem base = join(left, right);
        Term p = app("mul", x, c);
        Term q = app("mul", y, c);
        Theorem ih = k.assume(eq(app("mul", xy, c), add(p, q)));
        Theorem l = rw(k.reflexivity(app("mul", xy, succ(c))), inst(mulSucc, "a", xy, "b", c), false); // (xy×c)+(x+y)
        l = rw(l, ih, false);                                                                          // (p+q)+(x+y)
        l = rw(l, inst(assoc, "a", p, "b", q, "c", xy), false);                                        // p+(q+(x+y))
        l = rw(l, inst(assoc, "a", q, "b", x, "c", y), true);                                          // p+((q+x)+y)
        l = rw(l, inst(comm, "a", q, "b", x), false);                                                  // p+((x+q)+y)
        l = rw(l, inst(assoc, "a", x, "b", q, "c", y), false);                                         // p+(x+(q+y))
        Theorem r = k.reflexivity(add(app("mul", x, succ(c)), app("mul", y, succ(c))));
        r = rw(rw(r, inst(mulSucc, "a", x, "b", c), false), inst(mulSucc, "a", y, "b", c), false);    // (p+x)+(q+y)
        r = rw(r, inst(assoc, "a", p, "b", x, "c", add(q, y)), false);                                // p+(x+(q+y))
        return k.induction(base, k.discharge(join(l, r), ih.hypotheses().get(0)), "c");
    }

    // ---- The laws ---------------------------------------------------------------------------

    private Theorem refl() {
        Theorem e = k.reflexivity(add(a, m5(zero())));
        return toCong(witness(e, a, a, zero(), zero()), a, a);
    }

    private Theorem sym() {
        Theorem h = k.assume(cong(a, b));
        Theorem ex = fromCong(h);
        Kernel.Opening oj = k.openExists((Formula.Exists) ex.formula(), "j1");
        Kernel.Opening ok = k.openExists((Formula.Exists) oj.hypothesis().formula(), "k1");
        Theorem flipped = flipSides(ok.hypothesis());                                    // b + k1×5 = a + j1×5
        Theorem result = toCong(witness(flipped, b, a, var("k1"), var("j1")), b, a);
        result = k.closeExists(oj.hypothesis(), ok, result);
        result = k.closeExists(ex, oj, result);
        return k.discharge(result, h.hypotheses().get(0));
    }

    private Theorem trans(Theorem dist) {
        Theorem h1 = k.assume(cong(a, b));
        Theorem h2 = k.assume(cong(b, c));
        Theorem ex1 = fromCong(h1);
        Theorem ex2 = fromCong(h2);
        Kernel.Opening o1 = k.openExists((Formula.Exists) ex1.formula(), "j1");
        Kernel.Opening o1k = k.openExists((Formula.Exists) o1.hypothesis().formula(), "k1");
        Kernel.Opening o2 = k.openExists((Formula.Exists) ex2.formula(), "j2");
        Kernel.Opening o2k = k.openExists((Formula.Exists) o2.hypothesis().formula(), "k2");
        Term mj1 = m5(var("j1"));
        Term mk1 = m5(var("k1"));
        Term mj2 = m5(var("j2"));
        Term mk2 = m5(var("k2"));
        Theorem t = k.reflexivity(add(a, m5(add(var("j1"), var("j2")))));
        t = rw(t, inst(dist, "x", var("j1"), "y", var("j2"), "c", five), false);  // a+(Mj1+Mj2)
        t = rw(t, inst(assoc, "a", a, "b", mj1, "c", mj2), true);                  // (a+Mj1)+Mj2
        t = rw(t, o1k.hypothesis(), false);                                         // (b+Mk1)+Mj2
        t = rw(t, inst(assoc, "a", b, "b", mk1, "c", mj2), false);                 // b+(Mk1+Mj2)
        t = rw(t, inst(comm, "a", mk1, "b", mj2), false);                          // b+(Mj2+Mk1)
        t = rw(t, inst(assoc, "a", b, "b", mj2, "c", mk1), true);                  // (b+Mj2)+Mk1
        t = rw(t, o2k.hypothesis(), false);                                         // (c+Mk2)+Mk1
        t = rw(t, inst(assoc, "a", c, "b", mk2, "c", mk1), false);                 // c+(Mk2+Mk1)
        t = rw(t, inst(dist, "x", var("k2"), "y", var("k1"), "c", five), true);    // c+M(k2+k1)
        Theorem result = toCong(witness(t, a, c, add(var("j1"), var("j2")), add(var("k2"), var("k1"))), a, c);
        result = k.closeExists(o2.hypothesis(), o2k, result);
        result = k.closeExists(ex2, o2, result);
        result = k.closeExists(o1.hypothesis(), o1k, result);
        result = k.closeExists(ex1, o1, result);
        return k.discharge(k.discharge(result, h2.hypotheses().get(0)), h1.hypotheses().get(0));
    }

    /** Cong5(x, y) → Cong5(c, d) → Cong5(x + c, y + d). */
    private Theorem congAdd(Theorem dist) {
        Theorem h1 = k.assume(cong(x, y));
        Theorem h2 = k.assume(cong(c, d));
        Theorem ex1 = fromCong(h1);
        Theorem ex2 = fromCong(h2);
        Kernel.Opening o1 = k.openExists((Formula.Exists) ex1.formula(), "j1");
        Kernel.Opening o1k = k.openExists((Formula.Exists) o1.hypothesis().formula(), "k1");
        Kernel.Opening o2 = k.openExists((Formula.Exists) ex2.formula(), "j2");
        Kernel.Opening o2k = k.openExists((Formula.Exists) o2.hypothesis().formula(), "k2");
        Term mj1 = m5(var("j1"));
        Term mk1 = m5(var("k1"));
        Term mj2 = m5(var("j2"));
        Term mk2 = m5(var("k2"));
        Theorem l = k.reflexivity(add(add(x, c), m5(add(var("j1"), var("j2")))));
        l = rw(l, inst(dist, "x", var("j1"), "y", var("j2"), "c", five), false);   // (x+c)+(Mj1+Mj2)
        l = shuffle(l, x, c, mj1, mj2);                                             // (x+Mj1)+(c+Mj2)
        l = rw(rw(l, o1k.hypothesis(), false), o2k.hypothesis(), false);           // (y+Mk1)+(d+Mk2)
        Theorem r = k.reflexivity(add(add(y, d), m5(add(var("k1"), var("k2")))));
        r = rw(r, inst(dist, "x", var("k1"), "y", var("k2"), "c", five), false);
        r = shuffle(r, y, d, mk1, mk2);                                             // (y+Mk1)+(d+Mk2)
        Theorem e = join(l, r);
        Theorem result = toCong(witness(e, add(x, c), add(y, d), add(var("j1"), var("j2")),
                add(var("k1"), var("k2"))), add(x, c), add(y, d));
        result = k.closeExists(o2.hypothesis(), o2k, result);
        result = k.closeExists(ex2, o2, result);
        result = k.closeExists(o1.hypothesis(), o1k, result);
        result = k.closeExists(ex1, o1, result);
        return k.discharge(k.discharge(result, h2.hypotheses().get(0)), h1.hypotheses().get(0));
    }

    // ---- Tests ------------------------------------------------------------------------------

    @Test
    void congruenceModFiveIsACertifiedEquivalence() {
        Theorem dist = distribute();
        List<Theorem> laws = List.of(refl(), sym(), trans(dist));
        for (Theorem law : laws) {
            assertTrue(law.isClosed() && !law.isConjectural(), law.formula().toString());
        }
        Library.StructureCert cert = state.library().certifyStructure(k, Structures.EQUIVALENCE, laws);
        assertEquals(CONG, cert.mapping().get("Equivalence.R"));
        Theorem plus = congAdd(dist);
        assertTrue(plus.isClosed(), plus.formula().toString());
        state.library().name("cong5_add", plus);

        // x + y×5 ≡ x (witnesses 0 and y), then rewrite it inside a sum: x + z ≡ (x + y×5) + z.
        Theorem zeroTimes = zeroMul();
        Theorem e = k.reflexivity(add(add(x, m5(y)), m5(zero())));
        e = rw(e, inst(zeroTimes, "c", five), false);                                  // = (x+M y)+0
        e = rw(e, inst(addZero, "a", add(x, m5(y))), false);                           // = x+M y
        Theorem multiple = toCong(witness(e, add(x, m5(y)), x, zero(), y), add(x, m5(y)), x);
        Term z = var("z");
        Theorem target = k.reflexivity(add(add(x, m5(y)), z));
        var lawsForRewriter = new EquivalenceRewriting.Laws(CONG, cert.laws().get(0), cert.laws().get(1),
                cert.laws().get(2), List.copyOf(state.library().theorems().values()));
        Theorem out = EquivalenceRewriting.rewrite(k, target, multiple, lawsForRewriter, false, 0);
        assertTrue(Syntax.alphaEquals(cong(add(x, z), add(add(x, m5(y)), z)), out.formula()), out.formula().toString());
        assertTrue(out.isClosed() && !out.isConjectural());

        // Without the congruence law for ×, rewriting inside a product is refused.
        Theorem product = k.reflexivity(app("mul", add(x, m5(y)), z));
        assertThrows(ProofException.class,
                () -> EquivalenceRewriting.rewrite(k, product, multiple, lawsForRewriter, false, 0));
    }

    /** 0 × c = 0, by induction on c. */
    private Theorem zeroMul() {
        Theorem ih = k.assume(eq(app("mul", zero(), c), zero()));
        Theorem s = rw(k.reflexivity(app("mul", zero(), succ(c))), inst(mulSucc, "a", zero(), "b", c), false);
        s = rw(rw(s, ih, false), inst(addZero, "a", zero()), false);
        return k.induction(inst(mulZero, "a", zero()), k.discharge(s, ih.hypotheses().get(0)), "c");
    }
}
