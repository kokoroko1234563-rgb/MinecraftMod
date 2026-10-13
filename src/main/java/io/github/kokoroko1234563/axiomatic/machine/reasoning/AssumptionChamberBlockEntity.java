package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
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
 * Assumption Chamber. Put a statement H in the assumption slot: the chamber holds it and returns
 * a hypothesis item stamped with it (its variables are locked). Put a conclusion Q derived from
 * it in the discharge slot: out comes the proven "H → Q". A proven "∃k. φ(k)" in the assumption
 * slot is opened instead: the hypothesis is φ of a fresh variable, and discharging gives Q itself
 * (if Q does not mention that variable). Release clears the chamber; Copy gives another
 * hypothesis item.
 */
public class AssumptionChamberBlockEntity extends ReasoningBlockEntity {
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.assumption"), MachineLayout.input(44, 31, "slot.axiomatic.discharge"),
            MachineLayout.output(116, 31, null), MachineLayout.output(134, 31, null),
            MachineLayout.input(26, 51, "slot.axiomatic.open_as")),
            List.of("button.axiomatic.release", "button.axiomatic.copy"));

    /** The hypothesis theorem this chamber holds, or null when empty. */
    private StatementData hypothesis;
    /** For an opened ∃: the fresh variable and the proof of the ∃ statement. */
    private String openedVariable;
    private StatementData existence;
    private boolean copyRequested;
    private int lastBranch;

    public AssumptionChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.ASSUMPTION_CHAMBER.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        if (hypothesis == null) {
            return Component.translatable("label.axiomatic.chamber_empty");
        }
        return Component.translatable("label.axiomatic.assuming", hypothesis.display());
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        if (id == 0) {
            hypothesis = null;
            openedVariable = null;
            existence = null;
            setStatus(Component.translatable("status.axiomatic.released"));
        } else if (hypothesis != null) {
            copyRequested = true;
        }
        markChanged();
    }

    @Override
    protected List<Integer> consumed() {
        return switch (lastBranch) {
            case 1 -> List.of(0);
            case 2 -> List.of(1);
            default -> List.of();
        };
    }

    @Override
    protected List<Integer> required() {
        return List.of();
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        Kernel kernel = ctx.state().kernel();
        lastBranch = 0;
        if (copyRequested && hypothesis != null) {
            copyRequested = false;
            return List.of(MathItems.statementStack(hypothesis));
        }
        if (!items.get(1).isEmpty()) {
            if (hypothesis == null) {
                throw new ProofException(Component.translatable("status.axiomatic.nothing_assumed").getString());
            }
            Theorem conclusion = ctx.theorem(1);
            Theorem hyp = hypothesis.theorem(kernel);
            Theorem result = openedVariable == null
                    ? kernel.discharge(conclusion, hyp.hypotheses().get(0))
                    : kernel.closeExists(existence.theorem(kernel), new Kernel.Opening(hyp, openedVariable), conclusion);
            lastBranch = 2;
            return List.of(ctx.proven(result));
        }
        if (!items.get(0).isEmpty()) {
            if (hypothesis != null) {
                throw new ProofException(Component.translatable("status.axiomatic.chamber_busy").getString());
            }
            StatementData in = MathItems.statement(items.get(0));
            if (in == null) {
                throw new ProofException(Component.translatable("status.axiomatic.needs_statement", 1).getString());
            }
            if (!outputsFit(List.of(items.get(0).copyWithCount(1)))) {
                return List.of();
            }
            Theorem hyp;
            if (in.proven() && in.formula() instanceof Formula.Exists ex) {
                // A proven ∃ (possibly stamped by another chamber) is opened, not assumed.
                Kernel.Opening opening = items.get(4).isEmpty() ? kernel.openExists(ex)
                        : kernel.openExists(ex, ctx.variable(4));
                hyp = opening.hypothesis();
                openedVariable = opening.variable();
                existence = in;
            } else {
                hyp = kernel.assume(in.formula());
            }
            hypothesis = StatementData.of(hyp, ctx.printer());
            lastBranch = 1;
            markChanged();
            return List.of(MathItems.statementStack(hypothesis));
        }
        return List.of();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("hypothesis", StatementData.CODEC, hypothesis);
        output.storeNullable("existence", StatementData.CODEC, existence);
        if (openedVariable != null) {
            output.putString("opened", openedVariable);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hypothesis = input.read("hypothesis", StatementData.CODEC).orElse(null);
        existence = input.read("existence", StatementData.CODEC).orElse(null);
        openedVariable = input.getString("opened").orElse(null);
    }
}
