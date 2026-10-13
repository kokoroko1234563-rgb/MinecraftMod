package io.github.kokoroko1234563.axiomatic.machine;

import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Base for every machine: an inventory laid out by a {@link MachineLayout}, an energy buffer, and
 * jobs. A job is started when its inputs are present, takes its inputs at once, and pays its
 * energy cost over its duration; when paid, its outputs go to the output slots.
 *
 * <p>Display state (energy, progress, status line) is synced to clients through block entity
 * updates, so the screen reads it straight from the client's copy of this block entity.
 */
public abstract class MachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    /** A unit of work: the energy it costs, how many ticks it takes, and what it produces. */
    public record Job(long cost, int duration, List<ItemStack> outputs) {
        public Job {
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }
    }

    protected NonNullList<ItemStack> items;
    protected long energy;
    protected final long capacity;
    private Job job;
    private long paid;
    private int ticks;
    private Component status = Component.empty();
    private boolean syncNeeded;
    private java.util.concurrent.CompletableFuture<?> pending;
    private java.util.function.Function<Object, Job> pendingCommit;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, long capacity) {
        super(type, pos, state);
        this.items = NonNullList.withSize(layout().slots().size(), ItemStack.EMPTY);
        this.capacity = capacity;
    }

    /** Slots and buttons. Must not depend on instance state (it is called from the constructor). */
    public abstract MachineLayout layout();

    /**
     * Looks at the inputs and, if a job can run, takes its inputs and returns it. Returns null to
     * stay idle. Only called on the server when no job is running.
     */
    protected abstract Job tryStartJob(ServerLevel level);

    /** Called when a screen button is clicked; {@code id} indexes {@link MachineLayout#buttons()}. */
    public void onButton(ServerLevel level, Player player, int id) {}

    /** Whether this machine draws energy from cables. Generators return false. */
    public boolean acceptsEnergy() {
        return true;
    }

    // ---- Ticking --------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    protected void tick(ServerLevel level) {
        if (job == null && level.getGameTime() % 4 == 0) {
            job = tryStartJob(level);
            paid = 0;
            ticks = 0;
            if (job != null) {
                markChanged();
            }
        }
        if (job != null) {
            if (ticks < job.duration()) {
                int remainingTicks = job.duration() - ticks;
                long due = Math.ceilDiv(job.cost() - paid, remainingTicks);
                if (energy >= due) {
                    energy -= due;
                    paid += due;
                    ticks++;
                    markChanged();
                } else {
                    setStatus(Component.translatable("status.axiomatic.no_energy"));
                }
            }
            if (ticks >= job.duration() && insertOutputs(job.outputs())) {
                job = null;
                markChanged();
            }
        }
        if (syncNeeded && level.getGameTime() % 5 == 0) {
            syncNeeded = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Runs {@code work} on a worker thread ({@link Async}) and, once it is done, {@code commit}
     * on the server thread to turn its result into a job. Call it from {@link #tryStartJob}: the
     * first call starts the work and returns null; later calls return null until the work is
     * done, then return what {@code commit} returns. While work is pending, new {@code work} and
     * {@code commit} arguments are ignored, so {@code commit} must re-check that the inputs it
     * used are still there.
     */
    @SuppressWarnings("unchecked")
    protected final <T> Job runAsync(java.util.function.Supplier<T> work, java.util.function.Function<T, Job> commit) {
        if (pending == null) {
            pending = java.util.concurrent.CompletableFuture.supplyAsync(work, Async.EXECUTOR);
            pendingCommit = (java.util.function.Function<Object, Job>) commit;
            setStatus(Component.translatable("status.axiomatic.computing"));
            return null;
        }
        if (!pending.isDone()) {
            return null;
        }
        java.util.function.Function<Object, Job> c = pendingCommit;
        Object result;
        try {
            result = pending.join();
        } catch (RuntimeException e) {
            result = null;
            setStatus(Component.translatable("status.axiomatic.compute_failed"));
        } finally {
            pending = null;
            pendingCommit = null;
        }
        return result == null ? null : c.apply(result);
    }

    private Object failedKey;

    /**
     * Remembers that the inputs described by {@code key} were refused, so the machine does not
     * redo the same expensive computation every few ticks. Any change to the inputs gives a new key.
     */
    protected void rememberFailure(Object key) {
        failedKey = key;
    }

    protected boolean failedBefore(Object key) {
        return key.equals(failedKey);
    }

    /** Whether a computation started by {@link #runAsync} is still running. */
    protected boolean computing() {
        return pending != null;
    }

    /** Puts all outputs into output slots if they all fit; otherwise changes nothing. */
    protected boolean insertOutputs(List<ItemStack> outputs) {
        NonNullList<ItemStack> trial = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            trial.set(i, items.get(i).copy());
        }
        for (ItemStack out : outputs) {
            ItemStack remaining = out.copy();
            for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
                if (layout().slots().get(i).role() != MachineLayout.Role.OUTPUT) {
                    continue;
                }
                ItemStack slot = trial.get(i);
                if (slot.isEmpty()) {
                    trial.set(i, remaining);
                    remaining = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                    int move = Math.min(remaining.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(move);
                    remaining.shrink(move);
                }
            }
            if (!remaining.isEmpty()) {
                setStatus(Component.translatable("status.axiomatic.output_full"));
                return false;
            }
        }
        items = trial;
        setChanged();
        return true;
    }

    /** True if every given stack would fit into the output slots right now. */
    protected boolean outputsFit(List<ItemStack> outputs) {
        NonNullList<ItemStack> saved = items;
        boolean fits = insertOutputs(outputs);
        items = saved;
        return fits;
    }

    // ---- Energy ---------------------------------------------------------------------------

    /** Adds up to {@code amount} energy; returns how much was accepted. */
    public long receiveEnergy(long amount) {
        long accepted = Math.min(amount, capacity - energy);
        if (accepted > 0) {
            energy += accepted;
            markChanged();
        }
        return accepted;
    }

    public long energy() {
        return energy;
    }

    public long capacity() {
        return capacity;
    }

    /** Progress of the current job from 0 to 1, for the screen. */
    public float progress() {
        return job == null || job.duration() == 0 ? 0 : (float) ticks / job.duration();
    }

    /** A line shown at the top of the screen, such as the selected property or mode. */
    public Component info() {
        return Component.empty();
    }

    public Component status() {
        return status;
    }

    protected void setStatus(Component status) {
        if (!status.equals(this.status)) {
            this.status = status;
            markChanged();
        }
    }

    /** Plays one of the mod's sounds at this machine. */
    protected void playSound(java.util.function.Supplier<net.minecraft.sounds.SoundEvent> sound, float volume) {
        if (level != null) {
            level.playSound(null, worldPosition, sound.get(), net.minecraft.sounds.SoundSource.BLOCKS, volume, 1.0f);
        }
    }

    protected void markChanged() {
        syncNeeded = true;
        setChanged();
    }

    protected static AxiomaticData data(ServerLevel level) {
        return AxiomaticData.get(level.getServer());
    }

    // ---- Saving and syncing ---------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putLong("energy", energy);
        output.store("status", ComponentSerialization.CODEC, status);
        if (job != null) {
            ValueOutput j = output.child("job");
            j.putLong("cost", job.cost());
            j.putInt("duration", job.duration());
            j.putLong("paid", paid);
            j.putInt("ticks", ticks);
            ValueOutput.TypedOutputList<ItemStack> outs = j.list("outputs", ItemStack.OPTIONAL_CODEC);
            job.outputs().forEach(outs::add);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(layout().slots().size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy = input.getLongOr("energy", 0);
        status = input.read("status", ComponentSerialization.CODEC).orElse(Component.empty());
        job = input.child("job").map(j -> {
            List<ItemStack> outs = new ArrayList<>();
            j.listOrEmpty("outputs", ItemStack.OPTIONAL_CODEC).stream().forEach(outs::add);
            paid = j.getLongOr("paid", 0);
            ticks = j.getIntOr("ticks", 0);
            return new Job(j.getLongOr("cost", 0), j.getIntOr("duration", 1), outs);
        }).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Container ------------------------------------------------------------------------

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new MachineMenu(containerId, inventory, this);
    }

    /** Whether the item may go in this slot (inputs and fuel only; machines fill outputs). */
    public boolean canPlace(int slot, ItemStack stack) {
        return layout().slots().get(slot).role() != MachineLayout.Role.OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canPlace(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] all = new int[items.size()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlace(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return layout().slots().get(slot).role() == MachineLayout.Role.OUTPUT;
    }
}
