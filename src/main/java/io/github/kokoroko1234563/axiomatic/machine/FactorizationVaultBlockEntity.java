package io.github.kokoroko1234563.axiomatic.machine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Factorization Vault: stores many kinds of item as one number. The i-th kind gets the i-th prime,
 * and how many are stored is that prime's exponent: 5 coal and 3 iron are 2⁵ × 3³ = 864. Put items
 * in the top-left slot; the output slots offer up to a stack of each of the first six kinds.
 */
public class FactorizationVaultBlockEntity extends MachineBlockEntity {
    private static final int KINDS_SHOWN = 6;
    private static final List<MachineLayout.SlotSpec> SLOTS = new ArrayList<>();

    static {
        SLOTS.add(MachineLayout.input(26, 31, "slot.axiomatic.vault_in"));
        for (int i = 0; i < KINDS_SHOWN; i++) {
            SLOTS.add(MachineLayout.output(116 + 18 * (i % 3), 22 + 18 * (i / 3), null));
        }
    }

    private static final MachineLayout LAYOUT = new MachineLayout(SLOTS, List.of());

    /** One stored kind: an example stack (count 1) and how many are stored. */
    private final List<ItemStack> kinds = new ArrayList<>();
    private final List<BigInteger> counts = new ArrayList<>();
    /** What each output slot showed last tick, to notice what was taken. */
    private final int[] shown = new int[KINDS_SHOWN];

    public FactorizationVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.FACTORIZATION_VAULT.type(), pos, state, 0);
    }

    @Override
    public MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public boolean acceptsEnergy() {
        return false;
    }

    @Override
    protected Job tryStartJob(ServerLevel level) {
        return null;
    }

    /** The number this vault is: the product of prime^count over the stored kinds. */
    public BigInteger number() {
        BigInteger n = BigInteger.ONE;
        for (int i = 0; i < kinds.size(); i++) {
            n = n.multiply(BigInteger.valueOf(nthPrime(i)).pow(counts.get(i).min(BigInteger.valueOf(100_000)).intValue()));
        }
        return n;
    }

    @Override
    public Component info() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(kinds.size(), 4); i++) {
            sb.append(i > 0 ? " × " : "").append(nthPrime(i)).append('^').append(counts.get(i));
        }
        if (kinds.size() > 4) {
            sb.append(" × …");
        }
        return kinds.isEmpty() ? Component.translatable("label.axiomatic.vault_empty")
                : Component.literal(sb.toString());
    }

    @Override
    protected void tick(ServerLevel level) {
        // Notice what was taken from the output slots since last tick.
        for (int i = 0; i < KINDS_SHOWN && i < kinds.size(); i++) {
            ItemStack slot = items.get(1 + i);
            int now = ItemStack.isSameItemSameComponents(slot, kinds.get(i)) ? slot.getCount() : 0;
            if (now < shown[i]) {
                counts.set(i, counts.get(i).subtract(BigInteger.valueOf(shown[i] - now)));
                markChanged();
            }
        }
        // Take in deposits.
        ItemStack in = items.get(0);
        if (!in.isEmpty()) {
            int kind = -1;
            for (int i = 0; i < kinds.size(); i++) {
                if (ItemStack.isSameItemSameComponents(kinds.get(i), in)) {
                    kind = i;
                    break;
                }
            }
            if (kind < 0) {
                kinds.add(in.copyWithCount(1));
                counts.add(BigInteger.ZERO);
                kind = kinds.size() - 1;
            }
            counts.set(kind, counts.get(kind).add(BigInteger.valueOf(in.getCount())));
            items.set(0, ItemStack.EMPTY);
            markChanged();
        }
        // Drop kinds that ran out (keeping primes in order would renumber the rest, so only the
        // last kinds are removed when empty).
        while (!counts.isEmpty() && counts.get(counts.size() - 1).signum() == 0) {
            kinds.remove(kinds.size() - 1);
            counts.remove(counts.size() - 1);
        }
        // Offer up to a stack of each shown kind.
        for (int i = 0; i < KINDS_SHOWN; i++) {
            if (i < kinds.size()) {
                ItemStack kind = kinds.get(i);
                int offer = counts.get(i).min(BigInteger.valueOf(kind.getMaxStackSize())).intValue();
                items.set(1 + i, offer > 0 ? kind.copyWithCount(offer) : ItemStack.EMPTY);
                shown[i] = offer;
            } else {
                items.set(1 + i, ItemStack.EMPTY);
                shown[i] = 0;
            }
        }
        setStatus(Component.translatable("status.axiomatic.vault_number", number().toString().length()));
        super.tick(level);
    }

    /** The i-th prime (0-based): 2, 3, 5, 7, … */
    static int nthPrime(int i) {
        int count = -1;
        for (int n = 2; ; n++) {
            boolean prime = true;
            for (int d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime && ++count == i) {
                return n;
            }
        }
    }

    /** Breaking the vault spills what it stores (at most 64 stacks of each kind), not the offered copies. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        for (int i = 1; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int i = 0; i < kinds.size(); i++) {
            ItemStack kind = kinds.get(i);
            BigInteger left = counts.get(i).min(BigInteger.valueOf(64L * kind.getMaxStackSize()));
            while (left.signum() > 0) {
                int n = left.min(BigInteger.valueOf(kind.getMaxStackSize())).intValue();
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        kind.copyWithCount(n));
                left = left.subtract(BigInteger.valueOf(n));
            }
        }
        kinds.clear();
        counts.clear();
    }

    @Override
    public boolean canPlace(int slot, ItemStack stack) {
        return slot == 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.TypedOutputList<ItemStack> kindList = output.list("kinds", ItemStack.OPTIONAL_CODEC);
        kinds.forEach(kindList::add);
        ValueOutput.TypedOutputList<String> countList = output.list("counts", com.mojang.serialization.Codec.STRING);
        counts.forEach(c -> countList.add(c.toString()));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        kinds.clear();
        counts.clear();
        input.listOrEmpty("kinds", ItemStack.OPTIONAL_CODEC).stream().forEach(kinds::add);
        input.listOrEmpty("counts", com.mojang.serialization.Codec.STRING).stream().map(BigInteger::new).forEach(counts::add);
        while (counts.size() < kinds.size()) {
            counts.add(BigInteger.ZERO);
        }
        // Output slots are rebuilt from the counts on the next tick; what they showed is not stored.
        for (int i = 0; i < KINDS_SHOWN; i++) {
            items.set(1 + i, ItemStack.EMPTY);
            shown[i] = 0;
        }
    }
}
