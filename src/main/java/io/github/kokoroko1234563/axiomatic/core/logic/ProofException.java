package io.github.kokoroko1234563.axiomatic.core.logic;

/** Thrown when the checker refuses a step. The message is meant to be shown to the player. */
public class ProofException extends RuntimeException {
    public ProofException(String message) {
        super(message);
    }
}
