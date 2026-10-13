package io.github.kokoroko1234563.axiomatic.core.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Eq;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Exists;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula.Implies;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem.Hypothesis;
import io.github.kokoroko1234563.axiomatic.core.logic.Term.Var;

/**
 * The proof checker. Each public method is one inference rule, which in game is one reasoning
 * machine. A rule either returns a new {@link Theorem} or throws {@link ProofException}.
 *
 * <p>The logic: Peano axioms 3 and 4, equality (reflexivity plus rewriting, which gives symmetry,
 * transitivity and substitution), induction, ∧, →, ∃ and ⊥ (intuitionistic natural deduction),
 * definitions by explicit equation, primitive recursion, or statement. Axioms 1 and 2 are the
 * term syntax itself (0 and S).
 *
 * <p>Free variables in a theorem mean "for every value", except variables that appear in an open
 * hypothesis: those are locked, and may not be instantiated or inducted on.
 */
public final class Kernel {
    public static final String AXIOM_SUCC_NOT_ZERO = "axiom:succ_not_zero";
    public static final String AXIOM_SUCC_INJECTIVE = "axiom:succ_injective";

    private final Signature signature;
    private long nextId = 1;
    private Map<String, Structures.Structure> structures;

    public Kernel(Signature signature) {
        this.signature = signature;
    }

    public Signature signature() {
        return signature;
    }

    /** The next stamp/fresh-variable number, saved with the world so names stay unique. */
    public long nextId() {
        return nextId;
    }

    public void setNextId(long nextId) {
        this.nextId = Math.max(this.nextId, nextId);
    }

    /**
     * Recreates a theorem that was proven before and saved (in the world or on an item). Only
     * save-loading code may call this: anything else would bypass the checker.
     */
    public Theorem restoreSaved(Formula formula, List<Hypothesis> hypotheses, Set<String> dependencies, long steps) {
        return new Theorem(formula, hypotheses, dependencies, steps);
    }

    // ---- Axioms and definitions -----------------------------------------------------------

    /** Axiom 3: ¬(S(x) = 0). */
    public Theorem succNotZero() {
        return axiom(Formula.not(Formula.eq(Term.succ(Term.var("x")), Term.zero())), AXIOM_SUCC_NOT_ZERO);
    }

    /** Axiom 4: S(x) = S(y) → x = y. */
    public Theorem succInjective() {
        Term x = Term.var("x");
        Term y = Term.var("y");
        return axiom(Formula.implies(Formula.eq(Term.succ(x), Term.succ(y)), Formula.eq(x, y)),
                AXIOM_SUCC_INJECTIVE);
    }

    private Theorem axiom(Formula f, String name) {
        return new Theorem(f, List.of(), Set.of(name), 1);
    }

    /** Equality is reflexive: t = t. */
    public Theorem reflexivity(Term t) {
        signature.checkTerm(t);
        return new Theorem(Formula.eq(t, t), List.of(), Set.of("axiom:eq_refl"), 1);
    }

    /** Compiles a definition and returns its defining statements, which are proven by definition. */
    public List<Theorem> define(Definition def) {
        signature.define(def);
        return definingStatements(def.name());
    }

    /**
     * The defining statements of an accepted definition, for example "a + 0 = a" and
     * "a + S(b) = S(a + b)". For a property P(x̄) :⇔ φ, the two statements P(x̄) → φ and φ → P(x̄).
     */
    public List<Theorem> definingStatements(String name) {
        Definition def = signature.get(name);
        if (def == null) {
            throw new ProofException("Unknown definition: " + name);
        }
        Set<String> dep = Set.of("def:" + name);
        List<Term> params = def.params().stream().<Term>map(Var::new).toList();
        List<Formula> statements = switch (def) {
            case Definition.Explicit e -> List.of(Formula.eq(new Term.App(name, params), e.body()));
            case Definition.PrimitiveRecursive r -> {
                List<Term> fixed = params.subList(0, params.size() - 1);
                Term y = params.get(params.size() - 1);
                List<Term> atZero = new ArrayList<>(fixed);
                atZero.add(Term.zero());
                List<Term> atSucc = new ArrayList<>(fixed);
                atSucc.add(Term.succ(y));
                yield List.of(Formula.eq(new Term.App(name, atZero), r.base()),
                        Formula.eq(new Term.App(name, atSucc), r.step()));
            }
            case Definition.Predicate p -> {
                Formula atom = new Formula.Pred(name, params);
                yield List.of(Formula.implies(atom, p.body()), Formula.implies(p.body(), atom));
            }
            // Abstract symbols have no defining statements; only their structure's axioms.
            case Definition.Abstract a -> List.of();
            case Definition.GeneralRecursive g -> List.of(Formula.eq(new Term.App(name, params), g.body()));
        };
        return statements.stream().map(f -> new Theorem(f, List.of(), dep, 1)).toList();
    }

