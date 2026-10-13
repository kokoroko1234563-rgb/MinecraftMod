package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

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
 * Rewriter: in a proven statement, replaces the left side of a proven equation by its right side
 * (or the reverse). The equation's variables match anything, so "x + y = y + x" rewrites any sum;
 * the occurrence button picks which match, in reading order, or all. The equation stays.
 */
public class RewriterBlockEntity extends ReasoningBlockEntity {
    private static final int MAX_OCCURRENCE = 8;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.statement"), MachineLayout.input(44, 31, "slot.axiomatic.equation"),
            MachineLayout.output(116, 31, null)),
            List.of("button.axiomatic.direction", "button.axiomatic.occurrence"));

    private boolean rightToLeft;
    private int occurrence = -1;

    public RewriterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.REWRITER.type(), pos, state);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        Component which = occurrence < 0 ? Component.translatable("label.axiomatic.all")
                : Component.literal("#" + (occurrence + 1));
        return Component.translatable(rightToLeft ? "label.axiomatic.rewrite_rtl" : "label.axiomatic.rewrite_ltr", which);
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        if (id == 0) {
            rightToLeft = !rightToLeft;
        } else {
            occurrence = occurrence >= MAX_OCCURRENCE - 1 ? -1 : occurrence + 1;
        }
        markChanged();
    }

    @Override
    protected List<Integer> consumed() {
        return List.of(0);
    }

    @Override
    protected List<Integer> required() {
        return List.of(0, 1);
    }

    @Override
    protected List<ItemStack> apply(Context ctx) {
        // Pattern rewriting: the equation's free variables match any subterm (like rw).
        return List.of(ctx.proven(io.github.kokoroko1234563.axiomatic.core.logic.Tactics.rewriteMatching(
                ctx.state().kernel(), ctx.theorem(0), ctx.theorem(1), rightToLeft, occurrence)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("right_to_left", rightToLeft);
        output.putInt("occurrence", occurrence);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rightToLeft = input.getBooleanOr("right_to_left", false);
        occurrence = input.getIntOr("occurrence", -1);
    }
}
