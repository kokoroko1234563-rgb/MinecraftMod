package io.github.kokoroko1234563.axiomatic.item;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Fact;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * What a Numeral item carries: its value and its certificates. Numerals with the same value and
 * certificates stack. Certificates about several numbers (relations) are not stored on a single
 * numeral; they live on pairs (later milestone).
 */
public record NumeralData(BigInteger value, List<Fact> facts) implements TooltipProvider {
    public static final Codec<BigInteger> BIG_INTEGER = Codec.STRING.xmap(BigInteger::new, BigInteger::toString);

    public static final Codec<Fact> FACT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("property").forGetter(Fact::property),
            BIG_INTEGER.listOf().fieldOf("args").forGetter(Fact::args),
            Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("dependencies").forGetter(Fact::dependencies))
            .apply(i, Fact::new));

    public static final Codec<NumeralData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BIG_INTEGER.fieldOf("value").forGetter(NumeralData::value),
            FACT_CODEC.listOf().optionalFieldOf("facts", List.of()).forGetter(NumeralData::facts))
            .apply(i, NumeralData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NumeralData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static final NumeralData ZERO = new NumeralData(BigInteger.ZERO, List.of());

    public NumeralData {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Numerals are natural numbers");
        }
        // Sorted so that the same certificates in any order still stack.
        facts = facts.stream().sorted(java.util.Comparator.comparing(Fact::toString)
                .thenComparing(f -> f.dependencies().toString())).distinct().toList();
    }

    public static NumeralData of(long value) {
        return new NumeralData(BigInteger.valueOf(value), List.of());
    }

    /** A plain numeral with the same value: what a machine outputs when the value changes. */
    public NumeralData withValue(BigInteger newValue) {
        return new NumeralData(newValue, List.of());
    }

    public NumeralData withFact(Fact fact) {
        List<Fact> all = new ArrayList<>(facts);
        all.add(fact);
        return new NumeralData(value, all);
    }

    /** Whether this numeral is certified to have a one-place property. */
    public boolean hasProperty(String property, boolean allowConjectural) {
        return facts.stream().anyMatch(f -> f.sameClaim(property, List.of(value))
                && (allowConjectural || !f.isConjectural()));
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag,
            DataComponentGetter components) {
        for (Fact fact : facts) {
            Component line = Component.translatable("tooltip.axiomatic.certified", fact.property())
                    .withStyle(fact.isConjectural() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
            if (fact.isConjectural()) {
                line = line.copy().append(Component.translatable("tooltip.axiomatic.conjectural"));
            }
            consumer.accept(line);
        }
    }
}