    // ---- General recursion ----------------------------------------------------------------

    /**
     * What must be proven before {@code def} is accepted: for every recursive call f(ā) in the
     * body, under the selector conditions that lead to it, μ(ā) < μ(x̄), where a < b is
     * ∃k. a + S(k) = b with the world's addition. Several calls give a conjunction, in reading
     * order. Proving it shows f is total, so its defining equation is safe.
     */
    public Formula terminationObligation(Definition.GeneralRecursive def) {
        if (signature.get(def.name()) != null) {
            throw new ProofException(def.name() + " is already defined");
        }
        signature.checkGeneral(def);
        String add = signature.canonicalAddition();
        if (add == null) {
            throw new ProofException("Define addition first: termination is measured with it");
        }
        List<Formula> obligations = new ArrayList<>();
        collectObligations(def, def.body(), List.of(), add, obligations);
        if (obligations.isEmpty()) {
            throw new ProofException("The body never calls " + def.name() + ": define it explicitly instead");
        }
        Formula all = obligations.get(obligations.size() - 1);
        for (int i = obligations.size() - 2; i >= 0; i--) {
            all = Formula.and(obligations.get(i), all);
        }
        return all;
    }

    private void collectObligations(Definition.GeneralRecursive def, Term t, List<Formula> guards, String add,
            List<Formula> out) {
        switch (t) {
            case Term.App a when a.function().equals(def.name()) -> {
                Map<String, Term> args = new LinkedHashMap<>();
                for (int i = 0; i < def.params().size(); i++) {
                    args.put(def.params().get(i), a.args().get(i));
                }
                Term smaller = substituteAll(def.measure(), args);
                String k = Syntax.freshName("k", new HashSet<>(def.params()));
                Formula shrinks = Formula.exists(k, Formula.eq(
                        Term.app(add, smaller, Term.succ(Term.var(k))), def.measure()));
                Formula ob = shrinks;
                for (int i = guards.size() - 1; i >= 0; i--) {
                    ob = Formula.implies(guards.get(i), ob);
                }
                out.add(ob);
            }
            case Term.App a -> {
                int[] sel = signature.selector(a.function());
                if (sel == null) {
                    a.args().forEach(x -> collectObligations(def, x, guards, add, out));
                    return;
                }
                Term c = a.args().get(a.args().size() - 1);
                Formula isZero = Formula.eq(c, Term.zero());
                for (int i = 0; i < a.args().size(); i++) {
                    List<Formula> g = new ArrayList<>(guards);
                    if (i == sel[1]) {
                        g.add(Formula.not(isZero));
                    } else if (i == sel[0]) {
                        g.add(isZero);
                    }
                    collectObligations(def, a.args().get(i), g, add, out);
                }
            }
            case Term.Succ s -> collectObligations(def, s.arg(), guards, add, out);
            default -> {}
        }
    }

    /** Simultaneous substitution of variables in a term. */
    private static Term substituteAll(Term t, Map<String, Term> map) {
        return switch (t) {
            case Var v -> map.getOrDefault(v.name(), v);
            case Term.Succ s -> Term.succ(substituteAll(s.arg(), map));
            case Term.App a -> new Term.App(a.function(), a.args().stream().map(x -> substituteAll(x, map)).toList());
            default -> t;
        };
    }

