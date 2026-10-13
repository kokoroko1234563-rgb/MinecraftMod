package io.github.kokoroko1234563.axiomatic.client;

import java.util.ArrayList;
import java.util.List;

import io.github.kokoroko1234563.axiomatic.network.LibraryPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The Library: one tab per section, each a scrolling list of wrapped lines. */
public class LibraryScreen extends Screen {
    private static final int PANEL = 0xE0101418;
    private static final int BORDER = 0xFF4A5A70;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int LINE = 10;

    private final List<LibraryPayload.Section> sections;
    private final List<Button> tabs = new ArrayList<>();
    private int tab;
    private int scroll;
    private List<FormattedCharSequence> lines = List.of();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public LibraryScreen(LibraryPayload payload) {
        super(Component.translatable("library.axiomatic.title"));
        this.sections = payload.sections();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 20, 380);
        panelHeight = Math.min(height - 20, 240);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        tabs.clear();
        int x = left + 4;
        int y = top + 4;
        for (int i = 0; i < sections.size(); i++) {
            int id = i;
            Component title = sections.get(i).title();
            int w = font.width(title) + 10;
            if (x + w > left + panelWidth - 4) {
                x = left + 4;
                y += 22;
            }
            tabs.add(addRenderableWidget(Button.builder(title, b -> select(id)).bounds(x, y, w, 20).build()));
            x += w + 2;
        }
        select(Math.min(tab, Math.max(0, sections.size() - 1)));
    }

    private int contentTop() {
        return (tabs.isEmpty() ? top + 4 : tabs.getLast().getY() + 24);
    }

    private int visibleLines() {
        return Math.max(1, (top + panelHeight - 6 - contentTop()) / LINE);
    }

    private void select(int index) {
        if (index != tab) {
            scroll = 0;
        }
        tab = index;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).active = i != index;
        }
        lines = new ArrayList<>();
        List<Component> source = sections.isEmpty() ? List.of() : sections.get(index).lines();
        if (source.isEmpty()) {
            lines.add(Component.translatable("library.axiomatic.empty").getVisualOrderText());
        }
        for (Component line : source) {
            lines.addAll(font.split(line, panelWidth - 16));
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visibleLines()));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, Math.max(0, lines.size() - visibleLines()));
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, BORDER);
        g.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int y = contentTop();
        int shown = visibleLines();
        for (int i = scroll; i < Math.min(lines.size(), scroll + shown); i++) {
            g.text(font, lines.get(i), left + 8, y, TEXT, false);
            y += LINE;
        }
        if (lines.size() > shown) {
            // A scroll bar along the right edge.
            int barTop = contentTop();
            int barHeight = shown * LINE;
            int thumb = Math.max(8, barHeight * shown / lines.size());
            int offset = (barHeight - thumb) * scroll / Math.max(1, lines.size() - shown);
            g.fill(left + panelWidth - 5, barTop, left + panelWidth - 3, barTop + barHeight, 0xFF303840);
            g.fill(left + panelWidth - 5, barTop + offset, left + panelWidth - 3, barTop + offset + thumb, BORDER);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
