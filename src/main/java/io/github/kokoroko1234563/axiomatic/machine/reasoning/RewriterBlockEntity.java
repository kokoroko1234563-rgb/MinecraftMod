package io.github.kokoroko1234563.axiomatic.machine.reasoning;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.logic.EquivalenceRewriting;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.ProofException;
import io.github.kokoroko1234563.axiomatic.core.logic.Structures;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
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
 *
 * <p>With a certified equivalence instead of an equation (a statement s ≡ t whose relation has an
 * Equivalence certificate in the Library), it rewrites inside u ≡ v or u = v, giving u' ≡ v'. Every
 * function around the match must respect ≡: the Library must hold a named congruence law for it,
 * such as a ≡ b → c ≡ d → a + c ≡ b + d.
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
        Theorem equation = ctx.theorem(1);
        if (equation.formula() instanceof Formula.Pred p && p.args().size() == 2) {
            return List.of(ctx.proven(EquivalenceRewriting.rewrite(ctx.state().kernel(), ctx.theorem(0), equation,
                    laws(ctx.state().library(), p.name()), rightToLeft, occurrence)));
        }
        // Pattern rewriting: the equation's free variables match any subterm (like rw).
        return List.of(ctx.proven(io.github.kokoroko1234563.axiomatic.core.logic.Tactics.rewriteMatching(
                ctx.state().kernel(), ctx.theorem(0), equation, rightToLeft, occurrence)));
    }

    /** The laws of a certified equivalence: its certificate's three laws and the Library's named congruences. */
    static EquivalenceRewriting.Laws laws(Library library, String relation) {
        for (Library.StructureCert cert : library.certificates()) {
            if (cert.structure().equals(Structures.EQUIVALENCE) && relation.equals(cert.mapping().get("Equivalence.R"))) {
                return new EquivalenceRewriting.Laws(relation, cert.laws().get(0), cert.laws().get(1), cert.laws().get(2),
                        List.copyOf(library.theorems().values()));
            }
        }
        throw new ProofException(Component.translatable("status.axiomatic.not_an_equivalence", relation).getString());
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