    /**
     * Defines a function by general recursion, given a proof of its termination obligation. The
     * proof must be unconditional and must not rest on conjectures: a definition is forever.
     */
    public List<Theorem> defineRecursive(Definition.GeneralRecursive def, Theorem termination) {
        Formula obligation = terminationObligation(def);
        if (!termination.isClosed() || termination.isConjectural()) {
            throw new ProofException("The termination proof must not rest on assumptions or conjectures");
        }
        if (!Syntax.alphaEquals(termination.formula(), obligation)) {
            throw new ProofException("Termination needs a proof of " + obligation + ", got " + termination.formula());
        }
        signature.defineTerminating(def);
        return definingStatements(def.name());
    }

    // ---- Recognition ----------------------------------------------------------------------

    /**
     * Equivalence Bench: if f and g are the same design (identical defining equations up to
     * renaming, see {@link Designs}), proves f(x̄) = g(x̄); for two properties, P(x̄) → Q(x̄) and
     * Q(x̄) → P(x̄). Same equations, same function: every definition here is total.
     */
    public List<Theorem> sameDesign(String f, String g) {
        Definition df = signature.get(f);
        Definition dg = signature.get(g);
        if (df == null || dg == null) {
            throw new ProofException("Unknown definition: " + (df == null ? f : g));
        }
        if (!new Designs(signature).same(f, g)) {
            throw new ProofException(f + " and " + g + " are not the same design");
        }
        List<Term> vars = df.params().stream().<Term>map(Var::new).toList();
        Set<String> deps = Set.of("def:" + f, "def:" + g);
        if (df instanceof Definition.Predicate) {
            Formula p = new Formula.Pred(f, vars);
            Formula q = new Formula.Pred(g, vars);
            return List.of(new Theorem(Formula.implies(p, q), List.of(), deps, 1),
                    new Theorem(Formula.implies(q, p), List.of(), deps, 1));
        }
        return List.of(new Theorem(Formula.eq(new Term.App(f, vars), new Term.App(g, vars)), List.of(), deps, 1));
    }

    // ---- Implementations ------------------------------------------------------------------

    /**
     * Implementation Bench: a proof of ∀x̄. fast(x̄) = slow(x̄) makes every computation of slow run
     * fast instead. Theorems about slow stay true, since the two are proven equal; only the cost
     * changes. Returns {slow, fast}.
     */
    public String[] implement(Theorem proof) {
        if (!proof.isClosed() || proof.isConjectural()) {
            throw new ProofException("The proof must not rest on assumptions or conjectures");
        }
        if (!(proof.formula() instanceof Eq eq) || !(eq.left() instanceof Term.App fast)
                || !(eq.right() instanceof Term.App slow)) {
            throw new ProofException("Needs a proof of fast(x, …) = slow(x, …)");
        }
        List<Term> vars = fast.args();
        if (!vars.equals(slow.args()) || !vars.stream().allMatch(t -> t instanceof Var)
                || new HashSet<>(vars).size() != vars.size()) {
            throw new ProofException("Both sides must take the same distinct variables, in the same order");
        }
        if (fast.function().equals(slow.function())) {
            throw new ProofException("A function cannot implement itself");
        }
        if (!Signature.isFunction(signature.get(fast.function())) || signature.get(fast.function()) instanceof Definition.Abstract
                || !Signature.isFunction(signature.get(slow.function()))) {
            throw new ProofException("Both sides must be defined functions");
        }
        if (signature.dependsOn(fast.function(), slow.function())) {
            throw new ProofException(fast.function() + " itself computes " + slow.function() + ": that would loop");
        }
        signature.setImplementation(slow.function(), fast.function());
        return new String[] {slow.function(), fast.function()};
    }

    /**
     * Turns an unproven statement into a conjecture that may be used like a theorem. Everything
     * derived from it is marked conjectural. Which statements may be conjectured (only ones the
     * game can check on numbers) is the Conjecture Bench's rule, not the kernel's.
     */
    public Theorem conjecture(String name, Formula f) {
        if (!Syntax.isValidName(name)) {
            throw new ProofException("Invalid conjecture name: " + name);
        }
        signature.checkFormula(f);
        return new Theorem(f, List.of(), Set.of("conjecture:" + name), 1);
    }

    // ---- Structures -----------------------------------------------------------------------

