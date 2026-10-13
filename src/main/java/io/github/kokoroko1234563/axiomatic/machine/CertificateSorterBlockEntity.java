package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Certificate Sorter: the item filter of a factory. Put a property statement such as "Even(n)" in
 * the filter slot; numerals certified to have it go to the "yes" output, which a hopper below can
 * pull, and the rest go to the "no" output, which the sorter pushes into containers beside it.
 * The button chooses whether conjectural certificates count. One unit per numeral.
 */
public class CertificateSorterBlockEntity extends MachineBlockEntity {
    public static final int INPUT = 0;
    public static final int FILTER = 1;
    public static final int YES = 2;
    public static final int NO = 3;
    private static final MachineLayout LAYOUT = new MachineLayout(List.of(
            MachineLayout.input(26, 31, "slot.axiomatic.numeral"), MachineLayout.input(44, 31, "slot.axiomatic.filter"),
            MachineLayout.output(116, 31, "slot.axiomatic.sort_yes"), MachineLayout.output(134, 31, "slot.axiomatic.sort_no")),
            List.of("button.axiomatic.certificates"));

    private boolean provenOnly;
    /** Where the current job's numeral goes: the yes output or the no output. */
    private boolean toYes;

    public CertificateSorterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.CERTIFICATE_SORTER.type(), pos, state, 2_000);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public Component info() {
        String property = property();
        return Component.translatable(provenOnly ? "label.axiomatic.sorts_proven" : "label.axiomatic.sorts_any",
                property == null ? "?" : property);
    }

    /** The property named by the filter statement P(x), or null. */
    public String property() {
        StatementData filter = MathItems.statement(items.get(FILTER));
        return filter != null && filter.formula() instanceof Formula.Pred p && p.args().size() == 1 ? p.name() : null;
    }

    @Override
    public void onButton(ServerLevel level, Player player, int id) {
        provenOnly = !provenOnly;
        markChanged();
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> stack.is(ModRegistries.NUMERAL_ITEM.get());
            case FILTER -> stack.is(ModRegistries.STATEMENT_ITEM.get());
            default -> false;
        };
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        ItemStack in = items.get(INPUT);
        String property = property();
        if (in.isEmpty()) {
            return null;
        }
        if (property == null) {
            setStatus(Component.translatable("status.axiomatic.needs_filter"));
            return null;
        }
        boolean yes = NumeralItem.data(in).hasProperty(property, !provenOnly);
        ItemStack one = in.copyWithCount(1);
        if (!fits(yes ? YES : NO, one)) {
            return null;
        }
        toYes = yes;
        in.shrink(1);
        setStatus(Component.empty());
        return new Job(1, 2, List.of(one));
    }

    private boolean fits(int slot, ItemStack stack) {
        ItemStack there = items.get(slot);
        return there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack)
                && there.getCount() + stack.getCount() <= there.getMaxStackSize();
    }

    /** The job's numeral goes to the output its certificate chose. */
    @Override
    protected boolean insertOutputs(List<ItemStack> outputs) {
        int slot = toYes ? YES : NO;
        NonNullList<ItemStack> trial = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            trial.set(i, items.get(i).copy());
        }
        for (ItemStack out : outputs) {
            ItemStack there = trial.get(slot);
            if (there.isEmpty()) {
                trial.set(slot, out.copy());
            } else if (ItemStack.isSameItemSameComponents(there, out)
                    && there.getCount() + out.getCount() <= there.getMaxStackSize()) {
                there.grow(out.getCount());
            } else {
                return false;
            }
        }
        items = trial;
        return true;
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        // Push rejects sideways, like a hopper would, every 8 ticks.
        if (level.getGameTime() % 8 == 0 && !items.get(NO).isEmpty()) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Container target = HopperBlockEntity.getContainerAt(level, worldPosition.relative(d));
                if (target == null) {
                    continue;
                }
                ItemStack one = items.get(NO).copyWithCount(1);
                ItemStack left = HopperBlockEntity.addItem(this, target, one, d.getOpposite());
                if (left.isEmpty()) {
                    items.get(NO).shrink(1);
                    target.setChanged();
                    markChanged();
                    break;
                }
            }
        }
    }

    /** Numerals in from above or the sides; the yes output out below; the no output out the sides. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> new int[] {INPUT};
            case DOWN -> new int[] {YES};
            default -> new int[] {INPUT, NO};
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == INPUT && canPlace(slot, stack);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("proven_only", provenOnly);
        output.putBoolean("to_yes", toYes);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        provenOnly = input.getBooleanOr("proven_only", false);
        toYes = input.getBooleanOr("to_yes", false);
    }
}
