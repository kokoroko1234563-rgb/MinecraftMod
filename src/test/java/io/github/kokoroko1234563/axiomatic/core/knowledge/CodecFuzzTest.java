package io.github.kokoroko1234563.axiomatic.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/** Saved statements come back exactly: random formulas through the pointed-graph codec. */
class CodecFuzzTest {
    private static final List<String> VARS = List.of("a", "b", "n", "k#3", "x'");
    private static final List<String> FUNCTIONS = List.of("add", "mul", "f", "Monoid.op");

    private Term term(Random r, int depth) {
        int pick = depth <= 0 ? r.nextInt(3) : r.nextInt(5);
        return switch (pick) {
            case 0 -> Term.var(VARS.get(r.nextInt(VARS.size())));
            case 1 -> Term.zero();
            case 2 -> Term.numeral(r.nextInt(200));
            case 3 -> Term.succ(term(r, depth - 1));
            default -> Term.app(FUNCTIONS.get(r.nextInt(FUNCTIONS.size())), term(r, depth - 1), term(r, depth - 1));
        };
    }

    private Formula formula(Random r, int depth) {
        int pick = depth <= 0 ? r.nextInt(3) : r.nextInt(6);
        return switch (pick) {
            case 0 -> Formula.eq(term(r, 2), term(r, 2));
            case 1 -> Formula.pred(r.nextBoolean() ? "Even" : "le", term(r, 2));
            case 2 -> new Formula.Bottom();
            case 3 -> Formula.and(formula(r, depth - 1), formula(r, depth - 1));
            case 4 -> Formula.implies(formula(r, depth - 1), formula(r, depth - 1));
            default -> Formula.exists(VARS.get(r.nextInt(VARS.size())), formula(r, depth - 1));
        };
    }

    @Test
    void formulasSurviveTheCodec() throws IOException {
        Random r = new Random(5);
        for (int i = 0; i < 3000; i++) {
            Formula f = formula(r, 4);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            MathState.writeFormula(new DataOutputStream(bytes), f);
            Formula back = MathState.readFormula(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertEquals(f, back);
        }
    }
}