    /** A built-in structure (Monoid, Equivalence, Order); declares its abstract symbols on first use. */
    public Structures.Structure structure(String name) {
        if (structures == null) {
            structures = Structures.standard(signature);
        }
        Structures.Structure s = structures.get(name);
        if (s == null) {
            throw new ProofException("Unknown structure: " + name);
        }
        return s;
    }

    /** Axiom {@code i} of a structure, for proving generic theorems ("for any monoid …"). */
    public Theorem structureAxiom(String structure, int i) {
        Structures.Structure s = structure(structure);
        if (i < 0 || i >= s.axioms().size()) {
            throw new ProofException(structure + " has " + s.axioms().size() + " axioms");
        }
        return Structures.axiom(s, i);
    }

    /**
     * Applies a generic theorem to concrete definitions: {@code mapping} sends each abstract
     * symbol of the structure to a concrete definition, and {@code laws} prove the structure's
     * axioms for them, in order. Templates on the abstract symbols are instantiated as needed.
     */
    public Theorem interpret(Theorem thm, String structure, Map<String, String> mapping, List<Theorem> laws) {
        return Structures.interpret(signature, structure(structure), thm, mapping, laws);
    }

    // ---- Equality -------------------------------------------------------------------------

    /**
     * The Rewriter: in {@code target}, replaces the left side of {@code equation} with its right
     * side (or the reverse). {@code occurrence} picks one occurrence in reading order, starting at
     * 0; a negative value replaces all of them.
     */
    public Theorem rewrite(Theorem target, Theorem equation, boolean rightToLeft, int occurrence) {
        if (!(equation.formula() instanceof Eq eq)) {
            throw new ProofException("The Rewriter needs a proven equation, got: " + equation.formula());
        }
        Term from = rightToLeft ? eq.right() : eq.left();
        Term to = rightToLeft ? eq.left() : eq.right();
        Formula result = Syntax.rewrite(target.formula(), from, to, occurrence);
        if (result == null) {
            throw new ProofException("Nothing to rewrite: " + from + " does not occur in " + target.formula());
        }
        return derive(result, target, equation);
    }

    // ---- Instantiation --------------------------------------------------------------------

    /** The Instantiator: puts {@code term} in place of {@code var} everywhere it occurs free. */
    public Theorem instantiate(Theorem thm, String var, Term term) {
        return instantiate(thm, Map.of(var, term));
    }

    /** Instantiates several variables at once, so a := b, b := a swaps them. */
    public Theorem instantiate(Theorem thm, Map<String, Term> assignment) {
        Set<String> locked = lockedVars(thm.hypotheses());
        Set<String> avoid = new HashSet<>(Syntax.freeVars(thm.formula()));
        for (var entry : assignment.entrySet()) {
            if (locked.contains(entry.getKey())) {
                throw new ProofException("Variable " + entry.getKey()
                        + " is locked by an open assumption and cannot be instantiated");
            }
            signature.checkTerm(entry.getValue());
            avoid.addAll(Syntax.freeVars(entry.getValue()));
        }
        // Rename each variable to a placeholder first, then fill the placeholders: simultaneous substitution.
        Formula f = thm.formula();
        Map<String, Term> placeholders = new LinkedHashMap<>();
        for (var entry : new TreeMap<>(assignment).entrySet()) {
            String placeholder = Syntax.freshName(entry.getKey() + "#", avoid);
            avoid.add(placeholder);
            f = Syntax.substitute(f, entry.getKey(), new Var(placeholder));
            placeholders.put(placeholder, entry.getValue());
        }
        for (var entry : placeholders.entrySet()) {
            f = Syntax.substitute(f, entry.getKey(), entry.getValue());
        }
        return derive(f, thm);
    }

    // ---- Connectives ----------------------------------------------------------------------

    /** Conjunction Press: P and Q give P ∧ Q. */
    public Theorem andIntro(Theorem left, Theorem right) {
        return derive(Formula.and(left.formula(), right.formula()), left, right);
    }

    /** Conjunction Press, splitting: P ∧ Q gives P. */
    public Theorem andLeft(Theorem conj) {
        if (!(conj.formula() instanceof Formula.And and)) {
            throw new ProofException("Expected a statement of the form P ∧ Q, got: " + conj.formula());
        }
        return derive(and.left(), conj);
    }

