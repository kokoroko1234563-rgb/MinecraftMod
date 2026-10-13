package io.github.kokoroko1234563.axiomatic.client;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Axiomatic.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Axiomatic.MODID, value = Dist.CLIENT)
public class AxiomaticClient {
    public AxiomaticClient() {
        DevTour.registerIfEnabled();
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.MACHINE_MENU.get(), MachineScreen::new);
    }

    @SubscribeEvent
    static void registerPayloads(net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) {
        event.register(io.github.kokoroko1234563.axiomatic.network.LibraryPayload.TYPE,
                (payload, context) -> net.minecraft.client.Minecraft.getInstance().setScreen(new LibraryScreen(payload)));
    }
}
