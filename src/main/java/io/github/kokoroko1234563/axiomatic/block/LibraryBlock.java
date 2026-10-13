package io.github.kokoroko1234563.axiomatic.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.kokoroko1234563.axiomatic.core.knowledge.Library;
import io.github.kokoroko1234563.axiomatic.core.knowledge.MathState;
import io.github.kokoroko1234563.axiomatic.core.logic.Definition;
import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import io.github.kokoroko1234563.axiomatic.world.AxiomaticData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import io.github.kokoroko1234563.axiomatic.network.LibraryPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Library: the world's record of mathematics. Using it opens a screen with one tab each for
 * definitions, named theorems with what they depend on, conjectures (with post-mortems), structure
 * certificates, and statement statuses.
 */
public class LibraryBlock extends Block {
    public LibraryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new LibraryPayload(sections(AxiomaticData.get(server.getServer()).state())));
        }
        return InteractionResult.SUCCESS;
    }

    /** The Library's contents, formatted, one section per tab. */
    public static List<LibraryPayload.Section> sections(MathState state) {
        Printer printer = new Printer(state.signature());
        Library library = state.library();
        List<LibraryPayload.Section> sections = new ArrayList<>();

        List<Component> definitions = new ArrayList<>();
        for (Definition d : state.signature().all()) {
            String kind = switch (d) {
                case Definition.Predicate p -> "property";
                case Definition.PrimitiveRecursive r -> "recursive";
                case Definition.Explicit e -> "explicit";
                case Definition.Abstract a -> "abstract";
                case Definition.GeneralRecursive g -> "general";
            };
            definitions.add(Component.literal(d.name() + "(" + String.join(", ", d.params()) + ")")
                    .append(Component.translatable("library.axiomatic.kind." + kind).withStyle(ChatFormatting.GRAY)));
        }
        sections.add(section("library.axiomatic.definitions", definitions));

        List<Component> theorems = new ArrayList<>();
        for (Map.Entry<String, Theorem> e : library.theorems().entrySet()) {
            Theorem t = e.getValue();
            theorems.add(Component.literal(e.getKey() + ": ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(printer.statement(t.formula())).withStyle(ChatFormatting.WHITE))
                    .append(Component.translatable("library.axiomatic.uses", t.dependencies().size(), t.steps())
                            .withStyle(ChatFormatting.GRAY)));
            // The dependency map: conjectures first (they are the risk), then structures and definitions.
            List<String> deps = t.dependencies().stream()
                    .filter(d -> !d.startsWith("axiom:"))
                    .sorted(java.util.Comparator.comparing((String d) -> !d.startsWith("conjecture:")).thenComparing(d -> d))
                    .toList();
            if (!deps.isEmpty()) {
                MutableComponent line = Component.translatable("library.axiomatic.rests_on").withStyle(ChatFormatting.GRAY);
                for (int i = 0; i < deps.size(); i++) {
                    String d = deps.get(i);
                    String shown = d.substring(d.indexOf(':') + 1);
                    line.append(Component.literal((i == 0 ? " " : ", ") + shown).withStyle(
                            d.startsWith("conjecture:") ? ChatFormatting.LIGHT_PURPLE
                                    : d.startsWith("structure:") ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
                }
                theorems.add(line);
            }
        }
        sections.add(section("library.axiomatic.theorems", theorems));

        List<Component> conjectures = new ArrayList<>();
        for (Library.Conjecture c : library.conjectures()) {
            ChatFormatting colour = switch (c.state()) {
                case ACTIVE -> ChatFormatting.LIGHT_PURPLE;
                case DORMANT -> ChatFormatting.DARK_PURPLE;
                case PROVEN -> ChatFormatting.GOLD;
                case REFUTED -> ChatFormatting.RED;
            };
            MutableComponent text = Component.literal(c.name() + ": " + printer.statement(c.statement()) + " ")
                    .withStyle(colour)
                    .append(Component.translatable("library.axiomatic.state." + c.state().name().toLowerCase()));
            if (c.counterexample() != null) {
                text.append(Component.translatable("library.axiomatic.postmortem", c.counterexample().toString())
                        .withStyle(ChatFormatting.RED));
            }
            conjectures.add(text);
        }
        sections.add(section("library.axiomatic.conjectures", conjectures));

        List<Component> certificates = new ArrayList<>();
        for (Library.StructureCert cert : library.certificates()) {
            certificates.add(Component.literal(cert.structure() + ": " + cert.mapping()).withStyle(ChatFormatting.AQUA));
        }
        sections.add(section("library.axiomatic.certificates", certificates));

        List<Component> statuses = new ArrayList<>();
        for (Map.Entry<Formula, Library.Status> e : library.allStatuses().entrySet()) {
            Component status = switch (e.getValue()) {
                case Library.Tested t -> Component.translatable("library.axiomatic.tested", t.upTo().toString())
                        .withStyle(ChatFormatting.AQUA);
                case Library.Proven p -> Component.translatable("library.axiomatic.proven").withStyle(ChatFormatting.GOLD);
                case Library.Refuted r -> Component.translatable("library.axiomatic.refuted", r.counterexample().toString())
                        .withStyle(ChatFormatting.RED);
                case Library.Unknown u -> Component.translatable("library.axiomatic.unknown");
            };
            statuses.add(Component.literal(printer.statement(e.getKey()) + "  ").append(status));
        }
        sections.add(section("library.axiomatic.statuses", statuses));
        return sections;
    }

    private static LibraryPayload.Section section(String key, List<Component> lines) {
        if (lines.size() > LibraryPayload.MAX_LINES) {
            int more = lines.size() - LibraryPayload.MAX_LINES;
            lines = new ArrayList<>(lines.subList(0, LibraryPayload.MAX_LINES));
            lines.add(Component.translatable("library.axiomatic.truncated", more).withStyle(ChatFormatting.GRAY));
        }
        return new LibraryPayload.Section(Component.translatable(key), lines);
    }
}
