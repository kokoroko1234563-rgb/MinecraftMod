package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
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

    private final Map<String, Entry> statuses = new LinkedHashMap<>();
    private final Map<String, Theorem> named = new LinkedHashMap<>();
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
            throw new ProofException("No machine could ever catch this failing: it relies on an unbounded ∃");
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
        if (truth.evaluate(c.statement, instance) == Truth.Value.FALSE) {
            c.state = ConjectureState.REFUTED;
            c.counterexample = Map.copyOf(instance);
            put(c.statement, new Refuted(c.counterexample));
            return true;
        }
        return false;
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
}
