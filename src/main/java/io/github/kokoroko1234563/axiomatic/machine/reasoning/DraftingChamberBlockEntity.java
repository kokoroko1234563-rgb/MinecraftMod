package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Signature;
import io.github.kokoroko1234563.axiomatic.core.logic.Syntax;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.CircuitData;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.ReasoningBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Drafting Chamber, with four modes:
 * <ul>
 * <li><b>Assemble</b>: a function (picked with the Function button: S, any defined function, or ↺
 * the recursive call) applied to the circuits in the input slots gives a new circuit.</li>
 * <li><b>Define</b>: compiles a definition named by renaming its first input in an anvil. A circuit
 * alone defines a function; a circuit (base case), a circuit (step, using ↺) and a variable (the
 * one recursed on) define one by recursion; a statement defines a property or relation. Out come
 * its defining statements, proven. An unnamed circuit f(…) or statement P(…) instead gives the
 * defining statements of the existing f or P again (needed for the starter definitions).</li>
 * <li><b>Name</b>: a proven statement renamed in an anvil becomes a named theorem in the Library.
 * This is the compile step, and costs the whole proof.</li>
 * <li><b>Tablet</b>: an Axiom Tablet supplies its axiom as a proven statement.</li>
 * </ul>
 */
public class DraftingChamberBlockEntity extends ReasoningBlockEntity {
    /** Placeholder for the call one step down in a recursive definition. Not a valid player name. */
    public static final String RECURSIVE_CALL = "#rec";

