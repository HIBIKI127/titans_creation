package com.example.titans_creation.client.accessory;

import com.example.titans_creation.item.accessory.AccessorySlot;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/**
 * 装备栏 UI 的布局计算。
 *
 * <p>位置全部相对当前容器界面（{@code AbstractContainerScreen}）的左上角计算，
 * 因此打开配方书、切换创造模式标签页、改变窗口尺寸时装备栏都会跟着移动。
 *
 * <ul>
 *   <li>装备栏面板位于界面窗口<b>左侧</b>；</li>
 *   <li><b>生存模式背包</b>：开关按钮位于人物预览窗口的<b>右上角</b>；</li>
 *   <li><b>创造模式背包</b>：该界面尺寸为 195×136，人物预览只在「物品栏」标签页显示且位置不同，
 *       因此按钮放在面板自己的标题栏右侧（面板整体下移，给按钮留出空间）。</li>
 * </ul>
 */
public final class AccessoryLayout {

    /** 槽位边长。 */
    public static final int SLOT_SIZE = 18;
    /** 槽位间距（含 4 像素空隙）。 */
    public static final int SLOT_SPACING = 22;
    /** 面板宽度。 */
    public static final int PANEL_WIDTH = 58;
    /** 面板相对窗口顶部的偏移。 */
    public static final int PANEL_TOP = 6;
    /** 开关按钮边长。 */
    public static final int TOGGLE_SIZE = 16;
    /** 创造模式：面板顶部为按钮预留的高度。 */
    private static final int CREATIVE_HEADER = 22;

    private AccessoryLayout() {
    }

    /** 是否为创造模式背包界面。 */
    public static boolean isCreative(AbstractContainerScreen<?> screen) {
        return screen instanceof CreativeModeInventoryScreen;
    }

    /** 面板左边界（贴住界面窗口左侧，窗口太窄时至少留 2 像素）。 */
    public static int panelX(AbstractContainerScreen<?> screen) {
        return Math.max(2, screen.getGuiLeft() - PANEL_WIDTH - 4);
    }

    public static int panelY(AbstractContainerScreen<?> screen) {
        return screen.getGuiTop() + PANEL_TOP;
    }

    /** 面板顶部为按钮预留的高度（仅创造模式）。 */
    private static int headerHeight(AbstractContainerScreen<?> screen) {
        return isCreative(screen) ? CREATIVE_HEADER : 0;
    }

    public static int panelHeight(AbstractContainerScreen<?> screen) {
        return AccessorySlot.COUNT * SLOT_SPACING + 8 + headerHeight(screen);
    }

    /** 槽位列（面板内靠右）。 */
    public static int slotX(AbstractContainerScreen<?> screen) {
        return panelX(screen) + PANEL_WIDTH - SLOT_SIZE - 4;
    }

    /** 某个槽位的 Y 坐标。 */
    public static int slotY(AbstractContainerScreen<?> screen, AccessorySlot slot) {
        return panelY(screen) + 4 + headerHeight(screen) + slot.ordinal() * SLOT_SPACING;
    }

    /** 开关按钮 X。 */
    public static int toggleX(AbstractContainerScreen<?> screen) {
        if (isCreative(screen)) {
            return panelX(screen) + PANEL_WIDTH - TOGGLE_SIZE - 4;
        }
        // 生存模式背包：人物预览窗口（guiLeft+26 ~ +80）的右上角
        return screen.getGuiLeft() + 80;
    }

    /** 开关按钮 Y。 */
    public static int toggleY(AbstractContainerScreen<?> screen) {
        if (isCreative(screen)) {
            return panelY(screen) + 3;
        }
        return screen.getGuiTop() + 6;
    }
}
