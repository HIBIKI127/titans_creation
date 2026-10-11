package com.example.titans_creation.client.accessory;

import com.example.titans_creation.CombatNetwork;
import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.item.accessory.AccessorySlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 装备栏中的一个槽位控件。
 *
 * <p>点击时把槽位序号发给服务端，由服务端在「光标（carried）物品」与「饰品槽位」之间搬运，
 * 与背包物品的操作手感一致。
 *
 * <p>同时支持生存模式背包（{@code InventoryScreen}）与创造模式背包（{@code CreativeModeInventoryScreen}）。
 */
public class AccessorySlotWidget extends AbstractWidget {

    /** 槽位背景贴图（18×18，本模组自备）。 */
    public static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TitansCreationMod.MODID, "textures/gui/accessory_slot.png");

    private final AbstractContainerScreen<?> screen;
    private final AccessorySlot slot;

    public AccessorySlotWidget(AbstractContainerScreen<?> screen, AccessorySlot slot) {
        super(0, 0, AccessoryLayout.SLOT_SIZE, AccessoryLayout.SLOT_SIZE, Component.empty());
        this.screen = screen;
        this.slot = slot;
        this.visible = ClientAccessoryData.isPanelOpen();
    }

    public AccessorySlot getSlot() {
        return this.slot;
    }

    // 位置随界面实时计算（配方书展开、创造模式切换标签页导致窗口位移时也能跟随）
    @Override
    public int getX() {
        return AccessoryLayout.slotX(this.screen);
    }

    @Override
    public int getY() {
        return AccessoryLayout.slotY(this.screen, this.slot);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 第一个槽位负责把面板底与标签画出来（保证在界面背景之上、槽位之下）
        if (this.slot == AccessorySlot.VALUES[0]) {
            this.renderPanel(guiGraphics);
        }
        int x = this.getX();
        int y = this.getY();
        guiGraphics.blit(SLOT_TEXTURE, x, y, 0, 0, AccessoryLayout.SLOT_SIZE, AccessoryLayout.SLOT_SIZE,
                AccessoryLayout.SLOT_SIZE, AccessoryLayout.SLOT_SIZE);

        ItemStack stack = ClientAccessoryData.get(this.slot);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 1, y + 1);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, x + 1, y + 1);
        }

        if (this.isHovering(mouseX, mouseY)) {
            guiGraphics.fill(x + 1, y + 1, x + AccessoryLayout.SLOT_SIZE - 1,
                    y + AccessoryLayout.SLOT_SIZE - 1, 0x66FFFFFF);
            Font font = Minecraft.getInstance().font;
            if (stack.isEmpty()) {
                guiGraphics.renderTooltip(font, Component.translatable(this.slot.getTranslationKey()), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    private void renderPanel(GuiGraphics guiGraphics) {
        int x = AccessoryLayout.panelX(this.screen);
        int y = AccessoryLayout.panelY(this.screen);
        int width = AccessoryLayout.PANEL_WIDTH;
        int height = AccessoryLayout.panelHeight(this.screen);
        guiGraphics.fill(x, y, x + width, y + height, 0xC0101010);
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF8B8B8B);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF8B8B8B);
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF8B8B8B);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF8B8B8B);

        Font font = Minecraft.getInstance().font;
        for (AccessorySlot each : AccessorySlot.VALUES) {
            // 按槽位显示（区分左右手 / 左右脚），而不是按大类
            Component label = Component.translatable(each.getTranslationKey());
            int labelX = AccessoryLayout.slotX(this.screen) - 4 - font.width(label);
            guiGraphics.drawString(font, label, labelX,
                    AccessoryLayout.slotY(this.screen, each) + 5, 0xFFE0E0E0, false);
        }
    }

    private boolean isHovering(int mouseX, int mouseY) {
        int x = this.getX();
        int y = this.getY();
        return mouseX >= x && mouseX < x + AccessoryLayout.SLOT_SIZE
                && mouseY >= y && mouseY < y + AccessoryLayout.SLOT_SIZE;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        CombatNetwork.CHANNEL.sendToServer(new CombatNetwork.AccessoryClickPacket(this.slot.ordinal()));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