    /** Conjunction Press, splitting: P ∧ Q gives Q. */
    public Theorem andRight(Theorem conj) {
        if (!(conj.formula() instanceof Formula.And and)) {
            throw new ProofException("Expected a statement of the form P ∧ Q, got: " + conj.formula());
        }
        return derive(and.right(), conj);
    }

    /** Modus Ponens Press: P → Q and P give Q. */
    public Theorem modusPonens(Theorem implication, Theorem premise) {
        if (!(implication.formula() instanceof Implies imp)) {
            throw new ProofException("Expected a statement of the form P → Q, got: " + implication.formula());
        }
        if (!Syntax.alphaEquals(imp.premise(), premise.formula())) {
            throw new ProofException("The premise " + premise.formula() + " does not match " + imp.premise());
        }
        return derive(imp.conclusion(), implication, premise);
    }

    /** Ex falso: from a proof of ⊥, any statement follows. */
    public Theorem exFalso(Theorem bottom, Formula anything) {
        if (!(bottom.formula() instanceof Formula.Bottom)) {
            throw new ProofException("Ex falso needs a proof of ⊥, got: " + bottom.formula());
        }
        signature.checkFormula(anything);
        return derive(anything, bottom);
    }

    // ---- Assumption Chamber ---------------------------------------------------------------

    /** Assumption Chamber: assumes {@code h}. Its free variables are locked until it is discharged. */
    public Theorem assume(Formula h) {
        signature.checkFormula(h);
        Hypothesis hyp = new Hypothesis(nextId++, h);
        return new Theorem(h, List.of(hyp), Set.of(), 1);
    }

    /**
     * Assumption Chamber output slot: a conclusion Q derived under hypothesis H gives H → Q.
     * Q need not actually use H; H → Q is still true.
     */
    public Theorem discharge(Theorem conclusion, Hypothesis hyp) {
        List<Hypothesis> remaining = new ArrayList<>(conclusion.hypotheses());
        remaining.remove(hyp);
        return new Theorem(Formula.implies(hyp.formula(), conclusion.formula()), remaining,
                conclusion.dependencies(), conclusion.steps() + 1);
    }

    /** The result of opening ∃k. φ(k): a fresh locked variable and the hypothesis φ(fresh). */
    public record Opening(Theorem hypothesis, String variable) {
        public Hypothesis hyp() {
            return hypothesis.hypotheses().get(0);
        }
    }

    /** Assumption Chamber: opens ∃k. φ(k) into a fresh variable and the hypothesis φ of it. */
    public Opening openExists(Exists ex) {
        return openExists(ex, ex.var() + "#" + nextId++);
    }

    /**
     * Opens ∃k. φ(k) as the variable {@code name} chosen by the player, so circuits can refer to
     * it. It must not occur in the ∃ statement; that it is new everywhere else that matters (the
     * conclusion, the other assumptions) is checked when the ∃ is closed.
     */
    public Opening openExists(Exists ex, String name) {
        signature.checkFormula(ex);
        if (name.isEmpty() || name.startsWith("#") || Syntax.freeVars(ex).contains(name)) {
            throw new ProofException("Cannot open as " + name + ": " + ex + " already mentions it");
        }
        Theorem hyp = assume(Syntax.substitute(ex.body(), ex.var(), new Var(name)));
        return new Opening(hyp, name);
    }

    /**
     * Closes an opened ∃: from a proof of ∃k. φ(k), and a conclusion Q derived from the opened
     * hypothesis φ(v), concludes Q, provided Q and the other hypotheses do not mention v.
     */
    public Theorem closeExists(Theorem existence, Opening opening, Theorem conclusion) {
        if (!(existence.formula() instanceof Exists ex)) {
            throw new ProofException("Expected a statement of the form ∃k. φ, got: " + existence.formula());
        }
        Hypothesis hyp = opening.hyp();
        String v = opening.variable();
        if (Syntax.freeVars(ex).contains(v)) {
            throw new ProofException("The opened variable " + v + " must be new, but " + ex + " mentions it");
        }
        if (!Syntax.alphaEquals(hyp.formula(), Syntax.substitute(ex.body(), ex.var(), new Var(v)))) {
            throw new ProofException("The opened assumption does not come from " + ex);
        }
        if (Syntax.freeVars(conclusion.formula()).contains(v)) {
            throw new ProofException("The conclusion still mentions " + v + ", which only exists inside the chamber");
        }
        List<Hypothesis> remaining = new ArrayList<>(conclusion.hypotheses());
        remaining.remove(hyp);
        if (lockedVars(remaining).contains(v) || lockedVars(existence.hypotheses()).contains(v)) {
            throw new ProofException("Another assumption mentions " + v);
        }
        return new Theorem(conclusion.formula(), merge(remaining, existence.hypotheses()),
                union(existence.dependencies(), conclusion.dependencies()),
                existence.steps() + conclusion.steps() + 1);
    }

