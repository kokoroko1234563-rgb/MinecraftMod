package io.github.kokoroko1234563.axiomatic.core.logic;

import static io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.zero;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Monoids and generic theorems: proven once "for any monoid", applied to addition. */
class StructuresTest {
    private final Term a = var("a");
    private final Term b = var("b");
    private final Term c = var("c");
    private final Term x = var("x");
    private final Term n = var("n");
    private final Term e = new Term.App("Monoid.e", List.of());

    private Kernel kernel;
    private Theorem addZero;
    private Theorem addSucc;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        List<Theorem> add = kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"),
                a, succ(app("add", a, b)), "+"));
        addZero = add.get(0);
        addSucc = add.get(1);
        kernel.define(new Definition.Explicit("zeroC", List.of(), zero(), null));
        kernel.structure(Structures.MONOID);
        // pow over any monoid: pow(x, 0) = e, pow(x, S(n)) = pow(x, n) ∘ x.
        kernel.define(new Definition.PrimitiveRecursive("pow", List.of("x", "n"), e,
                app("Monoid.op", app("pow", x, n), x), null));
    }

    /** pow(x, 1) = x for any monoid: pow(x,1) = pow(x,0) ∘ x = e ∘ x = x. */
    private Theorem powOne() {
        List<Theorem> pow = kernel.definingStatements("pow");
        Theorem s1 = kernel.instantiate(pow.get(1), "n", zero());                // pow(x,S0) = pow(x,0) ∘ x
        Theorem s2 = kernel.rewrite(s1, pow.get(0), false, -1);                    // = e ∘ x
        Theorem leftId = kernel.instantiate(kernel.structureAxiom(Structures.MONOID, 1), "a", x);
        return kernel.rewrite(s2, leftId, false, -1);                              // = x
    }

    /** The three monoid laws for (add, zeroC), proven from Peano. */
    private List<Theorem> addLaws() {
        // Associativity, by induction on c.
        Term ab = app("add", a, b);
        Theorem base = kernel.instantiate(addZero, "a", ab);                       // (a+b)+0 = a+b
        Theorem bz = kernel.instantiate(addZero, "a", b);                          // b+0 = b
        base = kernel.rewrite(base, bz, true, 1);                                  // (a+b)+0 = a+(b+0)
        Theorem hyp = kernel.assume(eq(app("add", ab, c), app("add", a, app("add", b, c))));
        Theorem s = kernel.instantiate(addSucc, Map.of("a", ab, "b", c));          // (a+b)+S(c) = S((a+b)+c)
        s = kernel.rewrite(s, hyp, false, -1);                                     // = S(a+(b+c))
        Theorem inner = kernel.instantiate(addSucc, Map.of("a", a, "b", app("add", b, c))); // a+S(b+c) = S(a+(b+c))
        s = kernel.rewrite(s, inner, true, -1);                                    // = a+S(b+c)
        Theorem bsc = kernel.instantiate(addSucc, Map.of("a", b, "b", c));         // b+S(c) = S(b+c)
        s = kernel.rewrite(s, bsc, true, -1);                                      // = a+(b+S(c))
        Theorem assoc = kernel.induction(base, kernel.discharge(s, hyp.hypotheses().get(0)), "c");

        Term z = new Term.App("zeroC", List.of());
        Theorem zdef = kernel.definingStatements("zeroC").get(0);                 // zeroC = 0
        // 0 + a = a, by induction.
        Theorem zb = kernel.instantiate(addZero, "a", zero());
        Theorem zh = kernel.assume(eq(app("add", zero(), a), a));
        Theorem zs = kernel.instantiate(addSucc, Map.of("a", zero(), "b", a));
        zs = kernel.rewrite(zs, zh, false, -1);
        Theorem zeroAdd = kernel.induction(zb, kernel.discharge(zs, zh.hypotheses().get(0)), "a");
        Theorem left = kernel.rewrite(zeroAdd, zdef, true, 0);                     // zeroC + a = a
        Theorem right = kernel.rewrite(addZero, zdef, true, 0);                    // a + zeroC = a
        assertTrue(Syntax.alphaEquals(left.formula(), eq(app("add", z, a), a)), left.toString());
        return List.of(assoc, left, right);
    }

    @Test
    void genericTheoremAppliesToAddition() {
        Theorem generic = powOne();
        assertTrue(generic.dependencies().contains("structure:Monoid"));
        Theorem concrete = kernel.interpret(generic, Structures.MONOID,
                Map.of("Monoid.op", "add", "Monoid.e", "zeroC"), addLaws());
        assertFalse(concrete.dependencies().contains("structure:Monoid"));
        String powAdd = "pow⟨Monoid.e:=zeroC,Monoid.op:=add⟩";
        assertTrue(Syntax.alphaEquals(concrete.formula(), eq(app(powAdd, x, Term.numeral(1)), x)), concrete.toString());
        // pow over addition is multiplication.
        Evaluator ev = new Evaluator(kernel.signature(), 100_000);
        assertEquals(BigInteger.valueOf(42), ev.apply(powAdd, List.of(BigInteger.valueOf(6), BigInteger.valueOf(7))));
    }

    @Test
    void certificatesAreReadOffTheLawsAndSaved() throws java.io.IOException {
        var state = io.github.kokoroko1234563.axiomatic.core.knowledge.MathState.newWorld();
        kernel = state.kernel();
        addZero = kernel.definingStatements("add").get(0);
        addSucc = kernel.definingStatements("add").get(1);
        kernel.define(new Definition.Explicit("zeroC", List.of(), zero(), null));
        var cert = state.library().certifyStructure(kernel, Structures.MONOID, addLaws());
        assertEquals(Map.of("Monoid.op", "add", "Monoid.e", "zeroC"), cert.mapping());
        var loaded = io.github.kokoroko1234563.axiomatic.core.knowledge.MathState.fromBytes(state.toBytes());
        assertEquals(cert.mapping(), loaded.library().certificates().get(0).mapping());
        // The laws in the wrong order are not a certificate.
        List<Theorem> laws = addLaws();
        assertThrows(ProofException.class, () -> state.library().certifyStructure(kernel, Structures.MONOID,
                List.of(laws.get(2), laws.get(1), laws.get(0))));
    }

    @Test
    void lawsMustBeProvenForTheConcreteOperation() {
        Theorem generic = powOne();
        List<Theorem> laws = addLaws();
        // Swapping two laws, or claiming a law for the wrong operation, is refused.
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.MONOID,
                Map.of("Monoid.op", "add", "Monoid.e", "zeroC"), List.of(laws.get(1), laws.get(0), laws.get(2))));
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.MONOID,
                Map.of("Monoid.op", "add"), laws));
        // A law proven only under an open assumption does not count.
        Theorem assumed = kernel.assume(laws.get(0).formula());
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.MONOID,
                Map.of("Monoid.op", "add", "Monoid.e", "zeroC"), List.of(assumed, laws.get(1), laws.get(2))));
    }

    @Test
    void axiomsAreOnlyAboutTheAbstractSymbols() {
        // An axiom cannot be instantiated into a claim about a concrete function directly:
        // Monoid.op stays abstract, and "a ∘ e = a" says nothing about add.
        Theorem rightId = kernel.structureAxiom(Structures.MONOID, 2);
        assertTrue(rightId.dependencies().contains("structure:Monoid"));
        assertThrows(ProofException.class, () -> kernel.define(new Definition.Explicit("Monoid.op",
                List.of("a", "b"), a, null)));
        // Abstract symbols cannot be computed.
        assertThrows(IllegalArgumentException.class, () -> new Evaluator(kernel.signature(), 100)
                .apply("pow", List.of(BigInteger.ONE, BigInteger.ONE)));
    }

    @Test
    void equivalenceAndOrderExist() {
        Theorem refl = kernel.structureAxiom(Structures.EQUIVALENCE, 0);
        assertEquals("a ≡ a", new Printer(kernel.signature()).formula(refl.formula()));
        assertEquals(3, kernel.structure(Structures.ORDER).axioms().size());
    }

    // ---- Monoids over a set ----------------------------------------------------------------

    private static final String SM_OP = "SetMonoid.op";
    private static final String SM_A = "SetMonoid.A";
    private final Term se = new Term.App("SetMonoid.e", List.of());

    /** For any monoid on a set: A(a) → (e ∘ a) ∘ e = a. */
    private Theorem genericSetIdentity() {
        Theorem idLaw = kernel.structureAxiom(Structures.SET_MONOID, 2);        // A(a) → (e∘a = a ∧ a∘e = a)
        Theorem inA = kernel.assume(Formula.pred(SM_A, a));
        Theorem both = kernel.modusPonens(idLaw, inA);
        Theorem t = kernel.reflexivity(app(SM_OP, app(SM_OP, se, a), se));
        t = kernel.rewrite(t, kernel.andLeft(both), false, 1);                  // = a ∘ e
        t = kernel.rewrite(t, kernel.andRight(both), false, 0);                 // = a
        return kernel.discharge(t, inA.hypotheses().get(0));
    }

    /** The three set-monoid laws for (add, zeroC) over Any(a) := a = a. */
    private List<Theorem> anyLaws() {
        kernel.define(new Definition.Predicate("Any", List.of("a"), eq(a, a), null));
        Theorem intro = kernel.definingStatements("Any").get(1);                // a = a → Any(a)
        Term zc = new Term.App("zeroC", List.of());
        List<Theorem> monoid = addLaws();                                       // over ℕ, with zeroC
        Theorem anyZ = kernel.modusPonens(kernel.instantiate(intro, "a", zc), kernel.reflexivity(zc));
        Theorem anySum = kernel.modusPonens(kernel.instantiate(intro, "a", app("add", a, b)),
                kernel.reflexivity(app("add", a, b)));
        Theorem ha = kernel.assume(Formula.pred("Any", a));
        Theorem hb = kernel.assume(Formula.pred("Any", b));
        Theorem hc = kernel.assume(Formula.pred("Any", c));
        Theorem closure = kernel.andIntro(anyZ,
                kernel.discharge(kernel.discharge(anySum, hb.hypotheses().get(0)), ha.hypotheses().get(0)));
        Theorem assoc = kernel.discharge(kernel.discharge(kernel.discharge(monoid.get(0), hc.hypotheses().get(0)),
                hb.hypotheses().get(0)), ha.hypotheses().get(0));
        Theorem identity = kernel.discharge(kernel.andIntro(monoid.get(1), monoid.get(2)), ha.hypotheses().get(0));
        return List.of(closure, assoc, identity);
    }

    @Test
    void setMonoidTheoremsApplyToCertifiedOperations() {
        Theorem generic = genericSetIdentity();
        assertTrue(generic.dependencies().contains("structure:" + Structures.SET_MONOID));
        List<Theorem> laws = anyLaws();
        Map<String, String> mapping = Structures.inferMapping(kernel.structure(Structures.SET_MONOID),
                laws.stream().map(Theorem::formula).toList());
        assertEquals(Map.of("SetMonoid.op", "add", "SetMonoid.e", "zeroC", "SetMonoid.A", "Any"), mapping);
        Theorem applied = kernel.interpret(generic, Structures.SET_MONOID, mapping, laws);
        Term zc = new Term.App("zeroC", List.of());
        assertTrue(Syntax.alphaEquals(Formula.implies(Formula.pred("Any", a), eq(app("add", app("add", zc, a), zc), a)),
                applied.formula()), applied.formula().toString());
        assertFalse(applied.dependencies().contains("structure:" + Structures.SET_MONOID));
    }

    @Test
    void setMonoidRefusesLawsWithoutTheSet() {
        Theorem generic = genericSetIdentity();
        List<Theorem> laws = anyLaws();
        // The ℕ-monoid laws are not the set laws: the premises about A are part of each law.
        List<Theorem> plain = addLaws();
        assertEquals(null, Structures.inferMapping(kernel.structure(Structures.SET_MONOID),
                plain.stream().map(Theorem::formula).toList()));
        Map<String, String> mapping = Map.of("SetMonoid.op", "add", "SetMonoid.e", "zeroC", "SetMonoid.A", "Any");
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.SET_MONOID, mapping, plain));
        // A carrier that is not a property, or laws in the wrong order, are refused.
        Map<String, String> notAProperty = Map.of("SetMonoid.op", "add", "SetMonoid.e", "zeroC", "SetMonoid.A", "add");
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.SET_MONOID, notAProperty, laws));
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.SET_MONOID, mapping,
                List.of(laws.get(1), laws.get(0), laws.get(2))));
        // Assumed laws are not laws.
        List<Theorem> assumed = laws.stream().map(l -> kernel.assume(l.formula())).toList();
        assertThrows(ProofException.class, () -> kernel.interpret(generic, Structures.SET_MONOID, mapping, assumed));
    }
}
