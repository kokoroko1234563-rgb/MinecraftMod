package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Every accepted definition, in the order it was made. A definition may only use definitions made
 * before it (plus itself, in the restricted recursive form), so the library has no cycles.
 */
public final class Signature {
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    /** slow function → a proven-equal faster function that computes it (Implementation Bench). */
    private final Map<String, String> implementations = new LinkedHashMap<>();

    /**
     * An independent copy, for computing on another thread while the world keeps changing.
     * Definitions themselves are immutable, so they are shared.
     */
    public Signature copy() {
        Signature out = new Signature();
        out.definitions.putAll(definitions);
        out.implementations.putAll(implementations);
        return out;
    }

    /** The function that actually computes {@code name}: its proven-equal implementation, if any. */
    public String implementation(String name) {
        String impl = implementations.get(name);
        return impl == null ? name : implementation(impl);
    }

    public Map<String, String> implementations() {
        return java.util.Collections.unmodifiableMap(implementations);
    }

    /** Records an implementation; only {@link Kernel#implement} and save loading may call this. */
    void setImplementation(String slow, String fast) {
        implementations.put(slow, fast);
    }

    /** Puts back a saved implementation (trusted, like everything loaded from a save). */
    public void restoreImplementation(String slow, String fast) {
        implementations.put(slow, fast);
    }

    /** Whether computing {@code name} may call {@code other}, directly or through other definitions or implementations. */
    public boolean dependsOn(String name, String other) {
        return dependsOn(name, other, new HashSet<>());
    }

    private boolean dependsOn(String name, String other, Set<String> seen) {
        if (name.equals(other)) {
            return true;
        }
        if (!seen.add(name)) {
            return false;
        }
        String impl = implementations.get(name);
        if (impl != null && dependsOn(impl, other, seen)) {
            return true;
        }
        Definition def = definitions.get(name);
        Set<String> used = switch (def) {
            case null -> Set.of();
            case Definition.Explicit e -> namesIn(e.body());
            case Definition.PrimitiveRecursive r -> {
                Set<String> s = new HashSet<>(namesIn(r.base()));
                s.addAll(namesIn(r.step()));
                yield s;
            }
            case Definition.GeneralRecursive g -> namesIn(g.body());
            case Definition.Predicate p -> namesIn(p.body());
            case Definition.Abstract a -> Set.of();
        };
        for (String u : used) {
            if (!u.equals(name) && dependsOn(u, other, seen)) {
                return true;
            }
        }
        return false;
    }

    public Definition get(String name) {
        return definitions.get(name);
    }

    public Collection<Definition> all() {
        return definitions.values();
    }

    /** Checks a definition and adds it. Throws {@link ProofException} with the reason if it is refused. */
    public Definition define(Definition def) {
        if (!Syntax.isValidName(def.name())) {
            throw new ProofException("Invalid name: " + def.name());
        }
        if (def instanceof Definition.GeneralRecursive) {
            throw new ProofException("General recursion needs a termination proof: use Kernel.defineRecursive");
        }
        return defineChecked(def);
    }

    /** Adds a general recursive definition whose termination the kernel has checked. */
    Definition defineTerminating(Definition.GeneralRecursive def) {
        if (!Syntax.isValidName(def.name())) {
            throw new ProofException("Invalid name: " + def.name());
        }
        return defineChecked(def);
    }

    /**
     * Like {@link #define}, but for names the kernel makes (structure symbols such as
     * "Monoid.op", instantiated templates), which players cannot type.
     */
    public Definition defineInternal(Definition def) {
        if (def.name().isBlank() || def.name().startsWith("#")) {
            throw new ProofException("Invalid internal name: " + def.name());
        }
        return defineChecked(def);
    }

    private Definition defineChecked(Definition def) {
        if (definitions.containsKey(def.name())) {
            throw new ProofException(def.name() + " is already defined");
        }
        Set<String> params = new HashSet<>();
        for (String p : def.params()) {
            if (!Syntax.isValidName(p) || !params.add(p)) {
                throw new ProofException("Parameters must be distinct variable names: " + def.params());
            }
        }
        switch (def) {
            case Definition.Explicit e -> checkTerm(e.body(), params, "the body");
            case Definition.PrimitiveRecursive r -> checkRecursive(r);
            case Definition.Predicate p -> {
                checkFormula(p.body());
                if (!params.containsAll(Syntax.freeVars(p.body()))) {
                    throw new ProofException("The body may only mention the parameters " + p.params());
                }
            }
            case Definition.Abstract a -> {}
            case Definition.GeneralRecursive g -> checkGeneral(g);
        }
        definitions.put(def.name(), def);
        return def;
    }

