package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Structures;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
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
 * Structure Bench, for monoids, equivalences and orders:
 * <ul>
 * <li><b>Axioms</b>: outputs the chosen axiom of the chosen structure, for proving generic
 * theorems ("for any monoid …").</li>
 * <li><b>Certify</b>: the structure's three laws, proven for concrete definitions, in the three
 * inputs (in axiom order) give a certificate in the Library.</li>
 * <li><b>Apply</b>: a generic theorem plus a circuit naming a certified operation (such as
 * add(a, b)) give the theorem for that operation, with no new proof.</li>
 * </ul>
 * Inputs are tools here: nothing is used up.
 */
public class StructureBenchBlockEntity extends ReasoningBlockEntity {
    public enum Mode { AXIOMS, CERTIFY, APPLY }

    private static final List<String> STRUCTURES = List.of(Structures.MONOID, Structures.EQUIVALENCE, Structures.ORDER);
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.structure_1"), MachineLayout.input(44, 31, "slot.axiomatic.structure_2"),
            MachineLayout.input(62, 31, "slot.axiomatic.structure_3"), MachineLayout.output(116, 31, null)),
            List.of("button.axiomatic.mode", "button.axiomatic.structure", "button.axiomatic.axiom"));

    private Mode mode = Mode.AXIOMS;
    private String structure = Structures.MONOID;
    private int axiom;

    public StructureBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.STRUCTURE_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        String suffix = mode == Mode.AXIOMS ? " #" + (axiom + 1) : "";
        return Component.translatable("label.axiomatic.structure_mode",
                Component.translatable("label.axiomatic.structure_mode." + mode.name().toLowerCase()), structure + suffix);
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        switch (id) {
            case 0 -> mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            case 1 -> structure = STRUCTURES.get((STRUCTURES.indexOf(structure) + 1) % STRUCTURES.size());
            default -> axiom = (axiom + 1) % 3;
        }
        markChanged();
    }

    /** Showing an axiom or t = t is not proving anything: no "Q.E.D." advancement. */
    @Override
    protected List<String> milestones(List<ItemStack> out) {
        return List.of();
    }

    @Override
    protected List<Integer> consumed() {
        return List.of();
    }

    @Override
    protected List<Integer> required() {
        return List.of();
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        Kernel kernel = ctx.state().kernel();
        if (!items.get(3).isEmpty()) {
            return List.of();
        }
        return switch (mode) {
            case AXIOMS -> List.of(ctx.proven(kernel.structureAxiom(structure, axiom)));
            case CERTIFY -> {
                if (items.get(0).isEmpty() || items.get(1).isEmpty() || items.get(2).isEmpty()) {
                    yield List.of();
                }
                List<Theorem> laws = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    laws.add(ctx.theorem(i));
                }
                Library library = ctx.state().library();
                var mapping = Structures.inferMapping(kernel.structure(structure), laws.stream().map(Theorem::formula).toList());
                if (mapping != null && library.certificates().stream()
                        .anyMatch(c -> c.structure().equals(structure) && c.mapping().equals(mapping))) {
                    setStatus(Component.translatable("status.axiomatic.certified_structure", structure, mapping.toString()));
                    yield List.of();
                }
                Library.StructureCert cert = library.certifyStructure(kernel, structure, laws);
                data((ServerLevel) level).setDirty();
                io.github.kokoroko1234563.axiomatic.progress.Milestones.award((ServerLevel) level, worldPosition, io.github.kokoroko1234563.axiomatic.progress.Milestones.STRUCTURE);
                setStatus(Component.translatable("status.axiomatic.certified_structure", structure, cert.mapping().toString()));
                yield List.of();
            }
            case APPLY -> {
                if (items.get(0).isEmpty() || items.get(1).isEmpty()) {
                    yield List.of();
                }
                Theorem generic = ctx.theorem(0);
                if (!(ctx.term(1) instanceof Term.App app)) {
                    throw new ProofException(Component.translatable("status.axiomatic.needs_design").getString());
                }
                Library.StructureCert cert = ctx.state().library().certificates().stream()
                        .filter(c -> c.structure().equals(structure) && c.mapping().containsValue(app.function()))
                        .findFirst().orElseThrow(() -> new ProofException(Component.translatable(
                                "status.axiomatic.not_certified_structure", app.function(), structure).getString()));
                yield List.of(ctx.proven(kernel.interpret(generic, structure, cert.mapping(), cert.laws())));
            }
        };
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("mode", mode.name());
        output.putString("structure", structure);
        output.putInt("axiom", axiom);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            mode = Mode.valueOf(input.getStringOr("mode", "AXIOMS"));
        } catch (IllegalArgumentException e) {
            mode = Mode.AXIOMS;
        }
        structure = input.getStringOr("structure", Structures.MONOID);
        axiom = input.getIntOr("axiom", 0);
    }
}
