package com.example.titans_creation.item.accessory;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 向 Curios 槽位写入的兼容类（快速装备用）。
 *
 * <p><b>本类引用了 Curios 的 API，只在 {@link CuriosCompat#isLoaded()} 为 true 时被加载</b>
 * （调用点见 {@link AccessoryManager#tryEquip}），未安装 Curios 时永远不会加载。
 *
 * <p>槽位对应关系与数据包保持一致：手套 → {@code hands}、鞋子 → {@code feet}、背部 → {@code back}。
 */
public final class CuriosEquipHelper {

    private CuriosEquipHelper() {
    }

    /** 饰品大类对应的 Curios 槽位 id。 */
    public static String slotIdFor(AccessoryType type) {
        return switch (type) {
            case GLOVES -> "hands";
            case SHOES -> "feet";
            case BACK -> "back";
        };
    }

    /** 本模组槽位在 Curios 同类型槽位中的序号（左 = 0，右 = 1）。 */
    public static int indexFor(AccessorySlot slot) {
        return switch (slot) {
            case GLOVES_LEFT, SHOES_LEFT -> 0;
            case GLOVES_RIGHT, SHOES_RIGHT -> 1;
            case BACK -> 0;
        };
    }

    /** 读取 Curios 中本模组对应槽位里的物品；没有则返回空。 */
    public static ItemStack getEquipped(Player player, AccessorySlot slot) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ICurioStacksHandler stacksHandler = handler.getCurios().get(slotIdFor(slot.getType()));
        if (stacksHandler == null) {
            return ItemStack.EMPTY;
        }
        IDynamicStackHandler stacks = stacksHandler.getStacks();
        int index = indexFor(slot);
        return index < stacks.getSlots() ? stacks.getStackInSlot(index) : ItemStack.EMPTY;
    }

    /**
     * 尝试把饰品装进 Curios 的对应槽位（第一个空位）。
     *
     * @return 是否成功；对应槽位已满时返回 false
     */
    public static boolean tryEquip(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof AccessoryItem accessory)) {
            return false;
        }
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return false;
        }
        String slotId = slotIdFor(accessory.accessoryType());
        ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
        if (stacksHandler == null) {
            return false;
        }
        IDynamicStackHandler stacks = stacksHandler.getStacks();
        for (int index = 0; index < stacks.getSlots(); index++) {
            if (stacks.getStackInSlot(index).isEmpty()) {
                handler.setEquippedCurio(slotId, index, stack.copy());
                return true;
            }
        }
        // 对应栏位已满
        return false;
    }
}
