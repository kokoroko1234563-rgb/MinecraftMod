package io.github.kokoroko1234563.axiomatic.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import io.github.kokoroko1234563.axiomatic.item.CircuitData;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Developer tool, off unless the JVM runs with {@code -Daxiomatic.devtour=true} (Gradle:
 * {@code ./gradlew runClient -Pdevtour}). Creates a world, places the machines, opens their
 * screens and saves screenshots to {@code run/screenshots/tour_*.png}, then quits. Used to check
 * GUIs, models and textures without a person at the keyboard (for example under Xvfb).
 */
public final class DevTour {
    private DevTour() {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static boolean worldRequested;
    private static int tick;
    private static int step;

    private record Step(int delay, Consumer<Minecraft> action) {}

    /** Screenshot names to take, or null for all. */
    private static java.util.Set<String> only;

    private static boolean wanted(String name) {
        return only == null || only.contains(name);
    }

    public static void registerIfEnabled() {
        String setting = System.getProperty("axiomatic.devtour");
        if (setting == null || setting.isEmpty() || setting.equals("false")) {
            return;
        }
        only = setting.equals("true") ? null : java.util.Set.of(setting.split(","));
        Axiomatic.LOGGER.info("Axiomatic dev tour enabled");
        buildSteps();
        NeoForge.EVENT_BUS.addListener(DevTour::onTick);
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!worldRequested) {
            if (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) {
                worldRequested = true;
                String folder = "tour" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings("Axiomatic tour", GameType.SURVIVAL,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(42, false, false),
                        WorldPresets::createNormalWorldDimensions, mc.screen);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || step >= STEPS.size()) {
            return;
        }
        if (++tick < STEPS.get(step).delay()) {
            return;
        }
        tick = 0;
        Step s = STEPS.get(step++);
        try {
            s.action().accept(mc);
        } catch (RuntimeException e) {
            Axiomatic.LOGGER.error("Dev tour step {} failed", step, e);
        }
    }

    // ---- The tour -------------------------------------------------------------------------

    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final List<Block> ROW = new ArrayList<>();

    private static void buildSteps() {
        STEPS.add(new Step(100, mc -> server(mc, DevTour::stage)));
        STEPS.add(new Step(60, mc -> server(mc, server -> command(server, "tp @a 4 151 -3 50 30"))));
        STEPS.add(new Step(20, mc -> {
            mc.getToastManager().clear();
            mc.gui.getChat().clearMessages(false);
        }));
        STEPS.add(new Step(10, mc -> shot(mc, "world")));
        STEPS.add(new Step(10, mc -> server(mc, server -> command(server, "tp @a 0 150 -3 0 15"))));
        STEPS.add(new Step(10, mc -> mc.setScreen(new InventoryScreen(mc.player))));
        STEPS.add(new Step(20, mc -> hover(mc, 0)));
        STEPS.add(new Step(10, mc -> shot(mc, "inventory")));
        String[] machines = {"test_bench", "drafting_chamber", "assumption_chamber", "rewriter", "compiled_machine",
                "coal_dynamo", "structure_bench", "conjecture_bench", "zero_source", "successor", "duplicator",
                "factorization_vault", "infinite_prime_well", "network_surveyor", "equivalence_bench", "prime_forge", "certificate_sorter",
                "courier_station"};
        for (int i = 0; i < machines.length; i++) {
            int index = i;
            String name = machines[i];
            if (!wanted(name)) {
                continue;
            }
            STEPS.add(new Step(10, mc -> mc.setScreen(null)));
            STEPS.add(new Step(10, mc -> {
                mc.getToastManager().clear();
                server(mc, server -> open(server, index));
            }));
            STEPS.add(new Step(30, mc -> shot(mc, name)));
        }
        STEPS.add(new Step(10, mc -> mc.setScreen(null)));
        STEPS.add(new Step(10, mc -> mc.setScreen(new LibraryScreen(new io.github.kokoroko1234563.axiomatic.network.LibraryPayload(
                io.github.kokoroko1234563.axiomatic.block.LibraryBlock.sections(
                        io.github.kokoroko1234563.axiomatic.world.AxiomaticData.get(mc.getSingleplayerServer()).state()))))));
        STEPS.add(new Step(30, mc -> shot(mc, "library")));
        STEPS.add(new Step(10, mc -> ((LibraryScreen) mc.screen).select(1)));
        STEPS.add(new Step(20, mc -> shot(mc, "library_theorems")));
        // The Notebook (Modonomicon): the chapter map, then an entry with its recipe page.
        STEPS.add(new Step(10, mc -> mc.setScreen(null)));
        STEPS.add(new Step(10, mc -> com.klikli_dev.modonomicon.client.gui.BookGuiManager.get().openBook(
                com.klikli_dev.modonomicon.client.gui.book.BookAddress.ignoreSaved(
                        io.github.kokoroko1234563.axiomatic.book.Notebook.BOOK,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "basics"), null, -1))));
        STEPS.add(new Step(30, mc -> shot(mc, "notebook")));
        STEPS.add(new Step(10, mc -> com.klikli_dev.modonomicon.client.gui.BookGuiManager.get().openBook(
                com.klikli_dev.modonomicon.client.gui.book.BookAddress.ignoreSaved(
                        io.github.kokoroko1234563.axiomatic.book.Notebook.BOOK,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "basics"),
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "basics/power"), 0))));
        STEPS.add(new Step(30, mc -> shot(mc, "notebook_power")));
        STEPS.add(new Step(10, mc -> com.klikli_dev.modonomicon.client.gui.BookGuiManager.get().openBook(
                com.klikli_dev.modonomicon.client.gui.book.BookAddress.ignoreSaved(
                        io.github.kokoroko1234563.axiomatic.book.Notebook.BOOK,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "primes"),
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "primes/prime_cores"), 0))));
        STEPS.add(new Step(30, mc -> shot(mc, "notebook_prime_cores")));
        STEPS.add(new Step(10, mc -> com.klikli_dev.modonomicon.client.gui.BookGuiManager.get().openBook(
                com.klikli_dev.modonomicon.client.gui.book.BookAddress.ignoreSaved(
                        io.github.kokoroko1234563.axiomatic.book.Notebook.BOOK,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "factory"),
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(Axiomatic.MODID, "factory/factories"), 0))));
        STEPS.add(new Step(20, mc -> hoverSlotAt(mc, 0.61, 0.255)));
        STEPS.add(new Step(30, mc -> shot(mc, "notebook_factories")));

        STEPS.add(new Step(10, mc -> mc.setScreen(null)));
        STEPS.add(new Step(20, mc -> {
            Axiomatic.LOGGER.info("Dev tour finished");
            mc.stop();
        }));
    }

    private static void server(Minecraft mc, Consumer<MinecraftServer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server));
    }

    private static void command(MinecraftServer server, String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    /** Builds a platform with every machine in a row and fills the player's inventory. */
    private static void stage(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // A couple of named theorems for the Library shot: one from a definition, one conjectural.
        var math = io.github.kokoroko1234563.axiomatic.world.AxiomaticData.get(server).state();
        var kernel = math.kernel();
        if (math.library().theorem("add_zero") == null) {
            math.library().name("add_zero", kernel.definingStatements("add").get(0));
            var n = io.github.kokoroko1234563.axiomatic.core.logic.Term.var("n");
            math.library().name("twin_hope", kernel.conjecture("twin_hope",
                    io.github.kokoroko1234563.axiomatic.core.logic.Formula.pred("Prime",
                            io.github.kokoroko1234563.axiomatic.core.logic.Term.app("add", n, n))));
        }
        command(server, "time set day");
        command(server, "gamerule doDaylightCycle false");
        command(server, "fill -6 149 -4 6 149 4 minecraft:smooth_stone");
        ROW.clear();
        ROW.addAll(List.of(ModRegistries.TEST_BENCH.get(), ModRegistries.DRAFTING_CHAMBER.get(),
                ModRegistries.ASSUMPTION_CHAMBER.get(), ModRegistries.REWRITER.get(), ModRegistries.COMPILED_MACHINE.get(),
                ModRegistries.COAL_DYNAMO.get(), ModRegistries.STRUCTURE_BENCH.get(), ModRegistries.CONJECTURE_BENCH.get(),
                ModRegistries.ZERO_SOURCE.get(), ModRegistries.SUCCESSOR.get(), ModRegistries.DUPLICATOR.get(),
                ModRegistries.FACTORIZATION_VAULT.get(), ModRegistries.PRIME_WELL.get(), ModRegistries.NETWORK_SURVEYOR.get(),
                ModRegistries.EQUIVALENCE_BENCH.get(), ModRegistries.PRIME_FORGE.get(),
                ModRegistries.CERTIFICATE_SORTER.get(), ModRegistries.COURIER_STATION.get()));
        for (int i = 0; i < ROW.size(); i++) {
            level.setBlockAndUpdate(position(i), ROW.get(i).defaultBlockState());
        }
        level.setBlockAndUpdate(ORIGIN.offset(-5, 1, 3), ModRegistries.COPPER_CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(-3, 1, 3), ModRegistries.LIBRARY.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(-1, 1, 3), ModRegistries.STATEMENT_CONVEYOR.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(1, 1, 3), ModRegistries.CONTAINMENT_CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(3, 1, 3), ModRegistries.CONJECTURE_BLOCK.get().defaultBlockState());
        // A cable network in front of the row: from the dynamo at (0, 3) toward the player, a cross
        // with a riser, and two phase-B cables, so the world shot shows how cables join up.
        List<BlockPos> cables = new java.util.ArrayList<>();
        for (int z = 0; z <= 2; z++) {
            cables.add(ORIGIN.offset(0, 0, z));
        }
        for (int x = -2; x <= 2; x++) {
            if (x != 0) {
                cables.add(ORIGIN.offset(x, 0, 0));
            }
        }
        cables.add(ORIGIN.offset(2, 1, 0));
        for (BlockPos p : cables) {
            boolean phaseB = p.getX() == ORIGIN.getX() + 1 || p.getZ() == ORIGIN.getZ() + 1;
            level.setBlockAndUpdate(p, ModRegistries.COPPER_CABLE.get().defaultBlockState()
                    .setValue(io.github.kokoroko1234563.axiomatic.energy.CableBlock.PHASE, phaseB));
        }
        for (BlockPos p : cables) {
            level.setBlockAndUpdate(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                    level.getBlockState(p), level, p));
        }
        command(server, "tp @a 0 150 -3 0 15");

        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        var state = AxiomaticData.get(server).state();
        Printer printer = new Printer(state.signature());
        Term n = Term.var("n");
        NumeralData twelve = NumeralData.of(12).withFact(new Fact("Even", List.of(java.math.BigInteger.valueOf(12)),
                java.util.Set.of()));
        give(player, NumeralItem.stack(twelve, 4));
        give(player, NumeralItem.stack(NumeralData.of(7), 1));
        ItemStack variable = new ItemStack(ModRegistries.VARIABLE_ITEM.get(), 8);
        give(player, variable);
        Term addZn = Term.app("add", Term.zero(), n);
        give(player, MathItems.circuitStack(new CircuitData(addZn, printer.term(addZn))));
        give(player, MathItems.statementStack(StatementData.of(state.kernel().definingStatements("add").get(1), printer)));
        give(player, MathItems.statementStack(StatementData.unproven(Formula.eq(addZn, n), printer)));
        give(player, new ItemStack(ModRegistries.CIRCUIT_BOARD.get(), 3));
        give(player, new ItemStack(ModRegistries.PRIME_CORE.get()));
        give(player, new ItemStack(ModRegistries.GRAPH_LENS.get()));
        give(player, new ItemStack(ModRegistries.TABLET_SUCC_INJECTIVE.get()));
        give(player, new ItemStack(ModRegistries.PARADOX_JUNK.get()));
        give(player, new ItemStack(Items.COAL, 16));
        give(player, new ItemStack(ModRegistries.PERFECT_CORE.get()));
        for (int i = 0; i < ROW.size(); i++) {
            give(player, new ItemStack(ROW.get(i)));
        }
        // Fill some machines so their screens show something.
        if (level.getBlockEntity(ORIGIN.offset(-5, 0, 3)) instanceof MachineBlockEntity bench) {
            bench.setItem(0, NumeralItem.stack(NumeralData.of(12), 1));
            bench.setItem(2, NumeralItem.stack(NumeralData.of(6), 1));
        }
        if (level.getBlockEntity(ORIGIN.offset(-4, 0, 3)) instanceof MachineBlockEntity drafting) {
            drafting.setItem(0, MathItems.circuitStack(new CircuitData(addZn, printer.term(addZn))));
            drafting.setItem(1, variable.copyWithCount(1));
        }
        if (level.getBlockEntity(ORIGIN.offset(-3, 0, 3)) instanceof MachineBlockEntity chamber) {
            chamber.receiveEnergy(1000);
            chamber.setItem(0, MathItems.statementStack(StatementData.unproven(Formula.eq(addZn, n), printer)));
        }
        if (level.getBlockEntity(ORIGIN.offset(0, 0, 3)) instanceof MachineBlockEntity dynamo) {
            dynamo.setItem(0, new ItemStack(Items.COAL, 5));
        }
        if (level.getBlockEntity(position(11)) instanceof MachineBlockEntity vault) {
            vault.setItem(0, new ItemStack(Items.COAL, 5));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
    }

    /** Machines stand in two rows of eleven in front of the player. */
    private static BlockPos position(int i) {
        return i < 11 ? ORIGIN.offset(i - 5, 0, 3) : ORIGIN.offset(i - 13, 0, 4);
    }

    private static void open(MinecraftServer server, int index) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        BlockPos pos = position(index);
        if (server.overworld().getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            player.openMenu(machine, pos);
        }
    }

    /** Moves the mouse over a slot of the open inventory screen so its tooltip shows. */
    private static void hover(Minecraft mc, int hotbarSlot) {
        if (!(mc.screen instanceof InventoryScreen)) {
            return;
        }
        double scale = mc.getWindow().getGuiScale();
        int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
        int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
        // Main inventory first row (where the first given items land after the hotbar fills).
        double x = (left + 8 + 18 * hotbarSlot + 8) * scale;
        double y = (top + 142 + 8) * scale;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(), x, y);
    }

    /** Moves the mouse to a point given as fractions of the window, so a tooltip shows. */
    private static void hoverSlotAt(Minecraft mc, double fx, double fy) {
        var window = mc.getWindow();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.handle(), window.getScreenWidth() * fx, window.getScreenHeight() * fy);
    }

    private static void shot(Minecraft mc, String name) {
        if (!wanted(name)) {
            return;
        }
        Screenshot.grab(mc.gameDirectory, "tour_" + name + ".png", mc.getMainRenderTarget(), 1,
                message -> Axiomatic.LOGGER.info("Dev tour screenshot {}: {}", name, message.getString()));
    }
}
