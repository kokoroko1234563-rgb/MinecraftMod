package io.github.kokoroko1234563.axiomatic.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A circuit item's content: a term, and how it reads (rendered on the server). */
public record CircuitData(Term term, String display) {
    public static final Codec<CircuitData> CODEC = RecordCodecBuilder.create(i -> i.group(
            MathCodecs.TERM.fieldOf("term").forGetter(CircuitData::term),
            Codec.STRING.fieldOf("display").forGetter(CircuitData::display)).apply(i, CircuitData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CircuitData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
