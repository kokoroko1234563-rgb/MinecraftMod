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

    /** Machines built from risky parts say so, like the parts themselves (RiskyItem). */
    @SubscribeEvent
    static void riskTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var stack = event.getItemStack();
        java.util.List<String> deps = stack.get(ModRegistries.DEPENDS.get());
        if (deps != null && !deps.isEmpty() && !(stack.getItem() instanceof io.github.kokoroko1234563.axiomatic.item.RiskyItem)) {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.axiomatic.depends", deps.size())
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
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
