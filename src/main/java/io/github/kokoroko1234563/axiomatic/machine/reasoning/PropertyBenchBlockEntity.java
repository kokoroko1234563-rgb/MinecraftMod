package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
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
 * Property Bench. In property mode, circuits give the (unproven) statement "P(circuits)" for the
 * selected property or relation. In seal mode, a statement and a variable give "∃var. statement".
 */
public class PropertyBenchBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 35, "slot.axiomatic.circuit_or_statement"),
            MachineLayout.input(44, 35, "slot.axiomatic.second_circuit"),
            MachineLayout.input(62, 35, "slot.axiomatic.variable_to_seal"), MachineLayout.output(116, 35, null)),
            List.of("button.axiomatic.next_property", "button.axiomatic.mode"));

    private String property = "Even";
    private boolean seal;

    public PropertyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.PROPERTY_BENCH.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        return seal ? Component.translatable("label.axiomatic.seal_mode")
                : Component.translatable("label.axiomatic.property", property);
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        if (id == 1) {
            seal = !seal;
        } else {
            List<String> props = data(level).state().signature().all().stream()
                    .filter(d -> d instanceof Definition.Predicate).map(Definition::name).toList();
            if (!props.isEmpty()) {
                property = props.get((props.indexOf(property) + 1) % props.size());
            }
        }
        markChanged();
    }

    @Override
    protected List<Integer> consumed() {
        return seal ? List.of(0) : items.get(1).isEmpty() ? List.of(0) : List.of(0, 1);
    }

    @Override
    protected List<Integer> required() {
        return seal ? List.of(0, 2) : List.of(0);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        if (seal) {
            Formula sealed = Formula.exists(ctx.variable(2), ctx.formula(0));
            return List.of(ctx.unproven(sealed));
        }
        if (!(ctx.state().signature().get(property) instanceof Definition.Predicate def)) {
            throw new ProofException(Component.translatable("status.axiomatic.unknown_property", property).getString());
        }
        List<Term> args = new ArrayList<>();
        args.add(ctx.term(0));
        if (!items.get(1).isEmpty()) {
            args.add(ctx.term(1));
        }
        Formula f = new Formula.Pred(property, args);
        if (args.size() != def.params().size()) {
            throw new ProofException(Component.translatable("status.axiomatic.arity", property, def.params().size()).getString());
        }
        ctx.state().signature().checkFormula(f);
        return List.of(ctx.unproven(f));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("property", property);
        output.putBoolean("seal", seal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        property = input.getStringOr("property", "Even");
        seal = input.getBooleanOr("seal", false);
    }
}
