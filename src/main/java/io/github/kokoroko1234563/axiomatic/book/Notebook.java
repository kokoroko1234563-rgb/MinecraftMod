package io.github.kokoroko1234563.axiomatic.book;

import com.klikli_dev.modonomicon.registry.DataComponentRegistry;
import com.klikli_dev.modonomicon.registry.ItemRegistry;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Mathematician's Notebook, given on first join; the only item the mod hands out.
 *
 * <p>It is a Modonomicon book ({@code data/axiomatic/modonomicon/books/notebook}), generated
 * with its research gates ({@code data/axiomatic/modonomicon/research/notebook}) and text (the
 * language file) by tools/gen_assets.py from the NOTEBOOK pages. Entries open as the player earns
 * the matching advancements, and machine entries show their recipes.
 */
public final class Notebook {
    private Notebook() {}

    public static final Identifier BOOK = Identifier.fromNamespaceAndPath(Axiomatic.MODID, "notebook");
    private static final String GIVEN = "axiomatic_notebook_given";

    public static ItemStack create() {
        ItemStack book = new ItemStack(ItemRegistry.MODONOMICON.get());
        book.set(DataComponentRegistry.BOOK_ID.get(), BOOK);
        return book;
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        if (persisted.getBooleanOr(GIVEN, false)) {
            return;
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = create();
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }
}
