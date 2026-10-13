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
    /** The stored kinds and counts, as kept on the vault's item when it is broken. */
    public record Contents(List<ItemStack> kinds, List<String> counts) {
        public static final com.mojang.serialization.Codec<Contents> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                        ItemStack.CODEC.listOf().fieldOf("kinds").forGetter(Contents::kinds),
                        com.mojang.serialization.Codec.STRING.listOf().fieldOf("counts").forGetter(Contents::counts))
                        .apply(i, Contents::new));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Contents>
                STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
                        ItemStack.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()), Contents::kinds,
                        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                        Contents::counts, Contents::new);
    }

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
    /** Which kind each output slot shows (-1 for none): the first six kinds with anything left. */
    private final int[] slotKind = {-1, -1, -1, -1, -1, -1};

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
        for (int i = 0; i < KINDS_SHOWN; i++) {
            int k = slotKind[i];
            if (k < 0 || k >= kinds.size()) {
                continue;
            }
            ItemStack slot = items.get(1 + i);
            int now = ItemStack.isSameItemSameComponents(slot, kinds.get(k)) ? slot.getCount() : 0;
            if (now < shown[i]) {
                counts.set(k, counts.get(k).subtract(BigInteger.valueOf(shown[i] - now)).max(BigInteger.ZERO));
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
        // Offer up to a stack of each of the first six kinds that have anything left.
        int next = 0;
        for (int i = 0; i < KINDS_SHOWN; i++) {
            while (next < kinds.size() && counts.get(next).signum() == 0) {
                next++;
            }
            if (next < kinds.size()) {
                ItemStack kind = kinds.get(next);
                int offer = counts.get(next).min(BigInteger.valueOf(kind.getMaxStackSize())).intValue();
                items.set(1 + i, kind.copyWithCount(offer));
                shown[i] = offer;
                slotKind[i] = next++;
            } else {
                items.set(1 + i, ItemStack.EMPTY);
                shown[i] = 0;
                slotKind[i] = -1;
            }
        }
        setStatus(Component.translatable("status.axiomatic.vault_number", digits()));
        super.tick(level);
    }

    /** How many decimal digits {@link #number()} has, from logarithms (no need to build it). */
    public long digits() {
        double log = 0;
        for (int i = 0; i < kinds.size(); i++) {
            log += counts.get(i).doubleValue() * Math.log10(nthPrime(i));
        }
        return (long) Math.floor(log + 1e-9) + 1;
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

    /** Breaking the vault keeps what it stores on its item (see {@link Contents}), not the offered copies. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        for (int i = 1; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!kinds.isEmpty()) {
            components.set(ModRegistries.VAULT_CONTENTS.get(),
                    new Contents(List.copyOf(kinds), counts.stream().map(BigInteger::toString).toList()));
        }
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Contents contents = components.get(ModRegistries.VAULT_CONTENTS.get());
        if (contents != null) {
            kinds.clear();
            counts.clear();
            kinds.addAll(contents.kinds());
            contents.counts().forEach(c -> counts.add(new BigInteger(c)));
            while (counts.size() < kinds.size()) {
                counts.add(BigInteger.ZERO);
            }
        }
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
            slotKind[i] = -1;
        }
    }
}
