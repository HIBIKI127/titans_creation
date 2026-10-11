package com.example.titans_creation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 自定义战斗光标叠加层。 启用战斗模式时，在屏幕中心绘制一个空心圆，并在圆环外侧绘制一个三角形指针， 指针的位置代表挥动方向（指针位于正下方 =
 * 从正上方向正下方挥舞）。
 */
public class CombatCursorOverlay implements IGuiOverlay {

    public static final CombatCursorOverlay INSTANCE = new CombatCursorOverlay();

    private static final int CIRCLE_COLOR = 0xFFE0E0E0; // 浅灰色空心圆
    private static final int POINTER_COLOR = 0xFFFFAA00; // 橙色三角形指针
    private static final double CIRCLE_RADIUS = 11.0;
    private static final double POINTER_HALF_WIDTH = 4.0;
    private static final double POINTER_TIP_DIST = 8.0;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight)
    {
        if (!CombatClientState.isCombatMode())
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null)
        {
            return;
        }

        double centerX = screenWidth / 2.0;
        double centerY = screenHeight / 2.0;

        // 空心圆
        drawHollowCircle(guiGraphics, centerX, centerY, CIRCLE_RADIUS, CIRCLE_COLOR);

        if (!CombatClientState.hasDirection())
        {
            // 未确定方向：在圆心显示一个中心点（中性状态）
            int dotX = (int) Math.round(centerX);
            int dotY = (int) Math.round(centerY);
            guiGraphics.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, POINTER_COLOR);
            return;
        }

        // 三角形指针
        float angleDeg = CombatClientState.getSwingAngle();
        double angleRad = Math.toRadians(angleDeg);

        // 屏幕坐标系（y 向下），角度从正上方顺时针测量
        // angle=0   -> dir=(0,-1)  指针在正上方
        // angle=90  -> dir=(1, 0)  指针在正右方
        // angle=180 -> dir=(0, 1)  指针在正下方
        // angle=270 -> dir=(-1,0)  指针在正左方
        double dirX = Math.sin(angleRad);
        double dirY = -Math.cos(angleRad);
        // 垂直于 dir 的方向（用于构造三角形底边）
        double perpX = Math.cos(angleRad);
        double perpY = Math.sin(angleRad);

        double baseDist = CIRCLE_RADIUS + 1.0;
        double baseX = centerX + baseDist * dirX;
        double baseY = centerY + baseDist * dirY;
        double tipX = centerX + (CIRCLE_RADIUS + POINTER_TIP_DIST) * dirX;
        double tipY = centerY + (CIRCLE_RADIUS + POINTER_TIP_DIST) * dirY;
        double b1X = baseX + POINTER_HALF_WIDTH * perpX;
        double b1Y = baseY + POINTER_HALF_WIDTH * perpY;
        double b2X = baseX - POINTER_HALF_WIDTH * perpX;
        double b2Y = baseY - POINTER_HALF_WIDTH * perpY;

        fillTriangle(guiGraphics, tipX, tipY, b1X, b1Y, b2X, b2Y, POINTER_COLOR);
    }

    /**
     * 用密集的小方块点描出一个空心圆。 使用 GuiGraphics.fill 绘制，确保在不同显卡上行为一致。
     */
    private static void drawHollowCircle(GuiGraphics gui, double centerX, double centerY, double radius, int color) {
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a = 2.0 * Math.PI * i / segments;
            int x = (int) Math.round(centerX + radius * Math.cos(a)) - 1;
            int y = (int) Math.round(centerY + radius * Math.sin(a)) - 1;
            gui.fill(x, y, x + 2, y + 2, color);
        }
    }

    /**
     * 使用扫描线算法填充三角形（仅使用轴对齐的 fill 调用，保证兼容性）。
     */
    private static void fillTriangle(GuiGraphics gui,
            double ax, double ay,
            double bx, double by,
            double cx, double cy,
            int color) {
        int minY = (int) Math.floor(Math.min(Math.min(ay, by), cy));
        int maxY = (int) Math.ceil(Math.max(Math.max(ay, by), cy));

        for (int y = minY; y <= maxY; y++) {
            double[] xs = new double[2];
            int count = 0;
            count += intersect(ax, ay, bx, by, y, xs, count);
            count += intersect(bx, by, cx, cy, y, xs, count);
            count += intersect(cx, cy, ax, ay, y, xs, count);
            if (count >= 2) {
                int xLeft = (int) Math.round(Math.min(xs[0], xs[1]));
                int xRight = (int) Math.round(Math.max(xs[0], xs[1]));
                if (xRight >= xLeft) {
                    gui.fill(xLeft, y, xRight + 1, y + 1, color);
                }
            }
        }
    }

    /**
     * 计算水平扫描线 y 与边 (x1,y1)-(x2,y2) 的交点 x（半开区间，保证恰好 0 或 2 个交点）。
     */
    private static int intersect(double x1, double y1, double x2, double y2, int y, double[] xs, int idx) {
        if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
            double t = (y - y1) / (y2 - y1);
            xs[idx] = x1 + t * (x2 - x1);
            return 1;
        }
        return 0;
    }
}
