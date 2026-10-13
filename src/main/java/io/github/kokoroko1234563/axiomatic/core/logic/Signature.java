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
        }
        definitions.put(def.name(), def);
        return def;
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

    /** Names starting with '#' are reserved for comparing statements up to renaming. */
    private static void checkVarName(String name) {
        if (name.isEmpty() || name.startsWith("#")) {
            throw new ProofException("Invalid variable name: " + name);
        }
    }

    private void checkArity(App a) {
        Definition def = definitions.get(a.function());
        if (!(def instanceof Definition.Explicit) && !(def instanceof Definition.PrimitiveRecursive)) {
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
                if (!(definitions.get(p.name()) instanceof Definition.Predicate def)) {
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
