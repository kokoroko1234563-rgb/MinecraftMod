package io.github.kokoroko1234563.axiomatic.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import io.github.kokoroko1234563.axiomatic.crafting.CertifiedNumeralIngredient;
import io.github.kokoroko1234563.axiomatic.energy.CableBlock;
import io.github.kokoroko1234563.axiomatic.item.NumeralData;
import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.machine.CoalDynamoBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.DuplicatorBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlock;
import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineMenu;
import io.github.kokoroko1234563.axiomatic.machine.SuccessorBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.TestBenchBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.ZeroSourceBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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

    // ---- Components -----------------------------------------------------------------------

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NumeralData>> NUMERAL =
            COMPONENTS.registerComponentType("numeral",
                    b -> b.persistent(NumeralData.CODEC).networkSynchronized(NumeralData.STREAM_CODEC));

    // ---- Items ----------------------------------------------------------------------------

    public static final DeferredItem<NumeralItem> NUMERAL_ITEM = tab(ITEMS.registerItem("numeral", NumeralItem::new));
    public static final DeferredItem<Item> CIRCUIT_BOARD = tab(ITEMS.registerSimpleItem("circuit_board"));

    // ---- Machines -------------------------------------------------------------------------

    public static final DeferredBlock<MachineBlock> COAL_DYNAMO = machine("coal_dynamo", MapColor.COLOR_ORANGE,
            () -> ModRegistries.COAL_DYNAMO_BE.get());
    public static final DeferredBlock<CableBlock> COPPER_CABLE = BLOCKS.registerBlock("copper_cable", CableBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<MachineBlock> ZERO_SOURCE = machine("zero_source", MapColor.COLOR_ORANGE,
            () -> ModRegistries.ZERO_SOURCE_BE.get());
    public static final DeferredBlock<MachineBlock> SUCCESSOR = machine("successor", MapColor.COLOR_ORANGE,
            () -> ModRegistries.SUCCESSOR_BE.get());
    public static final DeferredBlock<MachineBlock> DUPLICATOR = machine("duplicator", MapColor.COLOR_ORANGE,
            () -> ModRegistries.DUPLICATOR_BE.get());
    public static final DeferredBlock<MachineBlock> TEST_BENCH = machine("test_bench", MapColor.COLOR_BLUE,
            () -> ModRegistries.TEST_BENCH_BE.get());

    static {
        tab(ITEMS.registerSimpleBlockItem(COAL_DYNAMO));
        tab(ITEMS.registerSimpleBlockItem(COPPER_CABLE));
        tab(ITEMS.registerSimpleBlockItem(ZERO_SOURCE));
        tab(ITEMS.registerSimpleBlockItem(SUCCESSOR));
        tab(ITEMS.registerSimpleBlockItem(DUPLICATOR));
        tab(ITEMS.registerSimpleBlockItem(TEST_BENCH));
    }

    public static final Supplier<BlockEntityType<CoalDynamoBlockEntity>> COAL_DYNAMO_BE = BLOCK_ENTITIES.register(
            "coal_dynamo", () -> new BlockEntityType<>(CoalDynamoBlockEntity::new, COAL_DYNAMO.get()));
    public static final Supplier<BlockEntityType<ZeroSourceBlockEntity>> ZERO_SOURCE_BE = BLOCK_ENTITIES.register(
            "zero_source", () -> new BlockEntityType<>(ZeroSourceBlockEntity::new, ZERO_SOURCE.get()));
    public static final Supplier<BlockEntityType<SuccessorBlockEntity>> SUCCESSOR_BE = BLOCK_ENTITIES.register(
            "successor", () -> new BlockEntityType<>(SuccessorBlockEntity::new, SUCCESSOR.get()));
    public static final Supplier<BlockEntityType<DuplicatorBlockEntity>> DUPLICATOR_BE = BLOCK_ENTITIES.register(
            "duplicator", () -> new BlockEntityType<>(DuplicatorBlockEntity::new, DUPLICATOR.get()));
    public static final Supplier<BlockEntityType<TestBenchBlockEntity>> TEST_BENCH_BE = BLOCK_ENTITIES.register(
            "test_bench", () -> new BlockEntityType<>(TestBenchBlockEntity::new, TEST_BENCH.get()));

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

    private static DeferredBlock<MachineBlock> machine(String name, MapColor color,
            Supplier<BlockEntityType<? extends MachineBlockEntity>> type) {
        return BLOCKS.registerBlock(name, p -> new MachineBlock(p, type),
                p -> p.mapColor(color).strength(2.5f).sound(SoundType.COPPER).requiresCorrectToolForDrops());
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