    // ---- Existence and induction ----------------------------------------------------------

    /** Witness Bench: from φ(t), proves ∃k. φ(k). */
    public Theorem existsIntro(Exists target, Term witness, Theorem instance) {
        signature.checkFormula(target);
        signature.checkTerm(witness);
        Formula expected = Syntax.substitute(target.body(), target.var(), witness);
        if (!Syntax.alphaEquals(expected, instance.formula())) {
            throw new ProofException("With " + target.var() + " := " + witness + " the statement needs "
                    + expected + ", but the proof shows " + instance.formula());
        }
        return derive(target, instance);
    }

    /**
     * Induction Engine (axiom 5): from the base case P(0) and the step P(n) → P(S(n)), proves P(n)
     * for every n.
     */
    public Theorem induction(Theorem base, Theorem step, String var) {
        if (!(step.formula() instanceof Implies imp)) {
            throw new ProofException("The step must have the form P(n) → P(S(n)), got: " + step.formula());
        }
        Formula p = imp.premise();
        Var n = new Var(var);
        if (!Syntax.alphaEquals(base.formula(), Syntax.substitute(p, var, Term.zero()))) {
            throw new ProofException("The base case should be " + Syntax.substitute(p, var, Term.zero())
                    + ", got " + base.formula());
        }
        Formula expected = Syntax.substitute(p, var, Term.succ(n));
        if (!Syntax.alphaEquals(imp.conclusion(), expected)) {
            throw new ProofException("The step should conclude " + expected + ", got " + imp.conclusion());
        }
        if (lockedVars(base.hypotheses()).contains(var) || lockedVars(step.hypotheses()).contains(var)) {
            throw new ProofException("Variable " + var + " is locked by an open assumption");
        }
        Theorem result = derive(p, base, step);
        return new Theorem(result.formula(), result.hypotheses(), union(result.dependencies(), Set.of("axiom:induction")),
                result.steps());
    }

    // ---- Helpers --------------------------------------------------------------------------

    private static Set<String> lockedVars(List<Hypothesis> hyps) {
        Set<String> out = new HashSet<>();
        hyps.forEach(h -> out.addAll(Syntax.freeVars(h.formula())));
        return out;
    }

    private static Theorem derive(Formula f, Theorem... premises) {
        List<Hypothesis> hyps = List.of();
        Set<String> deps = new HashSet<>();
        long steps = 1;
        for (Theorem t : premises) {
            hyps = merge(hyps, t.hypotheses());
            deps.addAll(t.dependencies());
            steps += t.steps();
        }
        return new Theorem(f, hyps, deps, steps);
    }

    private static List<Hypothesis> merge(List<Hypothesis> a, List<Hypothesis> b) {
        TreeMap<Long, Hypothesis> byId = new TreeMap<>();
        for (Hypothesis h : a) {
            putHypothesis(byId, h);
        }
        for (Hypothesis h : b) {
            putHypothesis(byId, h);
        }
        return new ArrayList<>(byId.values());
    }

    /** Two different assumptions under one stamp (from another world, say) must not merge. */
    private static void putHypothesis(TreeMap<Long, Hypothesis> byId, Hypothesis h) {
        Hypothesis prev = byId.putIfAbsent(h.id(), h);
        if (prev != null && !Syntax.alphaEquals(prev.formula(), h.formula())) {
            throw new ProofException("Two different assumptions carry the same stamp #" + h.id());
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }
}
