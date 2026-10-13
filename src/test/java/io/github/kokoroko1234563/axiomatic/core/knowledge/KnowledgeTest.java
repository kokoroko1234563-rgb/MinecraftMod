package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static io.github.kokoroko1234563.axiomatic.core.logic.Term.app;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.numeral;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.succ;
import static io.github.kokoroko1234563.axiomatic.core.logic.Term.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Evaluator;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Prelude;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

class KnowledgeTest {
    private static final BigInteger CAP = BigInteger.valueOf(100);

    private Kernel kernel;
    private Deciders deciders;
    private Library library;

    @BeforeEach
    void setUp() {
        kernel = new Kernel(new Signature());
        Prelude.define(kernel);
        deciders = new Deciders(kernel.signature());
        library = new Library();
    }

    private Truth truth() {
        return new Truth(kernel.signature(), deciders, new Evaluator(kernel.signature(), 10_000_000), CAP);
    }

    private static List<BigInteger> nums(long... values) {
        return java.util.Arrays.stream(values).mapToObj(BigInteger::valueOf).toList();
    }

    @Test
    void witnessSearchCertifiesEvens() {
        Truth truth = truth();
        Fact twelve = Certifier.byComputation(truth, "Even", nums(12));
        assertNotNull(twelve);
        assertFalse(twelve.isConjectural());
        assertNull(Certifier.byComputation(truth, "Even", nums(7)));
        assertTrue(truth.evaluator().steps() > 0, "searching costs energy");
    }

    @Test
    void doubleOutputsAreCertifiedByItsTheorem() {
        Term n = var("n");
        Theorem dbl = kernel.definingStatements("Double").get(0);
        Theorem fold = kernel.instantiate(kernel.definingStatements("Even").get(1), "n", app("Double", n));
        Theorem thm = kernel.modusPonens(fold,
                kernel.existsIntro(new Formula.Exists("k", Formula.eq(app("Double", n), app("add", var("k"), var("k")))),
                        n, dbl));

        Truth truth = truth();
        Fact fact = Certifier.byTheorem(thm, Map.of("n", BigInteger.valueOf(7)), List.of(), truth);
        assertEquals(new Fact("Even", nums(14), thm.dependencies()), fact);
    }

    @Test
    void closureTheoremsPassCertificatesThrough() {
        Term a = var("a");
        Term b = var("b");
        // Proving even_add needs commutativity and associativity first; a conjecture stands in here.
        Theorem evenAdd = kernel.conjecture("evenAdd", Formula.implies(Formula.pred("Even", a),
                Formula.implies(Formula.pred("Even", b), Formula.pred("Even", app("add", a, b)))));
        Truth truth = truth();
        Fact four = Certifier.byComputation(truth, "Even", nums(4));
        Fact six = Certifier.byComputation(truth, "Even", nums(6));
        Map<String, BigInteger> inputs = Map.of("a", BigInteger.valueOf(4), "b", BigInteger.valueOf(6));

        Fact ten = Certifier.byTheorem(evenAdd, inputs, List.of(four, six), truth);
        assertEquals("Even(10)", ten.toString());
        assertTrue(ten.isConjectural());
        // Without a certificate for one input, nothing passes through.
        assertThrows(ProofException.class, () -> Certifier.byTheorem(evenAdd, inputs, List.of(four), truth));
    }

    @Test
    void linkedDeciderSettlesTheProperty() {
        Term n = var("n");
        Formula decides = Formula.eq(app("isEven", n), numeral(1));
        Formula holds = Formula.pred("Even", n);
        // The link proofs are real induction work; conjectures stand in, which taints the results.
        Theorem fwd = kernel.conjecture("linkF", Formula.implies(decides, holds));
        Theorem back = kernel.conjecture("linkB", Formula.implies(holds, decides));
        assertThrows(ProofException.class, () -> deciders.register("Even", "isEven", back, fwd));
        assertEquals("isEven", deciders.registerFromLinks(fwd, back).function());

        Truth truth = truth();
        assertEquals(Truth.Value.FALSE, truth.property("Even", nums(7)), "a decider can say no; search cannot");
        Fact fact = Certifier.byComputation(truth, "Even", nums(1000));
        assertTrue(fact.isConjectural());
    }

    @Test
    void testBenchChecksEveryCase() {
        Term a = var("a");
        Term b = var("b");
        Formula comm = Formula.eq(app("add", a, b), app("add", b, a));
        TestBench.Result result = TestBench.test(comm, BigInteger.valueOf(5), truth());
        assertEquals(new TestBench.Passed(BigInteger.valueOf(5), 36), result);
        assertInstanceOf(Library.Tested.class, library.recordTest(comm, result));

        Formula wrong = Formula.eq(app("sub", a, b), app("sub", b, a));
        TestBench.Result bad = TestBench.test(wrong, BigInteger.valueOf(5), truth());
        assertInstanceOf(TestBench.Counterexample.class, bad);
        assertInstanceOf(Library.Refuted.class, library.recordTest(wrong, bad));
        // Refuted is permanent.
        assertInstanceOf(Library.Refuted.class, library.recordTest(wrong, result));
    }

