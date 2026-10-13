package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.kokoroko1234563.axiomatic.core.logic.Term.App;

/**
 * Structures: a set of abstract symbols plus axioms about them (the doc's "structure ports"). A
 * theorem proven from a structure's axioms holds for every interpretation of its symbols that
 * satisfies the axioms, so {@link #interpret} turns it into a theorem about concrete functions
 * once their laws are proven. Definitions built on abstract symbols are templates, instantiated
 * on interpretation: pow over a monoid becomes repeated + for addition and repeated × for
 * multiplication.
 *
 * <p>Part of the trusted kernel. Only monoids over all of ℕ, equivalences and orders exist (the
 * scope rule: no algebra beyond monoids).
 */
public final class Structures {
    private Structures() {}

    /** A structure: its abstract symbols and its axioms (free variables mean "for all"). */
    public record Structure(String name, List<Definition.Abstract> symbols, List<Formula> axioms) {}

    public static final String MONOID = "Monoid";
    public static final String EQUIVALENCE = "Equivalence";
    public static final String ORDER = "Order";

    /** Declares the built-in structures if the signature lacks them. */
    static Map<String, Structure> standard(Signature signature) {
        Map<String, Structure> out = new LinkedHashMap<>();
        Term a = Term.var("a");
        Term b = Term.var("b");
        Term c = Term.var("c");

        Definition.Abstract op = new Definition.Abstract("Monoid.op", List.of("a", "b"), false, "∘");
        Definition.Abstract e = new Definition.Abstract("Monoid.e", List.of(), false, "e");
        Term ee = new App("Monoid.e", List.of());
        out.put(MONOID, new Structure(MONOID, List.of(op, e), List.of(
                Formula.eq(Term.app("Monoid.op", Term.app("Monoid.op", a, b), c), Term.app("Monoid.op", a, Term.app("Monoid.op", b, c))),
                Formula.eq(Term.app("Monoid.op", ee, a), a),
                Formula.eq(Term.app("Monoid.op", a, ee), a))));

        out.put(EQUIVALENCE, relationStructure(EQUIVALENCE, "Equivalence.R", "≡", true));
        out.put(ORDER, relationStructure(ORDER, "Order.R", "⊑", false));

        for (Structure s : out.values()) {
            for (Definition.Abstract sym : s.symbols()) {
                if (signature.get(sym.name()) == null) {
                    signature.defineInternal(sym);
                }
            }
        }
        return out;
    }

    private static Structure relationStructure(String name, String symbol, String display, boolean symmetric) {
        Term a = Term.var("a");
        Term b = Term.var("b");
        Term c = Term.var("c");
        Definition.Abstract r = new Definition.Abstract(symbol, List.of("a", "b"), true, display);
        Formula refl = Formula.pred(symbol, a, a);
        Formula second = symmetric
                ? Formula.implies(Formula.pred(symbol, a, b), Formula.pred(symbol, b, a))
                : Formula.implies(Formula.pred(symbol, a, b), Formula.implies(Formula.pred(symbol, b, a), Formula.eq(a, b)));
        Formula trans = Formula.implies(Formula.pred(symbol, a, b),
                Formula.implies(Formula.pred(symbol, b, c), Formula.pred(symbol, a, c)));
        return new Structure(name, List.of(r), List.of(refl, second, trans));
    }

    /** The i-th axiom of a structure, as a theorem resting on that structure. */
    static Theorem axiom(Structure s, int i) {
        return new Theorem(s.axioms().get(i), List.of(), Set.of("structure:" + s.name()), 1);
    }

