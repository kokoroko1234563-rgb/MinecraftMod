package io.github.kokoroko1234563.axiomatic.crafting;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.item.NumeralItem;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * A recipe ingredient matching any numeral certified to have {@code property}, for example the
 * certified evens in a Circuit Board. Conjectural certificates are accepted unless
 * {@code proven_only} is set: that is the gamble, since parts made from them carry the risk.
 *
 * <p>JSON: {@code {"neoforge:ingredient_type": "axiomatic:certified_numeral", "property": "Even"}}
 */
public record CertifiedNumeralIngredient(String property, boolean provenOnly) implements ICustomIngredient {
    public static final MapCodec<CertifiedNumeralIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("property").forGetter(CertifiedNumeralIngredient::property),
            Codec.BOOL.optionalFieldOf("proven_only", false).forGetter(CertifiedNumeralIngredient::provenOnly))
            .apply(i, CertifiedNumeralIngredient::new));

    public static final IngredientType<CertifiedNumeralIngredient> TYPE = new IngredientType<>(CODEC);

    @Override
    public boolean test(ItemStack stack) {
        return stack.is(ModRegistries.NUMERAL_ITEM.get()) && NumeralItem.data(stack).hasProperty(property, !provenOnly);
    }

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(ModRegistries.NUMERAL_ITEM);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModRegistries.CERTIFIED_NUMERAL_INGREDIENT.get();
    }

    @Override
    public SlotDisplay display() {
        return new SlotDisplay.ItemSlotDisplay(ModRegistries.NUMERAL_ITEM);
    }
}
