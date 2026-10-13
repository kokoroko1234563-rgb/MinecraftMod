package io.github.kokoroko1234563.axiomatic.item;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.kokoroko1234563.axiomatic.core.logic.Formula;
import io.github.kokoroko1234563.axiomatic.core.logic.Kernel;
import io.github.kokoroko1234563.axiomatic.core.logic.Printer;
import io.github.kokoroko1234563.axiomatic.core.logic.Theorem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A statement item's content: a formula, and if it is proven, the theorem's bookkeeping (open
 * hypotheses, dependencies, proof size). The display strings are rendered on the server, where
 * the world's definitions are, so the client can show them without knowing those definitions.
 */
public record StatementData(Formula formula, boolean proven, List<Hyp> hypotheses, List<String> dependencies,
        long steps, String display) {

    /** An open Assumption Chamber hypothesis the statement depends on. */
    public record Hyp(long id, Formula formula, String display) {
        public static final Codec<Hyp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Hyp::id),
                MathCodecs.FORMULA.fieldOf("formula").forGetter(Hyp::formula),
                Codec.STRING.fieldOf("display").forGetter(Hyp::display)).apply(i, Hyp::new));
    }

    public static final Codec<StatementData> CODEC = RecordCodecBuilder.create(i -> i.group(
            MathCodecs.FORMULA.fieldOf("formula").forGetter(StatementData::formula),
            Codec.BOOL.fieldOf("proven").forGetter(StatementData::proven),
            Hyp.CODEC.listOf().optionalFieldOf("hypotheses", List.of()).forGetter(StatementData::hypotheses),
            Codec.STRING.listOf().optionalFieldOf("dependencies", List.of()).forGetter(StatementData::dependencies),
            Codec.LONG.optionalFieldOf("steps", 0L).forGetter(StatementData::steps),
            Codec.STRING.fieldOf("display").forGetter(StatementData::display)).apply(i, StatementData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatementData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public StatementData {
        hypotheses = List.copyOf(hypotheses);
        dependencies = dependencies.stream().sorted().toList();
    }

    /** An unproven statement, as made by the Equation or Property Bench. */
    public static StatementData unproven(Formula f, Printer printer) {
        return new StatementData(f, false, List.of(), List.of(), 0, printer.statement(f));
    }

    public static StatementData of(Theorem thm, Printer printer) {
        List<Hyp> hyps = thm.hypotheses().stream()
                .map(h -> new Hyp(h.id(), h.formula(), printer.formula(h.formula()))).toList();
        // With open hypotheses the free variables are not all universal, so no leading ∀.
        String display = thm.isClosed() ? printer.statement(thm.formula()) : printer.formula(thm.formula());
        return new StatementData(thm.formula(), true, hyps, List.copyOf(thm.dependencies()), thm.steps(), display);
    }

    /** The theorem this item proves, or null for an unproven statement. */
    public Theorem theorem(Kernel kernel) {
        if (!proven) {
            return null;
        }
        return kernel.restoreSaved(formula,
                hypotheses.stream().map(h -> new Theorem.Hypothesis(h.id(), h.formula())).toList(),
                Set.copyOf(dependencies), steps);
    }

    public boolean isConjectural() {
        return dependencies.stream().anyMatch(d -> d.startsWith("conjecture:"));
    }

    public void addToTooltip(Consumer<Component> out) {
        out.accept(Component.literal(display).withStyle(ChatFormatting.WHITE));
        if (!proven) {
            out.accept(Component.translatable("tooltip.axiomatic.unproven").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isConjectural()) {
            out.accept(Component.translatable("tooltip.axiomatic.proven_conjectural").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            out.accept(Component.translatable("tooltip.axiomatic.proven", steps).withStyle(ChatFormatting.GOLD));
        }
        for (Hyp h : hypotheses) {
            out.accept(Component.translatable("tooltip.axiomatic.assuming", h.display(), h.id())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
