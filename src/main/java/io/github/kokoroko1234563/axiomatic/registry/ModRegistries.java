package io.github.kokoroko1234563.axiomatic.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import io.github.kokoroko1234563.axiomatic.crafting.CertifiedNumeralIngredient;
import io.github.kokoroko1234563.axiomatic.energy.CableBlock;
import io.github.kokoroko1234563.axiomatic.item.CircuitData;
import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.machine.CoalDynamoBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.CompiledMachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.ConjectureBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.ConjectureBenchBlockEntity;
import io.github.kokoroko1234563.axiomatic.item.RiskyItem;
import io.github.kokoroko1234563.axiomatic.item.GraphLensItem;
import io.github.kokoroko1234563.axiomatic.block.LibraryBlock;
import io.github.kokoroko1234563.axiomatic.machine.DuplicatorBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlock;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineMenu;
import io.github.kokoroko1234563.axiomatic.machine.SuccessorBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.TestBenchBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.ZeroSourceBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.AssumptionChamberBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.ConjunctionPressBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.DraftingChamberBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.EquationBenchBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.InductionEngineBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.InstantiatorBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.ModusPonensPressBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.PropertyBenchBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.RewriterBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.reasoning.WitnessBenchBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Every block, item, block entity, menu, component and tab the mod adds. */
public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Axiomatic.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Axiomatic.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Axiomatic.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Axiomatic.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Axiomatic.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Axiomatic.MODID);
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Axiomatic.MODID);

    /** Items shown in the creative tab, in registration order. */
    private static final List<DeferredItem<?>> TAB_ITEMS = new ArrayList<>();

    /** A machine's block and block entity type, registered together. */
    public record Machine<T extends MachineBlockEntity>(DeferredBlock<MachineBlock> block,
            DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> beType) {
        public MachineBlock get() {
            return block.get();
        }

        public BlockEntityType<T> type() {
            return beType.get();
        }
    }

    // ---- Components -----------------------------------------------------------------------

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NumeralData>> NUMERAL =
            COMPONENTS.registerComponentType("numeral",
                    b -> b.persistent(NumeralData.CODEC).networkSynchronized(NumeralData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StatementData>> STATEMENT =
            COMPONENTS.registerComponentType("statement",
                    b -> b.persistent(StatementData.CODEC).networkSynchronized(StatementData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CircuitData>> CIRCUIT =
            COMPONENTS.registerComponentType("circuit",
                    b -> b.persistent(CircuitData.CODEC).networkSynchronized(CircuitData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CONJECTURE =
            COMPONENTS.registerComponentType("conjecture",
                    b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Conjectures a crafted part depends on, from the certificates it was made with. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> DEPENDS =
            COMPONENTS.registerComponentType("depends", b -> b.persistent(com.mojang.serialization.Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> VARIABLE =
            COMPONENTS.registerComponentType("variable",
                    b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // ---- Items ----------------------------------------------------------------------------

    public static final DeferredItem<NumeralItem> NUMERAL_ITEM = tab(ITEMS.registerItem("numeral", NumeralItem::new));
    public static final DeferredItem<MathItems.VariableItem> VARIABLE_ITEM =
            tab(ITEMS.registerItem("variable", MathItems.VariableItem::new));
    public static final DeferredItem<MathItems.CircuitItem> CIRCUIT_ITEM =
            tab(ITEMS.registerItem("circuit", MathItems.CircuitItem::new));
    public static final DeferredItem<MathItems.StatementItem> STATEMENT_ITEM =
            tab(ITEMS.registerItem("statement", MathItems.StatementItem::new, p -> p.stacksTo(16)));
    public static final DeferredItem<MathItems.AxiomTabletItem> TABLET_SUCC_NOT_ZERO = tab(ITEMS.registerItem(
            "axiom_tablet_succ_not_zero", p -> new MathItems.AxiomTabletItem(p, "succ_not_zero"), p -> p.stacksTo(1)));
    public static final DeferredItem<MathItems.AxiomTabletItem> TABLET_SUCC_INJECTIVE = tab(ITEMS.registerItem(
            "axiom_tablet_succ_injective", p -> new MathItems.AxiomTabletItem(p, "succ_injective"), p -> p.stacksTo(1)));
    public static final DeferredItem<Item> CIRCUIT_BOARD = tab(ITEMS.registerItem("circuit_board", RiskyItem::new));
    public static final DeferredItem<Item> PRIME_CORE = tab(ITEMS.registerItem("prime_core", RiskyItem::new));
    public static final DeferredItem<Item> PARADOX_JUNK = tab(ITEMS.registerSimpleItem("paradox_junk"));
    public static final DeferredItem<GraphLensItem> GRAPH_LENS = tab(ITEMS.registerItem("graph_lens", GraphLensItem::new,
            p -> p.stacksTo(1)));

    // ---- Blocks ---------------------------------------------------------------------------

    public static final Machine<CoalDynamoBlockEntity> COAL_DYNAMO = machine("coal_dynamo", MapColor.COLOR_ORANGE,
            CoalDynamoBlockEntity::new);
    public static final DeferredBlock<CableBlock> COPPER_CABLE = BLOCKS.registerBlock("copper_cable", CableBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.COPPER).noOcclusion());

    public static final DeferredBlock<LibraryBlock> LIBRARY = BLOCKS.registerBlock("library", LibraryBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD));

    static {
        tab(ITEMS.registerSimpleBlockItem(COPPER_CABLE));
        tab(ITEMS.registerSimpleBlockItem(LIBRARY));
    }

    // Computation (copper).
    public static final Machine<ZeroSourceBlockEntity> ZERO_SOURCE = machine("zero_source", MapColor.COLOR_ORANGE,
            ZeroSourceBlockEntity::new);
    public static final Machine<SuccessorBlockEntity> SUCCESSOR = machine("successor", MapColor.COLOR_ORANGE,
            SuccessorBlockEntity::new);
    public static final Machine<DuplicatorBlockEntity> DUPLICATOR = machine("duplicator", MapColor.COLOR_ORANGE,
            DuplicatorBlockEntity::new);
    public static final Machine<TestBenchBlockEntity> TEST_BENCH = machine("test_bench", MapColor.METAL,
            TestBenchBlockEntity::new);

    // Reasoning (blue).
    public static final Machine<DraftingChamberBlockEntity> DRAFTING_CHAMBER = machine("drafting_chamber",
            MapColor.COLOR_BLUE, DraftingChamberBlockEntity::new);
    public static final Machine<EquationBenchBlockEntity> EQUATION_BENCH = machine("equation_bench",
            MapColor.COLOR_BLUE, EquationBenchBlockEntity::new);
    public static final Machine<PropertyBenchBlockEntity> PROPERTY_BENCH = machine("property_bench",
            MapColor.COLOR_BLUE, PropertyBenchBlockEntity::new);
    public static final Machine<WitnessBenchBlockEntity> WITNESS_BENCH = machine("witness_bench",
            MapColor.COLOR_BLUE, WitnessBenchBlockEntity::new);
    public static final Machine<InstantiatorBlockEntity> INSTANTIATOR = machine("instantiator",
            MapColor.COLOR_BLUE, InstantiatorBlockEntity::new);
    public static final Machine<RewriterBlockEntity> REWRITER = machine("rewriter",
            MapColor.COLOR_BLUE, RewriterBlockEntity::new);
    public static final Machine<AssumptionChamberBlockEntity> ASSUMPTION_CHAMBER = machine("assumption_chamber",
            MapColor.COLOR_BLUE, AssumptionChamberBlockEntity::new);
    public static final Machine<InductionEngineBlockEntity> INDUCTION_ENGINE = machine("induction_engine",
            MapColor.COLOR_BLUE, InductionEngineBlockEntity::new);
    public static final Machine<ConjunctionPressBlockEntity> CONJUNCTION_PRESS = machine("conjunction_press",
            MapColor.COLOR_BLUE, ConjunctionPressBlockEntity::new);
    public static final Machine<ModusPonensPressBlockEntity> MODUS_PONENS_PRESS = machine("modus_ponens_press",
            MapColor.COLOR_BLUE, ModusPonensPressBlockEntity::new);
    public static final Machine<CompiledMachineBlockEntity> COMPILED_MACHINE = machine("compiled_machine",
            MapColor.GOLD, CompiledMachineBlockEntity::new);

    // Conjectures (purple).
    public static final Machine<ConjectureBenchBlockEntity> CONJECTURE_BENCH = machine("conjecture_bench",
            MapColor.COLOR_PURPLE, ConjectureBenchBlockEntity::new);
    public static final Machine<ConjectureBlockEntity> CONJECTURE_BLOCK = machine("conjecture_block",
            MapColor.COLOR_PURPLE, ConjectureBlockEntity::new);

    // ---- Menus, ingredients, tab ----------------------------------------------------------

    public static final Supplier<MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine",
            () -> IMenuTypeExtension.create(MachineMenu::fromNetwork));

    public static final Supplier<IngredientType<CertifiedNumeralIngredient>> CERTIFIED_NUMERAL_INGREDIENT =
            INGREDIENT_TYPES.register("certified_numeral", () -> CertifiedNumeralIngredient.TYPE);

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.axiomatic"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> TEST_BENCH.get().asItem().getDefaultInstance())
            .displayItems((params, output) -> TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    // ---- Helpers --------------------------------------------------------------------------

    private static <T extends MachineBlockEntity> Machine<T> machine(String name, MapColor color,
            BlockEntityType.BlockEntitySupplier<T> factory) {
        // The block and its block entity type refer to each other, so each looks the other up lazily.
        @SuppressWarnings("unchecked")
        DeferredHolder<BlockEntityType<?>, BlockEntityType<T>>[] type = new DeferredHolder[1];
        DeferredBlock<MachineBlock> block = BLOCKS.registerBlock(name, p -> new MachineBlock(p, () -> type[0].get()),
                p -> p.mapColor(color).strength(2.5f).sound(SoundType.COPPER).requiresCorrectToolForDrops());
        type[0] = BLOCK_ENTITIES.register(name, () -> new BlockEntityType<>(factory, block.get()));
        tab(ITEMS.registerSimpleBlockItem(block));
        return new Machine<>(block, type[0]);
    }

    private static <T extends DeferredItem<?>> T tab(T item) {
        TAB_ITEMS.add(item);
        return item;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        INGREDIENT_TYPES.register(bus);
    }
}
