package io.github.kokoroko1234563.axiomatic.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Cable networks certified as trees by a Network Surveyor, by their exact shape (signature).
 * Changing a network gives it a new signature, which voids the certificate until it is surveyed
 * again. Kept per dimension.
 */
public final class NetworkData extends SavedData {
    private static final Codec<NetworkData> CODEC = Codec.LONG.listOf().xmap(
            list -> new NetworkData(new HashSet<>(list)), data -> List.copyOf(data.trees));

    public static final SavedDataType<NetworkData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Axiomatic.MODID, "networks"), () -> new NetworkData(new HashSet<>()), CODEC);

    private final Set<Long> trees;

    private NetworkData(Set<Long> trees) {
        this.trees = trees;
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
}
