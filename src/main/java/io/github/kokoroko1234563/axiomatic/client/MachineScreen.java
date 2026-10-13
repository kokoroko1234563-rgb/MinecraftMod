package io.github.kokoroko1234563.axiomatic.client;

import io.github.kokoroko1234563.axiomatic.machine.MachineBlockEntity;
import io.github.kokoroko1234563.axiomatic.machine.MachineLayout;
import io.github.kokoroko1234563.axiomatic.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen for every machine, drawn with plain rectangles so it needs no texture: slots from
 * the layout, an energy bar on the left, a progress arrow, the status line and any buttons.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int ENERGY = 0xFFD4302B;
    private static final int PROGRESS = 0xFF3F76E4;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        var buttons = menu.machine().layout().buttons();
        for (int i = 0; i < buttons.size(); i++) {
            int id = i;
            addRenderableWidget(Button.builder(Component.translatable(buttons.get(i)),
                            b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                    .bounds(leftPos + 108, topPos + 58 - 14 * (buttons.size() - 1 - i), 62, 14).build());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos;
        int y = topPos;
        bevel(g, x, y, imageWidth, imageHeight, PANEL_LIGHT, PANEL_DARK, PANEL);
        MachineBlockEntity m = menu.machine();
        for (MachineLayout.SlotSpec s : m.layout().slots()) {
            slot(g, x + s.x() - 1, y + s.y() - 1);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 7 + col * 18, y + 83 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 7 + col * 18, y + 141);
        }
        // Energy bar.
        bevel(g, x + 8, y + 17, 10, 52, PANEL_DARK, PANEL_LIGHT, 0xFF2B2B2B);
        int filled = m.capacity() == 0 ? 0 : (int) (50 * Math.min(1.0, (double) m.energy() / m.capacity()));
        g.fill(x + 9, y + 68 - filled, x + 17, y + 68, ENERGY);
        // Progress arrow between input and output columns.
        g.fill(x + 79, y + 38, x + 103, y + 48, PANEL_DARK);
        g.fill(x + 79, y + 38, x + 79 + (int) (24 * m.progress()), y + 48, PROGRESS);
    }

    private static void slot(GuiGraphicsExtractor g, int x, int y) {
        bevel(g, x, y, 18, 18, 0xFF373737, PANEL_LIGHT, SLOT);
    }

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int topLeft, int bottomRight, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w - 1, y + 1, topLeft);
        g.fill(x, y, x + 1, y + h - 1, topLeft);
        g.fill(x + 1, y + h - 1, x + w, y + h, bottomRight);
        g.fill(x + w - 1, y + 1, x + w, y + h, bottomRight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        MachineBlockEntity m = menu.machine();
        g.text(font, m.info(), 22, 18, 0xFF404040, false);
        // Status sits left of the buttons, under the slots.
        var wrapped = font.split(m.status(), 84);
        for (int i = 0; i < Math.min(2, wrapped.size()); i++) {
            g.text(font, wrapped.get(i), 22, 54 + i * 9, 0xFF404040, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        MachineBlockEntity m = menu.machine();
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.container == m) {
            String label = m.layout().slots().get(hoveredSlot.index).label();
            if (label != null) {
                g.setTooltipForNextFrame(Component.translatable(label), mouseX, mouseY);
            }
        }
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 18 && mouseY >= topPos + 17 && mouseY < topPos + 69) {
            g.setTooltipForNextFrame(Component.translatable("tooltip.axiomatic.energy", m.energy(), m.capacity()),
                    mouseX, mouseY);
        }
    }
}
