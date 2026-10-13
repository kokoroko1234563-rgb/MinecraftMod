package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * The binary upgrade: proving that every number is (h + h) + p with p ≤ 1. The whole proof is
 * done here with kernel rules, to show it is within a player's reach.
 */
class BinaryTest {
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term c = var("c");
    private final Term n = var("n");
    private final Term one = succ(zero());
    private MathState state;
    private Kernel k;
    private Theorem addZero;
    private Theorem addSucc;

    @BeforeEach
    void setUp() {
        state = MathState.newWorld();
        k = state.kernel();
        addZero = k.definingStatements("add").get(0);   // a + 0 = a
        addSucc = k.definingStatements("add").get(1);   // a + S(b) = S(a + b)
    }

    private static Term add(Term x, Term y) {
        return app("add", x, y);
    }

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
            case Term.App ap -> ap.args().stream().mapToInt(x -> count(x, pattern)).sum();
            default -> 0;
        };
    }

    /** Rewrites the first occurrence on the right-hand side of an equation. */
    private Theorem rw(Theorem t, Theorem by, boolean rightToLeft) {
        Formula.Eq e = (Formula.Eq) t.formula();
        Formula.Eq be = (Formula.Eq) by.formula();
        return k.rewrite(t, by, rightToLeft, count(e.left(), rightToLeft ? be.right() : be.left()));
    }

    /** From x = y, y = x. */
    private Theorem flipSides(Theorem t) {
        return k.rewrite(k.reflexivity(((Formula.Eq) t.formula()).left()), t, false, 0);
    }

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

    /** ∀n. n = (half(n) + half(n)) + par(n) ∧ par(n) ≤ 1. */
    private Theorem binary() {
        List<Theorem> flip = k.define(new Definition.PrimitiveRecursive("flip", List.of("x"), one, zero(), null));
        List<Theorem> par = k.define(new Definition.PrimitiveRecursive("par", List.of("n"), zero(),
                app("flip", app("par", n)), null));
        List<Theorem> half = k.define(new Definition.PrimitiveRecursive("half", List.of("n"), zero(),
                add(app("half", n), app("par", n)), null));
        Theorem comm = addComm();
        Theorem assoc = addAssoc();
        Term p = app("par", n);
        Term h = app("half", n);
        Term q = app("flip", p);

        // flip(flip(par(n))) = par(n)
        Theorem t = k.reflexivity(app("flip", app("flip", app("par", zero()))));
        t = rw(rw(rw(rw(t, par.get(0), false), flip.get(0), false), inst(flip.get(1), "x", zero()), false), par.get(0), true);
        Theorem ih = k.assume(eq(app("flip", q), p));
        Theorem s = k.reflexivity(app("flip", app("flip", app("par", succ(n)))));
        s = rw(rw(rw(s, par.get(1), false), ih, false), par.get(1), true);
        Theorem flipFlip = k.induction(t, k.discharge(s, ih.hypotheses().get(0)), "n");

        // par(n) + flip(par(n)) = 1
        t = k.reflexivity(add(app("par", zero()), app("flip", app("par", zero()))));
        t = rw(rw(rw(t, par.get(0), false), par.get(0), false), flip.get(0), false);
        t = rw(rw(t, inst(addSucc, "a", zero(), "b", zero()), false), inst(addZero, "a", zero()), false);
        ih = k.assume(eq(add(p, q), one));
        s = k.reflexivity(add(app("par", succ(n)), app("flip", app("par", succ(n)))));
        s = rw(rw(s, par.get(1), false), par.get(1), false);
        s = rw(rw(rw(s, flipFlip, false), inst(comm, "a", q, "b", p), false), ih, false);
        Theorem bitSum = k.induction(t, k.discharge(s, ih.hypotheses().get(0)), "n");

        // n = (half(n) + half(n)) + par(n)
        Term h0 = app("half", zero());
        t = k.reflexivity(add(add(h0, h0), app("par", zero())));
        t = rw(rw(rw(t, half.get(0), false), half.get(0), false), par.get(0), false);
        t = rw(rw(t, inst(addZero, "a", add(zero(), zero())), false), inst(addZero, "a", zero()), false);
        Theorem base = flipSides(t);
        ih = k.assume(eq(n, add(add(h, h), p)));
        Term hs = app("half", succ(n));
        s = k.reflexivity(add(add(hs, hs), app("par", succ(n))));
        s = rw(rw(rw(s, half.get(1), false), half.get(1), false), par.get(1), false);   // ((h+p)+(h+p))+q
        s = rw(s, inst(assoc, "a", add(h, p), "b", add(h, p), "c", q), false);        // (h+p)+((h+p)+q)
        s = rw(s, inst(assoc, "a", h, "b", p, "c", q), false);                        // (h+p)+(h+(p+q))
        s = rw(s, bitSum, false);                                                      // (h+p)+(h+1)
        s = rw(rw(s, inst(addSucc, "a", h, "b", zero()), false), inst(addZero, "a", h), false); // (h+p)+S(h)
        s = rw(s, inst(addSucc, "a", add(h, p), "b", h), false);                       // S((h+p)+h)
        s = rw(s, inst(assoc, "a", h, "b", p, "c", h), false);                         // S(h+(p+h))
        s = rw(s, inst(comm, "a", p, "b", h), false);                                  // S(h+(h+p))
        s = rw(s, inst(assoc, "a", h, "b", h, "c", p), true);                          // S((h+h)+p)
        s = rw(s, ih, true);                                                           // S(n)
        Theorem split = k.induction(base, k.discharge(flipSides(s), ih.hypotheses().get(0)), "n");

        // par(n) ≤ 1, with witness flip(par(n))
        Formula.Exists body = (Formula.Exists) Formula.exists("k", eq(add(p, var("k")), one));
        Theorem exists = k.existsIntro(body, q, bitSum);
        Theorem le = k.modusPonens(inst(k.definingStatements("le").get(1), "a", p, "b", one), exists);
        return k.andIntro(split, le);
    }

    @Test
    void provingBinaryUnlocksIt() {
        Library library = state.library();
        assertFalse(library.binaryProven(state.signature()));
        Theorem thm = binary();
        assertTrue(thm.isClosed() && !thm.isConjectural());
        assertTrue(Library.isBinaryDecomposition(thm.formula(), state.signature()), thm.formula().toString());
        // Proven but not named in the Library: not yet.
        assertFalse(library.binaryProven(state.signature()));
        library.name("binary", thm);
        assertTrue(library.binaryProven(state.signature()));
    }

    @Test
    void aConjectureProvenLaterUnlocksIt() {
        Library library = state.library();
        Theorem proof = binary();
        Theorem claim = library.conjecture(k, state.deciders(), "binary_hope", proof.formula());
        library.name("binary_claim", claim);
        assertFalse(library.binaryProven(state.signature()));
        library.proveConjecture("binary_hope", proof);
        assertTrue(library.binaryProven(state.signature()));
        assertTrue(state.signature().nativeArithmetic(), "the listener noticed");
    }

    @Test
    void cheatsAreRefused() {
        Library library = state.library();
        Theorem thm = binary();
        // A conjectured decomposition does not count.
        library.name("hope", k.conjecture("hope", thm.formula()));
        assertFalse(library.binaryProven(state.signature()));
        // The halving equation alone does not count: p could be n itself.
        library.name("half_only", k.andLeft(thm));
        assertFalse(library.binaryProven(state.signature()));
        // Nor does a bound other than 1.
        Formula.And f = (Formula.And) thm.formula();
        Formula.Pred le = (Formula.Pred) f.right();
        assertFalse(Library.isBinaryDecomposition(Formula.and(f.left(),
                Formula.pred(le.name(), le.args().get(0), succ(one))), state.signature()));
    }

    @Test
    void binaryMakesArithmeticFast() throws java.io.IOException {
        var three = java.math.BigInteger.valueOf(3000);
        var slow = new io.github.kokoroko1234563.axiomatic.core.logic.Evaluator(state.signature(), Long.MAX_VALUE);
        var unary = slow.apply("mul", List.of(three, three));
        assertTrue(slow.steps() > 1_000_000, "unary multiplication: " + slow.steps());
        state.library().name("binary", binary());
        var fast = new io.github.kokoroko1234563.axiomatic.core.logic.Evaluator(state.signature(), Long.MAX_VALUE);
        assertTrue(fast.apply("mul", List.of(three, three)).equals(unary));
        assertTrue(fast.steps() < 1_000, "binary multiplication: " + fast.steps());
        // Saved worlds keep it, and copies for worker threads too.
        MathState loaded = MathState.fromBytes(state.toBytes());
        assertTrue(loaded.signature().nativeArithmetic() && loaded.signature().copy().nativeArithmetic());
        // Prime(997) by trial division is now within a machine's budget.
        var ev = new io.github.kokoroko1234563.axiomatic.core.logic.Evaluator(loaded.signature(), 200_000_000);
        var truth = new Truth(loaded.signature(), loaded.deciders(), ev, java.math.BigInteger.valueOf(997));
        assertTrue(truth.property("Prime", List.of(java.math.BigInteger.valueOf(997))) == Truth.Value.TRUE,
                "steps " + ev.steps());
        assertTrue(ev.steps() < 1_000_000, "∃ solved by subtraction and division: " + ev.steps() + " steps");
        // A seven-digit prime, and a composite caught by its divisor.
        var big = new io.github.kokoroko1234563.axiomatic.core.logic.Evaluator(loaded.signature(), 200_000_000);
        var bigTruth = new Truth(loaded.signature(), loaded.deciders(), big, java.math.BigInteger.valueOf(1_000_003));
        assertTrue(bigTruth.property("Prime", List.of(java.math.BigInteger.valueOf(1_000_003))) == Truth.Value.TRUE);
        assertTrue(bigTruth.property("Prime", List.of(java.math.BigInteger.valueOf(1_000_001))) == Truth.Value.FALSE);
        assertTrue(bigTruth.property("Even", List.of(java.math.BigInteger.valueOf(1_000_002))) == Truth.Value.TRUE);
        assertTrue(bigTruth.property("Even", List.of(java.math.BigInteger.valueOf(1_000_001))) == Truth.Value.FALSE);
        long before = big.steps();
        assertTrue(bigTruth.property("Even", List.of(java.math.BigInteger.TEN.pow(30))) == Truth.Value.TRUE);
        assertTrue(big.steps() - before < 1_000, "Even(10^30) by halving: " + (big.steps() - before));
    }
}
