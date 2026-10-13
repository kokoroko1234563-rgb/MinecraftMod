package io.github.kokoroko1234563.axiomatic.machine;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The one menu used by every machine. Its slots follow the machine's {@link MachineLayout}. */
public class MachineMenu extends AbstractContainerMenu {
    private final MachineBlockEntity machine;
    private final int machineSlots;

    public MachineMenu(int containerId, Inventory inventory, MachineBlockEntity machine) {
        super(ModRegistries.MACHINE_MENU.get(), containerId);
        this.machine = machine;
        MachineLayout layout = machine.layout();
        this.machineSlots = layout.slots().size();
        for (int i = 0; i < machineSlots; i++) {
            MachineLayout.SlotSpec spec = layout.slots().get(i);
            int index = i;
            addSlot(new Slot(machine, i, spec.x(), spec.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return machine.canPlace(index, stack);
                }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
    }

    /** Client side: the block entity is looked up from the position the server sent. */
    public static MachineMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        var be = inventory.player.level().getBlockEntity(buf.readBlockPos());
        if (!(be instanceof MachineBlockEntity machine)) {
            throw new IllegalStateException("No machine at the menu's position");
        }
        return new MachineMenu(containerId, inventory, machine);
    }

    public MachineBlockEntity machine() {
        return machine;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= machine.layout().buttons().size()) {
            return false;
        }
        if (player.level() instanceof ServerLevel level) {
            machine.onButton(level, player, id);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine.stillValid(player);
    }
}