    @Test
    void primeDeciderComputes() {
        Evaluator ev = new Evaluator(kernel.signature(), 10_000_000);
        List<Integer> primes = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            if (ev.apply("isPrime", nums(i)).equals(BigInteger.ONE)) {
                primes.add(i);
            }
        }
        assertEquals(List.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29), primes);
    }

    @Test
    void theTwoNPlusOneTrap() {
        Term n = var("n");
        Formula claim = Formula.eq(app("isPrime", succ(app("add", n, n))), numeral(1));
        Theorem conj = library.conjecture(kernel, deciders, "twoNPlusOne", claim);
        Theorem used = kernel.instantiate(conj, "n", numeral(4));
        assertTrue(used.isConjectural());
        assertEquals(java.util.Set.of("conjecture:twoNPlusOne"), library.unproven(used.dependencies()));

        Truth truth = truth();
        for (int i = 1; i <= 3; i++) {
            assertFalse(library.observe("twoNPlusOne", Map.of("n", BigInteger.valueOf(i)), truth), "3, 5, 7 are prime");
        }
        assertTrue(library.observe("twoNPlusOne", Map.of("n", BigInteger.valueOf(4)), truth), "9 = 3 × 3");
        assertEquals(Library.ConjectureState.REFUTED, library.conjectureNamed("twoNPlusOne").state());
        assertEquals(java.util.Set.of("conjecture:twoNPlusOne"), library.refuted(used.dependencies()));
        assertInstanceOf(Library.Refuted.class, library.status(claim));
    }

    @Test
    void unboundedExistsCannotBeConjectured() {
        // Even(n) unfolds to ∃k. n = k + k: a failure could never be caught by search.
        Formula claim = Formula.pred("Even", app("Double", var("n")));
        assertThrows(ProofException.class, () -> library.conjecture(kernel, deciders, "evens", claim));
        // Under a premise, ∃ is fine: finding the witness refutes the claim.
        Formula negated = Formula.not(Formula.pred("Even", succ(app("Double", var("n")))));
        library.conjecture(kernel, deciders, "oddsAreNotEven", negated);
    }

    @Test
    void primeConjectureIsCaughtByFindingADivisor() {
        Term n = var("n");
        Term d = var("d");
        Term j = var("j");
        // le(a, b) :⇔ ∃j. a + j = b; Prime(n) :⇔ 2 ≤ n ∧ ¬∃d. (2 ≤ d ∧ S(d) ≤ n ∧ divides(d, n) = 1)
        kernel.define(new io.github.kokoroko1234563.axiomatic.core.logic.Definition.Predicate("le", List.of("a", "b"),
                Formula.exists("j", Formula.eq(app("add", var("a"), j), var("b"))), "≤"));
        kernel.define(new io.github.kokoroko1234563.axiomatic.core.logic.Definition.Predicate("Prime", List.of("n"),
                Formula.and(Formula.pred("le", numeral(2), n), Formula.not(Formula.exists("d", Formula.and(
                        Formula.pred("le", numeral(2), d), Formula.and(Formula.pred("le", succ(d), n),
                                Formula.eq(app("divides", d, n), numeral(1))))))), null));
        Formula claim = Formula.pred("Prime", succ(app("add", n, n)));
        library.conjecture(kernel, deciders, "oddPrimes", claim);
        Truth truth = new Truth(kernel.signature(), deciders, new Evaluator(kernel.signature(), 10_000_000),
                BigInteger.valueOf(20));
        assertFalse(library.observe("oddPrimes", Map.of("n", BigInteger.ONE), truth));
        assertFalse(library.observe("oddPrimes", Map.of("n", BigInteger.valueOf(3)), truth));
        assertTrue(library.observe("oddPrimes", Map.of("n", BigInteger.valueOf(4)), truth), "9 has the divisor 3");
    }

    @Test
    void aConjecturedLinkCannotVouchForItself() {
        Term n = var("n");
        // "isZero is a decider for Even" is false (isZero(2) = 0 but Even(2)), and links via conjectures.
        Formula decides = Formula.eq(app("isZero", n), numeral(1));
        // The forward link could never be caught failing (Even is an unbounded ∃), so the bench
        // refuses it; it is made directly here to set up the decider.
        assertThrows(ProofException.class, () -> library.conjecture(kernel, deciders, "fakeF",
                Formula.implies(decides, Formula.pred("Even", n))));
        Theorem fwd = kernel.conjecture("fakeF", Formula.implies(decides, Formula.pred("Even", n)));
        Theorem back = library.conjecture(kernel, deciders, "fakeB", Formula.implies(Formula.pred("Even", n), decides));
        deciders.register("Even", "isZero", fwd, back);
        // Checking fakeB at n = 2 must unfold Even (witness k = 1), not ask the decider fakeB vouches for.
        assertTrue(library.observe("fakeB", Map.of("n", BigInteger.TWO), truth()));
    }

    @Test
    void statusesIgnoreVariableNames() {
        Term n = var("n");
        Theorem dbl = kernel.definingStatements("Double").get(0);
        library.name("double_def", dbl);
        Formula renamed = Formula.eq(app("Double", var("m")), app("add", var("m"), var("m")));
        assertInstanceOf(Library.Proven.class, library.status(renamed));
        assertThrows(ProofException.class, () -> library.name("double_def", dbl));
        assertThrows(ProofException.class, () -> library.name("open", kernel.assume(Formula.eq(n, n))));
    }

    @Test
    void printerReadsLikeTheDesignDoc() {
        Printer p = new Printer(kernel.signature());
        Term n = var("n");
        assertEquals("∀n. 0 + n = n", p.statement(Formula.eq(app("add", Term.zero(), n), n)));
        assertEquals("∃k. 12 = k + k", p.statement(Formula.exists("k", Formula.eq(numeral(12), app("add", var("k"), var("k"))))));
        assertEquals("∀a b. (a + b) × a = a", p.statement(Formula.eq(app("mul", app("add", var("a"), var("b")), var("a")), var("a"))));
        assertEquals("∀n. ¬Even(S(n + n))", p.statement(Formula.not(Formula.pred("Even", succ(app("add", n, n))))));
    }
}