    public enum Mode { ASSEMBLE, DEFINE, NAME, TABLET }

    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.drafting_1"), MachineLayout.input(44, 31, "slot.axiomatic.drafting_2"),
            MachineLayout.input(62, 31, "slot.axiomatic.drafting_3"),
            MachineLayout.output(116, 31, null), MachineLayout.output(134, 31, null)),
            List.of("button.axiomatic.mode", "button.axiomatic.function"));

    private Mode mode = Mode.ASSEMBLE;
    private String function = "S";
    private List<Integer> lastConsumed = List.of();
    private long lastCost = 1;

    public DraftingChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.DRAFTING_CHAMBER.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        Component modeName = Component.translatable("label.axiomatic.mode." + mode.name().toLowerCase());
        return mode == Mode.ASSEMBLE
                ? Component.translatable("label.axiomatic.assemble", modeName, function.equals(RECURSIVE_CALL) ? "↺" : function)
                : modeName;
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        if (id == 0) {
            mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        } else {
            List<String> functions = functions(data(level).state().signature());
            function = functions.get((functions.indexOf(function) + 1) % functions.size());
        }
        markChanged();
    }

    /** What the Function button cycles through: S, every defined function, then the recursive call. */
    static List<String> functions(Signature signature) {
        List<String> out = new ArrayList<>();
        out.add("S");
        signature.all().stream().filter(d -> Signature.isFunction(d) && !d.name().contains("⟨"))
                .forEach(d -> out.add(d.name()));
        out.add(RECURSIVE_CALL);
        return out;
    }

    @Override
    protected List<Integer> consumed() {
        return lastConsumed;
    }

    @Override
    protected List<Integer> required() {
        // Assembling a constant (a function with no inputs) needs no input at all.
        return mode == Mode.ASSEMBLE ? List.of() : List.of(0);
    }

    @Override
    protected long cost(Context ctx) {
        return lastCost;
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        lastCost = 1;
        return switch (mode) {
            case ASSEMBLE -> assemble(ctx);
            case DEFINE -> define(ctx);
            case NAME -> name(ctx);
            case TABLET -> tablet(ctx);
        };
    }

    private List<ItemStack> assemble(Context ctx) {
        List<Term> args = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < 3 && !items.get(i).isEmpty(); i++) {
            args.add(ctx.term(i));
            used.add(i);
        }
        if (args.isEmpty() && (function.equals("S") || function.equals(RECURSIVE_CALL)
                || ctx.state().signature().get(function) == null
                || !ctx.state().signature().get(function).params().isEmpty() || !items.get(3).isEmpty())) {
            return List.of();
        }
        Term result;
        if (function.equals("S")) {
            if (args.size() != 1) {
                throw new ProofException(Component.translatable("status.axiomatic.arity", "S", 1).getString());
            }
            result = Term.succ(args.get(0));
        } else if (function.equals(RECURSIVE_CALL)) {
            result = new Term.App(RECURSIVE_CALL, args);
        } else {
            Definition def = ctx.state().signature().get(function);
            if (def == null) {
                throw new ProofException(Component.translatable("status.axiomatic.unknown_function", function).getString());
            }
            if (def.params().size() != args.size()) {
                throw new ProofException(Component.translatable("status.axiomatic.arity", function, def.params().size()).getString());
            }
            result = new Term.App(function, args);
        }
        lastConsumed = used;
        return List.of(MathItems.circuitStack(new CircuitData(result, display(ctx, result))));
    }

    private static String display(Context ctx, Term t) {
        return ctx.printer().term(t).replace(RECURSIVE_CALL, "↺");
    }

    private static String nameOf(ItemStack stack) {
        Component custom = stack.getCustomName();
        if (custom == null) {
            throw new ProofException(Component.translatable("status.axiomatic.needs_name").getString());
        }
        String name = custom.getString().trim();
        if (!Syntax.isValidName(name)) {
            throw new ProofException(Component.translatable("status.axiomatic.bad_name", name).getString());
        }
        return name;
    }

    private List<ItemStack> define(Context ctx) {
        Kernel kernel = ctx.state().kernel();
        if (items.get(0).getCustomName() == null && items.get(1).isEmpty()) {
            // Looking up an existing definition: f(…) or P(…) unnamed gives f's defining statements again.
            String existing = existingDefinition(items.get(0));
            if (existing != null) {
                lastConsumed = List.of();
                if (!items.get(3).isEmpty() || !items.get(4).isEmpty()) {
                    return List.of();
                }
                return kernel.definingStatements(existing).stream().map(ctx::proven).toList();
            }
        }
        String name = nameOf(items.get(0));
        StatementData statement = MathItems.statement(items.get(0));
        Definition def;
        if (statement != null) {
            if (statement.proven()) {
                throw new ProofException(Component.translatable("status.axiomatic.define_unproven").getString());
            }
            def = new Definition.Predicate(name, List.copyOf(new TreeSet<>(Syntax.freeVars(statement.formula()))),
                    statement.formula(), null);
            lastConsumed = List.of(0);
        } else if (items.get(1).isEmpty()) {
            Term body = ctx.term(0);
            def = new Definition.Explicit(name, List.copyOf(new TreeSet<>(Syntax.freeVars(body))), body, null);
            lastConsumed = List.of(0);
        } else {
            Term base = ctx.term(0);
            Term step = ctx.term(1);
            String y = ctx.variable(2);
            List<String> fixed = recursiveFixedParams(step, y);
            if (fixed == null) {
                TreeSet<String> vars = new TreeSet<>(Syntax.freeVars(base));
                vars.addAll(Syntax.freeVars(step));
                vars.remove(y);
                fixed = List.copyOf(vars);
            }
            List<String> params = new ArrayList<>(fixed);
            params.add(y);
            def = new Definition.PrimitiveRecursive(name, params, base, renameCall(step, name), null);
            lastConsumed = List.of(0, 1);
        }
        List<Theorem> statements = kernel.define(def);
        lastCost = 10;
        return statements.stream().map(ctx::proven).toList();
    }

    private String existingDefinition(ItemStack stack) {
        Term t = MathItems.termOf(stack);
        if (t instanceof Term.App a && !a.function().equals(RECURSIVE_CALL)) {
            return a.function();
        }
        StatementData st = MathItems.statement(stack);
        if (st != null && st.formula() instanceof io.github.kokoroko1234563.axiomatic.core.logic.Formula.Pred p) {
            return p.name();
        }
        return null;
    }

    /** The fixed parameters as the recursive call ↺(x̄, y) in the step lists them, or null if there is no call. */
    private static List<String> recursiveFixedParams(Term t, String y) {
        return switch (t) {
            case Term.App a when a.function().equals(RECURSIVE_CALL) -> {
                List<String> names = new ArrayList<>();
                for (Term arg : a.args()) {
                    if (!(arg instanceof Term.Var v)) {
                        throw new ProofException(Component.translatable("status.axiomatic.bad_recursive_call").getString());
                    }
                    names.add(v.name());
                }
                if (names.isEmpty() || !names.get(names.size() - 1).equals(y)) {
                    throw new ProofException(Component.translatable("status.axiomatic.bad_recursive_call").getString());
                }
                yield names.subList(0, names.size() - 1);
            }
            case Term.App a -> {
                for (Term arg : a.args()) {
                    List<String> found = recursiveFixedParams(arg, y);
                    if (found != null) {
                        yield found;
                    }
                }
                yield null;
            }
            case Term.Succ s -> recursiveFixedParams(s.arg(), y);
            default -> null;
        };
    }

    private static Term renameCall(Term t, String name) {
        return switch (t) {
            case Term.App a -> new Term.App(a.function().equals(RECURSIVE_CALL) ? name : a.function(),
                    a.args().stream().map(x -> renameCall(x, name)).toList());
            case Term.Succ s -> Term.succ(renameCall(s.arg(), name));
            default -> t;
        };
    }

    private List<ItemStack> name(Context ctx) {
        String name = nameOf(items.get(0));
        Theorem thm = ctx.theorem(0);
        ctx.state().library().name(name, thm);
        lastConsumed = List.of(0);
        lastCost = Math.max(1, thm.steps());
        ItemStack out = ctx.proven(thm);
        out.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        return List.of(out);
    }

    private List<ItemStack> tablet(Context ctx) {
        if (!(items.get(0).getItem() instanceof MathItems.AxiomTabletItem tablet)) {
            throw new ProofException(Component.translatable("status.axiomatic.needs_tablet").getString());
        }
        Kernel kernel = ctx.state().kernel();
        Theorem axiom = tablet.axiom().equals("succ_not_zero") ? kernel.succNotZero() : kernel.succInjective();
        lastConsumed = List.of();
        // Only one at a time: the tablet stays, so wait for the output slot to be emptied.
        if (!items.get(3).isEmpty()) {
            return List.of();
        }
        return List.of(ctx.proven(axiom));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("mode", mode.name());
        output.putString("function", function);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            mode = Mode.valueOf(input.getStringOr("mode", "ASSEMBLE"));
        } catch (IllegalArgumentException e) {
            mode = Mode.ASSEMBLE;
        }
        function = input.getStringOr("function", "S");
    }
}
