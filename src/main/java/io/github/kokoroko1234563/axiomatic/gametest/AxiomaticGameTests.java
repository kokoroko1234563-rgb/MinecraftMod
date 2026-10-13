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
            h.assertValueEqual(handler.insert(41, tx), 40, "4 FE per unit, remainder refused");
        }
        h.assertValueEqual(successor.energy(), 0L, "an aborted insert is undone");
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            handler.insert(400, tx);
            tx.commit();
        }
        h.assertValueEqual(successor.energy(), 100L, "a committed insert stays");
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
        h.succeed();
    }
}
