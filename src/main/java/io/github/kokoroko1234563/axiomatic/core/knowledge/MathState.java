package io.github.kokoroko1234563.axiomatic.core.knowledge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kokoroko1234563.axiomatic.core.graph.GraphCodec;
import io.github.kokoroko1234563.axiomatic.core.graph.PointedGraph;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;

/**
 * All the mathematics of one world: definitions (in the kernel's signature), deciders and the
 * library, with a binary save format. Statements are stored as pointed graphs.
 */
public final class MathState {
    private static final int FORMAT_VERSION = 3;

    private final Kernel kernel;
    private final Deciders deciders;
    private final Library library;

    public MathState() {
        this.kernel = new Kernel(new Signature());
        this.deciders = new Deciders(kernel.signature());
        this.library = new Library();
    }

    /**
     * A new world's mathematics. The opening hour certifies evens on the Test Bench before the
     * player has a Drafting Chamber, and recipes ask for numerals certified Even or Prime, so these
     * come predefined as starter definitions, exactly as the design doc defines them. They are
     * definitions only: every theorem about them is still the player's to prove. Fixing them also
     * keeps the names honest: nobody can define their own "Prime" as something trivially true.
     */
    public static MathState newWorld() {
        MathState state = new MathState();
        Term a = Term.var("a");
        Term b = Term.var("b");
        Term n = Term.var("n");
        Term k = Term.var("k");
        Term d = Term.var("d");
        Term q = Term.var("q");
        state.kernel.define(new Definition.PrimitiveRecursive("add", List.of("a", "b"), a,
                Term.succ(new Term.App("add", List.of(a, b))), "+"));
        state.kernel.define(new Definition.PrimitiveRecursive("mul", List.of("a", "b"), Term.zero(),
                new Term.App("add", List.of(new Term.App("mul", List.of(a, b)), a)), "×"));
        state.kernel.define(new Definition.Predicate("Even", List.of("n"),
                Formula.exists("k", Formula.eq(n, new Term.App("add", List.of(k, k)))), null));
        // a ≤ b :⇔ ∃k. a + k = b
        state.kernel.define(new Definition.Predicate("le", List.of("a", "b"),
                Formula.exists("k", Formula.eq(new Term.App("add", List.of(a, k)), b)), "≤"));
        // d | n :⇔ ∃q. d × q = n
        state.kernel.define(new Definition.Predicate("Divides", List.of("d", "n"),
                Formula.exists("q", Formula.eq(new Term.App("mul", List.of(d, q)), n)), "|"));
        // Prime(n) :⇔ 2 ≤ n ∧ ¬∃d. (2 ≤ d ∧ d < n ∧ d | n), with d < n written S(d) ≤ n
        Term two = Term.numeral(2);
        // pow(a, 0) = 1, pow(a, S(b)) = pow(a, b) × a; Mersenne(n) :⇔ ∃p. n + 1 = 2^p
        state.kernel.define(new Definition.PrimitiveRecursive("pow", List.of("a", "b"), Term.numeral(1),
                new Term.App("mul", List.of(new Term.App("pow", List.of(a, b)), a)), "^"));
        state.kernel.define(new Definition.Predicate("Mersenne", List.of("n"), Formula.exists("p", Formula.eq(
                new Term.App("add", List.of(n, Term.numeral(1))), new Term.App("pow", List.of(Term.numeral(2), Term.var("p"))))),
                null));
        state.kernel.define(new Definition.Predicate("Prime", List.of("n"), Formula.and(
                Formula.pred("le", two, n),
                Formula.not(Formula.exists("d", Formula.and(Formula.pred("le", two, d),
                        Formula.and(Formula.pred("le", Term.succ(d), n), Formula.pred("Divides", d, n)))))), null));
        return state;
    }

    public Kernel kernel() {
        return kernel;
    }

    public Signature signature() {
        return kernel.signature();
    }

    public Deciders deciders() {
        return deciders;
    }

    public Library library() {
        return library;
    }

