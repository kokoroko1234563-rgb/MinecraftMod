package io.github.kokoroko1234563.axiomatic.crafting;

import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.registry.ModRegistries;
import io.github.kokoroko1234563.axiomatic.risk.Paradox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * A shaped recipe whose result carries the conjectures its ingredients rest on (the DEPENDS
 * component), however it is crafted: by hand, shift-click, the recipe book or a Crafter. Every
 * Axiomatic crafting recipe uses it ({@code "type": "axiomatic:risky_shaped"}).
 */
public class RiskyShapedRecipe extends ShapedRecipe {
    private final ItemStackTemplate output;

    public RiskyShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.output = result;
    }

    public static final MapCodec<ShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(o -> ((RiskyShapedRecipe) o).commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> ((RiskyShapedRecipe) o).bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> ((RiskyShapedRecipe) o).output))
            .apply(i, RiskyShapedRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> ((RiskyShapedRecipe) o).commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> ((RiskyShapedRecipe) o).bookInfo,
            ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
            ItemStackTemplate.STREAM_CODEC, o -> ((RiskyShapedRecipe) o).output,
            RiskyShapedRecipe::new);

    public static final RecipeSerializer<ShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return ModRegistries.RISKY_SHAPED.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack out = super.assemble(input);
        Set<String> deps = new HashSet<>();
        for (ItemStack in : input.items()) {
            deps.addAll(Paradox.conjecturesOf(in));
        }
        if (!deps.isEmpty()) {
            out.set(ModRegistries.DEPENDS.get(), deps.stream().sorted().toList());
        }
        return out;
    }
}
