package com.example.titans_creation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 攻击冷却进度条叠加层。
 *
 * 在战斗模式下，屏幕右下角显示一个进度条，用于判断攻击间隔：
 * - 挥舞进行中：红色填充（从左到右增长），表示正在挥舞
 * - 收招冷却中：黄色填充（从左到右增长），表示正在冷却
 * - 冷却完毕（可攻击）：绿色满条闪烁提示
 *
 * 位置在屏幕右下角，避开字幕显示区域（字幕在底部居中）。
 */
public class CombatCooldownOverlay implements IGuiOverlay {

    public static final CombatCooldownOverlay INSTANCE = new CombatCooldownOverlay();

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 70; // 避开字幕区域（字幕约在底部 50px 居中）

    private static final int COLOR_BG = 0x80000000;       // 半透明黑色背景
    private static final int COLOR_SWING = 0xFFFF4444;     // 红色：挥舞中
    private static final int COLOR_COOLDOWN = 0xFFFFAA00;  // 橙色：冷却中
    private static final int COLOR_READY = 0xFF44FF44;     // 绿色：就绪
    private static final int COLOR_BORDER = 0xFFCCCCCC;    // 边框

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!CombatClientState.isCombatMode()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        int x = screenWidth - BAR_WIDTH - MARGIN_RIGHT;
        int y = screenHeight - MARGIN_BOTTOM;

        boolean busy = CombatClientState.isBusy();
        boolean swingActive = CombatClientState.isSwingActive();
        int swingTick = CombatClientState.getSwingTick();

        // 背景框
        guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, COLOR_BG);

        // 边框
        guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y, COLOR_BORDER);
        guiGraphics.fill(x - 1, y + BAR_HEIGHT, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, COLOR_BORDER);
        guiGraphics.fill(x - 1, y, x, y + BAR_HEIGHT, COLOR_BORDER);
        guiGraphics.fill(x + BAR_WIDTH, y, x + BAR_WIDTH + 1, y + BAR_HEIGHT, COLOR_BORDER);

        if (!busy) {
            // 就绪：满绿条
            guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_READY);
        } else if (swingActive) {
            // 挥舞中：红色进度条（挥舞进度）
            float progress = CombatClientState.getSwingProgress();
            int fillWidth = Math.max(1, (int) (BAR_WIDTH * progress));
            guiGraphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, COLOR_SWING);
        } else {
            // 冷却中：橙色进度条（冷却进度）
            float progress = CombatClientState.getCooldownProgress();
            int fillWidth = Math.max(1, (int) (BAR_WIDTH * progress));
            guiGraphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, COLOR_COOLDOWN);
        }
    }
}
