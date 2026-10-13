package io.github.kokoroko1234563.axiomatic.item;

import io.github.kokoroko1234563.axiomatic.core.graph.GraphCodec;
import io.github.kokoroko1234563.axiomatic.core.graph.PointedGraph;
import io.github.kokoroko1234563.axiomatic.core.logic.Term;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Graph Lens: shows the pointed graph of the object in your other hand, node by node, with
 * shared nodes listed once. A numeral shows as its chain of S nodes (summarised when long).
 */
public class GraphLensItem extends Item {
    private static final int MAX_LINES = 24;

    public GraphLensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        PointedGraph graph = graphOf(other);
        if (graph == null) {
            player.sendSystemMessage(Component.translatable("message.axiomatic.lens_nothing"));
            return InteractionResult.SUCCESS;
        }
        player.sendSystemMessage(Component.translatable("message.axiomatic.lens_header", graph.nodes().size())
                .withStyle(ChatFormatting.BLUE));
        int shown = 0;
        for (int i = graph.nodes().size() - 1; i >= 0 && shown < MAX_LINES; i--, shown++) {
            PointedGraph.Node n = graph.nodes().get(i);
            String marker = i == graph.point() ? "● " : "  ";
            player.sendSystemMessage(Component.literal(marker + "#" + i + " " + n.label()
                    + (n.children().isEmpty() ? "" : " → " + n.children())));
        }
        if (shown < graph.nodes().size()) {
            player.sendSystemMessage(Component.translatable("message.axiomatic.lens_more", graph.nodes().size() - shown)
                    .withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    private static PointedGraph graphOf(ItemStack stack) {
        StatementData st = MathItems.statement(stack);
        if (st != null) {
            return GraphCodec.encode(st.formula());
        }
        if (stack.is(io.github.kokoroko1234563.axiomatic.registry.ModRegistries.NUMERAL_ITEM.get())) {
            var value = NumeralItem.data(stack).value();
            // Long chains are summarised: the lens draws at most a few hundred S nodes.
            return GraphCodec.encode(Term.numeral(value.min(java.math.BigInteger.valueOf(512)).intValue()));
        }
        Term t = MathItems.termOf(stack);
        return t == null ? null : GraphCodec.encode(t);
    }
}
