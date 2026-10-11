package com.example.titans_creation.client.accessory;

import com.example.titans_creation.TitansCreationMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 装备栏开关按钮：生存模式背包中位于人物预览窗口右上角，创造模式背包中位于面板标题栏右侧；
 * 点击显示 / 关闭左侧装备栏。
 */
public class AccessoryToggleButton extends AbstractWidget {

    /** 按钮图标贴图（16×16，本模组自备）。 */
    public static final ResourceLocation ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TitansCreationMod.MODID, "textures/gui/accessory_toggle.png");

    private final AbstractContainerScreen<?> screen;

    public AccessoryToggleButton(AbstractContainerScreen<?> screen) {
        super(0, 0, AccessoryLayout.TOGGLE_SIZE, AccessoryLayout.TOGGLE_SIZE,
                Component.translatable("titans_creation.accessory.toggle"));
        this.screen = screen;
    }

    @Override
    public int getX() {
        return AccessoryLayout.toggleX(this.screen);
    }

    @Override
    public int getY() {
        return AccessoryLayout.toggleY(this.screen);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int size = AccessoryLayout.TOGGLE_SIZE;
        boolean open = ClientAccessoryData.isPanelOpen();
        boolean hovered = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;

        guiGraphics.fill(x, y, x + size, y + size, open ? 0xFF6E6E6E : 0xFF3C3C3C);
        guiGraphics.fill(x, y, x + size, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y + size - 1, x + size, y + size, 0xFF8B8B8B);
        guiGraphics.fill(x, y, x + 1, y + size, 0xFFFFFFFF);
        guiGraphics.fill(x + size - 1, y, x + size, y + size, 0xFF8B8B8B);
        guiGraphics.blit(ICON_TEXTURE, x, y, 0, 0, size, size, size, size);
        if (hovered) {
            guiGraphics.fill(x, y, x + size, y + size, 0x40FFFFFF);
            Font font = Minecraft.getInstance().font;
            guiGraphics.renderTooltip(font, Component.translatable(open
                    ? "titans_creation.accessory.toggle.hide"
                    : "titans_creation.accessory.toggle.show"), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        boolean open = !ClientAccessoryData.isPanelOpen();
        ClientAccessoryData.setPanelOpen(open);
        AccessoryInventoryHook.setSlotsVisible(this.screen, open);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
