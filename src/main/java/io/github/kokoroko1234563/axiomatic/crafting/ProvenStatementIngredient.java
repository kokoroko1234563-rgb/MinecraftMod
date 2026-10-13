package io.github.kokoroko1234563.axiomatic.crafting;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.item.MathItems;
import io.github.kokoroko1234563.axiomatic.item.StatementData;
import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * A recipe ingredient matching a proven statement (no open assumptions, no conjectures) that reads
 * exactly {@code statement}, such as "∀n. 0 + n = n": progression gated by a theorem. The match is
 * on the displayed form, so it relies on the starter definitions' symbols.
 *
 * <p>JSON: {@code {"neoforge:ingredient_type": "axiomatic:proven_statement", "statement": "∀n. 0 + n = n"}}
 */
public record ProvenStatementIngredient(String statement) implements ICustomIngredient {
    public static final MapCodec<ProvenStatementIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("statement").forGetter(ProvenStatementIngredient::statement))
            .apply(i, ProvenStatementIngredient::new));

    public static final IngredientType<ProvenStatementIngredient> TYPE = new IngredientType<>(CODEC);

    @Override
    public boolean test(ItemStack stack) {
        StatementData data = MathItems.statement(stack);
        return data != null && data.proven() && data.hypotheses().isEmpty() && !data.isConjectural()
                && data.display().equals(statement);
    }

    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(ModRegistries.STATEMENT_ITEM);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModRegistries.PROVEN_STATEMENT_INGREDIENT.get();
    }

    @Override
    public SlotDisplay display() {
        return new SlotDisplay.ItemSlotDisplay(ModRegistries.STATEMENT_ITEM);
    }
}
