package io.github.kokoroko1234563.axiomatic.machine;

import java.util.List;

/**
 * Where a machine's slots and buttons go in its screen, and what each slot is for. Shared by the
 * server menu and the client screen, so both agree on slot order.
 */
public record MachineLayout(List<SlotSpec> slots, List<String> buttons) {
    public enum Role {
        /** Accepts items from players and from hoppers above or beside. */
        INPUT,
        /** Only the machine puts items here; hoppers below may take them. */
        OUTPUT,
        /** Items the machine burns or consumes over time, such as coal. */
        FUEL
    }

    /** @param label short hint drawn next to the slot, a translation key or null */
    public record SlotSpec(int x, int y, Role role, String label) {}

    public MachineLayout {
        slots = List.copyOf(slots);
        buttons = List.copyOf(buttons);
    }

    public static SlotSpec input(int x, int y, String label) {
        return new SlotSpec(x, y, Role.INPUT, label);
    }

    public static SlotSpec output(int x, int y, String label) {
        return new SlotSpec(x, y, Role.OUTPUT, label);
    }

    public static SlotSpec fuel(int x, int y, String label) {
        return new SlotSpec(x, y, Role.FUEL, label);
    }
}
