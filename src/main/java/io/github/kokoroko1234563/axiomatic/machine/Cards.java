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
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import net.minecraft.network.chat.Component;

/**
 * Theorem cards: statements slotted into machines to certify what they make. Shared by the
 * Compiled Machine and the Prime Forge. A card may be proven, Tested (then only inside its
 * tested range) or conjectural (then every use is checked in the background).
 *
 * <p>Using a card has three parts so the computing can run off the server thread:
 * {@link #usable} and {@link #prepare} on the server thread, {@link Use#evaluate} anywhere (it
 * only computes), and {@link Outcome#commit} back on the server thread.
 */
public final class Cards {
    private Cards() {}

    /** A planned card use: the card and the conjectures it must be checked against. */
    public record Use(StatementData card, BigInteger testedUpTo, List<Library.Conjecture> watched) {
        /**
         * Computes the background checks and the certificate. Pure computation: safe on a worker
         * thread with a {@link Truth} over copied definitions.
         */
        public Outcome evaluate(Map<String, BigInteger> assignment, List<Fact> premises, boolean computePremises,
                Truth truth) {
            if (testedUpTo != null) {
                for (BigInteger v : assignment.values()) {
                    if (v.compareTo(testedUpTo) > 0) {
                        return Outcome.refused(Component.translatable("status.axiomatic.outside_tested", testedUpTo.toString()));
                    }
                }
            }
            for (Library.Conjecture c : watched) {
                Truth.Value v = truth.ignoringDecidersFrom("conjecture:" + c.name()).evaluate(c.statement(), assignment);
                truth.ignoringDecidersFrom(null);
                if (v == Truth.Value.FALSE) {
                    return new Outcome(null, null, c.name(), Map.copyOf(assignment));
                }
            }
            try {
                return new Outcome(Certifier.byStatement(card.formula(), Set.copyOf(card.dependencies()), assignment,
                        premises, truth, computePremises), null, null, null);
            } catch (ProofException e) {
                return Outcome.refused(Component.literal(e.getMessage()));
            }
        }
    }

    /** What a card use came to: a certificate, a refusal, or a refuted conjecture. */
    public record Outcome(Fact fact, Component refusal, String refutedConjecture, Map<String, BigInteger> counterexample) {
        static Outcome refused(Component why) {
            return new Outcome(null, why, null, null);
        }

        /** Records a refutation in the Library. Server thread only. Returns whether one happened. */
        public boolean commit(Library library) {
            if (refutedConjecture == null) {
                return false;
            }
            library.refute(refutedConjecture, counterexample);
            return true;
        }

        public Component message() {
            if (refutedConjecture != null) {
                return Component.translatable("status.axiomatic.refuted_now", refutedConjecture);
            }
            return refusal == null ? Component.empty() : refusal;
        }
    }

    /** Plans a card use (server thread): checks the card may be used at all. */
    public static Use prepare(Library library, StatementData card) {
        BigInteger testedUpTo = usable(library, card);
        return new Use(card, testedUpTo, library.watching(Set.copyOf(card.dependencies()), card.formula()));
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
