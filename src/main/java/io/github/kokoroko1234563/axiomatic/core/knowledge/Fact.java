package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * A certificate: property or relation {@code property} holds for these numbers, for example
 * Even(12), or Le(3, 5) on a Subtractor's input pair. A certificate is always a full proof of
 * that one fact, so there is no "tested" certificate; but it may depend on a conjecture.
 */
public record Fact(String property, List<BigInteger> args, Set<String> dependencies) {
    public Fact {
        args = List.copyOf(args);
        dependencies = Set.copyOf(dependencies);
    }

    public boolean isConjectural() {
        return dependencies.stream().anyMatch(d -> d.startsWith("conjecture:"));
    }

    /** Whether this certifies the same claim, ignoring where it came from. */
    public boolean sameClaim(String otherProperty, List<BigInteger> otherArgs) {
        return property.equals(otherProperty) && args.equals(otherArgs);
    }

    @Override
    public String toString() {
        return property + args.toString().replace('[', '(').replace(']', ')');
    }
}
