package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * The central record: named theorems, the status of every design property, and conjectures.
 * Statuses attach to statements up to renaming, so "Even(Double(n))" and "Even(Double(m))" share one.
 */
public final class Library {

    /** Knowledge status of a statement. */
    public sealed interface Status permits Unknown, Tested, Proven, Refuted {}

    public record Unknown() implements Status {}

    /** Held for every case with all variables ≤ upTo. */
    public record Tested(BigInteger upTo) implements Status {}

    public record Proven(Theorem theorem) implements Status {}

    /** Permanently false, with the case that showed it. */
    public record Refuted(Map<String, BigInteger> counterexample) implements Status {}

    public enum ConjectureState { ACTIVE, DORMANT, PROVEN, REFUTED }

    /** A conjecture block's record. */
    public static final class Conjecture {
        private final String name;
        private final Formula statement;
        private ConjectureState state = ConjectureState.ACTIVE;
        private Map<String, BigInteger> counterexample;

        Conjecture(String name, Formula statement) {
            this.name = name;
            this.statement = statement;
        }

        public String name() {
            return name;
        }

        public Formula statement() {
            return statement;
        }

        public ConjectureState state() {
            return state;
        }

        public Map<String, BigInteger> counterexample() {
            return counterexample;
        }
    }

    private record Entry(Formula statement, Status status) {}

    /**
     * A structure certificate: these concrete definitions form a monoid (or equivalence, or
     * order), with the proofs of its laws. Generic theorems apply to it with no new proof.
     */
    public record StructureCert(String structure, Map<String, String> mapping, List<Theorem> laws) {
        public StructureCert {
            mapping = Map.copyOf(mapping);
            laws = List.copyOf(laws);
        }
    }

    private final List<StructureCert> certificates = new java.util.ArrayList<>();

    private final Map<String, Entry> statuses = new LinkedHashMap<>();
    private final Map<String, Theorem> named = new LinkedHashMap<>();
    private Runnable onNamed = () -> {};
    private java.util.function.Consumer<String> onRefuted = name -> {};

    /** Called when a conjecture is refuted, e.g. to drop decider links resting on it. */
    public void onRefuted(java.util.function.Consumer<String> listener) {
        onRefuted = listener;
    }

    /** Called whenever a theorem is named (or restored), e.g. to notice that binary is proven. */
    public void onNamed(Runnable listener) {
        onNamed = listener;
    }
    private final Map<String, Conjecture> conjectures = new LinkedHashMap<>();

    // ---- Statuses -------------------------------------------------------------------------

    public Status status(Formula statement) {
        Entry e = statuses.get(Syntax.key(statement));
        return e == null ? new Unknown() : e.status();
    }

    /** Records a Test Bench run. A refutation is permanent; a test never downgrades a proof. */
    public Status recordTest(Formula statement, TestBench.Result result) {
        Status current = status(statement);
        if (current instanceof Refuted || current instanceof Proven) {
            return current;
        }
        Status next = switch (result) {
            case TestBench.Counterexample c -> new Refuted(c.assignment());
            case TestBench.Passed p -> current instanceof Tested t && t.upTo().compareTo(p.upTo()) >= 0
                    ? current : new Tested(p.upTo());
            case TestBench.Inconclusive i -> current;
        };
        put(statement, next);
        return next;
    }

    /** Records a proof. Only an unconditional, non-conjectural proof makes a statement Proven. */
    public Status recordProof(Theorem thm) {
        if (thm.isClosed() && !thm.isConjectural()) {
            put(thm.formula(), new Proven(thm));
        }
        return status(thm.formula());
    }

    private void put(Formula statement, Status status) {
        statuses.put(Syntax.key(statement), new Entry(statement, status));
    }

    /** Every recorded status with its statement, for saving. */
    public Map<Formula, Status> allStatuses() {
        Map<Formula, Status> out = new LinkedHashMap<>();
        statuses.values().forEach(e -> out.put(e.statement(), e.status()));
        return out;
    }

    /** Puts back a saved status. */
    public void restoreStatus(Formula statement, Status status) {
        put(statement, status);
    }

    /** Puts back a saved conjecture. */
    public void restoreConjecture(String name, Formula statement, ConjectureState state,
            Map<String, BigInteger> counterexample) {
        Conjecture c = new Conjecture(name, statement);
        c.state = state;
        c.counterexample = counterexample;
        conjectures.put(name, c);
    }

