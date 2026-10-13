package io.github.kokoroko1234563.axiomatic.world;

import java.io.IOException;
import java.util.Base64;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's mathematics, saved with the world. Machines must call {@link #setDirty()} after
 * changing the state.
 */
public final class AxiomaticData extends SavedData {
    private static final Codec<AxiomaticData> CODEC = Codec.STRING.comapFlatMap(AxiomaticData::decode,
            data -> Base64.getEncoder().encodeToString(data.state.toBytes()));

    public static final SavedDataType<AxiomaticData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Axiomatic.MODID, "mathematics"), AxiomaticData::new, CODEC);

    private final MathState state;

    public AxiomaticData() {
        this(MathState.newWorld());
    }

    private AxiomaticData(MathState state) {
        this.state = state;
    }

    private static DataResult<AxiomaticData> decode(String encoded) {
        try {
            return DataResult.success(new AxiomaticData(MathState.fromBytes(Base64.getDecoder().decode(encoded))));
        } catch (IOException | IllegalArgumentException e) {
            return DataResult.error(() -> "Could not load Axiomatic mathematics: " + e.getMessage());
        }
    }

    public static AxiomaticData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public MathState state() {
        return state;
    }
}
