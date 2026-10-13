package io.github.kokoroko1234563.axiomatic.core.logic;

import java.math.BigInteger;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula.And;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Bottom;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Eq;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Exists;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Implies;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Pred;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.App;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Succ;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Var;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Zero;

/**
 * Formats terms and statements for tooltips and the Notebook: closed numerals as digits, infix
 * symbols for definitions that have one, "¬P" for P → ⊥, and a leading ∀ for free variables.
 */
public final class Printer {
    private final Signature signature;

    public Printer(Signature signature) {
        this.signature = signature;
    }

    /** A statement as a player reads it, e.g. "∀n. 0 + n = n". */
    public String statement(Formula f) {
        Set<String> free = Syntax.freeVars(f);
        String body = formula(f, false);
        return free.isEmpty() ? body : "∀" + String.join(" ", free) + ". " + body;
    }

    public String formula(Formula f) {
        return formula(f, false);
    }

    private String formula(Formula f, boolean nested) {
        String s = switch (f) {
            case Eq e -> term(e.left(), false) + " = " + term(e.right(), false);
            case And a -> formula(a.left(), true) + " ∧ " + formula(a.right(), true);
            case Implies i when i.conclusion() instanceof Bottom -> "¬" + formula(i.premise(), true);
            case Implies i -> formula(i.premise(), true) + " → " + formula(i.conclusion(), true);
            case Exists ex -> "∃" + ex.var() + ". " + formula(ex.body(), false);
            case Bottom b -> "⊥";
            case Pred p -> {
                Definition def = signature.get(p.name());
                if (def != null && def.symbol() != null && p.args().size() == 2) {
                    yield term(p.args().get(0), true) + " " + def.symbol() + " " + term(p.args().get(1), true);
                }
                yield p.name() + "(" + p.args().stream().map(x -> term(x, false)).collect(Collectors.joining(", ")) + ")";
            }
        };
        boolean atomic = f instanceof Eq || f instanceof Bottom || f instanceof Pred
                || (f instanceof Implies i && i.conclusion() instanceof Bottom);
        return nested && !atomic ? "(" + s + ")" : s;
    }

    public String term(Term t) {
        return term(t, false);
    }

    private String term(Term t, boolean nested) {
        BigInteger value = numeralValue(t);
        if (value != null) {
            return value.toString();
        }
        return switch (t) {
            case Zero z -> "0";
            case Succ s -> "S(" + term(s.arg(), false) + ")";
            case Var v -> v.name();
            case App a -> {
                Definition def = signature.get(a.function());
                if (def != null && def.symbol() != null && a.args().size() == 2) {
                    String s = term(a.args().get(0), true) + " " + def.symbol() + " " + term(a.args().get(1), true);
                    yield nested ? "(" + s + ")" : s;
                }
                yield a.function() + "(" + a.args().stream().map(x -> term(x, false)).collect(Collectors.joining(", "))
                        + ")";
            }
        };
    }

    /** Variables as printed: single letters (possibly with ' or #n), not followed by "(". */
    private static final java.util.regex.Pattern VARIABLE =
            java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])[a-z](?:'|#\\d+)*(?![A-Za-z0-9_(])");

    /**
     * Whether two printed statements are the same up to the names of their variables, so
     * "∀m. ∃q. m ≤ q ∧ Prime(q)" matches "∀n. ∃p. n ≤ p ∧ Prime(p)". Used by theorem-gated recipes.
     */
    public static boolean sameUpToVariables(String a, String b) {
        return normalizeVariables(a).equals(normalizeVariables(b));
    }

    /** Renames printed variables to v0, v1, … in order of first appearance. */
    public static String normalizeVariables(String display) {
        java.util.Map<String, String> names = new java.util.HashMap<>();
        var m = VARIABLE.matcher(display);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = names.computeIfAbsent(m.group(), k -> "v" + names.size());
            m.appendReplacement(out, name);
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The value of a closed numeral S(S(…0)), or null if the term is not one. */
    public static BigInteger numeralValue(Term t) {
        long count = 0;
        while (t instanceof Succ s) {
            count++;
            t = s.arg();
        }
        return t instanceof Zero ? BigInteger.valueOf(count) : null;
    }
}
