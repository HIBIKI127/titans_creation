package com.example.titans_creation.client.accessory;

import javax.annotation.Nullable;

import com.example.titans_creation.CombatNetwork;
import com.example.titans_creation.TitansCreationMod;
import com.example.titans_creation.item.accessory.AccessoryItem;
import com.example.titans_creation.item.accessory.AccessorySlot;
import com.example.titans_creation.item.accessory.AccessoryTags;
import com.example.titans_creation.item.accessory.CuriosCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把装备栏注入原版背包界面，并提供饰品的快捷操作。
 *
 * <p>注入内容：开关按钮 + 左侧的 5 个饰品槽位。支持两种界面：
 * <ul>
 *   <li>生存模式背包 {@link InventoryScreen}（按钮在人物预览右上角）；</li>
 *   <li>创造模式背包 {@link CreativeModeInventoryScreen}（另一个界面类，尺寸 195×136，
 *       人物预览只在「物品栏」标签页出现，因此按钮放在面板标题栏右侧）。</li>
 * </ul>
 *
 * <p>快捷操作：
 * <ul>
 *   <li><b>Shift+左键</b>点击背包里的饰品 → 快速穿戴（服务端判定对应栏位是否有空位）；</li>
 *   <li>面板空白区域（非槽位）的点击一律吞掉 —— 装备栏在窗口之外，若交给原版容器处理，
 *       会被当成"点击窗口外"而把光标上的物品丢出去。</li>
 * </ul>
 *
 * <p><b>与 Curios 的兼容</b>：安装了 Curios 时不注入我们自己的 UI，物品由 Curios 槽位承载
 * （Shift+左键同样有效，服务端会装进 Curios 的对应槽位）。
 */
@Mod.EventBusSubscriber(modid = TitansCreationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AccessoryInventoryHook {

    private AccessoryInventoryHook() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        if (!CuriosCompat.useOwnAccessoryUi()) {
            return;
        }
        event.addListener(new AccessoryToggleButton(containerScreen));
        for (AccessorySlot slot : AccessorySlot.VALUES) {
            event.addListener(new AccessorySlotWidget(containerScreen, slot));
        }
    }

    /** 鼠标按下前：处理面板区域的点击吞掉，以及 Shift+左键快速穿戴。 */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        // ① 面板空白处（非槽位）的点击吞掉，避免原版容器把手上的物品丢出去
        if (CuriosCompat.useOwnAccessoryUi() && ClientAccessoryData.isPanelOpen()
                && isInsidePanel(screen, mouseX, mouseY) && !isOverAccessorySlot(screen, mouseX, mouseY)) {
            event.setCanceled(true);
            return;
        }

        // ② Shift+左键点背包里的饰品 → 快速穿戴
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getButton() != 0 || !Screen.hasShiftDown() || minecraft.player == null) {
            return;
        }
        Slot slot = slotUnderMouse(screen, mouseX, mouseY);
        if (slot == null || !slot.hasItem() || !(slot.container instanceof Inventory)) {
            return;
        }
        if (!isAccessoryItem(slot.getItem())) {
            return;
        }
        event.setCanceled(true);
        CombatNetwork.CHANNEL.sendToServer(new CombatNetwork.AccessoryEquipPacket(slot.index));
    }

    /** 按展开状态显示/隐藏全部槽位控件。 */
    public static void setSlotsVisible(Screen screen, boolean visible) {
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AccessorySlotWidget widget) {
                widget.visible = visible;
            }
        }
    }

    // ==================== 工具 ====================

    /** 该物品是否属于本模组的饰品（实现了接口，或落在饰品标签内）。 */
    private static boolean isAccessoryItem(ItemStack stack) {
        return stack.getItem() instanceof AccessoryItem
                || stack.is(AccessoryTags.GLOVES)
                || stack.is(AccessoryTags.SHOES)
                || stack.is(AccessoryTags.BACK);
    }

    @Nullable
    private static Slot slotUnderMouse(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isInsidePanel(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int x = AccessoryLayout.panelX(screen);
        int y = AccessoryLayout.panelY(screen);
        return mouseX >= x && mouseX < x + AccessoryLayout.PANEL_WIDTH
                && mouseY >= y && mouseY < y + AccessoryLayout.panelHeight(screen);
    }

    private static boolean isOverAccessorySlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int x = AccessoryLayout.slotX(screen);
        for (AccessorySlot slot : AccessorySlot.VALUES) {
            int y = AccessoryLayout.slotY(screen, slot);
            if (mouseX >= x && mouseX < x + AccessoryLayout.SLOT_SIZE
                    && mouseY >= y && mouseY < y + AccessoryLayout.SLOT_SIZE) {
                return true;
            }
        }
        return false;
    }
}
