package io.github.kokoroko1234563.axiomatic.block;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Library: the world's record of mathematics. Using it lists definitions, named theorems with what
 * they depend on, open and failed conjectures (with post-mortems), and tested statements. Sneak to
 * see only conjectures and statuses.
 */
public class LibraryBlock extends Block {
    public LibraryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            report(AxiomaticData.get(server.getServer()).state(), player, !player.isSecondaryUseActive());
        }
        return InteractionResult.SUCCESS;
    }

    static void report(MathState state, Player player, boolean full) {
        Printer printer = new Printer(state.signature());
        Library library = state.library();
        header(player, "library.axiomatic.header");
        if (full) {
            header(player, "library.axiomatic.definitions");
            for (Definition d : state.signature().all()) {
                String kind = switch (d) {
                    case Definition.Predicate p -> "property";
                    case Definition.PrimitiveRecursive r -> "recursive";
                    case Definition.Explicit e -> "explicit";
                    case Definition.Abstract a -> "abstract";
                    case Definition.GeneralRecursive g -> "general";
                };
                line(player, Component.literal(" " + d.name() + "(" + String.join(", ", d.params()) + ")")
                        .append(Component.translatable("library.axiomatic.kind." + kind).withStyle(ChatFormatting.GRAY)));
            }
            header(player, "library.axiomatic.theorems");
            for (Map.Entry<String, Theorem> e : library.theorems().entrySet()) {
                Theorem t = e.getValue();
                long uses = t.dependencies().size();
                line(player, Component.literal(" " + e.getKey() + ": ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(printer.statement(t.formula())).withStyle(ChatFormatting.WHITE))
                        .append(Component.translatable("library.axiomatic.uses", uses, t.steps()).withStyle(ChatFormatting.GRAY)));
            }
        }
        header(player, "library.axiomatic.conjectures");
        for (Library.Conjecture c : library.conjectures()) {
            ChatFormatting colour = switch (c.state()) {
                case ACTIVE -> ChatFormatting.LIGHT_PURPLE;
                case DORMANT -> ChatFormatting.DARK_PURPLE;
                case PROVEN -> ChatFormatting.GOLD;
                case REFUTED -> ChatFormatting.RED;
            };
            Component text = Component.literal(" " + c.name() + ": " + printer.statement(c.statement()) + " ")
                    .withStyle(colour)
                    .append(Component.translatable("library.axiomatic.state." + c.state().name().toLowerCase()));
            if (c.counterexample() != null) {
                text = text.copy().append(Component.translatable("library.axiomatic.postmortem", c.counterexample().toString())
                        .withStyle(ChatFormatting.RED));
            }
            line(player, text);
        }
        header(player, "library.axiomatic.certificates");
        for (Library.StructureCert cert : library.certificates()) {
            line(player, Component.literal(" " + cert.structure() + ": " + cert.mapping()).withStyle(ChatFormatting.AQUA));
        }
        header(player, "library.axiomatic.statuses");
        for (Map.Entry<Formula, Library.Status> e : library.allStatuses().entrySet()) {
            Component status = switch (e.getValue()) {
                case Library.Tested t -> Component.translatable("library.axiomatic.tested", t.upTo().toString())
                        .withStyle(ChatFormatting.AQUA);
                case Library.Proven p -> Component.translatable("library.axiomatic.proven").withStyle(ChatFormatting.GOLD);
                case Library.Refuted r -> Component.translatable("library.axiomatic.refuted", r.counterexample().toString())
                        .withStyle(ChatFormatting.RED);
                case Library.Unknown u -> Component.translatable("library.axiomatic.unknown");
            };
            line(player, Component.literal(" " + printer.statement(e.getKey()) + "  ").append(status));
        }
    }

    private static void header(Player player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.BOLD, ChatFormatting.BLUE));
    }

    private static void line(Player player, Component text) {
        player.sendSystemMessage(text);
    }
}