    /** Puts back a saved named theorem without re-recording its status. */
    public void restoreTheorem(String name, Theorem thm) {
        named.put(name, thm);
        onNamed.run();
    }

    // ---- Structure certificates -----------------------------------------------------------

    /**
     * Certifies a structure from proven laws, reading off which definitions play its symbols.
     * Throws if the laws are not the structure's axioms for some concrete definitions.
     */
    public StructureCert certifyStructure(Kernel kernel, String structure, List<Theorem> laws) {
        var s = kernel.structure(structure);
        Map<String, String> mapping = io.github.kokoroko1234563.axiomatic.core.logic.Structures.inferMapping(s,
                laws.stream().map(Theorem::formula).toList());
        if (mapping == null) {
            throw new ProofException("These are not the " + structure + " laws, in order");
        }
        // The kernel's interpretation rule re-checks everything: an axiom maps to its law.
        kernel.interpret(kernel.structureAxiom(structure, 0), structure, mapping, laws);
        StructureCert cert = new StructureCert(structure, mapping, laws);
        certificates.removeIf(c -> c.structure().equals(structure) && c.mapping().equals(cert.mapping()));
        certificates.add(cert);
        return cert;
    }

    public List<StructureCert> certificates() {
        return List.copyOf(certificates);
    }

    /** Puts back a saved certificate. */
    public void restoreCertificate(StructureCert cert) {
        certificates.add(cert);
    }

    // ---- Named theorems -------------------------------------------------------------------

    /** Compiles a proof under a name, as a theorem block. */
    public void name(String name, Theorem thm) {
        if (!Syntax.isValidName(name)) {
            throw new ProofException("Invalid name: " + name);
        }
        if (named.containsKey(name)) {
            throw new ProofException(name + " already names a theorem");
        }
        if (!thm.isClosed()) {
            throw new ProofException("A theorem block cannot depend on an open assumption");
        }
        named.put(name, thm);
        recordProof(thm);
        onNamed.run();
    }

    public Theorem theorem(String name) {
        return named.get(name);
    }

    public Map<String, Theorem> theorems() {
        return named;
    }

    // ---- Conjectures ----------------------------------------------------------------------

    /**
     * Conjecture Bench: creates a conjecture block. Refused for statements whose failure could
     * never be detected by computation.
     */
    public Theorem conjecture(Kernel kernel, Deciders deciders, String name, Formula statement) {
        if (conjectures.containsKey(name)) {
            throw new ProofException(name + " already names a conjecture");
        }
        if (status(statement) instanceof Refuted r) {
            throw new ProofException("Already refuted at " + r.counterexample());
        }
        if (!Truth.isRefutable(statement, kernel.signature(), deciders)) {
            throw new ProofException("No machine could ever catch this failing: it only claims that something exists");
        }
        Theorem thm = kernel.conjecture(name, statement);
        conjectures.put(name, new Conjecture(name, statement));
        return thm;
    }

    public Conjecture conjectureNamed(String name) {
        return conjectures.get(name);
    }

    public Collection<Conjecture> conjectures() {
        return conjectures.values();
    }

    /**
     * Background check: the conjecture was applied to these numbers, so compute that instance.
     * Returns true when this instance refutes it (a disaster). Cost is in {@code truth.evaluator()}.
     */
    public boolean observe(String name, Map<String, BigInteger> instance, Truth truth) {
        Conjecture c = conjectures.get(name);
        if (c == null || c.state != ConjectureState.ACTIVE) {
            return false;
        }
        if (truth.ignoringDecidersFrom("conjecture:" + name).evaluate(c.statement, instance) == Truth.Value.FALSE) {
            c.state = ConjectureState.REFUTED;
            c.counterexample = Map.copyOf(instance);
            put(c.statement, new Refuted(c.counterexample));
            return true;
        }
        return false;
    }

    /** Active conjectures that a use of this card must be checked against. */
    public List<Conjecture> watching(Set<String> cardDependencies, Formula cardStatement) {
        return conjectures.values().stream().filter(c -> c.state == ConjectureState.ACTIVE
                && cardDependencies.contains("conjecture:" + c.name)
                && io.github.kokoroko1234563.axiomatic.core.logic.Syntax.alphaEquals(c.statement, cardStatement)).toList();
    }

