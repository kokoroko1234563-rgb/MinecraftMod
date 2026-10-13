package io.github.kokoroko1234563.axiomatic;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.kokoroko1234563.axiomatic.gametest.AxiomaticGameTests;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import io.github.kokoroko1234563.axiomatic.risk.Paradox;
import io.github.kokoroko1234563.axiomatic.book.Notebook;

/**
 * Mod entry point. Game-side code (blocks, items, machines) lives in this package and its
 * subpackages; the pure-Java mathematics lives in {@code ...axiomatic.core} and must not
 * import Minecraft classes, so it can be unit-tested without the game.
 */
@Mod(Axiomatic.MODID)
public class Axiomatic {
    public static final String MODID = "axiomatic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Axiomatic(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);
        AxiomaticGameTests.register(modEventBus);
        io.github.kokoroko1234563.axiomatic.progress.Milestones.register(modEventBus);
        modEventBus.addListener(io.github.kokoroko1234563.axiomatic.energy.ForeignEnergy::register);
        modEventBus.addListener(io.github.kokoroko1234563.axiomatic.network.LibraryPayload::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, AxiomaticConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(Paradox::onCrafted);
        NeoForge.EVENT_BUS.addListener(Notebook::onLogin);
    }
}
