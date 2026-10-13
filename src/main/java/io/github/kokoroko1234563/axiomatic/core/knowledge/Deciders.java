package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * Deciders joined to their property by a proven link theorem. Before its link is proven a decider
 * is just a circuit; afterwards running it on numerals settles the property for them.
 *
 * <p>A link for property P(x̄) and decider f is the pair of proven statements
 * {@code f(x̄) = 1 → P(x̄)} and {@code P(x̄) → f(x̄) = 1}.
 */
public final class Deciders {
    /** A registered decider, with the dependencies of its link theorems (conjectures taint results). */
    public record Link(String property, String function, Set<String> dependencies) {}

    private final Signature signature;
    private final Map<String, Link> byProperty = new HashMap<>();

    public Deciders(Signature signature) {
        this.signature = signature;
    }

    public Link get(String property) {
        return byProperty.get(property);
    }

    public java.util.Collection<Link> all() {
        return byProperty.values();
    }

    /** Puts back a saved decider link. */
    public void restore(Link link) {
        byProperty.put(link.property(), link);
    }

    /** Checks the two link theorems and registers {@code function} as the decider for {@code property}. */
    public Link register(String property, String function, Theorem forward, Theorem backward) {
        if (!(signature.get(property) instanceof Definition.Predicate pred)) {
            throw new ProofException("Unknown property: " + property);
        }
        Definition fn = signature.get(function);
        if (!(fn instanceof Definition.Explicit) && !(fn instanceof Definition.PrimitiveRecursive)) {
            throw new ProofException("Unknown function: " + function);
        }
        if (fn.params().size() != pred.params().size()) {
            throw new ProofException(function + " must take the same inputs as " + property);
        }
        List<Term> params = pred.params().stream().<Term>map(Term::var).toList();
        Formula decides = Formula.eq(new Term.App(function, params), Term.numeral(1));
        Formula holds = new Formula.Pred(property, params);
        requireLink(forward, Formula.implies(decides, holds));
        requireLink(backward, Formula.implies(holds, decides));
        Set<String> deps = new java.util.HashSet<>(forward.dependencies());
        deps.addAll(backward.dependencies());
        Link link = new Link(property, function, deps);
        byProperty.put(property, link);
        return link;
    }

    private static void requireLink(Theorem thm, Formula expected) {
        if (!thm.isClosed()) {
            throw new ProofException("The link theorem still depends on an open assumption");
        }
        if (!Syntax.alphaEquals(thm.formula(), expected)) {
            throw new ProofException("Expected a proof of " + expected + ", got " + thm.formula());
        }
    }
}
