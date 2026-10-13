package io.github.kokoroko1234563.axiomatic.gametest;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world tests, run with {@code ./gradlew runGameTestServer}. Each test builds a small setup
 * on an empty stage and checks what the machines produce.
 */
public final class AxiomaticGameTests {
    private AxiomaticGameTests() {}

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Axiomatic.MODID);
    private static final List<String> NAMES = new ArrayList<>();

    static {
        test("successor_counts_up", AxiomaticGameTests::successorCountsUp);
        test("zero_source_makes_zero", AxiomaticGameTests::zeroSourceMakesZero);
        test("test_bench_certifies_evens", AxiomaticGameTests::testBenchCertifiesEvens);
        test("duplicator_copies_certificates", AxiomaticGameTests::duplicatorCopiesCertificates);
        test("circuit_board_needs_certified_evens", AxiomaticGameTests::circuitBoardNeedsCertifiedEvens);
        test("zero_add_by_machines", ProofGameTests::zeroAddByMachines);
        test("double_outputs_evens_by_machines", ProofGameTests::doubleOutputsEvensByMachines);
        test("two_n_plus_one_trap", TrapGameTests::twoNPlusOneTrap);
        test("prime_forge_finds_primes", TrapGameTests::primeForgeFindsPrimes);
        test("prime_forge_catches_a_bad_method", TrapGameTests::primeForgeCatchesABadMethod);
        test("theorem_card_certifies_outputs", TrapGameTests::theoremCardCertifiesOutputs);
        test("test_bench_range_mode", AxiomaticGameTests::testBenchRangeMode);
        test("structure_bench_certify_and_apply", StructureGameTests::certifyAndApply);
        test("statement_conveyor_moves_statements", AxiomaticGameTests::conveyorMovesStatements);
        test("even_add_by_machines", EvenAddGameTest::evenAdd);
        test("milestones_award_advancements", AxiomaticGameTests::milestonesAwardAdvancements);
        test("surveyor_certifies_tree_networks", AxiomaticGameTests::surveyorCertifiesTrees);
        test("surveyor_rejects_cycles", AxiomaticGameTests::surveyorRejectsCycles);
        test("prime_well_emits_primes", AxiomaticGameTests::primeWellEmitsPrimes);
        test("factorization_vault_stores_as_a_number", AxiomaticGameTests::vaultStoresAsANumber);
        test("mersenne_and_the_perfect_core", AxiomaticGameTests::mersenneAndThePerfectCore);
        test("foreign_energy_powers_machines", AxiomaticGameTests::foreignEnergyPowersMachines);
        test("rewriter_uses_certified_equivalences", EquivalenceGameTest::rewritesUnderSuccessor);
        test("two_coloured_network_runs_alternating", AxiomaticGameTests::twoColouredNetworkRunsAlternating);
        test("clashing_phases_short_circuit", AxiomaticGameTests::clashingPhasesShortCircuit);
        test("add_comm_speed_card", SpeedCardGameTest::addCommSwapsInputs);
        test("conjectured_speed_card_refused", SpeedCardGameTest::conjecturedSymmetryIsRefused);
        test("certificate_sorter_splits_by_property", AxiomaticGameTests::sorterSplitsByProperty);
        test("courier_runs_on_eulerian_networks", AxiomaticGameTests::courierRunsOnEulerianNetworks);
        test("courier_refuses_odd_networks", AxiomaticGameTests::courierRefusesOddNetworks);
        test("pipes_see_machine_inventories", AxiomaticGameTests::pipesSeeMachineInventories);
        test("notebook_pages_unlock", AxiomaticGameTests::notebookPagesUnlock);
        test("dynamo_judges_each_network", AxiomaticGameTests::dynamoJudgesEachNetwork);
        test("breaking_an_unpaid_job_drops_nothing", AxiomaticGameTests::breakingAnUnpaidJobDropsNothing);
        test("vault_keeps_contents_when_broken", AxiomaticGameTests::vaultKeepsContentsWhenBroken);
    }

    private static void test(String name, Consumer<GameTestHelper> body) {
        FUNCTIONS.register(name, () -> body);
        NAMES.add(name);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(AxiomaticGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(Axiomatic.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (String name : NAMES) {
            Identifier id = Identifier.fromNamespaceAndPath(Axiomatic.MODID, name);
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id);
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(env,
                    Identifier.fromNamespaceAndPath(Axiomatic.MODID, "empty_stage"), 1200, 0, true);
            event.registerTest(id, new FunctionGameTestInstance(fn, data));
        }
    }

    // ---- Helpers --------------------------------------------------------------------------

    private static final BlockPos DYNAMO = new BlockPos(1, 1, 1);
    private static final BlockPos CABLE = new BlockPos(2, 1, 1);
    private static final BlockPos MACHINE = new BlockPos(3, 1, 1);

    /** A Coal Dynamo with coal, a cable, and the given machine at {@link #MACHINE}. */
    private static <T extends MachineBlockEntity> T powered(GameTestHelper h, net.minecraft.world.level.block.Block machine,
            Class<T> type) {
        h.setBlock(DYNAMO, ModRegistries.COAL_DYNAMO.get());
        h.setBlock(CABLE, ModRegistries.COPPER_CABLE.get());
        h.setBlock(MACHINE, machine);
        MachineBlockEntity dynamo = h.getBlockEntity(DYNAMO, MachineBlockEntity.class);
        dynamo.setItem(0, new ItemStack(Items.COAL, 4));
        return h.getBlockEntity(MACHINE, type);
    }

    private static NumeralData numeralIn(MachineBlockEntity m, int slot) {
        ItemStack stack = m.getItem(slot);
        return stack.is(ModRegistries.NUMERAL_ITEM.get()) ? NumeralItem.data(stack) : null;
    }

    // ---- Tests ----------------------------------------------------------------------------

    /** Other mods' generators can insert through the energy capability; aborted inserts undo. */
    private static void foreignEnergyPowersMachines(GameTestHelper h) {
        h.setBlock(MACHINE, ModRegistries.SUCCESSOR.get());
        h.setBlock(DYNAMO, ModRegistries.COAL_DYNAMO.get());
        MachineBlockEntity successor = h.getBlockEntity(MACHINE, MachineBlockEntity.class);
        var level = h.getLevel();
        var cap = net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK;
        var handler = level.getCapability(cap, h.absolutePos(MACHINE), null);
        h.assertTrue(handler != null, "machines expose the energy capability");
        h.assertTrue(level.getCapability(cap, h.absolutePos(DYNAMO), null) == null, "generators do not take energy");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertValueEqual(handler.insert(41, tx), 41, "4 FE per unit, the remainder kept as credit");
        }
        h.assertValueEqual(successor.energy(), 0L, "an aborted insert is undone");
        for (int i = 0; i < 4; i++) {
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                handler.insert(1, tx);
                tx.commit();
            }
        }
        h.assertValueEqual(successor.energy(), 1L, "a 1 FE trickle adds up to a unit");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            handler.insert(400, tx);
            tx.commit();
        }
        h.assertValueEqual(successor.energy(), 101L, "a committed insert stays");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertValueEqual(handler.extract(100, tx), 0, "Axiomatic energy cannot be pulled out");
        }
        successor.setItem(0, NumeralItem.stack(NumeralData.of(4), 1));
        h.succeedWhen(() -> h.assertTrue(numeralIn(successor, 1) != null, "the machine ran on foreign energy"));
    }

    private static void successorCountsUp(GameTestHelper h) {
        MachineBlockEntity successor = powered(h, ModRegistries.SUCCESSOR.get(), MachineBlockEntity.class);
        successor.setItem(0, NumeralItem.stack(NumeralData.of(4), 3));
        h.succeedWhen(() -> {
            NumeralData out = numeralIn(successor, 1);
            h.assertTrue(out != null && successor.getItem(1).getCount() == 3, "expected three outputs");
            h.assertValueEqual(out.value(), BigInteger.valueOf(5), "output value");
        });
    }

    private static void zeroSourceMakesZero(GameTestHelper h) {
        MachineBlockEntity zero = powered(h, ModRegistries.ZERO_SOURCE.get(), MachineBlockEntity.class);
        h.succeedWhen(() -> {
            NumeralData out = numeralIn(zero, 0);
            h.assertTrue(out != null && out.value().signum() == 0, "expected numeral 0");
        });
    }

    private static void testBenchCertifiesEvens(GameTestHelper h) {
        MachineBlockEntity bench = powered(h, ModRegistries.TEST_BENCH.get(), MachineBlockEntity.class);
        bench.setItem(0, NumeralItem.stack(NumeralData.of(12), 1));
        h.succeedWhen(() -> {
            NumeralData out = numeralIn(bench, 1);
            h.assertTrue(out != null, "expected an output");
            h.assertTrue(out.hasProperty("Even", false), "12 should be certified Even");
        });
    }

    private static void duplicatorCopiesCertificates(GameTestHelper h) {
        MachineBlockEntity dup = powered(h, ModRegistries.DUPLICATOR.get(), MachineBlockEntity.class);
        NumeralData even = NumeralData.of(6)
                .withFact(new Fact("Even", List.of(BigInteger.valueOf(6)), java.util.Set.of()));
        dup.setItem(0, NumeralItem.stack(even, 1));
        h.succeedWhen(() -> {
            int copies = dup.getItem(1).getCount() + dup.getItem(2).getCount();
            h.assertValueEqual(copies, 2, "copies");
            NumeralData a = numeralIn(dup, 1);
            h.assertTrue(a != null && a.hasProperty("Even", false), "copies keep certificates");
        });
    }

    private static void testBenchRangeMode(GameTestHelper h) {
        MachineBlockEntity bench = powered(h, ModRegistries.TEST_BENCH.get(), MachineBlockEntity.class);
        var state = io.github.kokoroko1234563.axiomatic.world.AxiomaticData.get(h.getLevel().getServer()).state();
        var printer = new io.github.kokoroko1234563.axiomatic.core.logic.Printer(state.signature());
        var a = io.github.kokoroko1234563.axiomatic.core.logic.Term.var("a");
        var b = io.github.kokoroko1234563.axiomatic.core.logic.Term.var("b");
        var comm = io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq(
                io.github.kokoroko1234563.axiomatic.core.logic.Term.app("add", a, b),
                io.github.kokoroko1234563.axiomatic.core.logic.Term.app("add", b, a));
        bench.setItem(0, io.github.kokoroko1234563.axiomatic.item.MathItems.statementStack(
                io.github.kokoroko1234563.axiomatic.item.StatementData.unproven(comm, printer)));
        bench.setItem(2, NumeralItem.stack(NumeralData.of(6), 1));
        h.succeedWhen(() -> h.assertTrue(state.library().status(comm)
                instanceof io.github.kokoroko1234563.axiomatic.core.knowledge.Library.Tested t
                && t.upTo().intValue() == 6, "a + b = b + a should be Tested up to 6; status: " + bench.status().getString()));
    }

    private static void conveyorMovesStatements(GameTestHelper h) {
        BlockPos from = new BlockPos(1, 1, 5);
        BlockPos conveyor = new BlockPos(2, 1, 5);
        BlockPos to = new BlockPos(3, 1, 5);
        h.setBlock(from, net.minecraft.world.level.block.Blocks.CHEST);
        h.setBlock(to, net.minecraft.world.level.block.Blocks.CHEST);
        h.setBlock(conveyor, ModRegistries.STATEMENT_CONVEYOR.get().defaultBlockState()
                .setValue(io.github.kokoroko1234563.axiomatic.block.StatementConveyorBlock.FACING,
                        h.getTestRotation().rotate(net.minecraft.core.Direction.EAST)));
        var source = h.getBlockEntity(from, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        var target = h.getBlockEntity(to, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        var state = io.github.kokoroko1234563.axiomatic.world.AxiomaticData.get(h.getLevel().getServer()).state();
        var printer = new io.github.kokoroko1234563.axiomatic.core.logic.Printer(state.signature());
        source.setItem(0, io.github.kokoroko1234563.axiomatic.item.MathItems.statementStack(
                io.github.kokoroko1234563.axiomatic.item.StatementData.unproven(
                        io.github.kokoroko1234563.axiomatic.core.logic.Formula.eq(
                                io.github.kokoroko1234563.axiomatic.core.logic.Term.zero(),
                                io.github.kokoroko1234563.axiomatic.core.logic.Term.zero()), printer)));
        source.setItem(1, new ItemStack(Items.DIRT));
        h.succeedWhen(() -> {
            h.assertTrue(target.getItem(0).is(ModRegistries.STATEMENT_ITEM.get()), "the statement should arrive");
            h.assertTrue(source.getItem(1).is(Items.DIRT), "other items stay");
        });
    }

    /** A fresh player's Notebook lacks the gated pages; earning "Q.E.D." adds its pages. */
    // Advancements need a ServerPlayer; GameTestHelper has no non-deprecated way to make one yet.
    @SuppressWarnings("removal")
    private static void notebookPagesUnlock(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, io.github.kokoroko1234563.axiomatic.book.Notebook.create());
        io.github.kokoroko1234563.axiomatic.book.Notebook.refresh(player);
        var content = player.getInventory().getItem(0).get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        int before = content.pages().size();
        h.assertTrue(before < io.github.kokoroko1234563.axiomatic.book.Notebook.PAGES, "gated pages are hidden: " + before);
        var server = h.getLevel().getServer();
        var proven = server.getAdvancements().get(Identifier.fromNamespaceAndPath(Axiomatic.MODID, "proven"));
        player.getAdvancements().award(proven, "proven");
        int after = player.getInventory().getItem(0).get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT)
                .pages().size();
        h.assertValueEqual(after, before + 6, "Q.E.D. unlocks six pages");
        h.succeed();
    }

    // Advancements need a ServerPlayer; GameTestHelper has no non-deprecated way to make one yet.
    @SuppressWarnings("removal")
    private static void milestonesAwardAdvancements(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 3)));
        powered(h, ModRegistries.ZERO_SOURCE.get(), MachineBlockEntity.class);
        var server = h.getLevel().getServer();
        var zero = server.getAdvancements().get(Identifier.fromNamespaceAndPath(Axiomatic.MODID, "zero"));
        h.assertTrue(zero != null, "the zero advancement should load");
        h.succeedWhen(() -> h.assertTrue(player.getAdvancements().getOrStartProgress(zero).isDone(),
                "making a 0 near the player should award 'Zero'"));
    }

    /** Dynamo — cable — cable — Successor, with a surveyor on the first cable. */
    private static void network(GameTestHelper h, boolean withLoop) {
        h.setBlock(new BlockPos(1, 1, 1), ModRegistries.COAL_DYNAMO.get());
        h.setBlock(new BlockPos(2, 1, 1), ModRegistries.COPPER_CABLE.get());
        h.setBlock(new BlockPos(3, 1, 1), ModRegistries.COPPER_CABLE.get());
        h.setBlock(new BlockPos(4, 1, 1), ModRegistries.SUCCESSOR.get());
        h.setBlock(new BlockPos(2, 2, 1), ModRegistries.NETWORK_SURVEYOR.get());
        if (withLoop) {
            h.setBlock(new BlockPos(2, 1, 2), ModRegistries.COPPER_CABLE.get());
            h.setBlock(new BlockPos(3, 1, 2), ModRegistries.COPPER_CABLE.get());
        }
        h.getBlockEntity(new BlockPos(1, 1, 1), MachineBlockEntity.class).setItem(0, new ItemStack(Items.COAL, 4));
    }

    /**
     * Dynamo at (1,1,1) with a Surveyor on top; cables A at (2,1,1), B at (3,1,1) and (2,2,1); a
     * Successor at (4,1,1). With {@code clash}, a B cable at (3,2,1) touches two B cables.
     */
    private static io.github.kokoroko1234563.axiomatic.machine.CoalDynamoBlockEntity phased(GameTestHelper h, boolean clash) {
        var cable = ModRegistries.COPPER_CABLE.get().defaultBlockState();
        var b = cable.setValue(io.github.kokoroko1234563.axiomatic.energy.CableBlock.PHASE, true);
        h.setBlock(new BlockPos(1, 1, 1), ModRegistries.COAL_DYNAMO.get());
        h.setBlock(new BlockPos(2, 1, 1), cable);
        h.setBlock(new BlockPos(3, 1, 1), b);
        h.setBlock(new BlockPos(2, 2, 1), b);
        if (clash) {
            h.setBlock(new BlockPos(3, 2, 1), b);
        }
        h.setBlock(new BlockPos(4, 1, 1), ModRegistries.SUCCESSOR.get());
        h.setBlock(new BlockPos(1, 2, 1), ModRegistries.NETWORK_SURVEYOR.get());
        var dynamo = h.getBlockEntity(new BlockPos(1, 1, 1), io.github.kokoroko1234563.axiomatic.machine.CoalDynamoBlockEntity.class);
        dynamo.setItem(0, new ItemStack(Items.COAL, 4));
        h.getBlockEntity(new BlockPos(4, 1, 1), MachineBlockEntity.class).setItem(0, NumeralItem.stack(NumeralData.of(4), 1));
        return dynamo;
    }

    private static void twoColouredNetworkRunsAlternating(GameTestHelper h) {
        var dynamo = phased(h, false);
        var successor = h.getBlockEntity(new BlockPos(4, 1, 1), MachineBlockEntity.class);
        var level = h.getLevel();
        h.succeedWhen(() -> {
            var graph = io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork.graph(level, h.absolutePos(new BlockPos(1, 1, 1)));
            h.assertTrue(graph.alternating() && graph.twoColoured(), "phases A and B alternate");
            h.assertTrue(io.github.kokoroko1234563.axiomatic.world.NetworkData.get(level).isCertifiedBipartite(graph.signature()),
                    "the surveyor touching the dynamo certifies the colouring");
            h.assertTrue(dynamo.alternating() && !dynamo.shorted(), "the dynamo runs alternating power");
            h.assertTrue(numeralIn(successor, 1) != null, "the successor ran on it");
        });
    }

    /** Copying a million costs a million units: mining the Duplicator mid-job must not hand out the copies. */
    private static void breakingAnUnpaidJobDropsNothing(GameTestHelper h) {
        h.setBlock(MACHINE, ModRegistries.DUPLICATOR.get());
        MachineBlockEntity dup = h.getBlockEntity(MACHINE, MachineBlockEntity.class);
        dup.receiveEnergy(1_000);
        dup.setItem(0, NumeralItem.stack(NumeralData.of(1_000_000), 1));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(dup.getItem(0).isEmpty(), "the job started"))
                .thenExecute(() -> h.getLevel().destroyBlock(h.absolutePos(MACHINE), true))
                .thenExecuteAfter(5, () -> {
                    boolean copies = h.getEntities(net.minecraft.world.entity.EntityType.ITEM, MACHINE, 3).stream()
                            .anyMatch(e -> e.getItem().is(ModRegistries.NUMERAL_ITEM.get()));
                    h.assertFalse(copies, "an unpaid copy must not drop");
                })
                .thenSucceed();
    }

    /** Broken, the vault drops itself with what it stores, like a shulker box. */
    private static void vaultKeepsContentsWhenBroken(GameTestHelper h) {
        h.setBlock(MACHINE, ModRegistries.FACTORIZATION_VAULT.get());
        MachineBlockEntity vault = h.getBlockEntity(MACHINE, MachineBlockEntity.class);
        vault.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(vault.getItem(0).isEmpty(), "deposited"))
                .thenExecute(() -> vault.setItem(0, new ItemStack(Items.COBBLESTONE, 36)))
                .thenWaitUntil(() -> h.assertTrue(vault.getItem(0).isEmpty(), "deposited again"))
                .thenExecute(() -> h.getLevel().destroyBlock(h.absolutePos(MACHINE), true))
                .thenExecuteAfter(5, () -> {
                    var drops = h.getEntities(net.minecraft.world.entity.EntityType.ITEM, MACHINE, 3);
                    var vaultItem = drops.stream().map(e -> e.getItem())
                            .filter(st -> st.is(ModRegistries.FACTORIZATION_VAULT.get().asItem())).findFirst();
                    h.assertTrue(vaultItem.isPresent(), "the vault drops itself");
                    var contents = vaultItem.get().get(ModRegistries.VAULT_CONTENTS.get());
                    h.assertTrue(contents != null && contents.counts().equals(List.of("100")), "keeps 100 cobblestone: " + contents);
                    h.assertFalse(drops.stream().anyMatch(e -> e.getItem().is(Items.COBBLESTONE)), "nothing spills");
                })
                .thenSucceed();
    }

    /** A second, plain network on the dynamo's other side does not spoil the first one's certificate. */
    private static void dynamoJudgesEachNetwork(GameTestHelper h) {
        var dynamo = phased(h, false);
        h.setBlock(new BlockPos(1, 1, 0), ModRegistries.COPPER_CABLE.get());
        h.setBlock(new BlockPos(0, 1, 0), ModRegistries.COPPER_CABLE.get());
        var level = h.getLevel();
        h.succeedWhen(() -> {
            h.assertValueEqual(io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork.networks(level,
                    h.absolutePos(new BlockPos(1, 1, 1))).size(), 2, "the dynamo touches two networks");
            h.assertTrue(dynamo.alternating() && !dynamo.shorted(), "the two-coloured network runs alternating power");
        });
    }

    private static void clashingPhasesShortCircuit(GameTestHelper h) {
        var dynamo = phased(h, true);
        var successor = h.getBlockEntity(new BlockPos(4, 1, 1), MachineBlockEntity.class);
        var level = h.getLevel();
        h.runAfterDelay(120, () -> {
            var graph = io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork.graph(level, h.absolutePos(new BlockPos(1, 1, 1)));
            h.assertTrue(graph.alternating() && !graph.twoColoured(), "two B cables touch");
            h.assertFalse(io.github.kokoroko1234563.axiomatic.world.NetworkData.get(level).isCertifiedBipartite(graph.signature()),
                    "a clashing colouring is not certified");
            h.assertTrue(dynamo.shorted(), "the dynamo is shorted");
            h.assertValueEqual(successor.energy(), 0L, "nothing reaches the machine over the cables");
            h.assertTrue(h.getBlockEntity(new BlockPos(1, 2, 1), MachineBlockEntity.class).status().getString().contains("clash"),
                    "the surveyor names the clash");
            h.succeed();
        });
    }

    /** Other mods' pipes use the item capability, with the same rules as hoppers. */
    private static void pipesSeeMachineInventories(GameTestHelper h) {
        h.setBlock(MACHINE, ModRegistries.SUCCESSOR.get());
        MachineBlockEntity successor = h.getBlockEntity(MACHINE, MachineBlockEntity.class);
        var handler = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                h.absolutePos(MACHINE), net.minecraft.core.Direction.UP);
        h.assertTrue(handler != null, "machines expose the item capability");
        h.assertTrue(h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                h.absolutePos(MACHINE), null) == null, "not without a face, which would skip the take rules");
        var numeral = net.neoforged.neoforge.transfer.item.ItemResource.of(NumeralItem.stack(NumeralData.of(4), 1));
        var dirt = net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIRT);
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertValueEqual(handler.insert(dirt, 1, tx), 0, "machines refuse what they cannot use");
            h.assertValueEqual(handler.insert(numeral, 2, tx), 2, "numerals go into the input");
            tx.commit();
        }
        h.assertValueEqual(successor.getItem(0).getCount(), 2, "the input holds the piped numerals");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertValueEqual(handler.extract(numeral, 1, tx), 0, "inputs cannot be pulled out");
        }
        successor.setItem(1, NumeralItem.stack(NumeralData.of(9), 1));
        var nine = net.neoforged.neoforge.transfer.item.ItemResource.of(NumeralItem.stack(NumeralData.of(9), 1));
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertValueEqual(handler.extract(nine, 1, tx), 1, "outputs can be pulled out");
            tx.commit();
        }
        h.assertTrue(successor.getItem(1).isEmpty(), "the output was taken");
        h.succeed();
    }

    /** Certified evens drop into the hopper below; the rest are pushed into the chest beside. */
    private static void sorterSplitsByProperty(GameTestHelper h) {
        BlockPos sorterPos = new BlockPos(2, 2, 2);
        h.setBlock(sorterPos, ModRegistries.CERTIFICATE_SORTER.get());
        h.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.HOPPER);
        h.setBlock(new BlockPos(3, 2, 2), net.minecraft.world.level.block.Blocks.CHEST);
        MachineBlockEntity sorter = h.getBlockEntity(sorterPos, MachineBlockEntity.class);
        sorter.receiveEnergy(sorter.capacity());
        var state = io.github.kokoroko1234563.axiomatic.world.AxiomaticData.get(h.getLevel().getServer()).state();
        var printer = new io.github.kokoroko1234563.axiomatic.core.logic.Printer(state.signature());
        sorter.setItem(1, io.github.kokoroko1234563.axiomatic.item.MathItems.statementStack(
                io.github.kokoroko1234563.axiomatic.item.StatementData.unproven(
                        io.github.kokoroko1234563.axiomatic.core.logic.Formula.pred("Even",
                                io.github.kokoroko1234563.axiomatic.core.logic.Term.var("n")), printer)));
        NumeralData even = NumeralData.of(6).withFact(new Fact("Even", List.of(BigInteger.valueOf(6)), java.util.Set.of()));
        NumeralData plain = NumeralData.of(8);  // even, but not certified: it goes to the side
        sorter.setItem(0, NumeralItem.stack(even, 2));
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getBlockEntity(new BlockPos(2, 1, 2),
                net.minecraft.world.level.block.entity.HopperBlockEntity.class);
        var chest = h.getBlockEntity(new BlockPos(3, 2, 2), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(sorter.getItem(0).isEmpty(), "sorting the certified evens"))
                .thenExecute(() -> sorter.setItem(0, NumeralItem.stack(plain, 3)))
                .thenWaitUntil(() -> {
                    int down = 0;
                    for (int i = 0; i < hopper.getContainerSize(); i++) {
                        if (hopper.getItem(i).is(ModRegistries.NUMERAL_ITEM.get())) {
                            h.assertTrue(NumeralItem.data(hopper.getItem(i)).equals(even), "only certified evens go down");
                            down += hopper.getItem(i).getCount();
                        }
                    }
                    int side = 0;
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        if (chest.getItem(i).is(ModRegistries.NUMERAL_ITEM.get())) {
                            h.assertTrue(NumeralItem.data(chest.getItem(i)).equals(plain), "only the rest go sideways");
                            side += chest.getItem(i).getCount();
                        }
                    }
                    h.assertValueEqual(down, 2, "certified evens below");
                    h.assertValueEqual(side, 3, "uncertified numerals beside");
                })
                .thenSucceed();
    }

    /**
     * A ring of eight cables at y = 1 around (2, 2); a Courier inside the ring at (2,1,2), a
     * Surveyor on top at (1,2,1) and a Successor on top at (3,2,3). Every cable has two cable
     * neighbours. With {@code spur}, one more cable hangs off the ring: a loose end.
     */
    private static MachineBlockEntity courierRing(GameTestHelper h, boolean spur) {
        for (int[] c : new int[][] {{1, 1}, {2, 1}, {3, 1}, {3, 2}, {3, 3}, {2, 3}, {1, 3}, {1, 2}}) {
            h.setBlock(new BlockPos(c[0], 1, c[1]), ModRegistries.COPPER_CABLE.get());
        }
        if (spur) {
            h.setBlock(new BlockPos(0, 1, 2), ModRegistries.COPPER_CABLE.get());
        }
        BlockPos courier = new BlockPos(2, 1, 2);
        h.setBlock(courier, ModRegistries.COURIER_STATION.get());
        h.setBlock(new BlockPos(1, 2, 1), ModRegistries.NETWORK_SURVEYOR.get());
        h.setBlock(new BlockPos(3, 2, 3), ModRegistries.SUCCESSOR.get());
        h.getBlockEntity(new BlockPos(1, 2, 1), MachineBlockEntity.class).receiveEnergy(10_000);
        MachineBlockEntity station = h.getBlockEntity(courier, MachineBlockEntity.class);
        station.receiveEnergy(station.capacity());
        station.setItem(0, NumeralItem.stack(NumeralData.of(4), 2));
        return station;
    }

    private static void courierRunsOnEulerianNetworks(GameTestHelper h) {
        MachineBlockEntity station = courierRing(h, false);
        MachineBlockEntity successor = h.getBlockEntity(new BlockPos(3, 2, 3), MachineBlockEntity.class);
        var level = h.getLevel();
        h.succeedWhen(() -> {
            var graph = io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork.graph(level, h.absolutePos(new BlockPos(2, 1, 2)));
            h.assertTrue(graph.eulerian(), "the ring is Eulerian");
            h.assertTrue(io.github.kokoroko1234563.axiomatic.world.NetworkData.get(level).isCertifiedEulerian(graph.signature()),
                    "the surveyor certifies the ring");
            h.assertTrue(station.getItem(0).isEmpty(), "the courier delivered both numerals; status: " + station.status().getString());
            h.assertTrue(!successor.getItem(0).isEmpty() || !successor.getItem(1).isEmpty(), "the successor got its input");
        });
    }

    private static void courierRefusesOddNetworks(GameTestHelper h) {
        MachineBlockEntity station = courierRing(h, true);
        h.runAfterDelay(80, () -> {
            h.assertValueEqual(station.getItem(0).getCount(), 2, "nothing delivered without an Euler certificate");
            h.assertTrue(station.status().getString().contains("Eulerian"), "status: " + station.status().getString());
            h.succeed();
        });
    }

    private static void surveyorCertifiesTrees(GameTestHelper h) {
        network(h, false);
        var level = h.getLevel();
        h.succeedWhen(() -> {
            var graph = io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork.graph(level, h.absolutePos(new BlockPos(1, 1, 1)));
            h.assertTrue(graph.isTree(), "a line with one branch is a tree");
            h.assertTrue(io.github.kokoroko1234563.axiomatic.world.NetworkData.get(level).isCertifiedTree(graph.signature()),
                    "the surveyor should certify it");
        });
    }

    private static void surveyorRejectsCycles(GameTestHelper h) {
        network(h, true);
        var level = h.getLevel();
        var surveyor = h.getBlockEntity(new BlockPos(2, 2, 1), MachineBlockEntity.class);
        h.succeedWhen(() -> {
            var graph = io.github.kokoroko1234563.axiomatic.energy.EnergyNetwork.graph(level, h.absolutePos(new BlockPos(1, 1, 1)));
            h.assertFalse(graph.isTree(), "a square of cables has a cycle");
            h.assertTrue(surveyor.status().getString().contains("Not a tree"), "status: " + surveyor.status().getString());
            h.assertFalse(io.github.kokoroko1234563.axiomatic.world.NetworkData.get(level).isCertifiedTree(graph.signature()),
                    "never certified");
        });
    }

    private static void primeWellEmitsPrimes(GameTestHelper h) {
        MachineBlockEntity well = powered(h, ModRegistries.PRIME_WELL.get(), MachineBlockEntity.class);
        h.succeedWhen(() -> {
            NumeralData first = numeralIn(well, 0);
            NumeralData second = numeralIn(well, 1);
            h.assertTrue(first != null && second != null, "waiting for two primes; status: " + well.status().getString());
            h.assertValueEqual(first.value(), BigInteger.TWO, "first prime");
            h.assertValueEqual(second.value(), BigInteger.valueOf(3), "second prime");
            h.assertTrue(first.hasProperty("Prime", false), "certified prime, not conjecturally");
        });
    }

    private static void vaultStoresAsANumber(GameTestHelper h) {
        BlockPos pos = new BlockPos(3, 1, 3);
        h.setBlock(pos, ModRegistries.FACTORIZATION_VAULT.get());
        var vault = h.getBlockEntity(pos, io.github.kokoroko1234563.axiomatic.machine.FactorizationVaultBlockEntity.class);
        h.startSequence()
                .thenExecute(() -> vault.setItem(0, new ItemStack(Items.COAL, 5)))
                .thenIdle(2)
                .thenExecute(() -> vault.setItem(0, new ItemStack(Items.IRON_INGOT, 3)))
                .thenIdle(2)
                .thenExecute(() -> h.assertValueEqual(vault.number(), BigInteger.valueOf(864), "2^5 × 3^3"))
                .thenExecute(() -> vault.getItem(1).shrink(2))   // take 2 coal
                .thenIdle(2)
                .thenExecute(() -> h.assertValueEqual(vault.number(), BigInteger.valueOf(216), "2^3 × 3^3"))
                .thenSucceed();
    }

    private static void mersenneAndThePerfectCore(GameTestHelper h) {
        MachineBlockEntity bench = powered(h, ModRegistries.TEST_BENCH.get(), MachineBlockEntity.class);
        var level = h.getLevel();
        // Cycle the bench to Mersenne.
        while (!((io.github.kokoroko1234563.axiomatic.machine.TestBenchBlockEntity) bench).property().equals("Mersenne")) {
            bench.onButton(level, null, 0);
        }
        bench.setItem(0, NumeralItem.stack(NumeralData.of(31), 1));
        h.succeedWhen(() -> {
            NumeralData out = numeralIn(bench, 1);
            h.assertTrue(out != null && out.hasProperty("Mersenne", false), "31 = 2^5 - 1 is certified Mersenne; status: "
                    + bench.status().getString());
            var seven = java.math.BigInteger.valueOf(7);
            NumeralData proven = NumeralData.of(7).withFact(new Fact("Prime", List.of(seven), java.util.Set.of()))
                    .withFact(new Fact("Mersenne", List.of(seven), java.util.Set.of()));
            NumeralData risky = NumeralData.of(7).withFact(new Fact("Prime", List.of(seven), java.util.Set.of("conjecture:x")))
                    .withFact(new Fact("Mersenne", List.of(seven), java.util.Set.of()));
            ItemStack core = new ItemStack(ModRegistries.PRIME_CORE.get());
            ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT);
            java.util.function.Function<NumeralData, CraftingInput> grid = m -> CraftingInput.of(3, 3, List.of(
                    core, netherite, core, core, NumeralItem.stack(m, 1), core, core, core, core));
            h.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid.apply(proven), level).isPresent(),
                    "a proven Mersenne prime crafts a Perfect Core");
            h.assertFalse(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid.apply(risky), level).isPresent(),
                    "a conjectural one does not");
        });
    }

    private static void circuitBoardNeedsCertifiedEvens(GameTestHelper h) {
        ItemStack copper = new ItemStack(Items.COPPER_INGOT);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack certified = NumeralItem.stack(NumeralData.of(2)
                .withFact(new Fact("Even", List.of(BigInteger.TWO), java.util.Set.of())), 1);
        ItemStack plain = NumeralItem.stack(NumeralData.of(2), 1);
        var level = h.getLevel();
        CraftingInput good = CraftingInput.of(3, 3, List.of(copper, certified, copper, certified, redstone, certified,
                copper, certified, copper));
        CraftingInput bad = CraftingInput.of(3, 3, List.of(copper, plain, copper, plain, redstone, plain,
                copper, plain, copper));
        var match = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, good, level);
        h.assertTrue(match.isPresent(), "certified evens should craft a Circuit Board");
        h.assertTrue(match.get().value().assemble(good).is(ModRegistries.CIRCUIT_BOARD.get()), "result is a Circuit Board");
        h.assertFalse(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, bad, level).isPresent(),
                "uncertified numerals must not craft");
        // Risk travels in the recipe result itself, so shift-click and Crafters keep it too.
        ItemStack risky = NumeralItem.stack(NumeralData.of(2)
                .withFact(new Fact("Even", List.of(BigInteger.TWO), java.util.Set.of("conjecture:hope"))), 1);
        CraftingInput gamble = CraftingInput.of(3, 3, List.of(copper, risky, copper, certified, redstone, certified,
                copper, certified, copper));
        ItemStack board = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, gamble, level).orElseThrow()
                .value().assemble(gamble);
        h.assertValueEqual(board.get(ModRegistries.DEPENDS.get()), List.of("conjecture:hope"), "the board rests on hope");
        h.assertTrue(match.get().value().assemble(good).get(ModRegistries.DEPENDS.get()) == null, "a clean board is clean");
        h.succeed();
    }
}
