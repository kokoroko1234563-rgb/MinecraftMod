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
 * transitivity and substitution), induction, ∧, →, ∃ and ⊥ (natural deduction without ex falso),
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
        };
        return statements.stream().map(f -> new Theorem(f, List.of(), dep, 1)).toList();
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
        signature.checkFormula(ex);
        String fresh = ex.var() + "#" + nextId++;
        Theorem hyp = assume(Syntax.substitute(ex.body(), ex.var(), new Var(fresh)));
        return new Opening(hyp, fresh);
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
        a.forEach(h -> byId.put(h.id(), h));
        b.forEach(h -> byId.put(h.id(), h));
        return new ArrayList<>(byId.values());
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }
}
