package io.github.kokoroko1234563.axiomatic.book;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import io.github.kokoroko1234563.axiomatic.Axiomatic;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Mathematician's Notebook, given on first join; the only item the mod hands out.
 *
 * <p>For now it is a vanilla written book whose pages are translation keys
 * ({@code notebook.axiomatic.page.N} in the language file), so the text can be edited without
 * code. Pages unlock as their prerequisite advancement is earned
 * ({@code data/axiomatic/notebook_pages.json}, generated with the pages by tools/gen_assets.py):
 * the Notebooks a player carries are rebuilt on login and whenever they earn an Axiomatic
 * advancement. The design doc's Modonomicon book replaces it once that library can be downloaded.
 */
public final class Notebook {
    private Notebook() {}

    public static final int PAGES = 37;
    private static final String TITLE = "The Mathematician's Notebook";
    private static final String AUTHOR = "Axiomatic";
    private static final String GIVEN = "axiomatic_notebook_given";

    /** For each page (index 0 = page 1), the advancement that unlocks it, or null. */
    private static final List<Identifier> REQUIRES = loadGates();

    private static List<Identifier> loadGates() {
        List<Identifier> out = new ArrayList<>();
        for (int i = 0; i < PAGES; i++) {
            out.add(null);
        }
        var in = Notebook.class.getResourceAsStream("/data/axiomatic/notebook_pages.json");
        if (in == null) {
            return out;
        }
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonArray gates = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement e : gates) {
                int page = e.getAsJsonObject().get("page").getAsInt();
                JsonElement req = e.getAsJsonObject().get("requires");
                if (page >= 1 && page <= PAGES && req != null && !req.isJsonNull()) {
                    out.set(page - 1, Identifier.parse(req.getAsString()));
                }
            }
        } catch (Exception e) {
            Axiomatic.LOGGER.warn("Could not read the Notebook's page gates; showing every page", e);
        }
        return out;
    }

    /** The whole Notebook, every page unlocked. */
    public static ItemStack create() {
        return create(id -> true);
    }

    /** The Notebook with the pages whose advancement {@code earned} accepts. */
    public static ItemStack create(Predicate<Identifier> earned) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content(earned));
        return book;
    }

    private static WrittenBookContent content(Predicate<Identifier> earned) {
        List<Filterable<Component>> pages = new ArrayList<>();
        boolean locked = false;
        for (int i = 1; i <= PAGES; i++) {
            Identifier req = REQUIRES.get(i - 1);
            if (req == null || earned.test(req)) {
                pages.add(Filterable.passThrough(Component.translatable("notebook.axiomatic.page." + i)));
            } else {
                locked = true;
            }
        }
        if (locked) {
            pages.add(Filterable.passThrough(Component.translatable("notebook.axiomatic.more")));
        }
        return new WrittenBookContent(Filterable.passThrough(TITLE), AUTHOR, 0, pages, true);
    }

    public static boolean isNotebook(ItemStack stack) {
        WrittenBookContent c = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return stack.is(Items.WRITTEN_BOOK) && c != null && c.title().raw().equals(TITLE) && c.author().equals(AUTHOR);
    }

    /** What this player has unlocked. */
    private static Predicate<Identifier> earnedBy(ServerPlayer player) {
        return id -> {
            AdvancementHolder adv = player.level().getServer().getAdvancements().get(id);
            return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
        };
    }

    /** Rebuilds every Notebook in the player's inventory with the pages they have unlocked. */
    public static void refresh(ServerPlayer player) {
        WrittenBookContent content = content(earnedBy(player));
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isNotebook(stack)) {
                stack.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
            }
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persisted = player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBooleanOr(GIVEN, false)) {
            persisted.putBoolean(GIVEN, true);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            ItemStack book = player instanceof ServerPlayer sp ? create(earnedBy(sp)) : create();
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        }
        if (player instanceof ServerPlayer sp) {
            refresh(sp);
        }
    }

    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getAdvancement().id().getNamespace().equals(Axiomatic.MODID)
                && event.getEntity() instanceof ServerPlayer sp) {
            refresh(sp);
        }
    }
}
