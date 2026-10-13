package io.github.kokoroko1234563.axiomatic.registry;

import io.github.kokoroko1234563.axiomatic.energy.ForeignEnergy;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * NeoForge capabilities, so other mods can work with Axiomatic machines: their generators can
 * power them ({@link ForeignEnergy}) and their pipes can move items in and out, with the same
 * per-face rules as hoppers.
 */
public final class ModCapabilities {
    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        ForeignEnergy.register(event);
        for (ModRegistries.Machine<?> m : ModRegistries.MACHINES) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, m.type(), WorldlyContainerWrapper::new);
        }
    }
}
