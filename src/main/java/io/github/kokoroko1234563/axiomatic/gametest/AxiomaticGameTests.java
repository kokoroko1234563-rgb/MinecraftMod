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