    /**
     * Interpretation: maps each abstract symbol of {@code s} to a concrete definition of the same
     * kind and arity, checks that {@code laws} prove the mapped axioms, and maps the theorem.
     */
    static Theorem interpret(Signature signature, Structure s, Theorem thm, Map<String, String> mapping, List<Theorem> laws) {
        if (!thm.isClosed()) {
            throw new ProofException("Only a theorem with no open assumptions can be applied to a structure");
        }
        Set<String> symbols = new HashSet<>();
        for (Definition.Abstract sym : s.symbols()) {
            symbols.add(sym.name());
            String target = mapping.get(sym.name());
            Definition def = target == null ? null : signature.get(target);
            if (def == null) {
                throw new ProofException("Say what " + sym.name() + " stands for");
            }
            if (def instanceof Definition.Abstract || !signature.abstractsUsed(target).isEmpty()) {
                throw new ProofException(target + " must be a concrete definition");
            }
            boolean kindOk = sym.predicate() ? def instanceof Definition.Predicate : Signature.isFunction(def);
            if (!kindOk || def.params().size() != sym.params().size()) {
                throw new ProofException(target + " does not fit " + sym.name() + " (" + sym.params().size() + " inputs)");
            }
        }
        if (!symbols.equals(mapping.keySet())) {
            throw new ProofException("The mapping must cover exactly " + symbols);
        }
        // The theorem may rest on this structure only (not on another one's axioms).
        for (String dep : thm.dependencies()) {
            if (dep.startsWith("structure:") && !dep.equals("structure:" + s.name())) {
                throw new ProofException("The theorem also rests on " + dep.substring("structure:".length()));
            }
        }
        Set<String> abstractsInThm = new HashSet<>();
        for (String n : Signature.namesIn(thm.formula())) {
            abstractsInThm.addAll(signature.abstractsUsed(n));
        }
        if (!symbols.containsAll(abstractsInThm)) {
            abstractsInThm.removeAll(symbols);
            throw new ProofException("The theorem mentions other abstract symbols: " + abstractsInThm);
        }
        if (laws.size() != s.axioms().size()) {
            throw new ProofException(s.name() + " needs " + s.axioms().size() + " laws proven");
        }
        Instantiator inst = new Instantiator(signature, mapping);
        Set<String> deps = new HashSet<>(thm.dependencies());
        deps.remove("structure:" + s.name());
        long steps = thm.steps() + 1;
        for (int i = 0; i < laws.size(); i++) {
            Theorem law = laws.get(i);
            Formula expected = inst.map(s.axioms().get(i));
            if (!law.isClosed() || !Syntax.alphaEquals(law.formula(), expected)) {
                throw new ProofException("Law " + (i + 1) + " should prove " + expected + ", got " + law.formula());
            }
            deps.addAll(law.dependencies());
            steps += law.steps();
        }
        Formula result = inst.map(thm.formula());
        mapping.values().forEach(v -> deps.add("def:" + v));
        inst.created.forEach(n -> deps.add("def:" + n));
        return new Theorem(result, List.of(), deps, steps);
    }

