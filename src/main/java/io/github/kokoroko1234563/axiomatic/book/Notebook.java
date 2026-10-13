package io.github.kokoroko1234563.axiomatic.book;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Mathematician's Notebook, given on first join; the only item the mod hands out.
 *
 * <p>For now it is a vanilla written book whose pages are translation keys
 * ({@code notebook.axiomatic.page.N} in the language file), so the text can be edited without
 * code. The design doc's Modonomicon book replaces it once that library can be downloaded.
 */
public final class Notebook {
    private Notebook() {}

    public static final int PAGES = 29;
    private static final String GIVEN = "axiomatic_notebook_given";

    public static ItemStack create() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 1; i <= PAGES; i++) {
            pages.add(Filterable.passThrough(Component.translatable("notebook.axiomatic.page." + i)));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("The Mathematician's Notebook"), "Axiomatic", 0, pages, true));
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
        if (!player.getInventory().add(create())) {
            player.drop(create(), false);
        }
    }
}
