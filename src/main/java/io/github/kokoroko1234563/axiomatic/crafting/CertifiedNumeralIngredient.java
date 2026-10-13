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
 * <p>JSON: {@code {"neoforge:ingredient_type": "axiomatic:certified_numeral", "property": "Even"}}; add
 * {@code "also": ["Mersenne"]} to require more certificates on the same numeral.
 */
public record CertifiedNumeralIngredient(String property, java.util.List<String> also, boolean provenOnly)
        implements ICustomIngredient {
    public static final MapCodec<CertifiedNumeralIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("property").forGetter(CertifiedNumeralIngredient::property),
            Codec.STRING.listOf().optionalFieldOf("also", java.util.List.of()).forGetter(CertifiedNumeralIngredient::also),
            Codec.BOOL.optionalFieldOf("proven_only", false).forGetter(CertifiedNumeralIngredient::provenOnly))
            .apply(i, CertifiedNumeralIngredient::new));

    public static final IngredientType<CertifiedNumeralIngredient> TYPE = new IngredientType<>(CODEC);

    @Override
    public boolean test(ItemStack stack) {
        if (!stack.is(ModRegistries.NUMERAL_ITEM.get())) {
            return false;
        }
        var data = NumeralItem.data(stack);
        return data.hasProperty(property, !provenOnly) && also.stream().allMatch(p -> data.hasProperty(p, !provenOnly));
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

    /**
     * Recipe viewers and the Notebook show an example numeral carrying the required certificates,
     * named "Any numeral certified …", so the requirement is visible.
     */
    @Override
    public SlotDisplay display() {
        java.util.List<String> all = new java.util.ArrayList<>();
        all.add(property);
        all.addAll(also);
        long value = all.contains("Prime") || all.contains("Mersenne") ? 7 : all.contains("Even") ? 2 : 1;
        var data = io.github.kokoroko1234563.axiomatic.item.NumeralData.of(value);
        for (String p : all) {
            data = data.withFact(new io.github.kokoroko1234563.axiomatic.core.knowledge.Fact(p,
                    java.util.List.of(java.math.BigInteger.valueOf(value)), java.util.Set.of()));
        }
        ItemStack example = NumeralItem.stack(data, 1);
        example.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable(
                provenOnly ? "ingredient.axiomatic.certified_proven" : "ingredient.axiomatic.certified", String.join(" + ", all)));
        return new SlotDisplay.ItemStackSlotDisplay(new net.minecraft.world.item.ItemStackTemplate(
                example.getItem(), example.getComponentsPatch()));
    }
}
