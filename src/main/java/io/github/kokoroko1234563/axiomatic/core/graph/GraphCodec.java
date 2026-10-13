package io.github.kokoroko1234563.axiomatic.core.graph;

import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;

/**
 * Converts terms and statements to and from pointed graphs.
 *
 * <p>Labels: "0", "S", "var:NAME", "app:NAME" for terms; "eq", "and", "imp", "bot", "ex:VAR",
 * "pred:NAME" for statements. A numeral is a chain of S nodes ending at 0, with the outermost S
 * as the point, as in the design doc.
 */
public final class GraphCodec {
    private GraphCodec() {}

    public static PointedGraph encode(Term t) {
        PointedGraph.Builder b = new PointedGraph.Builder();
        return b.build(add(b, t));
    }

    public static PointedGraph encode(Formula f) {
        PointedGraph.Builder b = new PointedGraph.Builder();
        return b.build(add(b, f));
    }

    private static int add(PointedGraph.Builder b, Term t) {
        return switch (t) {
            case Term.Zero z -> b.add("0", List.of());
            case Term.Succ s -> b.add("S", List.of(add(b, s.arg())));
            case Term.Var v -> b.add("var:" + v.name(), List.of());
            case Term.App a -> b.add("app:" + a.function(), a.args().stream().map(x -> add(b, x)).toList());
        };
    }

    private static int add(PointedGraph.Builder b, Formula f) {
        return switch (f) {
            case Formula.Eq e -> b.add("eq", List.of(add(b, e.left()), add(b, e.right())));
            case Formula.And a -> b.add("and", List.of(add(b, a.left()), add(b, a.right())));
            case Formula.Implies i -> b.add("imp", List.of(add(b, i.premise()), add(b, i.conclusion())));
            case Formula.Exists ex -> b.add("ex:" + ex.var(), List.of(add(b, ex.body())));
            case Formula.Bottom bot -> b.add("bot", List.of());
            case Formula.Pred p -> b.add("pred:" + p.name(), p.args().stream().map(x -> add(b, x)).toList());
        };
    }

    public static Term decodeTerm(PointedGraph g) {
        return term(g, g.point());
    }

    public static Formula decodeFormula(PointedGraph g) {
        return formula(g, g.point());
    }

    private static Term term(PointedGraph g, int i) {
        PointedGraph.Node n = g.nodes().get(i);
        String label = n.label();
        if (label.equals("0") && n.children().isEmpty()) {
            return Term.zero();
        }
        if (label.equals("S") && n.children().size() == 1) {
            return Term.succ(term(g, n.children().get(0)));
        }
        if (label.startsWith("var:") && n.children().isEmpty()) {
            return Term.var(label.substring(4));
        }
        if (label.startsWith("app:")) {
            List<Term> args = new ArrayList<>();
            n.children().forEach(c -> args.add(term(g, c)));
            return new Term.App(label.substring(4), args);
        }
        throw new IllegalArgumentException("Not a term node: " + label);
    }

    private static Formula formula(PointedGraph g, int i) {
        PointedGraph.Node n = g.nodes().get(i);
        String label = n.label();
        List<Integer> c = n.children();
        switch (label) {
            case "eq" -> {
                if (c.size() == 2) {
                    return Formula.eq(term(g, c.get(0)), term(g, c.get(1)));
                }
            }
            case "and" -> {
                if (c.size() == 2) {
                    return Formula.and(formula(g, c.get(0)), formula(g, c.get(1)));
                }
            }
            case "imp" -> {
                if (c.size() == 2) {
                    return Formula.implies(formula(g, c.get(0)), formula(g, c.get(1)));
                }
            }
            case "bot" -> {
                if (c.isEmpty()) {
                    return Formula.BOTTOM;
                }
            }
            default -> {
                if (label.startsWith("ex:") && c.size() == 1) {
                    return Formula.exists(label.substring(3), formula(g, c.get(0)));
                }
                if (label.startsWith("pred:")) {
                    List<Term> args = new ArrayList<>();
                    c.forEach(x -> args.add(term(g, x)));
                    return new Formula.Pred(label.substring(5), args);
                }
            }
        }
        throw new IllegalArgumentException("Not a statement node: " + label);
    }
}