    /**
     * A general recursive body may call itself with any arguments, but never inside the arguments
     * of such a call or of the measure (their termination would depend on the function itself).
     */
    void checkGeneral(Definition.GeneralRecursive g) {
        Set<String> params = new HashSet<>(g.params());
        checkGeneralTerm(g, g.body(), params, true);
        checkTerm(g.measure(), params, "the measure");
    }

    private void checkGeneralTerm(Definition.GeneralRecursive g, Term t, Set<String> params, boolean callsAllowed) {
        switch (t) {
            case Zero z -> {}
            case Succ s -> checkGeneralTerm(g, s.arg(), params, callsAllowed);
            case Var v -> {
                if (!params.contains(v.name())) {
                    throw new ProofException("The body may only mention " + g.params() + ", not " + v.name());
                }
            }
            case App a -> {
                if (a.function().equals(g.name())) {
                    if (!callsAllowed) {
                        throw new ProofException("A call of " + g.name() + " may not contain another call of it");
                    }
                    if (a.args().size() != g.params().size()) {
                        throw new ProofException(g.name() + " takes " + g.params().size() + " inputs");
                    }
                    a.args().forEach(arg -> checkGeneralTerm(g, arg, params, false));
                } else {
                    checkArity(a);
                    a.args().forEach(arg -> checkGeneralTerm(g, arg, params, callsAllowed));
                }
            }
        }
    }

    /**
     * If {@code name} is a selector, a definition by recursion on its last input c whose cases just
     * pick an input: sel(…, 0) = e and sel(…, S(y)) = t. Returns {else index, then index}, or null.
     * Selectors are evaluated lazily, which is what lets general recursion stop.
     */
    public int[] selector(String name) {
        if (!(definitions.get(name) instanceof Definition.PrimitiveRecursive r)) {
            return null;
        }
        List<String> fixed = r.fixedParams();
        if (r.base() instanceof Var e && r.step() instanceof Var t && fixed.contains(e.name()) && fixed.contains(t.name())) {
            return new int[] {fixed.indexOf(e.name()), fixed.indexOf(t.name())};
        }
        return null;
    }

    /**
     * A definition the kernel can trust to be addition: a + 0 = a and a + S(b) = S(a + b), whatever
     * it is called. Termination obligations are stated with it. Returns its name, or null.
     */
    /**
     * Whether the world has made the unary-to-binary upgrade: addition, the root of arithmetic,
     * has a proven implementation. Numerals are then handled by their digits (the Duplicator's
     * copy cost drops from a numeral's value to its bit count).
     */
    public boolean binaryNumerals() {
        String add = canonicalAddition();
        return add != null && !implementation(add).equals(add);
    }

    public String canonicalAddition() {
        for (Definition d : definitions.values()) {
            if (d instanceof Definition.PrimitiveRecursive r && r.params().size() == 2
                    && r.base().equals(new Var(r.params().get(0)))
                    && r.step().equals(new Succ(new App(r.name(), List.of(new Var(r.params().get(0)), new Var(r.params().get(1))))))) {
                return r.name();
            }
        }
        return null;
    }

    /**
     * The abstract symbols a definition rests on, directly or through other definitions. A
     * definition with any is a template: interpreting its structure instantiates it.
     */
    public Set<String> abstractsUsed(String name) {
        Set<String> out = new java.util.LinkedHashSet<>();
        collectAbstracts(name, out, new HashSet<>());
        return out;
    }

    private void collectAbstracts(String name, Set<String> out, Set<String> seen) {
        if (!seen.add(name)) {
            return;
        }
        Definition def = definitions.get(name);
        switch (def) {
            case null -> {}
            case Definition.Abstract a -> out.add(a.name());
            case Definition.Explicit e -> namesIn(e.body()).forEach(n -> collectAbstracts(n, out, seen));
            case Definition.PrimitiveRecursive r -> {
                namesIn(r.base()).forEach(n -> collectAbstracts(n, out, seen));
                namesIn(r.step()).forEach(n -> collectAbstracts(n, out, seen));
            }
            case Definition.Predicate p -> namesIn(p.body()).forEach(n -> collectAbstracts(n, out, seen));
            case Definition.GeneralRecursive g -> {
                namesIn(g.body()).forEach(n -> collectAbstracts(n, out, seen));
                namesIn(g.measure()).forEach(n -> collectAbstracts(n, out, seen));
            }
        }
    }

    /** Names of the functions and properties a term mentions. */
    public static Set<String> namesIn(Term t) {
        Set<String> out = new java.util.LinkedHashSet<>();
        addNames(t, out);
        return out;
    }

    /** Names of the functions and properties a statement mentions. */
    public static Set<String> namesIn(Formula f) {
        Set<String> out = new java.util.LinkedHashSet<>();
        addNames(f, out);
        return out;
    }

    private static void addNames(Term t, Set<String> out) {
        switch (t) {
            case Zero z -> {}
            case Var v -> {}
            case Succ s -> addNames(s.arg(), out);
            case App a -> {
                out.add(a.function());
                a.args().forEach(x -> addNames(x, out));
            }
        }
    }

