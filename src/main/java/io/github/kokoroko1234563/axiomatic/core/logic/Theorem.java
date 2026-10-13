package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.List;
import java.util.Set;

/**
 * A proven statement. The constructor is package-private and only {@link Kernel} calls it, so
 * holding a Theorem means the statement was checked. Free variables hold for every value.
 */
public final class Theorem {
    /** An assumed statement inside an Assumption Chamber, identified by its stamp. */
    public record Hypothesis(long id, Formula formula) {}

    private final Formula formula;
    private final List<Hypothesis> hypotheses;
    private final Set<String> dependencies;
    private final long steps;

    Theorem(Formula formula, List<Hypothesis> hypotheses, Set<String> dependencies, long steps) {
        this.formula = formula;
        this.hypotheses = List.copyOf(hypotheses);
        this.dependencies = Set.copyOf(dependencies);
        this.steps = steps;
    }

    /** What is proven. */
    public Formula formula() {
        return formula;
    }

    /** Open Assumption Chamber hypotheses it depends on, sorted by stamp. Empty means unconditional. */
    public List<Hypothesis> hypotheses() {
        return hypotheses;
    }

    /** Axioms, definitions and conjectures used, as "axiom:…", "def:…", "conjecture:…". */
    public Set<String> dependencies() {
        return dependencies;
    }

    /** Inference steps in the proof, the basis for its compile cost. */
    public long steps() {
        return steps;
    }

    /** True when the statement depends on an unproven conjecture. */
    public boolean isConjectural() {
        return dependencies.stream().anyMatch(d -> d.startsWith("conjecture:"));
    }

    /** True when no hypothesis is still open, so it may leave the Assumption Chamber. */
    public boolean isClosed() {
        return hypotheses.isEmpty();
    }

    @Override
    public String toString() {
        if (hypotheses.isEmpty()) {
            return formula.toString();
        }
        return String.join(", ", hypotheses.stream().map(h -> h.formula().toString()).toList()) + " ⊢ " + formula;
    }
}