    /** Records a counterexample found by a background check made elsewhere (another thread). */
    public void refute(String name, Map<String, BigInteger> counterexample) {
        Conjecture c = conjectures.get(name);
        if (c == null || c.state == ConjectureState.REFUTED || c.state == ConjectureState.PROVEN) {
            return;
        }
        c.state = ConjectureState.REFUTED;
        c.counterexample = Map.copyOf(counterexample);
        put(c.statement, new Refuted(c.counterexample));
        onRefuted.accept(name);
    }

    /** Unpaid upkeep: the block stops working until paid again. Not a disaster. */
    public void setDormant(String name, boolean dormant) {
        Conjecture c = conjectures.get(name);
        if (c != null && (c.state == ConjectureState.ACTIVE || c.state == ConjectureState.DORMANT)) {
            c.state = dormant ? ConjectureState.DORMANT : ConjectureState.ACTIVE;
        }
    }

    /** Proving a conjecture upgrades it in place: upkeep stops and its marks become proven. */
    public void proveConjecture(String name, Theorem proof) {
        Conjecture c = conjectures.get(name);
        if (c == null) {
            throw new ProofException("No conjecture named " + name);
        }
        if (!proof.isClosed() || proof.isConjectural()) {
            throw new ProofException("The proof must not depend on assumptions or conjectures");
        }
        if (!Syntax.alphaEquals(proof.formula(), c.statement)) {
            throw new ProofException("That proves " + proof.formula() + ", not " + c.statement);
        }
        c.state = ConjectureState.PROVEN;
        recordProof(proof);
        // Theorems resting on it may now count as proven (the binary upgrade, for one).
        onNamed.run();
    }

    /** Conjectures among these dependencies that have been refuted: anything built on them fails. */
    public Set<String> refuted(Set<String> dependencies) {
        return Set.copyOf(dependencies.stream().filter(d -> d.startsWith("conjecture:"))
                .filter(d -> {
                    Conjecture c = conjectures.get(d.substring("conjecture:".length()));
                    return c != null && c.state == ConjectureState.REFUTED;
                }).toList());
    }

    /** Conjectures among these dependencies that are still unproven ("Depends on 3 unproven conjectures"). */
    public Set<String> unproven(Set<String> dependencies) {
        return Set.copyOf(dependencies.stream().filter(d -> d.startsWith("conjecture:"))
                .filter(d -> {
                    Conjecture c = conjectures.get(d.substring("conjecture:".length()));
                    return c == null || c.state != ConjectureState.PROVEN;
                }).toList());
    }

    // ---- The binary upgrade ---------------------------------------------------------------

    /**
     * Whether binary is proven: the Library holds a named, fully proven theorem saying every
     * number splits into halves and a bit, ∀n. n = (h(n) + h(n)) + p(n) ∧ p(n) ≤ 1, for some
     * functions h and p (canonical + and ≤). Numerals are then handled by their digits.
     */
    public boolean binaryProven(Signature signature) {
        for (Theorem t : named.values()) {
            if (t.isClosed() && unproven(t.dependencies()).isEmpty() && isBinaryDecomposition(t.formula(), signature)) {
                return true;
            }
        }
        return false;
    }

    /** n = (h(n) + h(n)) + p(n) ∧ p(n) ≤ 1 (either side of the equation first). */
    public static boolean isBinaryDecomposition(Formula f, Signature signature) {
        if (!(f instanceof Formula.And and) || !(and.left() instanceof Formula.Eq eq)
                || !(and.right() instanceof Formula.Pred le) || le.args().size() != 2
                || !Bounds.isLe(signature, le.name()) || !le.args().get(1).equals(Term.succ(Term.zero()))) {
            return false;
        }
        Term n = eq.left() instanceof Term.Var ? eq.left() : eq.right();
        Term sum = n == eq.left() ? eq.right() : eq.left();
        if (!(n instanceof Term.Var) || !(sum instanceof Term.App outer) || !Bounds.isAdd(signature, outer.function())
                || outer.args().size() != 2 || !(outer.args().get(0) instanceof Term.App inner)
                || !inner.function().equals(outer.function()) || !inner.args().get(0).equals(inner.args().get(1))) {
            return false;
        }
        Term half = inner.args().get(0);
        Term bit = outer.args().get(1);
        return isUnaryCall(half, n, signature) && isUnaryCall(bit, n, signature) && le.args().get(0).equals(bit);
    }

    private static boolean isUnaryCall(Term t, Term n, Signature signature) {
        return t instanceof Term.App a && a.args().equals(List.of(n)) && Signature.isFunction(signature.get(a.function()));
    }
}
