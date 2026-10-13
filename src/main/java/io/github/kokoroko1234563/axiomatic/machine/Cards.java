package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Certifier;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Truth;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import net.minecraft.network.chat.Component;

/**
 * Theorem cards: statements slotted into machines to certify what they make. Shared by the
 * Compiled Machine and the Prime Forge. A card may be proven, Tested (then only inside its
 * tested range) or conjectural (then every use is checked in the background).
 */
public final class Cards {
    private Cards() {}

    /**
     * Applies a card to concrete values of its variables: checks it may be used, runs the
     * background check if it is a conjecture, and certifies its conclusion.
     *
     * @return the certificate, or throws {@link ProofException} with a player-readable reason
     *         (including "refuted now" when this very use refutes a conjecture)
     */
    public static Fact apply(Library library, StatementData card, Map<String, BigInteger> assignment, List<Fact> premises,
            boolean computePremises, Truth truth, Runnable onRefuted) {
        BigInteger testedUpTo = usable(library, card);
        if (testedUpTo != null) {
            for (BigInteger v : assignment.values()) {
                if (v.compareTo(testedUpTo) > 0) {
                    throw new ProofException(Component.translatable("status.axiomatic.outside_tested",
                            testedUpTo.toString()).getString());
                }
            }
        }
        // Background check: a conjecture applied to numbers is computed for those numbers.
        for (Library.Conjecture c : library.conjectures()) {
            if (c.state() == Library.ConjectureState.ACTIVE && card.dependencies().contains("conjecture:" + c.name())
                    && Syntax.alphaEquals(c.statement(), card.formula())) {
                if (library.observe(c.name(), assignment, truth)) {
                    onRefuted.run();
                    throw new ProofException(Component.translatable("status.axiomatic.refuted_now", c.name()).getString());
                }
            }
        }
        return Certifier.byStatement(card.formula(), Set.copyOf(card.dependencies()), assignment, premises, truth,
                computePremises);
    }

    /**
     * Checks that a card may be used at all. Returns the tested bound for a Tested card, or null
     * for a proven (possibly conjectural) one.
     */
    public static BigInteger usable(Library library, StatementData card) {
        BigInteger testedUpTo = null;
        if (!card.proven()) {
            if (library.status(card.formula()) instanceof Library.Tested t) {
                testedUpTo = t.upTo();
            } else {
                throw new ProofException(Component.translatable("status.axiomatic.card_unproven").getString());
            }
        } else if (!card.hypotheses().isEmpty()) {
            throw new ProofException(Component.translatable("status.axiomatic.card_unproven").getString());
        }
        Set<String> refuted = library.refuted(Set.copyOf(card.dependencies()));
        if (!refuted.isEmpty()) {
            throw new ProofException(Component.translatable("status.axiomatic.card_refuted", String.join(", ", refuted)).getString());
        }
        for (String dep : card.dependencies()) {
            if (dep.startsWith("conjecture:")) {
                Library.Conjecture c = library.conjectureNamed(dep.substring("conjecture:".length()));
                if (c != null && c.state() == Library.ConjectureState.DORMANT) {
                    throw new ProofException(Component.translatable("status.axiomatic.card_dormant", c.name()).getString());
                }
            }
        }
        return testedUpTo;
    }
}