    private static void addNames(Formula f, Set<String> out) {
        switch (f) {
            case Eq e -> {
                addNames(e.left(), out);
                addNames(e.right(), out);
            }
            case And a -> {
                addNames(a.left(), out);
                addNames(a.right(), out);
            }
            case Implies i -> {
                addNames(i.premise(), out);
                addNames(i.conclusion(), out);
            }
            case Exists ex -> addNames(ex.body(), out);
            case Bottom b -> {}
            case Pred p -> {
                out.add(p.name());
                p.args().forEach(x -> addNames(x, out));
            }
        }
    }

    private void checkRecursive(Definition.PrimitiveRecursive r) {
        if (r.params().isEmpty()) {
            throw new ProofException("A recursive definition needs a parameter to recurse on");
        }
        checkTerm(r.base(), new HashSet<>(r.fixedParams()), "the base case");
        Set<String> stepVars = new HashSet<>(r.params());
        checkStep(r, r.step(), stepVars);
    }

    /** The step may call the function being defined only as name(x̄, y), the call one level down. */
    private void checkStep(Definition.PrimitiveRecursive r, Term t, Set<String> allowed) {
        switch (t) {
            case Zero z -> {}
            case Succ s -> checkStep(r, s.arg(), allowed);
            case Var v -> {
                if (!allowed.contains(v.name())) {
                    throw new ProofException("The step case may only mention " + r.params() + ", not " + v.name());
                }
            }
            case App a -> {
                if (a.function().equals(r.name())) {
                    List<Term> expected = r.params().stream().<Term>map(Var::new).toList();
                    if (!a.args().equals(expected)) {
                        throw new ProofException("The step case may only call " + r.name() + " as "
                                + new App(r.name(), expected) + ", the call one step down");
                    }
                } else {
                    checkArity(a);
                    a.args().forEach(arg -> checkStep(r, arg, allowed));
                }
            }
        }
    }

    /** Checks that every function used exists with the right number of inputs and only allowed variables appear. */
    public void checkTerm(Term t, Set<String> allowedVars, String where) {
        checkTerm(t);
        for (String v : Syntax.freeVars(t)) {
            if (!allowedVars.contains(v)) {
                throw new ProofException("Variable " + v + " is not allowed in " + where);
            }
        }
    }

    public void checkTerm(Term t) {
        switch (t) {
            case Zero z -> {}
            case Succ s -> checkTerm(s.arg());
            case Var v -> checkVarName(v.name());
            case App a -> {
                checkArity(a);
                a.args().forEach(this::checkTerm);
            }
        }
    }

    /** Whether the definition is a function: defined, or an abstract structure operation. */
    public static boolean isFunction(Definition def) {
        return def instanceof Definition.Explicit || def instanceof Definition.PrimitiveRecursive
                || def instanceof Definition.GeneralRecursive
                || (def instanceof Definition.Abstract a && !a.predicate());
    }

    /** Whether the definition is a property or relation: defined, or an abstract structure relation. */
    public static boolean isPredicate(Definition def) {
        return def instanceof Definition.Predicate || (def instanceof Definition.Abstract a && a.predicate());
    }

    /** Names starting with '#' are reserved for comparing statements up to renaming. */
    private static void checkVarName(String name) {
        if (name.isEmpty() || name.startsWith("#")) {
            throw new ProofException("Invalid variable name: " + name);
        }
    }

    private void checkArity(App a) {
        Definition def = definitions.get(a.function());
        if (!isFunction(def)) {
            throw new ProofException("Unknown function: " + a.function());
        }
        if (def.params().size() != a.args().size()) {
            throw new ProofException(a.function() + " takes " + def.params().size() + " inputs, got "
                    + a.args().size());
        }
    }

    /** Checks that a statement only uses existing definitions, correctly. */
    public void checkFormula(Formula f) {
        switch (f) {
            case Eq e -> {
                checkTerm(e.left());
                checkTerm(e.right());
            }
            case And a -> {
                checkFormula(a.left());
                checkFormula(a.right());
            }
            case Implies i -> {
                checkFormula(i.premise());
                checkFormula(i.conclusion());
            }
            case Exists ex -> {
                checkVarName(ex.var());
                checkFormula(ex.body());
            }
            case Bottom b -> {}
            case Pred p -> {
                Definition def = definitions.get(p.name());
                if (!isPredicate(def)) {
                    throw new ProofException("Unknown property or relation: " + p.name());
                }
                if (def.params().size() != p.args().size()) {
                    throw new ProofException(p.name() + " takes " + def.params().size() + " inputs, got "
                            + p.args().size());
                }
                p.args().forEach(this::checkTerm);
            }
        }
    }
}