    // ---- Saving ---------------------------------------------------------------------------

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            write(new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MathState fromBytes(byte[] data) throws IOException {
        return read(new DataInputStream(new ByteArrayInputStream(data)));
    }

    public void write(DataOutput out) throws IOException {
        out.writeByte(FORMAT_VERSION);
        out.writeLong(kernel.nextId());

        List<Definition> defs = new ArrayList<>(signature().all());
        out.writeInt(defs.size());
        for (Definition def : defs) {
            writeDefinition(out, def);
        }

        out.writeInt(signature().implementations().size());
        for (var e : signature().implementations().entrySet()) {
            out.writeUTF(e.getKey());
            out.writeUTF(e.getValue());
        }

        out.writeInt(deciders.all().size());
        for (Deciders.Link link : deciders.all()) {
            out.writeUTF(link.property());
            out.writeUTF(link.function());
            writeStrings(out, link.dependencies());
        }

        out.writeInt(library.theorems().size());
        for (var e : library.theorems().entrySet()) {
            out.writeUTF(e.getKey());
            writeTheorem(out, e.getValue());
        }

        Map<Formula, Library.Status> statuses = library.allStatuses();
        out.writeInt(statuses.size());
        for (var e : statuses.entrySet()) {
            writeFormula(out, e.getKey());
            switch (e.getValue()) {
                case Library.Unknown u -> out.writeByte(0);
                case Library.Tested t -> {
                    out.writeByte(1);
                    out.writeUTF(t.upTo().toString());
                }
                case Library.Proven p -> {
                    out.writeByte(2);
                    writeTheorem(out, p.theorem());
                }
                case Library.Refuted r -> {
                    out.writeByte(3);
                    writeAssignment(out, r.counterexample());
                }
            }
        }

        out.writeInt(library.certificates().size());
        for (Library.StructureCert cert : library.certificates()) {
            out.writeUTF(cert.structure());
            out.writeInt(cert.mapping().size());
            for (var e : new java.util.TreeMap<>(cert.mapping()).entrySet()) {
                out.writeUTF(e.getKey());
                out.writeUTF(e.getValue());
            }
            out.writeInt(cert.laws().size());
            for (Theorem law : cert.laws()) {
                writeTheorem(out, law);
            }
        }

        out.writeInt(library.conjectures().size());
        for (Library.Conjecture c : library.conjectures()) {
            out.writeUTF(c.name());
            writeFormula(out, c.statement());
            out.writeByte(c.state().ordinal());
            out.writeBoolean(c.counterexample() != null);
            if (c.counterexample() != null) {
                writeAssignment(out, c.counterexample());
            }
        }
    }

    public static MathState read(DataInput in) throws IOException {
        int version = in.readByte();
        if (version < 1 || version > FORMAT_VERSION) {
            throw new IOException("Unknown mathematics save format " + version);
        }
        MathState state = new MathState();
        state.kernel.setNextId(in.readLong());

        int defs = in.readInt();
        for (int i = 0; i < defs; i++) {
            // Re-checked on load; they were accepted in this order, so they still are.
            state.signature().defineInternal(readDefinition(in));
        }

        if (version >= 3) {
            int impls = in.readInt();
            for (int i = 0; i < impls; i++) {
                state.signature().restoreImplementation(in.readUTF(), in.readUTF());
            }
        }

        int links = in.readInt();
        for (int i = 0; i < links; i++) {
            state.deciders.restore(new Deciders.Link(in.readUTF(), in.readUTF(), readStrings(in)));
        }

        int theorems = in.readInt();
        for (int i = 0; i < theorems; i++) {
            String name = in.readUTF();
            state.library.restoreTheorem(name, readTheorem(in, state.kernel));
        }

        int statuses = in.readInt();
        for (int i = 0; i < statuses; i++) {
            Formula f = readFormula(in);
            Library.Status status = switch (in.readByte()) {
                case 0 -> new Library.Unknown();
                case 1 -> new Library.Tested(new BigInteger(in.readUTF()));
                case 2 -> new Library.Proven(readTheorem(in, state.kernel));
                case 3 -> new Library.Refuted(readAssignment(in));
                default -> throw new IOException("Unknown status");
            };
            state.library.restoreStatus(f, status);
        }

        if (version >= 2) {
            int certs = in.readInt();
            for (int i = 0; i < certs; i++) {
                String structure = in.readUTF();
                int n = in.readInt();
                Map<String, String> mapping = new LinkedHashMap<>();
                for (int j = 0; j < n; j++) {
                    mapping.put(in.readUTF(), in.readUTF());
                }
                int lawCount = in.readInt();
                List<Theorem> laws = new ArrayList<>();
                for (int j = 0; j < lawCount; j++) {
                    laws.add(readTheorem(in, state.kernel));
                }
                state.library.restoreCertificate(new Library.StructureCert(structure, mapping, laws));
            }
        }

        int conjectures = in.readInt();
        for (int i = 0; i < conjectures; i++) {
            String name = in.readUTF();
            Formula f = readFormula(in);
            Library.ConjectureState cs = Library.ConjectureState.values()[in.readByte()];
            Map<String, BigInteger> cex = in.readBoolean() ? readAssignment(in) : null;
            state.library.restoreConjecture(name, f, cs, cex);
        }
        return state;
    }

    // ---- Pieces (public so items can store statements the same way) ----------------------

    public static void writeFormula(DataOutput out, Formula f) throws IOException {
        GraphCodec.encode(f).write(out);
    }

    public static Formula readFormula(DataInput in) throws IOException {
        try {
            return GraphCodec.decodeFormula(PointedGraph.read(in));
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public static void writeTerm(DataOutput out, Term t) throws IOException {
        GraphCodec.encode(t).write(out);
    }

    public static Term readTerm(DataInput in) throws IOException {
        try {
            return GraphCodec.decodeTerm(PointedGraph.read(in));
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public static void writeTheorem(DataOutput out, Theorem thm) throws IOException {
        writeFormula(out, thm.formula());
        out.writeInt(thm.hypotheses().size());
        for (Theorem.Hypothesis h : thm.hypotheses()) {
            out.writeLong(h.id());
            writeFormula(out, h.formula());
        }
        writeStrings(out, thm.dependencies());
        out.writeLong(thm.steps());
    }

    public static Theorem readTheorem(DataInput in, Kernel kernel) throws IOException {
        Formula f = readFormula(in);
        int n = in.readInt();
        List<Theorem.Hypothesis> hyps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            hyps.add(new Theorem.Hypothesis(in.readLong(), readFormula(in)));
        }
        Set<String> deps = readStrings(in);
        return kernel.restoreSaved(f, hyps, deps, in.readLong());
    }

    private static void writeDefinition(DataOutput out, Definition def) throws IOException {
        out.writeUTF(def.name());
        writeStrings(out, def.params());
        out.writeBoolean(def.symbol() != null);
        if (def.symbol() != null) {
            out.writeUTF(def.symbol());
        }
        switch (def) {
            case Definition.Explicit e -> {
                out.writeByte(0);
                writeTerm(out, e.body());
            }
            case Definition.PrimitiveRecursive r -> {
                out.writeByte(1);
                writeTerm(out, r.base());
                writeTerm(out, r.step());
            }
            case Definition.Predicate p -> {
                out.writeByte(2);
                writeFormula(out, p.body());
            }
            case Definition.Abstract a -> {
                out.writeByte(3);
                out.writeBoolean(a.predicate());
            }
            case Definition.GeneralRecursive g -> {
                out.writeByte(4);
                writeTerm(out, g.body());
                writeTerm(out, g.measure());
            }
        }
    }

    private static Definition readDefinition(DataInput in) throws IOException {
        String name = in.readUTF();
        List<String> params = new ArrayList<>(readStrings(in));
        String symbol = in.readBoolean() ? in.readUTF() : null;
        return switch (in.readByte()) {
            case 0 -> new Definition.Explicit(name, params, readTerm(in), symbol);
            case 1 -> new Definition.PrimitiveRecursive(name, params, readTerm(in), readTerm(in), symbol);
            case 2 -> new Definition.Predicate(name, params, readFormula(in), symbol);
            case 3 -> new Definition.Abstract(name, params, in.readBoolean(), symbol);
            // Termination was checked when it was defined; saved worlds are trusted.
            case 4 -> new Definition.GeneralRecursive(name, params, readTerm(in), readTerm(in), symbol);
            default -> throw new IOException("Unknown definition kind");
        };
    }

    private static void writeStrings(DataOutput out, java.util.Collection<String> strings) throws IOException {
        out.writeInt(strings.size());
        for (String s : strings) {
            out.writeUTF(s);
        }
    }

    /** Order-preserving (definitions' parameter order matters). */
    private static Set<String> readStrings(DataInput in) throws IOException {
        int n = in.readInt();
        Set<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            out.add(in.readUTF());
        }
        return out;
    }

    private static void writeAssignment(DataOutput out, Map<String, BigInteger> assignment) throws IOException {
        out.writeInt(assignment.size());
        for (var e : assignment.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeUTF(e.getValue().toString());
        }
    }

    private static Map<String, BigInteger> readAssignment(DataInput in) throws IOException {
        int n = in.readInt();
        Map<String, BigInteger> out = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            out.put(in.readUTF(), new BigInteger(in.readUTF()));
        }
        return out;
    }
}
