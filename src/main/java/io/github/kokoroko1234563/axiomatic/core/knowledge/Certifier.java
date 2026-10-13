package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * Issues certificates ({@link Fact}s) for numerals, the ways listed under "Numeral certificates"
 * in the design doc: by computation (witness search or a linked decider), or by a design's
 * statement such as "Even(Double(n))" or the closure theorem "Even(a) → Even(b) → Even(a + b)",
 * which pass certificates from a machine's inputs to its output for 1 unit.
 */
public final class Certifier {
    private Certifier() {}

    /**
     * Witness search or a linked decider: computes whether the property holds. Returns null when
     * it does not or the search found nothing below the cap. Cost is in {@code truth.evaluator()}.
     */
    public static Fact byComputation(Truth truth, String property, List<BigInteger> args) {
        if (truth.property(property, args) != Truth.Value.TRUE) {
            return null;
        }
        return new Fact(property, args, truth.dependencies());
    }

    /** By a proven statement: see {@link #byStatement}. */
    public static Fact byTheorem(Theorem thm, Map<String, BigInteger> inputs, List<Fact> premises, Truth truth) {
        if (!thm.isClosed()) {
            throw new ProofException("The theorem still depends on an open assumption");
        }
        return byStatement(thm.formula(), thm.dependencies(), inputs, premises, truth, false);
    }

    /**
     * Certifies the conclusion of a statement {@code H₁ → … → Hₖ → P(t̄)} for concrete inputs.
     * The statement must be proven (or tested over a range containing the inputs, or conjectured;
     * {@code dependencies} records which). Each premise Hᵢ that is a property must be matched by
     * one of the given certificates (or, if {@code computePremises}, settled by computation, as
     * the Prime Forge does with bare candidates); premises that are equations are checked by
     * computing them.
     *
     * @param inputs a value for every free variable of the statement
     */
    public static Fact byStatement(Formula statement, Set<String> dependencies, Map<String, BigInteger> inputs,
            List<Fact> premises, Truth truth, boolean computePremises) {
        if (!inputs.keySet().containsAll(Syntax.freeVars(statement))) {
            throw new ProofException("Every variable of " + statement + " needs an input value");
        }
        Set<String> deps = new HashSet<>(dependencies);
        Formula f = statement;
        while (f instanceof Formula.Implies imp) {
            requirePremise(imp.premise(), inputs, premises, truth, deps, computePremises);
            f = imp.conclusion();
        }
        if (!(f instanceof Formula.Pred p)) {
            throw new ProofException("The statement must conclude a property, such as Even(…), got " + f);
        }
        List<BigInteger> values = new ArrayList<>();
        p.args().forEach(t -> values.add(truth.evaluator().eval(t, inputs)));
        return new Fact(p.name(), values, deps);
    }

    private static void requirePremise(Formula premise, Map<String, BigInteger> inputs, List<Fact> premises,
            Truth truth, Set<String> deps, boolean computePremises) {
        switch (premise) {
            case Formula.Pred p -> {
                List<BigInteger> values = p.args().stream().map(t -> truth.evaluator().eval(t, inputs)).toList();
                Fact match = premises.stream().filter(fact -> fact.sameClaim(p.name(), values)).findFirst().orElse(null);
                if (match != null) {
                    deps.addAll(match.dependencies());
                } else if (computePremises && truth.property(p.name(), values) == Truth.Value.TRUE) {
                    // No certificate given, but computing settles it (a witness or a linked decider).
                    deps.addAll(truth.dependencies());
                } else {
                    throw new ProofException("Needs a certificate for " + p.name()
                            + values.toString().replace('[', '(').replace(']', ')'));
                }
            }
            case Formula.Eq e -> {
                if (truth.evaluate(e, inputs) != Truth.Value.TRUE) {
                    throw new ProofException("The precondition " + e + " does not hold for these inputs");
                }
            }
            default -> throw new ProofException("Unsupported precondition: " + premise);
        }
    }
}
