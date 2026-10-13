package io.github.kokoroko1234563.axiomatic.network;

import java.util.List;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Library's contents, sent to a player who uses a Library block: one section per tab
 * (definitions, theorems, conjectures, certificates, statuses), already formatted on the server.
 * The client handler that opens the screen is registered in {@code AxiomaticClient}.
 */
public record LibraryPayload(List<Section> sections) implements CustomPacketPayload {
    /** At most this many lines per section are sent; the rest are summarised in one line. */
    public static final int MAX_LINES = 1000;

    public record Section(Component title, List<Component> lines) {
        static final StreamCodec<RegistryFriendlyByteBuf, Section> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Section::title,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES + 1)), Section::lines,
                Section::new);
    }

    public static final Type<LibraryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Axiomatic.MODID, "library"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LibraryPayload> STREAM_CODEC =
            Section.STREAM_CODEC.apply(ByteBufCodecs.list(16)).map(LibraryPayload::new, LibraryPayload::sections);

    @Override
    public Type<LibraryPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC);
    }
}