    /**
     * Reads off which concrete definitions play the abstract symbols, by matching the proven laws
     * against the axioms (variable names may differ). Returns null if they do not match.
     */
    public static Map<String, String> inferMapping(Structure s, List<Formula> laws) {
        if (laws.size() != s.axioms().size()) {
            return null;
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        for (int i = 0; i < laws.size(); i++) {
            if (!match(s.axioms().get(i), laws.get(i), mapping, new java.util.HashMap<>())) {
                return null;
            }
        }
        return mapping;
    }

    private static boolean bind(Map<String, String> mapping, String abstractName, String concrete) {
        if (!abstractName.contains(".")) {
            return abstractName.equals(concrete);
        }
        String prev = mapping.putIfAbsent(abstractName, concrete);
        return prev == null || prev.equals(concrete);
    }

    private static boolean match(Formula p, Formula f, Map<String, String> m, Map<String, String> vars) {
        return switch (p) {
            case Formula.Eq e when f instanceof Formula.Eq g -> match(e.left(), g.left(), m, vars) && match(e.right(), g.right(), m, vars);
            case Formula.And a when f instanceof Formula.And g -> match(a.left(), g.left(), m, vars) && match(a.right(), g.right(), m, vars);
            case Formula.Implies i when f instanceof Formula.Implies g ->
                    match(i.premise(), g.premise(), m, vars) && match(i.conclusion(), g.conclusion(), m, vars);
            case Formula.Bottom b -> f instanceof Formula.Bottom;
            case Formula.Pred q when f instanceof Formula.Pred g -> q.args().size() == g.args().size()
                    && bind(m, q.name(), g.name()) && matchAll(q.args(), g.args(), m, vars);
            default -> false;
        };
    }

    private static boolean match(Term p, Term t, Map<String, String> m, Map<String, String> vars) {
        return switch (p) {
            case Term.Zero z -> t instanceof Term.Zero;
            case Term.Succ su -> t instanceof Term.Succ ts && match(su.arg(), ts.arg(), m, vars);
            case Term.Var v -> t instanceof Term.Var tv && (vars.putIfAbsent(v.name(), tv.name()) == null
                    ? !vars.entrySet().stream().anyMatch(e -> !e.getKey().equals(v.name()) && e.getValue().equals(tv.name()))
                    : vars.get(v.name()).equals(tv.name()));
            case App a -> t instanceof App ta && a.args().size() == ta.args().size()
                    && bind(m, a.function(), ta.function()) && matchAll(a.args(), ta.args(), m, vars);
        };
    }

    private static boolean matchAll(List<Term> ps, List<Term> ts, Map<String, String> m, Map<String, String> vars) {
        for (int i = 0; i < ps.size(); i++) {
            if (!match(ps.get(i), ts.get(i), m, vars)) {
                return false;
            }
        }
        return true;
    }

    /** Maps abstract symbols to concrete names and instantiates the templates built on them. */
    private static final class Instantiator {
        final Signature signature;
        final Map<String, String> mapping;
        final String suffix;
        final List<String> created = new ArrayList<>();

        Instantiator(Signature signature, Map<String, String> mapping) {
            this.signature = signature;
            this.mapping = mapping;
            StringBuilder sb = new StringBuilder("⟨");
            new TreeMap<>(mapping).forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append(k).append(":=").append(v));
            this.suffix = sb.append("⟩").toString();
        }

        String name(String n) {
            if (mapping.containsKey(n)) {
                return mapping.get(n);
            }
            if (signature.abstractsUsed(n).isEmpty()) {
                return n;
            }
            String instName = n + suffix;
            if (signature.get(instName) == null) {
                Definition def = signature.get(n);
                // Reserve the name first so a recursive definition's self-call maps to itself.
                Definition instance = switch (def) {
                    case Definition.Explicit e -> new Definition.Explicit(instName, e.params(), map(e.body()), e.symbol());
                    case Definition.PrimitiveRecursive r -> new Definition.PrimitiveRecursive(instName, r.params(),
                            map(r.base()), mapSelf(r.step(), n, instName), r.symbol());
                    case Definition.Predicate p -> new Definition.Predicate(instName, p.params(), map(p.body()), p.symbol());
                    case Definition.Abstract a -> throw new ProofException("Unmapped abstract symbol " + a.name());
                };
                signature.defineInternal(instance);
                created.add(instName);
            }
            return instName;
        }

        private Term mapSelf(Term t, String self, String instSelf) {
            return switch (t) {
                case App a when a.function().equals(self) ->
                        new App(instSelf, a.args().stream().map(x -> mapSelf(x, self, instSelf)).toList());
                case App a -> new App(name(a.function()), a.args().stream().map(x -> mapSelf(x, self, instSelf)).toList());
                case Term.Succ su -> Term.succ(mapSelf(su.arg(), self, instSelf));
                default -> t;
            };
        }

        Term map(Term t) {
            return switch (t) {
                case App a -> new App(name(a.function()), a.args().stream().map(this::map).toList());
                case Term.Succ su -> Term.succ(map(su.arg()));
                default -> t;
            };
        }

        Formula map(Formula f) {
            return switch (f) {
                case Formula.Eq e -> Formula.eq(map(e.left()), map(e.right()));
                case Formula.And a -> Formula.and(map(a.left()), map(a.right()));
                case Formula.Implies i -> Formula.implies(map(i.premise()), map(i.conclusion()));
                case Formula.Exists ex -> Formula.exists(ex.var(), map(ex.body()));
                case Formula.Bottom b -> b;
                case Formula.Pred p -> new Formula.Pred(name(p.name()), p.args().stream().map(this::map).toList());
            };
        }
    }
}
