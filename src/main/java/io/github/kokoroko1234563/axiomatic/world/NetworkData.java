package io.github.kokoroko1234563.axiomatic.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Cable networks certified by a Network Surveyor, by their exact shape and phases (signature):
 * trees (lossless transfer) and two-coloured networks (alternating power). Changing a network
 * gives it a new signature, which voids its certificates until it is surveyed again. Kept per
 * dimension.
 */
public final class NetworkData extends SavedData {
    private static final Codec<NetworkData> CURRENT = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.listOf().optionalFieldOf("trees", List.of()).forGetter(d -> List.copyOf(d.trees)),
            Codec.LONG.listOf().optionalFieldOf("bipartite", List.of()).forGetter(d -> List.copyOf(d.bipartite)))
            .apply(i, (t, b) -> new NetworkData(new HashSet<>(t), new HashSet<>(b))));

    /** Saves before bipartite certificates were a list of tree signatures. */
    private static final Codec<NetworkData> CODEC = Codec.withAlternative(CURRENT,
            Codec.LONG.listOf().xmap(list -> new NetworkData(new HashSet<>(list), new HashSet<>()), d -> List.copyOf(d.trees)));

    public static final SavedDataType<NetworkData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Axiomatic.MODID, "networks"),
            () -> new NetworkData(new HashSet<>(), new HashSet<>()), CODEC);

    private final Set<Long> trees;
    private final Set<Long> bipartite;

    private NetworkData(Set<Long> trees, Set<Long> bipartite) {
        this.trees = trees;
        this.bipartite = bipartite;
    }

    public static NetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isCertifiedTree(long signature) {
        return trees.contains(signature);
    }

    public void certifyTree(long signature) {
        if (trees.add(signature)) {
            setDirty();
        }
    }

    public boolean isCertifiedBipartite(long signature) {
        return bipartite.contains(signature);
    }

    public void certifyBipartite(long signature) {
        if (bipartite.add(signature)) {
            setDirty();
        }
    }
}
